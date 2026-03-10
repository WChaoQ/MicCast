@echo off
cd /d "%~dp0"

python -c "import PyInstaller" 2>nul || pip install pyinstaller

pyinstaller build.spec --clean --noconfirm
if errorlevel 1 (
    echo FAILED
    pause
    exit /b 1
)

xcopy /E /I /Y "platform-tools" "dist\MicCastReceiver\platform-tools\"
xcopy /E /I /Y "VBCABLE_Driver_Pack45" "dist\MicCastReceiver\VBCABLE_Driver_Pack45\"

echo Done. Output: dist\MicCastReceiver\
pause
