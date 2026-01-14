package com.phoneagent.remote.data

/**
 * 配置数据类
 */
data class Config(
    val serverIp: String = "",
    val serverPort: Int = 7000,
    val frpToken: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val remotePort: Int = 6104,
    val wsServerUrl: String = "",
    val autoStart: Boolean = true,
    val enableNotification: Boolean = true,
    val enableWebSocket: Boolean = true
) {
    
    /**
     * 是否已配置
     */
    fun isConfigured(): Boolean {
        return serverIp.isNotEmpty() && 
               frpToken.isNotEmpty() && 
               deviceId.isNotEmpty()
    }
    
    /**
     * 获取 WebSocket URL
     */
    fun getWebSocketUrl(): String {
        return if (wsServerUrl.isNotEmpty()) {
            wsServerUrl
        } else {
            "ws://$serverIp:9999"
        }
    }
    
    companion object {
        /**
         * 生成默认设备 ID
         */
        fun generateDeviceId(remotePort: Int): String {
            return "device_$remotePort"
        }
        
        /**
         * 生成默认设备名称
         */
        fun generateDeviceName(model: String, serial: String): String {
            val cleanModel = model.replace(" ", "_")
            val shortSerial = serial.takeLast(4)
            return "${cleanModel}_${shortSerial}"
        }
    }
}

