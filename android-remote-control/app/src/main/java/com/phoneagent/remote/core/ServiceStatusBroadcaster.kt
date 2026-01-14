package com.phoneagent.remote.core

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * 服务状态广播器
 * 
 * 用于在 Service 和 Activity 之间传递状态更新
 */
object ServiceStatusBroadcaster {
    
    const val ACTION_STATUS_UPDATE = "com.phoneagent.remote.STATUS_UPDATE"
    
    const val EXTRA_FRP_STATUS = "frp_status"
    const val EXTRA_WS_STATUS = "ws_status"
    const val EXTRA_UPTIME = "uptime"
    
    // 状态值
    const val STATUS_RUNNING = "running"
    const val STATUS_STOPPED = "stopped"
    const val STATUS_CONNECTING = "connecting"
    const val STATUS_ERROR = "error"
    
    /**
     * 广播状态更新
     */
    fun broadcastStatus(
        context: Context,
        frpStatus: String,
        wsStatus: String,
        uptime: Long = 0
    ) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_FRP_STATUS, frpStatus)
            putExtra(EXTRA_WS_STATUS, wsStatus)
            putExtra(EXTRA_UPTIME, uptime)
        }
        
        // 使用 LocalBroadcastManager 发送本地广播
        context.sendBroadcast(intent)
    }
}

