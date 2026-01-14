package com.phoneagent.remote.core

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WebSocket 管理器
 * 使用 Kotlin + OkHttp 实现，替代 Python 脚本
 */
class WebSocketManager(
    private val context: Context,
    private val serverUrl: String,
    private val deviceId: String,
    private val frpPort: Int,
    private val deviceName: String = "Android Device"
) {
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // 无超时，保持长连接
        .pingInterval(30, TimeUnit.SECONDS)  // 心跳检测
        .build()
    
    private var isConnecting = false
    private var reconnectJob: Job? = null
    
    // 连接状态回调
    var onStatusChanged: ((String) -> Unit)? = null
    
    /**
     * 连接到 WebSocket 服务器
     */
    fun connect() {
        if (isConnecting || webSocket != null) {
            Timber.tag(TAG).w("Already connecting or connected")
            return
        }
        
        isConnecting = true
        
        // 广播连接中状态
        onStatusChanged?.invoke(ServiceStatusBroadcaster.STATUS_CONNECTING)
        
        try {
            // serverUrl 已经包含完整路径，直接使用即可
            // 例如: wss://phoneagent.waytomaster.com/device-ws/device/6100
            val url = serverUrl
            Timber.tag(TAG).i("Connecting to WebSocket: $url")
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isConnecting = false
                    Timber.tag(TAG).i("✅ WebSocket connected")
                    
                    // 广播状态变化
                    onStatusChanged?.invoke(ServiceStatusBroadcaster.STATUS_RUNNING)
                    
                    // 发送注册消息
                    sendRegisterMessage()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Timber.tag(TAG).d("Received message: $text")
                    handleMessage(text)
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Timber.tag(TAG).w("WebSocket closing: code=$code, reason=$reason")
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isConnecting = false
                    Timber.tag(TAG).w("WebSocket closed: code=$code, reason=$reason")
                    
                    // 广播状态变化
                    onStatusChanged?.invoke(ServiceStatusBroadcaster.STATUS_STOPPED)
                    
                    scheduleReconnect()
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isConnecting = false
                    Timber.tag(TAG).w(t, "WebSocket connection failed")
                    
                    // 广播状态变化
                    onStatusChanged?.invoke(ServiceStatusBroadcaster.STATUS_ERROR)
                    
                    scheduleReconnect()
                }
            })
            
        } catch (e: Exception) {
            isConnecting = false
            Timber.tag(TAG).w(e, "Failed to create WebSocket connection")
            scheduleReconnect()
        }
    }
    
    /**
     * 发送注册消息
     * 
     * 服务器期望的消息格式：
     * {
     *   "type": "device_online",
     *   "specs": {
     *     "device_id": "device_6100",
     *     "frp_port": 6100,
     *     "device_name": "My Phone",
     *     "model": "Xiaomi 12",
     *     "android_version": "13",
     *     "screen_resolution": "1080x2400",
     *     "battery": 85,
     *     "network": "wifi"
     *   }
     * }
     */
    private fun sendRegisterMessage() {
        try {
            // 收集设备信息
            val specs = JSONObject().apply {
                put("device_id", deviceId)
                put("frp_port", frpPort)
                put("device_name", deviceName)
                put("device_type", "android")  // ✅ 明确标识为 Android 设备（与服务端保持一致）
                put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("android_version", Build.VERSION.RELEASE)
                put("screen_resolution", getScreenResolution())
                put("battery", getBatteryLevel())
                put("network", getNetworkType())
            }
            
            val registerMsg = JSONObject().apply {
                put("type", "device_online")  // ← 关键：必须是 "device_online"
                put("specs", specs)
            }
            
            webSocket?.send(registerMsg.toString())
            Timber.tag(TAG).d("Sent register message")
            Timber.tag(TAG).d("Device specs: ${specs.toString()}")
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send register message")
        }
    }
    
    /**
     * 获取屏幕分辨率
     */
    private fun getScreenResolution(): String {
        return try {
            val displayMetrics = context.resources.displayMetrics
            "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * 获取电池电量
     */
    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        } catch (e: Exception) {
            100
        }
    }
    
    /**
     * 获取网络类型
     */
    private fun getNetworkType(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
            
            when {
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
                else -> "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * 处理收到的消息
     */
    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "")
            
            when (type) {
                "registered" -> {
                    // 服务器注册确认
                    val confirmedDeviceId = json.optString("device_id", "")
                    val confirmedFrpPort = json.optInt("frp_port", 0)
                    val message = json.optString("message", "")
                    Timber.tag(TAG).i("✅ Device registered successfully")
                    Timber.tag(TAG).i("   Device ID: $confirmedDeviceId")
                    Timber.tag(TAG).i("   FRP Port: $confirmedFrpPort")
                    Timber.tag(TAG).i("   Message: $message")
                }
                "ping" -> {
                    // 心跳检测
                    sendPong()
                }
                "command" -> {
                    // 接收服务器命令（当前项目中未使用）
                    val command = json.optString("command", "")
                    Timber.tag(TAG).i("Received command: $command")
                    // TODO: 执行命令（预留）
                }
                "task_update" -> {
                    // 任务状态更新（预留）
                    Timber.tag(TAG).d("Received task update")
                }
                else -> {
                    Timber.tag(TAG).w("Unknown message type: $type")
                }
            }
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to handle message")
        }
    }
    
    /**
     * 发送 Pong 响应
     */
    private fun sendPong() {
        try {
            val pongMsg = JSONObject().apply {
                put("type", "pong")
            }
            webSocket?.send(pongMsg.toString())
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send pong")
        }
    }
    
    /**
     * 计划重连
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.Main).launch {
            delay(RECONNECT_DELAY)
            Timber.tag(TAG).i("Reconnecting...")
            connect()
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        
        Timber.tag(TAG).i("✅ WebSocket disconnected")
    }
    
    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
        return webSocket != null
    }
    
    companion object {
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY = 5000L  // 5 秒后重连
    }
}

