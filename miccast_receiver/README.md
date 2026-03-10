# MicCast Receiver

将手机麦克风音频通过 **WiFi** 或 **USB** 传输到电脑，输出到 VB-Cable 虚拟麦克风。

## 项目结构

```
MicCastReceiver/
├── main.py                      # 程序入口
├── build.spec                   # PyInstaller 打包配置
├── build.bat                    # 一键打包为 EXE
├── install_deps.bat             # 开发模式安装依赖
├── start.bat                    # 开发模式启动
├── platform-tools/
│   └── adb.exe                  # ADB（USB 模式必需）
├── VBCABLE_Driver_Pack45/
│   └── VBCABLE_Setup_x64.exe    # VB-Cable 安装包
├── core/
│   ├── constants.py             # 全局常量
│   ├── audio.py                 # 音频输出流 / Opus 解码
│   ├── jitter.py                # 抖动缓冲
│   ├── network_utils.py         # 网络工具
│   ├── vbcable.py               # VB-Cable 检测与安装
│   ├── wifi_receiver.py         # WiFi 接收逻辑（UDP）
│   └── usb_receiver.py          # USB 接收逻辑（ADB + TCP）
└── ui/
    ├── main_window.py           # 主窗口
    ├── wifi_panel.py            # WiFi 配置面板
    ├── usb_panel.py             # USB 配置面板
    ├── device_selector.py       # 输出设备选择 + VB-Cable 检测安装
    └── log_panel.py             # 日志面板
```

## 开发模式运行

```bash
pip install customtkinter pyaudio PyOgg
python main.py
```

## 打包为 EXE

```bat
build.bat
```

打包完成后，将 `platform-tools\` 和 `VBCABLE_Driver_Pack45\` 复制到
`dist\MicCastReceiver\` 目录中一起分发。

## 连接说明

### 📶 WiFi（UDP）
- 电脑和手机连同一 WiFi
- 程序界面显示本机 IP，填入手机 App

### 🔌 USB（ADB）
- 手机开启「开发者选项 → USB 调试」
- 数据线连接手机与电脑
- 点「检测 ADB 设备」确认后启动

## VB-Cable 虚拟麦克风

```
手机麦克风 → App(Opus) → WiFi/USB → MicCast → CABLE Input → 其他应用选 CABLE Output
```

界面中点「检测 VB-Cable」确认是否已安装，未安装时点「安装 VB-Cable」会自动
以管理员权限运行 VBCABLE_Driver_Pack45 中的安装程序。
