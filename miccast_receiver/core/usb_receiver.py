# ══════════════════════════════════════════════════════════════════════════════
#  core/usb_receiver.py
#  USB 接收逻辑：通过 ADB reverse 转发 + TCP 接收
# ══════════════════════════════════════════════════════════════════════════════

import os
import socket
import struct
import subprocess
import sys
import threading
import time
from typing import Callable

from .constants import JITTER_USB_DEFAULT
from .jitter import JitterBuffer
from .audio import AudioOutput, OpusDecoder


def get_project_root() -> str:
    """返回项目根目录（main.py 所在目录，兼容 PyInstaller 打包后的路径）。"""
    if getattr(sys, "frozen", False):
        # PyInstaller 打包后，可执行文件所在目录即为根目录
        return os.path.dirname(sys.executable)
    # 开发模式：core/ 的上一级
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def find_adb() -> str:
    """
    查找 adb 可执行文件（优先级从高到低）：
    1. <项目根目录>/platform-tools/adb.exe
    2. 系统 PATH 中的 adb
    3. fallback "adb"
    """
    bundled = os.path.normpath(
        os.path.join(get_project_root(), "platform-tools", "adb.exe")
    )
    if os.path.isfile(bundled):
        return bundled
    try:
        cmd = "where" if sys.platform == "win32" else "which"
        r = subprocess.run([cmd, "adb"], capture_output=True, text=True, timeout=5)
        if r.returncode == 0:
            path = r.stdout.strip().splitlines()[0].strip()
            if path:
                return path
    except Exception:
        pass
    return "adb"


def _recv_exact(conn: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            raise ConnectionResetError("连接断开")
        buf += chunk
    return buf


def _parse_packet(data: bytes) -> tuple[int | None, bytes | None]:
    if len(data) < 4:
        return None, None
    seq = struct.unpack(">I", data[:4])[0]
    return seq, data[4:]


class AdbHelper:
    """封装 ADB 命令调用。"""

    def __init__(self, adb_path: str):
        self.path = adb_path

    def check_devices(self) -> list[str]:
        try:
            r = subprocess.run(
                [self.path, "devices"],
                capture_output=True, text=True, timeout=6
            )
            lines = [l.strip() for l in r.stdout.splitlines()
                     if l.strip() and "List of devices" not in l]
            return [l for l in lines if "\t" in l and "offline" not in l]
        except Exception:
            return []

    def setup_reverse(self, port: int) -> tuple[bool, str]:
        try:
            r = subprocess.run(
                [self.path, "reverse", f"tcp:{port}", f"tcp:{port}"],
                capture_output=True, text=True, timeout=10
            )
            if r.returncode == 0:
                return True, f"ADB 转发成功：手机:{port} → PC:{port}"
            return False, f"ADB 转发失败：{r.stderr.strip()}"
        except FileNotFoundError:
            return False, f"找不到 adb（路径：{self.path}）"
        except subprocess.TimeoutExpired:
            return False, "adb reverse 超时"

    def remove_reverse(self, port: int):
        try:
            subprocess.run(
                [self.path, "reverse", "--remove", f"tcp:{port}"],
                capture_output=True, timeout=5
            )
        except Exception:
            pass


class USBReceiver:
    """USB 音频接收器（ADB reverse + TCP）。"""

    def __init__(
        self,
        port:          int,
        device_index:  int | None,
        jitter_frames: int,
        adb_path:      str,
        on_log:        Callable[[str], None],
        on_status:     Callable[[str], None],
        on_stats:      Callable[[str], None],
    ):
        self.port          = port
        self.device_index  = device_index
        self.jitter_frames = jitter_frames
        self._adb          = AdbHelper(adb_path)
        self._on_log       = on_log
        self._on_status    = on_status
        self._on_stats     = on_stats

        self._stop_evt = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self):
        self._stop_evt.clear()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self):
        self._stop_evt.set()
        if self._thread:
            self._thread.join(timeout=4)

    def _run(self):
        audio   = None
        decoder = None
        try:
            ok, msg = self._adb.setup_reverse(self.port)
            self._on_log(f"[USB/ADB] {msg}")
            if not ok:
                self._on_log(f"[USB] 请手动执行：adb reverse tcp:{self.port} tcp:{self.port}")

            audio   = AudioOutput(self.device_index)
            decoder = OpusDecoder()
            jitter  = JitterBuffer(target_size=self.jitter_frames)

            self._on_status("running")
            self._loop(audio, decoder, jitter)
        except Exception as e:
            self._on_log(f"[USB 错误] {e}")
        finally:
            if audio:
                audio.close()
            if decoder:
                decoder.destroy()
            self._adb.remove_reverse(self.port)
            self._on_log(f"[USB/ADB] 已清理端口转发 tcp:{self.port}")
            self._on_status("stopped")

    def _loop(self, audio, decoder, jitter):
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("0.0.0.0", self.port))
        server.listen(1)
        server.settimeout(1.0)
        self._on_log(f"[USB] 监听端口 {self.port}，等待手机连接…")

        try:
            while not self._stop_evt.is_set():
                try:
                    conn, addr = server.accept()
                except socket.timeout:
                    continue
                conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                jitter.reset()
                self._on_log(f"[USB] 手机已连接 {addr[0]}")
                self._session(conn, audio, decoder, jitter)
        finally:
            server.close()

    def _session(self, conn, audio, decoder, jitter):
        last_stats = time.time()
        try:
            while not self._stop_evt.is_set():
                len_bytes = _recv_exact(conn, 2)
                pkt_len   = struct.unpack(">H", len_bytes)[0]
                raw       = _recv_exact(conn, pkt_len)

                seq, opus_data = _parse_packet(raw)
                if seq is None:
                    continue
                jitter.push(seq, opus_data)
                opus_frame = jitter.pop()
                if opus_frame is None:
                    continue

                audio.write(decoder.decode(opus_frame))

                if time.time() - last_stats > 10:
                    self._on_stats(jitter.stats_str())
                    last_stats = time.time()
        except (ConnectionResetError, BrokenPipeError, struct.error):
            self._on_log("[USB] 手机断开，等待重连…")
        except Exception as e:
            self._on_log(f"[USB] 错误: {e}")
        finally:
            conn.close()
