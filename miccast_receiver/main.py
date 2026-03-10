#!/usr/bin/env python3
# ══════════════════════════════════════════════════════════════════════════════
#  main.py  —  程序入口
#  注意：opus DLL patch 必须在所有其他 import 之前执行，
#        否则 pyogg.opus.libopus 会在模块加载时就被赋值为 None。
# ══════════════════════════════════════════════════════════════════════════════

import ctypes
import os
import sys


def _patch_opus():
    """
    PyInstaller 打包后 ctypes.util.find_library 失效。
    在任何 pyogg import 之前，预先把 exe 目录下的 opus DLL 加载进来，
    使 pyogg.opus 模块初始化时能找到它。
    """
    if not getattr(sys, "frozen", False):
        return  # 开发模式，交给系统 / pyogg 自己处理

    exe_dir = os.path.dirname(sys.executable)

    if sys.platform == "win32":
        try:
            os.add_dll_directory(exe_dir)
        except AttributeError:
            pass  # Python < 3.8

        for name in ("opus.dll", "libopus.dll", "libopus-0.dll"):
            path = os.path.join(exe_dir, name)
            if os.path.isfile(path):
                ctypes.CDLL(path)
                break


_patch_opus()

# ── 所有业务 import 必须在 patch 之后 ─────────────────────────────────────────
from ui.main_window import MainWindow  # noqa: E402


def main():
    app = MainWindow()
    app.mainloop()


if __name__ == "__main__":
    main()
