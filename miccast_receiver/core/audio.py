# ══════════════════════════════════════════════════════════════════════════════
#  core/audio.py
#  音频输出流 + Opus 解码器
# ══════════════════════════════════════════════════════════════════════════════

import sys

from .constants import SAMPLE_RATE, CHANNELS, FRAME_SAMPLES

try:
    import pyaudio
except ImportError:
    print("ERROR: pip install pyaudio")
    sys.exit(1)

try:
    import pyogg
    from pyogg import opus as pyogg_opus
except ImportError:
    print("ERROR: pip install PyOgg")
    sys.exit(1)


# ══════════════════════════════════════════════════════════════════════════════
#  设备枚举
# ══════════════════════════════════════════════════════════════════════════════

def list_output_devices(pa: pyaudio.PyAudio) -> list[tuple[int, str]]:
    devices = []
    for i in range(pa.get_device_count()):
        info = pa.get_device_info_by_index(i)
        if info["maxOutputChannels"] > 0:
            devices.append((i, info["name"]))
    return devices


# ══════════════════════════════════════════════════════════════════════════════
#  Opus 解码器
# ══════════════════════════════════════════════════════════════════════════════

class OpusDecoder:
    def __init__(self):
        if pyogg_opus.libopus is None:
            raise RuntimeError(
                "找不到 opus DLL。\n"
                "请确认 opus.dll / libopus.dll 与 MicCastReceiver.exe 在同一目录。"
            )
        err = pyogg_opus.c_int(0)
        self._decoder = pyogg_opus.libopus.opus_decoder_create(
            SAMPLE_RATE, CHANNELS, pyogg_opus.ctypes.byref(err)
        )
        if err.value != 0:
            raise RuntimeError(f"Opus 解码器初始化失败，错误码: {err.value}")

    def decode(self, opus_data: bytes) -> bytes:
        pcm_buf  = (pyogg_opus.opus_int16 * (FRAME_SAMPLES * CHANNELS))()
        opus_arr = (pyogg_opus.ctypes.c_ubyte * len(opus_data))(*opus_data)
        samples  = pyogg_opus.libopus.opus_decode(
            self._decoder, opus_arr, len(opus_data),
            pcm_buf, FRAME_SAMPLES, 0
        )
        return bytes(pcm_buf)[: samples * CHANNELS * 2]

    def destroy(self):
        if self._decoder:
            pyogg_opus.libopus.opus_decoder_destroy(self._decoder)
            self._decoder = None

    def __del__(self):
        self.destroy()


# ══════════════════════════════════════════════════════════════════════════════
#  音频输出流管理
# ══════════════════════════════════════════════════════════════════════════════

class AudioOutput:
    def __init__(self, device_index: int | None = None):
        self._pa     = pyaudio.PyAudio()
        self._stream = self._pa.open(
            format              = pyaudio.paInt16,
            channels            = CHANNELS,
            rate                = SAMPLE_RATE,
            output              = True,
            output_device_index = device_index,
            frames_per_buffer   = FRAME_SAMPLES,
        )

    def write(self, pcm_bytes: bytes):
        self._stream.write(pcm_bytes)

    def close(self):
        try:
            self._stream.stop_stream()
            self._stream.close()
        except Exception:
            pass
        try:
            self._pa.terminate()
        except Exception:
            pass

    @staticmethod
    def get_pa_instance() -> pyaudio.PyAudio:
        return pyaudio.PyAudio()
