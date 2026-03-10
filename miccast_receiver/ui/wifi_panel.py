# ══════════════════════════════════════════════════════════════════════════════
#  ui/wifi_panel.py
#  WiFi 模式配置面板
# ══════════════════════════════════════════════════════════════════════════════

import customtkinter as ctk
from core.constants import WIFI_DEFAULT_PORT, JITTER_WIFI_DEFAULT


class WiFiPanel(ctk.CTkFrame):
    """WiFi 模式的配置控件集合。"""

    def __init__(self, master, local_ip: str = "获取中…", **kwargs):
        super().__init__(master, fg_color="transparent", **kwargs)
        self._build(local_ip)

    def _build(self, local_ip: str):
        # IP 提示
        self.ip_label = ctk.CTkLabel(
            self,
            text=f"本机 IP（填入手机 App）：{local_ip}",
            font=ctk.CTkFont(size=13),
            text_color="#7ecfff",
            anchor="w",
        )
        self.ip_label.pack(fill="x", padx=14, pady=(10, 4))

        # 端口
        row1 = ctk.CTkFrame(self, fg_color="transparent")
        row1.pack(fill="x", padx=14, pady=3)
        ctk.CTkLabel(row1, text="端口：", width=72, anchor="w").pack(side="left")
        self.port_entry = ctk.CTkEntry(row1, width=90, placeholder_text=str(WIFI_DEFAULT_PORT))
        self.port_entry.insert(0, str(WIFI_DEFAULT_PORT))
        self.port_entry.pack(side="left", padx=6)

        # 抖动缓冲
        row2 = ctk.CTkFrame(self, fg_color="transparent")
        row2.pack(fill="x", padx=14, pady=3)
        ctk.CTkLabel(row2, text="抖动缓冲：", width=72, anchor="w").pack(side="left")
        self.jitter_slider = ctk.CTkSlider(
            row2, from_=1, to=20, number_of_steps=19, width=140
        )
        self.jitter_slider.set(JITTER_WIFI_DEFAULT)
        self.jitter_slider.pack(side="left", padx=6)
        self._jitter_lbl = ctk.CTkLabel(row2, text=f"{JITTER_WIFI_DEFAULT} 帧", width=46)
        self._jitter_lbl.pack(side="left")
        self.jitter_slider.configure(
            command=lambda v: self._jitter_lbl.configure(text=f"{int(v)} 帧")
        )

        # 抖动缓冲说明
        ctk.CTkLabel(
            self,
            text="抖动缓冲：数值越大越抗网络抖动，延迟也越高；修改后重新连接生效",
            text_color="#888888",
            font=ctk.CTkFont(size=11),
            anchor="w",
        ).pack(fill="x", padx=14, pady=(0, 6))

    # ── 公开读值接口 ──────────────────────────────────────────────────────────

    def get_port(self) -> int:
        try:
            return int(self.port_entry.get())
        except ValueError:
            return WIFI_DEFAULT_PORT

    def get_jitter(self) -> int:
        return int(self.jitter_slider.get())

    def set_ip(self, ip: str):
        self.ip_label.configure(text=f"本机 IP（填入手机 App）：{ip}")
