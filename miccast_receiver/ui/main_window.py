# ══════════════════════════════════════════════════════════════════════════════
#  ui/main_window.py
#  主窗口：组装所有面板，协调 UI ↔ 逻辑层的交互
# ══════════════════════════════════════════════════════════════════════════════

import threading
import customtkinter as ctk

from core.network_utils import get_local_ip, is_port_available
from core.usb_receiver import find_adb, USBReceiver
from core.wifi_receiver import WiFiReceiver

from ui.wifi_panel import WiFiPanel
from ui.usb_panel import USBPanel
from ui.device_selector import DeviceSelector
from ui.log_panel import LogPanel

ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")


class MainWindow(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("MicCast Receiver")
        self.geometry("680x660")
        self.resizable(False, False)

        self._adb_path = find_adb()
        self._receiver = None
        self._running  = False

        self._build_ui()
        self._fetch_ip_async()
        self.protocol("WM_DELETE_WINDOW", self._on_close)

    # ══════════════════════════════════════════════════════════════════════════
    #  界面构建
    # ══════════════════════════════════════════════════════════════════════════

    def _build_ui(self):
        # ── 标题栏 ────────────────────────────────────────────────────────────
        title_row = ctk.CTkFrame(self, fg_color="transparent")
        title_row.pack(fill="x", padx=20, pady=(16, 0))

        ctk.CTkLabel(
            title_row,
            text="🎙  MicCast Receiver",
            font=ctk.CTkFont(size=22, weight="bold"),
        ).pack(side="left")

        self._status_lbl = ctk.CTkLabel(
            title_row,
            text="● 未运行",
            text_color="#888888",
            font=ctk.CTkFont(size=13),
        )
        self._status_lbl.pack(side="right", padx=4)

        # ── 选项卡（WiFi / USB）──────────────────────────────────────────────
        self._tabs = ctk.CTkTabview(self, width=640, height=185)
        self._tabs.pack(padx=20, pady=(12, 0))
        self._tabs.add("📶  WiFi")
        self._tabs.add("🔌  USB")
        self._tabs.configure(command=self._on_tab_change)

        self._wifi_panel = WiFiPanel(self._tabs.tab("📶  WiFi"))
        self._wifi_panel.pack(fill="both", expand=True)

        self._usb_panel = USBPanel(
            self._tabs.tab("🔌  USB"),
            adb_path=self._adb_path,
            on_log=self._log,
        )
        self._usb_panel.pack(fill="both", expand=True)

        # ── 输出设备 + VB-Cable ───────────────────────────────────────────────
        self._device_selector = DeviceSelector(self, on_log=self._log)
        self._device_selector.pack(fill="x", padx=20, pady=(10, 0))

        # ── 启动 / 停止按钮 ───────────────────────────────────────────────────
        btn_row = ctk.CTkFrame(self, fg_color="transparent")
        btn_row.pack(pady=(12, 0))

        self._toggle_btn = ctk.CTkButton(
            btn_row,
            text="▶  启动",
            width=170,
            height=42,
            font=ctk.CTkFont(size=15, weight="bold"),
            fg_color="#1a6b3a",
            hover_color="#145530",
            command=self._toggle,
        )
        self._toggle_btn.pack()

        # ── 日志面板 ──────────────────────────────────────────────────────────
        self._log_panel = LogPanel(self)
        self._log_panel.pack(fill="both", expand=True, padx=20, pady=(12, 16))

    # ══════════════════════════════════════════════════════════════════════════
    #  回调
    # ══════════════════════════════════════════════════════════════════════════

    def _log(self, msg: str):
        self.after(0, lambda: self._log_panel.append(msg))

    def _on_status(self, state: str):
        def _update():
            if state == "running":
                self._status_lbl.configure(text="● 运行中", text_color="#2ecc71")
                self._toggle_btn.configure(
                    text="■  停止",
                    fg_color="#8e2d2d",
                    hover_color="#5a1a1a",
                )
                self._running = True
            else:
                self._status_lbl.configure(text="● 未运行", text_color="#888888")
                self._toggle_btn.configure(
                    text="▶  启动",
                    fg_color="#1a6b3a",
                    hover_color="#145530",
                )
                self._running = False
        self.after(0, _update)

    def _on_stats(self, text: str):
        self.after(0, lambda: self._log_panel.set_stats(text))

    def _on_tab_change(self):
        if self._running:
            current = "📶  WiFi" if isinstance(self._receiver, WiFiReceiver) else "🔌  USB"
            self._tabs.set(current)
            self._log("[提示] 请先停止当前连接，再切换连接方式")

    # ══════════════════════════════════════════════════════════════════════════
    #  启动 / 停止
    # ══════════════════════════════════════════════════════════════════════════

    def _toggle(self):
        if self._running:
            self._stop()
        else:
            self._start()

    def _start(self):
        tab     = self._tabs.get()
        is_wifi = "WiFi" in tab
        device  = self._device_selector.get_device_index()

        if is_wifi:
            port   = self._wifi_panel.get_port()
            jitter = self._wifi_panel.get_jitter()
        else:
            port   = self._usb_panel.get_port()
            jitter = self._usb_panel.get_jitter()

        if not is_port_available(port):
            self._log(f"[错误] 端口 {port} 已被占用，请更换端口")
            return

        common = dict(
            port=port,
            device_index=device,
            jitter_frames=jitter,
            on_log=self._log,
            on_status=self._on_status,
            on_stats=self._on_stats,
        )

        if is_wifi:
            self._receiver = WiFiReceiver(**common)
            self._log(f"启动 WiFi/UDP 模式，端口={port}，缓冲={jitter}帧")
        else:
            self._receiver = USBReceiver(adb_path=self._adb_path, **common)
            self._log(f"启动 USB 模式，端口={port}，缓冲={jitter}帧")

        self._receiver.start()

    def _stop(self):
        if self._receiver:
            self._log("正在停止…")
            threading.Thread(target=self._receiver.stop, daemon=True).start()

    # ══════════════════════════════════════════════════════════════════════════
    #  其他
    # ══════════════════════════════════════════════════════════════════════════

    def _fetch_ip_async(self):
        def _work():
            ip = get_local_ip()
            self.after(0, lambda: self._wifi_panel.set_ip(ip))
        threading.Thread(target=_work, daemon=True).start()

    def _on_close(self):
        if self._receiver:
            self._receiver.stop()
        self.destroy()
