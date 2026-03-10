# ══════════════════════════════════════════════════════════════════════════════
#  core/constants.py
#  全局常量，必须与手机端 AudioCapture.kt 保持一致
# ══════════════════════════════════════════════════════════════════════════════

# 音频参数
SAMPLE_RATE   = 48000   # Hz，Opus 标准采样率
CHANNELS      = 1       # 单声道
FRAME_SAMPLES = 960     # 每帧采样数（20 ms @ 48 kHz）
FRAME_BYTES   = FRAME_SAMPLES * 2  # 16-bit PCM = 2 字节/采样

# 网络
UDP_RECV_BUF = 4096     # UDP 单包最大字节数
SEQ_WINDOW   = 1000     # 序号差超过此值视为乱序而非回绕
IDLE_TIMEOUT = 1.5      # 秒，超过此时间无包视为断连

# 默认端口
WIFI_DEFAULT_PORT = 5513
USB_DEFAULT_PORT  = 5514

# 抖动缓冲默认帧数
JITTER_WIFI_DEFAULT = 5
JITTER_USB_DEFAULT  = 3
