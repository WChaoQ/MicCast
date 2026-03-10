# ══════════════════════════════════════════════════════════════════════════════
#  core/wifi_receiver.py
#  WiFi 接收逻辑：UDP 协议
# ══════════════════════════════════════════════════════════════════════════════

import socket
import struct
import threading
import time
from typing import Callable

from .constants import UDP_RECV_BUF, IDLE_TIMEOUT
from .jitter import JitterBuffer
from .audio import AudioOutput, OpusDecoder


def _parse_packet(data: bytes) -> tuple[int | None, bytes | None]:
    """解析 [4字节序号 Big-Endian][Opus数据]，失败返回 (None, None)。"""
    if len(data) < 4:
        return None, None
    seq = struct.unpack(">I", data[:4])[0]
    return seq, data[4:]


class WiFiReceiver:
    """
    WiFi 音频接收器（UDP）。
    外部通过 start() / stop() 控制，所有接收工作在独立守护线程中运行。
    """

    def __init__(
        self,
        port:          int,
        device_index:  int | None,
        jitter_frames: int,
        on_log:        Callable[[str], None],
        on_status:     Callable[[str], None],
        on_stats:      Callable[[str], None],
    ):
        self.port          = port
        self.device_index  = device_index
        self.jitter_frames = jitter_frames
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
            audio   = AudioOutput(self.device_index)
            decoder = OpusDecoder()
            jitter  = JitterBuffer(target_size=self.jitter_frames)
            self._on_status("running")
            self._loop_udp(audio, decoder, jitter)
        except Exception as e:
            self._on_log(f"[WiFi 错误] {e}")
        finally:
            if audio:
                audio.close()
            if decoder:
                decoder.destroy()
            self._on_status("stopped")

    def _process_frame(self, raw, audio, decoder, jitter):
        seq, opus_data = _parse_packet(raw)
        if seq is None:
            return
        jitter.push(seq, opus_data)
        opus_frame = jitter.pop()
        if opus_frame is None:
            return
        audio.write(decoder.decode(opus_frame))

    def _loop_udp(self, audio, decoder, jitter):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 1024 * 1024)
        sock.bind(("0.0.0.0", self.port))
        sock.settimeout(1.0)
        self._on_log(f"[WiFi/UDP] 监听 0.0.0.0:{self.port}")

        last_stats = time.time()
        last_recv  = 0.0

        try:
            while not self._stop_evt.is_set():
                try:
                    raw, addr = sock.recvfrom(UDP_RECV_BUF)
                except socket.timeout:
                    if last_recv > 0:
                        self._on_log("[WiFi/UDP] 超时，等待重连…")
                        jitter.reset()
                        last_recv = 0.0
                    continue

                now = time.time()
                if last_recv > 0 and (now - last_recv) > IDLE_TIMEOUT:
                    self._on_log("[WiFi/UDP] 检测到重连，缓冲已重置")
                    jitter.reset()
                if last_recv == 0:
                    self._on_log(f"[WiFi/UDP] 连接来自 {addr[0]}")
                last_recv = now

                self._process_frame(raw, audio, decoder, jitter)

                if time.time() - last_stats > 10:
                    self._on_stats(jitter.stats_str())
                    last_stats = time.time()
        finally:
            sock.close()
