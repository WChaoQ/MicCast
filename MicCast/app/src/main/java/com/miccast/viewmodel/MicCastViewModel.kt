package com.miccast.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miccast.model.ConnectionMethod
import com.miccast.model.ConnectionState
import com.miccast.model.MicCastUiState
import com.miccast.service.AudioStreamService
import com.miccast.repository.StreamEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MicCastViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MicCastUiState())
    val uiState: StateFlow<MicCastUiState> = _uiState.asStateFlow()

    // ── Service 绑定 ─────────────────────────────────────────────────────────

    private var boundService: AudioStreamService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? AudioStreamService.LocalBinder ?: return
            boundService = localBinder.service
            isBound = true
            // 开始订阅 Service 内部 Repository 的事件
            observeServiceEvents()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isBound = false
        }
    }

    init {
        // 提前绑定服务（不启动），以便后续直接调用
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, AudioStreamService::class.java)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // ── UI 事件处理 ──────────────────────────────────────────────────────────

    fun onConnectionMethodChanged(method: ConnectionMethod) {
        _uiState.update { it.copy(connectionMethod = method) }
    }

    fun onIpAddressChanged(ip: String) {
        _uiState.update { it.copy(wifiConfig = it.wifiConfig.copy(ipAddress = ip)) }
    }

    fun onPortChanged(portStr: String) {
        val port = portStr.toIntOrNull() ?: return
        _uiState.update { it.copy(wifiConfig = it.wifiConfig.copy(port = port)) }
    }

    fun onUsbPortChanged(portStr: String) {
        val port = portStr.toIntOrNull() ?: return
        _uiState.update { it.copy(usbConfig = it.usbConfig.copy(port = port)) }
    }

    fun onConnectClicked() {
        val state = _uiState.value
        if (state.connectionState == ConnectionState.CONNECTED ||
            state.connectionState == ConnectionState.CONNECTING) return

        _uiState.update {
            it.copy(
                connectionState = ConnectionState.CONNECTING,
                errorMessage = null,
                statusMessage = "Connecting…"
            )
        }

        // 此时不启动前台服务，先尝试建立连接
        viewModelScope.launch {
            val svc = boundService
            if (svc == null) {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.ERROR,
                        errorMessage = "Service 未就绪，请重试",
                        statusMessage = "Error"
                    )
                }
                return@launch
            }
            svc.start(
                method      = state.connectionMethod,
                wifiConfig  = state.wifiConfig,
                usbConfig   = state.usbConfig,
                audioConfig = state.audioConfig
            )
        }
    }

    fun onDisconnectClicked() {
        viewModelScope.launch {
            boundService?.stopStream()
        }
        stopForegroundService()
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun startForegroundService() {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(
            Intent(ctx, AudioStreamService::class.java).apply {
                action = AudioStreamService.ACTION_START
            }
        )
    }

    private fun stopForegroundService() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, AudioStreamService::class.java).apply {
                action = AudioStreamService.ACTION_STOP
            }
        )
    }

    fun onMuteClicked() {
        boundService?.toggleMute()
    }

    // ── 订阅 Service 事件 ────────────────────────────────────────────────────

    private fun observeServiceEvents() {
        val svc = boundService ?: return
        viewModelScope.launch {
            svc.events.collect { event ->
                when (event) {
                    is StreamEvent.Connected -> {
                        // 只有连接成功后才启动前台服务显示麦克风图标
                        startForegroundService()
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.CONNECTED,
                                statusMessage   = "Streaming…",
                                errorMessage    = null
                            )
                        }
                    }
                    is StreamEvent.Disconnected -> {
                        stopForegroundService()
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.DISCONNECTED,
                                statusMessage   = "",
                                audioLevel      = 0f
                            )
                        }
                    }
                    is StreamEvent.AudioLevel -> _uiState.update {
                        it.copy(audioLevel = event.level)
                    }
                    is StreamEvent.Error -> {
                        // 报错时也要确保停止前台服务
                        stopForegroundService()
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.ERROR,
                                errorMessage    = event.message,
                                statusMessage   = "Error"
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { boundService?.stopStream() }
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }
}
