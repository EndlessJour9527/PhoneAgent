package com.phoneagent.remote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 全局单例 DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "phoneagent_config")

/**
 * 配置仓库
 * 使用 DataStore 持久化配置
 */
class ConfigRepository(private val context: Context) {
    
    /**
     * 配置 Flow
     */
    val configFlow: Flow<Config> = context.dataStore.data.map { preferences ->
        Config(
            serverIp = preferences[SERVER_IP] ?: "",
            serverPort = preferences[SERVER_PORT] ?: 7001,
            frpToken = preferences[FRP_TOKEN] ?: "",
            deviceId = preferences[DEVICE_ID] ?: "",
            deviceName = preferences[DEVICE_NAME] ?: "",
            remotePort = preferences[REMOTE_PORT] ?: 6104,  // 默认端口
            wsServerUrl = preferences[WS_SERVER_URL] ?: "",
            autoStart = preferences[AUTO_START] ?: true,
            enableNotification = preferences[ENABLE_NOTIFICATION] ?: true,
            enableWebSocket = preferences[ENABLE_WEBSOCKET] ?: true
        )
    }
    
    /**
     * 保存配置
     */
    suspend fun saveConfig(config: Config) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_IP] = config.serverIp
            preferences[SERVER_PORT] = config.serverPort
            preferences[FRP_TOKEN] = config.frpToken
            preferences[DEVICE_ID] = config.deviceId
            preferences[DEVICE_NAME] = config.deviceName
            preferences[REMOTE_PORT] = config.remotePort
            preferences[WS_SERVER_URL] = config.wsServerUrl
            preferences[AUTO_START] = config.autoStart
            preferences[ENABLE_NOTIFICATION] = config.enableNotification
            preferences[ENABLE_WEBSOCKET] = config.enableWebSocket
        }
    }
    
    /**
     * 获取配置（同步）
     */
    suspend fun getConfig(): Config {
        return configFlow.first()
    }
    
    /**
     * 清除配置
     */
    suspend fun clearConfig() {
        context.dataStore.edit { it.clear() }
    }
    
    companion object {
        private val SERVER_IP = stringPreferencesKey("server_ip")
        private val SERVER_PORT = intPreferencesKey("server_port")
        private val FRP_TOKEN = stringPreferencesKey("frp_token")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val DEVICE_NAME = stringPreferencesKey("device_name")
        private val REMOTE_PORT = intPreferencesKey("remote_port")
        private val WS_SERVER_URL = stringPreferencesKey("ws_server_url")
        private val AUTO_START = booleanPreferencesKey("auto_start")
        private val ENABLE_NOTIFICATION = booleanPreferencesKey("enable_notification")
        private val ENABLE_WEBSOCKET = booleanPreferencesKey("enable_websocket")
    }
}

