# ══════════════════════════════════════════════════════════════════════════════
#  core/vbcable.py
#  VB-Cable 检测与安装辅助
# ══════════════════════════════════════════════════════════════════════════════

import os
import subprocess
import sys


def get_project_root() -> str:
    if getattr(sys, "frozen", False):
        return os.path.dirname(sys.executable)
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def is_vbcable_installed() -> bool:
    """
    检测 VB-Cable 是否已安装。
    通过注册表查询驱动是否存在（Windows only）。
    """
    if sys.platform != "win32":
        return False
    try:
        import winreg
        # VB-Cable 在设备管理器里注册的硬件 ID
        key_paths = [
            r"SYSTEM\CurrentControlSet\Services\VBAudioVACMM",
            r"SYSTEM\CurrentControlSet\Services\VBAudio Virtual Cable",
            r"SYSTEM\CurrentControlSet\Services\VBCABLE",
        ]
        for path in key_paths:
            try:
                winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, path)
                return True
            except FileNotFoundError:
                continue
    except Exception:
        pass

    # 备用方案：检查音频设备名称
    try:
        import pyaudio
        pa = pyaudio.PyAudio()
        found = False
        for i in range(pa.get_device_count()):
            info = pa.get_device_info_by_index(i)
            if "CABLE" in info["name"].upper():
                found = True
                break
        pa.terminate()
        return found
    except Exception:
        return False


def find_installer() -> str | None:
    """
    在 <项目根目录>/VBCABLE_Driver_Pack45/ 中寻找安装程序。
    返回安装程序路径，找不到返回 None。
    """
    root = get_project_root()
    driver_dir = os.path.join(root, "VBCABLE_Driver_Pack45")
    if not os.path.isdir(driver_dir):
        return None
    # 优先找 64 位版本
    candidates = [
        "VBCABLE_Setup_x64.exe",
        "VBCABLE_Setup.exe",
    ]
    for name in candidates:
        path = os.path.join(driver_dir, name)
        if os.path.isfile(path):
            return path
    return None


def run_installer(installer_path: str) -> tuple[bool, str]:
    """
    以管理员权限运行 VB-Cable 安装程序。
    返回 (成功启动, 消息)。注意：安装程序本身是异步的，启动成功不代表安装完成。
    """
    try:
        if sys.platform == "win32":
            # ShellExecute runas 触发 UAC 提权
            import ctypes
            ret = ctypes.windll.shell32.ShellExecuteW(
                None, "runas", installer_path, None, None, 1
            )
            if ret > 32:
                return True, "安装程序已启动，请按提示完成安装，安装后重启程序"
            return False, f"启动安装程序失败（错误码 {ret}）"
        else:
            subprocess.Popen([installer_path])
            return True, "安装程序已启动"
    except Exception as e:
        return False, f"启动失败：{e}"
