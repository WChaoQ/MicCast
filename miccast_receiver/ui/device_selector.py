# ══════════════════════════════════════════════════════════════════════════════
#  ui/device_selector.py
#  音频输出设备选择 + VB-Cable 检测 / 安装
# ══════════════════════════════════════════════════════════════════════════════

import threading
import customtkinter as ctk
from core.audio import list_output_devices, AudioOutput
from core import vbcable


class DeviceSelector(ctk.CTkFrame):
    """
    输出设备下拉框 + 刷新按钮 + VB-Cable 检测 / 安装区域。
    """

    def __init__(self, master, on_log, **kwargs):
        super().__init__(master, **kwargs)
        self._on_log  = on_log
        self._devices: list[tuple[int, str]] = []
        self._build()
        self.refresh()

    def _build(self):
        ctk.CTkLabel(
            self,
            text="输出设备",
            font=ctk.CTkFont(size=14, weight="bold"),
            anchor="w",
        ).pack(anchor="w", padx=12, pady=(8, 4))

        # ── 下拉框 + 刷新 ─────────────────────────────────────────────────────
        row1 = ctk.CTkFrame(self, fg_color="transparent")
        row1.pack(fill="x", padx=12, pady=(0, 6))
        self._combo = ctk.CTkComboBox(row1, width=420, state="readonly")
        self._combo.pack(side="left")
        ctk.CTkButton(
            row1, text="刷新", width=70,
            fg_color="#2d5a8e",
            command=self.refresh,
        ).pack(side="left", padx=8)

        # ── VB-Cable 检测 / 安装 ──────────────────────────────────────────────
        row2 = ctk.CTkFrame(self, fg_color="transparent")
        row2.pack(fill="x", padx=12, pady=(0, 10))

        self._check_btn = ctk.CTkButton(
            row2, text="检测 VB-Cable", width=130,
            fg_color="#2d5a8e",
            command=self._check_vbcable,
        )
        self._check_btn.pack(side="left")

        self._install_btn = ctk.CTkButton(
            row2, text="安装 VB-Cable", width=130,
            fg_color="#5a3a8e",
            command=self._install_vbcable,
        )
        self._install_btn.pack(side="left", padx=8)

        self._vb_status_lbl = ctk.CTkLabel(
            row2, text="", text_color="#aaaaaa",
            font=ctk.CTkFont(size=11),
        )
        self._vb_status_lbl.pack(side="left", padx=4)

        # 检查安装包是否存在，若找不到则灰掉安装按钮
        if not vbcable.find_installer():
            self._install_btn.configure(
                state="disabled",
                fg_color="#333333",
            )
            self._vb_status_lbl.configure(
                text="未找到 VBCABLE_Driver_Pack45 安装包"
            )

    # ── VB-Cable 操作 ─────────────────────────────────────────────────────────

    def _check_vbcable(self):
        self._vb_status_lbl.configure(text="检测中…", text_color="#aaaaaa")
        self._check_btn.configure(state="disabled")

        def _work():
            installed = vbcable.is_vbcable_installed()
            if installed:
                msg   = "✅ VB-Cable 已安装"
                color = "#2ecc71"
            else:
                msg   = "❌ 未检测到 VB-Cable"
                color = "#e74c3c"
            self._on_log(f"[VB-Cable] {msg}")
            self.after(0, lambda: (
                self._vb_status_lbl.configure(text=msg, text_color=color),
                self._check_btn.configure(state="normal"),
            ))

        threading.Thread(target=_work, daemon=True).start()

    def _install_vbcable(self):
        installer = vbcable.find_installer()
        if not installer:
            self._on_log("[VB-Cable] 找不到安装包，请确认 VBCABLE_Driver_Pack45 目录存在")
            return
        self._on_log(f"[VB-Cable] 正在启动安装程序：{installer}")
        ok, msg = vbcable.run_installer(installer)
        self._on_log(f"[VB-Cable] {msg}")
        if ok:
            self._vb_status_lbl.configure(
                text="安装程序已启动，完成后点「刷新」",
                text_color="#f39c12",
            )

    # ── 设备列表 ──────────────────────────────────────────────────────────────

    def refresh(self):
        pa = AudioOutput.get_pa_instance()
        try:
            all_devices = list_output_devices(pa)
        finally:
            pa.terminate()

        # 只保留扬声器 / 耳机 / VB-Cable
        filtered = [
            (idx, name) for idx, name in all_devices
            if "CABLE"     in name.upper()
            or "SPEAKER"   in name.upper()
            or "扬声器"     in name
            or "耳机"       in name
            or "HEADPHONE" in name.upper()
            or "HEADSET"   in name.upper()
        ]
        self._devices = filtered if filtered else all_devices

        items = [f"[{idx}] {name}" for idx, name in self._devices]
        self._combo.configure(values=items)

        cable_item = next((item for item in items if "CABLE" in item.upper()), None)
        if cable_item:
            self._combo.set(cable_item)
            self._on_log(f"[设备] 已自动选择 VB-Cable: {cable_item}")
        elif items:
            self._combo.set(items[0])

    def get_device_index(self) -> int | None:
        sel = self._combo.get()
        if sel:
            try:
                return int(sel.split("]")[0].replace("[", "").strip())
            except (ValueError, IndexError):
                pass
        return None
