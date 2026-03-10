# ══════════════════════════════════════════════════════════════════════════════
#  build.spec
# ══════════════════════════════════════════════════════════════════════════════

import os
import glob
import sys
from PyInstaller.utils.hooks import collect_data_files

block_cipher = None

# ── 找到 PyOgg 包目录，收集其中所有 DLL ──────────────────────────────────────
import pyogg as _pyogg
PYOGG_DIR = os.path.dirname(_pyogg.__file__)
pyogg_dlls = []
for pattern in ("*.dll", "*.so", "*.dylib"):
    for f in glob.glob(os.path.join(PYOGG_DIR, pattern)):
        pyogg_dlls.append((f, "."))   # 打包到 exe 同级目录

# ── customtkinter 资源 ────────────────────────────────────────────────────────
ctk_datas = collect_data_files("customtkinter")

a = Analysis(
    ["main.py"],
    pathex=["."],
    binaries=pyogg_dlls,
    datas=[
        *ctk_datas,
    ],
    hiddenimports=[
        "customtkinter",
        "pyaudio",
        "pyogg",
        "pyogg.opus",
        "winreg",
        "ctypes",
        "ctypes.windll",
    ],
    hookspath=[],
    runtime_hooks=[],
    excludes=[
        "numpy", "scipy", "matplotlib",
        "tkinter.test",
    ],
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="MicCastReceiver",
    debug=False,
    strip=False,
    upx=True,
    console=False,
    icon=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="MicCastReceiver",
)
