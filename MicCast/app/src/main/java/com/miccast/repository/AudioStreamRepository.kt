package com.miccast.repository

import android.util.Log
import com.miccast.audio.AudioCapture
import com.miccast.model.AudioConfig
import com.miccast.model.ConnectionMethod
import com.miccast.model.UsbConfig
import com.miccast.model.WifiConfig
import com.miccast.network.AudioSender
import com.miccast.network.AudioSenderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

private const val TAG = "AudioStreamRepository"

/**
 * Events emitted back to the ViewModel.
 */
sealed class StreamEvent {
    object Connected : StreamEvent()
    object Disconnected : StreamEvent()
    data class AudioLevel(val level: Float) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

/**
 * Manages the full audio streaming pipeline:
 *  1. Establish connection (WiFi / USB TCP)
 *  2. Start AudioRecord
 *  3. Forward PCM chunks via the chosen transport
 *  4. Handle mute / stop
 */
class AudioStreamRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<StreamEvent> = _events.asSharedFlow()

    private var streamJob: Job? = null
    private var sender: AudioSender? = null

    @Volatile var isMuted: Boolean = false

    // ─────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────

    /**
     * Start streaming for WiFi connection method.
     */
    suspend fun startWifi(wifiConfig: WifiConfig, audioConfig: AudioConfig) {
        stopStream()
        streamJob = repoScope.launch {
            try {
                Log.d(TAG, "Connecting via WiFi to ${wifiConfig.ipAddress}:${wifiConfig.port}")
                val newSender = AudioSenderFactory.create(wifiConfig)
                runStream(newSender)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "WiFi Stream error: ${e.message}", e)
                _events.emit(StreamEvent.Error(e.message ?: "Unknown WiFi error"))
                // 移除 Disconnected 发送，保持 ERROR 状态
            }
        }
    }

    /**
     * Start streaming for USB (via ADB TCP forwarding).
     */
    suspend fun startUsb(usbConfig: UsbConfig, audioConfig: AudioConfig) {
        stopStream()
        streamJob = repoScope.launch {
            try {
                Log.d(TAG, "USB TCP connection starting on port ${usbConfig.port}")
                val newSender = AudioSenderFactory.createUsbTcp(usbConfig)
                runStream(newSender)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "USB Stream error: ${e.message}", e)
                _events.emit(StreamEvent.Error(
                    "USB 连接失败：${e.message}\n请确认已执行 adb forward tcp:${usbConfig.port} tcp:${usbConfig.port}"
                ))
                // 移除 Disconnected 发送，保持 ERROR 状态
            }
        }
    }

    /** 通用推流循环 */
    private suspend fun runStream(newSender: AudioSender) {
        sender = newSender
        _events.emit(StreamEvent.Connected)
        val capture = AudioCapture()
        try {
            capture.captureAudio().collect { chunk ->
                if (!isMuted) {
                    newSender.send(chunk.pcmBytes, chunk.pcmBytes.size)
                }
                _events.emit(StreamEvent.AudioLevel(if (isMuted) 0f else chunk.amplitude))
            }
        } finally {
            sender?.close()
            sender = null
        }
    }

    /**
     * Dispatch to the correct start method.
     */
    suspend fun start(
        method: ConnectionMethod,
        wifiConfig: WifiConfig,
        usbConfig: UsbConfig,
        audioConfig: AudioConfig
    ) {
        when (method) {
            ConnectionMethod.WIFI -> startWifi(wifiConfig, audioConfig)
            ConnectionMethod.USB  -> startUsb(usbConfig, audioConfig)
        }
    }

    /**
     * Stop the current stream and release resources.
     */
    suspend fun stopStream() {
        streamJob?.cancelAndJoin()
        streamJob = null
        sender?.close()
        sender = null
        _events.emit(StreamEvent.Disconnected)
    }

    fun toggleMute() {
        isMuted = !isMuted
    }
}
