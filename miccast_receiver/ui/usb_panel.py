# ══════════════════════════════════════════════════════════════════════════════
#  ui/usb_panel.py
#  USB 模式配置面板
# ══════════════════════════════════════════════════════════════════════════════

import threading
import customtkinter as ctk
from core.constants import USB_DEFAULT_PORT, JITTER_USB_DEFAULT
from core.usb_receiver import AdbHelper


class USBPanel(ctk.CTkFrame):
    """USB 模式的配置控件集合。"""

    def __init__(self, master, adb_path: str, on_log, **kwargs):
        super().__init__(master, fg_color="transparent", **kwargs)
        self._adb    = AdbHelper(adb_path)
        self._on_log = on_log
        self._build(adb_path)

    def _build(self, adb_path: str):
        # ADB 路径提示
        ctk.CTkLabel(
            self,
            text=f"ADB 路径：{adb_path}",
            font=ctk.CTkFont(size=11),
            text_color="#aaaaaa",
            anchor="w",
        ).pack(fill="x", padx=14, pady=(10, 2))

        ctk.CTkLabel(
            self,
            text="步骤：手机开启 USB 调试 → 用数据线连接 → 检测设备 → 启动",
            font=ctk.CTkFont(size=12),
            text_color="#cccccc",
            anchor="w",
        ).pack(fill="x", padx=14, pady=(0, 6))

        # 端口
        row1 = ctk.CTkFrame(self, fg_color="transparent")
        row1.pack(fill="x", padx=14, pady=3)
        ctk.CTkLabel(row1, text="端口：", width=72, anchor="w").pack(side="left")
        self.port_entry = ctk.CTkEntry(row1, width=90, placeholder_text=str(USB_DEFAULT_PORT))
        self.port_entry.insert(0, str(USB_DEFAULT_PORT))
        self.port_entry.pack(side="left", padx=6)

        # 抖动缓冲
        row2 = ctk.CTkFrame(self, fg_color="transparent")
        row2.pack(fill="x", padx=14, pady=3)
        ctk.CTkLabel(row2, text="抖动缓冲：", width=72, anchor="w").pack(side="left")
        self.jitter_slider = ctk.CTkSlider(
            row2, from_=1, to=10, number_of_steps=9, width=160
        )
        self.jitter_slider.set(JITTER_USB_DEFAULT)
        self.jitter_slider.pack(side="left", padx=6)
        self._jitter_lbl = ctk.CTkLabel(row2, text=f"{JITTER_USB_DEFAULT} 帧", width=50)
        self._jitter_lbl.pack(side="left")
        self.jitter_slider.configure(
            command=lambda v: self._jitter_lbl.configure(text=f"{int(v)} 帧")
        )

        # 抖动缓冲说明
        ctk.CTkLabel(
            self,
            text="抖动缓冲：数值越大越抗 USB 抖动，延迟也越高；修改后重新连接生效",
            text_color="#888888",
            font=ctk.CTkFont(size=11),
            anchor="w",
        ).pack(fill="x", padx=14, pady=(0, 2))

        # ADB 检测按钮
        row3 = ctk.CTkFrame(self, fg_color="transparent")
        row3.pack(fill="x", padx=14, pady=(6, 4))
        self._check_btn = ctk.CTkButton(
            row3,
            text="检测 ADB 设备",
            width=140,
            fg_color="#2d5a8e",
            command=self._check_adb,
        )
        self._check_btn.pack(side="left")
        self._status_lbl = ctk.CTkLabel(
            row3, text="", text_color="#aaaaaa", font=ctk.CTkFont(size=12)
        )
        self._status_lbl.pack(side="left", padx=10)

    # ── ADB 检测 ──────────────────────────────────────────────────────────────

    def _check_adb(self):
        self._status_lbl.configure(text="检测中…", text_color="#aaaaaa")
        self._check_btn.configure(state="disabled")

        def _work():
            devices = self._adb.check_devices()
            if devices:
                msg   = f"✅ 已连接 {len(devices)} 台设备"
                color = "#2ecc71"
            else:
                msg   = "❌ 未检测到设备"
                color = "#e74c3c"
            self._on_log(f"[ADB 检测] {msg}")
            self._status_lbl.after(
                0,
                lambda: (
                    self._status_lbl.configure(text=msg, text_color=color),
                    self._check_btn.configure(state="normal"),
                ),
            )

        threading.Thread(target=_work, daemon=True).start()

    # ── 公开读值接口 ──────────────────────────────────────────────────────────

    def get_port(self) -> int:
        try:
            return int(self.port_entry.get())
        except ValueError:
            return USB_DEFAULT_PORT

    def get_jitter(self) -> int:
        return int(self.jitter_slider.get())
