package com.miccast.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

private const val TAG = "AudioCapture"

/**
 * Opus 编码器要求固定帧大小：
 *   48000Hz × 20ms = 960 采样 = 1920 字节（16bit mono）
 */
const val OPUS_SAMPLE_RATE  = 48000
const val OPUS_FRAME_SAMPLES = 960                        // 20ms per frame
const val OPUS_FRAME_BYTES   = OPUS_FRAME_SAMPLES * 2    // 16bit = 2 bytes/sample

/**
 * 从麦克风采集 PCM 音频，按 Opus 帧大小（960 采样）切块后通过 Flow 发出。
 *
 * 使用 VOICE_COMMUNICATION 音源，系统会自动启用：
 *  - 回声消除（AEC）
 *  - 噪声抑制（NS）
 *  - 自动增益（AGC）
 */
class AudioCapture {

    data class AudioChunk(
        val pcmBytes: ByteArray,   // 固定 OPUS_FRAME_BYTES 字节
        val amplitude: Float       // 0..1 RMS，用于 VU 表显示
    )

    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat   = AudioFormat.ENCODING_PCM_16BIT

    private val minBufSize = AudioRecord.getMinBufferSize(
        OPUS_SAMPLE_RATE, channelConfig, audioFormat
    ).coerceAtLeast(OPUS_FRAME_BYTES * 4)  // 至少 4 帧的缓冲

    @SuppressLint("MissingPermission")
    fun captureAudio(): Flow<AudioChunk> = flow {

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // ← 系统级降噪
            OPUS_SAMPLE_RATE,
            channelConfig,
            audioFormat,
            minBufSize
        )

        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord 初始化失败，设备可能不支持 ${OPUS_SAMPLE_RATE}Hz"
        }

        // 在系统层额外开启硬件降噪（双重保险）
        val sessionId = recorder.audioSessionId
        enableAudioEffects(sessionId)

        // 用于跨 read() 调用积累不足一帧的数据
        val accumulator = ByteArray(OPUS_FRAME_BYTES * 2)
        var accumulated = 0

        val readBuf = ByteArray(OPUS_FRAME_BYTES)
        recorder.startRecording()
        Log.d(TAG, "AudioRecord 启动：${OPUS_SAMPLE_RATE}Hz，帧大小 $OPUS_FRAME_BYTES 字节")

        try {
            while (coroutineContext.isActive) {
                val bytesRead = recorder.read(readBuf, 0, readBuf.size)
                if (bytesRead <= 0) continue

                // 将读到的数据追加到累积缓冲
                System.arraycopy(readBuf, 0, accumulator, accumulated, bytesRead)
                accumulated += bytesRead

                // 每凑够一个完整 Opus 帧就发出去
                while (accumulated >= OPUS_FRAME_BYTES) {
                    val frame = accumulator.copyOf(OPUS_FRAME_BYTES)
                    // 把剩余数据移到头部
                    System.arraycopy(accumulator, OPUS_FRAME_BYTES, accumulator, 0, accumulated - OPUS_FRAME_BYTES)
                    accumulated -= OPUS_FRAME_BYTES

                    emit(AudioChunk(frame, calculateAmplitude(frame)))
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            Log.d(TAG, "AudioRecord 已释放")
        }
    }.flowOn(Dispatchers.IO)

    // ─────────────────────────────────────────────────────────
    //  硬件音频效果（不是所有设备都支持，做好降级处理）
    // ─────────────────────────────────────────────────────────

    private fun enableAudioEffects(sessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(sessionId)?.enabled = true
            Log.d(TAG, "AcousticEchoCanceler 已启用")
        }
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(sessionId)?.enabled = true
            Log.d(TAG, "AutomaticGainControl 已启用")
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(sessionId)?.enabled = true
            Log.d(TAG, "NoiseSuppressor 已启用")
        }
    }

    // ─────────────────────────────────────────────────────────
    //  RMS 振幅计算（用于 UI VU 表）
    // ─────────────────────────────────────────────────────────

    private fun calculateAmplitude(buffer: ByteArray): Float {
        val sampleCount = buffer.size / 2
        if (sampleCount == 0) return 0f
        var sumSquares = 0.0
        for (i in 0 until sampleCount) {
            val lo = buffer[i * 2].toInt() and 0xFF
            val hi = buffer[i * 2 + 1].toInt()
            val sample = (hi shl 8) or lo
            sumSquares += sample.toDouble() * sample.toDouble()
        }
        return (sqrt(sumSquares / sampleCount) / 32768.0).toFloat().coerceIn(0f, 1f)
    }
}