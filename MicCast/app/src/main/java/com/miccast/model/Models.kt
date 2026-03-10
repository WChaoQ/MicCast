package com.miccast.model

/**
 * Connection method options
 */
enum class ConnectionMethod {
    WIFI,
    USB
}

/**
 * Connection state
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Audio encoding format
 */
enum class AudioEncoding {
    PCM_16BIT,   // Raw PCM, minimal latency, higher bandwidth
    PCM_8BIT     // 8-bit PCM, lower quality, lower bandwidth
}

/**
 * Audio sample rate presets
 */
enum class SampleRate(val hz: Int) {
    RATE_8000(8000),
    RATE_16000(16000),
    RATE_44100(44100),
    RATE_48000(48000)
}

/**
 * WiFi connection configuration
 */
data class WifiConfig(
    val ipAddress: String = "",
    val port: Int = 5513,
    val protocol: WifiProtocol = WifiProtocol.UDP
)

enum class WifiProtocol {
    UDP,   // Low latency, preferred
    TCP    // Reliable, slightly higher latency
}

/**
 * Audio streaming configuration
 */
data class AudioConfig(
    val sampleRate: SampleRate = SampleRate.RATE_44100,
    val encoding: AudioEncoding = AudioEncoding.PCM_16BIT,
    val channelCount: Int = 1  // Mono for mic
)

/**
 * USB connection configuration (via ADB TCP forwarding)
 */
data class UsbConfig(
    val port: Int = 5514
)

/**
 * Overall app connection state exposed to UI
 */
data class MicCastUiState(
    val connectionMethod: ConnectionMethod = ConnectionMethod.WIFI,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val wifiConfig: WifiConfig = WifiConfig(),
    val usbConfig: UsbConfig = UsbConfig(),
    val audioConfig: AudioConfig = AudioConfig(),
    val audioLevel: Float = 0f,        // 0..1 for VU meter
    val statusMessage: String = "",
    val errorMessage: String? = null
)
