package com.miccast.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.miccast.MainActivity
import com.miccast.R
import com.miccast.model.AudioConfig
import com.miccast.model.ConnectionMethod
import com.miccast.model.UsbConfig
import com.miccast.model.WifiConfig
import com.miccast.repository.AudioStreamRepository
import com.miccast.repository.StreamEvent
import kotlinx.coroutines.flow.SharedFlow

/**
 * 前台服务，负责持有并驱动 AudioStreamRepository。
 *
 * Android 12+ 要求：麦克风录音必须在声明了
 * foregroundServiceType="microphone" 的前台服务中进行，
 * 否则系统会将 AudioRecord 数据强制静音（[audioRecordData][mute]）。
 *
 * ViewModel 通过 bindService() 拿到 LocalBinder，
 * 所有流控制委托给内部的 AudioStreamRepository。
 */
class AudioStreamService : Service() {

    // ── Binder ──────────────────────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        val service: AudioStreamService get() = this@AudioStreamService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    // ── Repository（在 Service 内部，确保录音绑定到前台服务上下文）────────

    val repository = AudioStreamRepository()

    val events: SharedFlow<StreamEvent> get() = repository.events

    // ── 流控制 API（供 ViewModel 调用）──────────────────────────────────────

    suspend fun start(
        method: ConnectionMethod,
        wifiConfig: WifiConfig,
        usbConfig: UsbConfig,
        audioConfig: AudioConfig
    ) = repository.start(method, wifiConfig, usbConfig, audioConfig)

    suspend fun stopStream() = repository.stopStream()

    fun toggleMute() = repository.toggleMute()

    val isMuted: Boolean get() = repository.isMuted

    // ── Service 生命周期 ─────────────────────────────────────────────────────

    companion object {
        const val ACTION_START = "com.miccast.START_STREAM"
        const val ACTION_STOP  = "com.miccast.STOP_STREAM"
        private const val CHANNEL_ID      = "miccast_stream_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                createNotificationChannel()
                // 关键修复：声明 foregroundServiceType = MICROPHONE
                // 这样 AudioRecord 才不会被 Android 12+ 系统强制静音
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    else
                        0
                )
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MicCast Streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Microphone streaming to PC"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AudioStreamService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MicCast")
            .setContentText("Microphone streaming active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}