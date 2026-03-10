# ══════════════════════════════════════════════════════════════════════════════
#  ui/log_panel.py
#  运行日志显示面板
# ══════════════════════════════════════════════════════════════════════════════

import time
import customtkinter as ctk


class LogPanel(ctk.CTkFrame):
    """滚动日志文本框 + 底部统计标签。"""

    def __init__(self, master, **kwargs):
        super().__init__(master, **kwargs)
        self._build()

    def _build(self):
        ctk.CTkLabel(
            self,
            text="运行日志",
            font=ctk.CTkFont(size=13, weight="bold"),
            anchor="w",
        ).pack(anchor="w", padx=10, pady=(6, 2))

        self._textbox = ctk.CTkTextbox(
            self,
            height=130,
            font=ctk.CTkFont(family="Consolas", size=11),
            state="disabled",
        )
        self._textbox.pack(fill="both", expand=True, padx=10, pady=(0, 4))

        self._stats_lbl = ctk.CTkLabel(
            self,
            text="",
            text_color="#7ecfff",
            font=ctk.CTkFont(size=11),
            anchor="w",
        )
        self._stats_lbl.pack(anchor="w", padx=10, pady=(0, 6))

    # ── 公开接口 ──────────────────────────────────────────────────────────────

    def append(self, msg: str):
        """追加一行日志（线程安全需由调用方通过 after() 保证）。"""
        self._textbox.configure(state="normal")
        ts = time.strftime("%H:%M:%S")
        self._textbox.insert("end", f"[{ts}] {msg}\n")
        self._textbox.see("end")
        self._textbox.configure(state="disabled")

    def set_stats(self, text: str):
        self._stats_lbl.configure(text=f"统计：{text}")

    def clear(self):
        self._textbox.configure(state="normal")
        self._textbox.delete("1.0", "end")
        self._textbox.configure(state="disabled")
        self._stats_lbl.configure(text="")
