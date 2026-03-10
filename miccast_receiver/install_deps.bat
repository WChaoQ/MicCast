@echo off
chcp 65001 >nul
echo ============================================
echo   MicCast Receiver - 安装依赖
echo ============================================
echo.
pip install customtkinter pyaudio PyOgg
echo.
echo 安装完成！运行 start.bat 启动程序（开发模式）。
echo 如需打包为 EXE，运行 build.bat。
pause
