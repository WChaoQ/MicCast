package com.miccast.network

import android.util.Log
import com.miccast.audio.OPUS_FRAME_SAMPLES
import com.miccast.audio.OPUS_SAMPLE_RATE
import com.miccast.model.UsbConfig
import com.miccast.model.WifiConfig
import com.miccast.model.WifiProtocol
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer

private const val TAG = "NetworkSender"
private const val OPUS_OUTPUT_BUF = 1500

/**
 * 包格式：[ 4字节序号 Big-Endian ][ Opus 压缩数据 ]
 * PC 端靠序号还原顺序，靠 Opus 帧边界对齐解码。
 */

// ─────────────────────────────────────────────────────────
//  公共接口
// ─────────────────────────────────────────────────────────

interface AudioSender {
    suspend fun send(data: ByteArray, size: Int): Unit
    suspend fun close(): Unit
}

// ─────────────────────────────────────────────────────────
//  Opus 编码辅助（UDP / TCP sender 共用）
// ─────────────────────────────────────────────────────────

private class OpusPacketBuilder {

    private val encoder = OpusEncoder(
        OPUS_SAMPLE_RATE,
        1,
        OpusApplication.OPUS_APPLICATION_VOIP
    ).apply {
        bitrate    = 32_000
        complexity = 5
    }

    private val outputBuf = ByteArray(OPUS_OUTPUT_BUF)
    private val pcmShorts = ShortArray(OPUS_FRAME_SAMPLES)
    private var sequence  = 0

    fun encode(pcmBytes: ByteArray): ByteArray {
        for (i in pcmShorts.indices) {
            val lo = pcmBytes[i * 2].toInt() and 0xFF
            val hi = pcmBytes[i * 2 + 1].toInt()
            pcmShorts[i] = ((hi shl 8) or lo).toShort()
        }
        val encodedLen = encoder.encode(
            pcmShorts, 0, OPUS_FRAME_SAMPLES,
            outputBuf, 0, outputBuf.size
        )
        return ByteBuffer.allocate(4 + encodedLen).apply {
            putInt(sequence++)
            put(outputBuf, 0, encodedLen)
        }.array()
    }
}

// ─────────────────────────────────────────────────────────
//  WiFi UDP sender
// ─────────────────────────────────────────────────────────

class WifiUdpSender(private val config: WifiConfig) : AudioSender {

    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null
    private val opus = OpusPacketBuilder()

    suspend fun connect(): Unit = withContext(Dispatchers.IO) {
        address = InetAddress.getByName(config.ipAddress)
        socket  = DatagramSocket()
        Log.d(TAG, "UDP socket 就绪 → ${config.ipAddress}:${config.port}")
    }

    override suspend fun send(data: ByteArray, size: Int): Unit = withContext(Dispatchers.IO) {
        val sock = socket  ?: return@withContext
        val addr = address ?: return@withContext
        val packet = opus.encode(data)
        sock.send(DatagramPacket(packet, packet.size, addr, config.port))
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
        Log.d(TAG, "UDP socket 已关闭")
    }
}

// ─────────────────────────────────────────────────────────
//  WiFi TCP sender
// ─────────────────────────────────────────────────────────

class WifiTcpSender(private val config: WifiConfig) : AudioSender {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private val opus = OpusPacketBuilder()

    suspend fun connect(): Unit = withContext(Dispatchers.IO) {
        val sock = Socket(config.ipAddress, config.port).apply {
            tcpNoDelay = true
            setPerformancePreferences(0, 1, 0)
        }
        outputStream = sock.getOutputStream()
        socket = sock
        Log.d(TAG, "TCP 已连接 → ${config.ipAddress}:${config.port}")
    }

    override suspend fun send(data: ByteArray, size: Int): Unit = withContext(Dispatchers.IO) {
        try {
            val packet = opus.encode(data)
            // TCP 流需要长度前缀，方便接收端分帧
            val lenPrefix = ByteBuffer.allocate(2).putShort(packet.size.toShort()).array()
            outputStream?.write(lenPrefix)
            outputStream?.write(packet)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "TCP 发送错误: ${e.message}")
            throw e
        }
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        outputStream?.close()
        socket?.close()
        socket = null
        outputStream = null
        Log.d(TAG, "TCP socket 已关闭")
    }
}

// ─────────────────────────────────────────────────────────
//  USB Sender (ADB TCP 转发)
//
//  原理：
//   1. PC 执行：adb forward tcp:5514 tcp:5514
//   2. 手机连接自身 127.0.0.1:5514
//   3. ADB 将流量透明转发到 PC 的接收程序
// ─────────────────────────────────────────────────────────

class UsbTcpSender(private val config: UsbConfig) : AudioSender {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private val opus = OpusPacketBuilder()

    suspend fun connect(): Unit = withContext(Dispatchers.IO) {
        // ADB forward 后手机连接自己的 localhost 即可
        // 必须使用 Inet4Address 强制 IPv4，避免某些 Android 版本优先选择
        // IPv6 栈连接到 ::1 而非 127.0.0.1，导致 ADB forward 无法路由
        val ipv4Addr = java.net.Inet4Address.getByAddress(byteArrayOf(127, 0, 0, 1))
        val sock = Socket().apply {
            tcpNoDelay = true
            setPerformancePreferences(0, 1, 0)
            connect(java.net.InetSocketAddress(ipv4Addr, config.port), 5000)
        }
        outputStream = sock.getOutputStream()
        socket = sock
        Log.d(TAG, "USB(ADB) TCP 已连接 → 127.0.0.1:${config.port}")
    }

    override suspend fun send(data: ByteArray, size: Int): Unit = withContext(Dispatchers.IO) {
        try {
            val packet = opus.encode(data)
            // 帧格式: [2字节长度][4字节序号][Opus数据]
            val lenPrefix = ByteBuffer.allocate(2).putShort(packet.size.toShort()).array()
            outputStream?.write(lenPrefix)
            outputStream?.write(packet)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "USB TCP 发送错误: ${e.message}")
            throw e
        }
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        outputStream?.close()
        socket?.close()
        socket = null
        outputStream = null
        Log.d(TAG, "USB TCP socket 已关闭")
    }
}

// ─────────────────────────────────────────────────────────
//  工厂
// ─────────────────────────────────────────────────────────

object AudioSenderFactory {
    suspend fun create(config: WifiConfig): AudioSender = when (config.protocol) {
        WifiProtocol.UDP -> WifiUdpSender(config).also { it.connect() }
        WifiProtocol.TCP -> WifiTcpSender(config).also { it.connect() }
    }

    suspend fun createUsbTcp(config: UsbConfig): AudioSender =
        UsbTcpSender(config).also { it.connect() }
}