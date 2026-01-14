package com.phoneagent.remote.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.phoneagent.remote.data.ConfigRepository
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Remote 配置提供者
 * 
 * 允许其他应用（如 Voice App）查询 Remote 的配置信息
 * 这是标准的 Android 跨应用通信机制
 * 
 * 使用方式:
 * ```kotlin
 * val uri = Uri.parse("content://com.phoneagent.remote.provider/config")
 * val cursor = contentResolver.query(uri, null, null, null, null)
 * cursor?.use {
 *     while (it.moveToNext()) {
 *         val key = it.getString(0)
 *         val value = it.getString(1)
 *         Log.d("RemoteConfig", "$key = $value")
 *     }
 * }
 * ```
 * 
 * 提供的配置信息:
 * - device_id: 设备 ID
 * - device_name: 设备名称
 * - server_ip: 服务器地址
 * - server_port: 服务器端口
 * - remote_port: 远程端口
 * - ws_server_url: WebSocket 服务器地址
 * - frp_connected: FRP 连接状态 (true/false)
 * - ws_connected: WebSocket 连接状态 (true/false)
 * - service_running: 服务运行状态 (true/false)
 */
class RemoteConfigProvider : ContentProvider() {
    
    companion object {
        private const val TAG = "RemoteConfigProvider"
        
        const val AUTHORITY = "com.phoneagent.remote.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/config")
        
        // 列名
        const val COLUMN_KEY = "key"
        const val COLUMN_VALUE = "value"
    }
    
    private lateinit var configRepository: ConfigRepository
    
    override fun onCreate(): Boolean {
        Timber.tag(TAG).d("RemoteConfigProvider onCreate")
        
        context?.let {
            configRepository = ConfigRepository(it)
            return true
        }
        
        return false
    }
    
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Timber.tag(TAG).d("Query config from: ${callingPackage ?: "unknown"}")
        
        val cursor = MatrixCursor(arrayOf(COLUMN_KEY, COLUMN_VALUE))
        
        try {
            // 使用 runBlocking 来调用 suspend 函数
            // 这在 ContentProvider 中是可以接受的，因为查询操作应该很快
            val config = runBlocking {
                configRepository.getConfig()
            }
            
            // 添加配置信息
            cursor.addRow(arrayOf("device_id", config.deviceId))
            cursor.addRow(arrayOf("device_name", config.deviceName))
            cursor.addRow(arrayOf("server_ip", config.serverIp))
            cursor.addRow(arrayOf("server_port", config.serverPort.toString()))
            cursor.addRow(arrayOf("remote_port", config.remotePort.toString()))
            cursor.addRow(arrayOf("ws_server_url", config.wsServerUrl))
            
            // 添加运行状态
            // 注意: 这些状态是静态的，实际状态需要从 Service 获取
            // 如果 Service 没有运行，这些值可能不准确
            cursor.addRow(arrayOf("frp_connected", "false"))
            cursor.addRow(arrayOf("ws_connected", "false"))
            cursor.addRow(arrayOf("service_running", "false"))
            
            Timber.tag(TAG).d("Config provided successfully")
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to provide config")
        }
        
        return cursor
    }
    
    // 以下方法不支持（只读 Provider）
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Timber.tag(TAG).w("Insert not supported")
        return null
    }
    
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        Timber.tag(TAG).w("Update not supported")
        return 0
    }
    
    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        Timber.tag(TAG).w("Delete not supported")
        return 0
    }
    
    override fun getType(uri: Uri): String? {
        return "vnd.android.cursor.dir/vnd.$AUTHORITY.config"
    }
}
