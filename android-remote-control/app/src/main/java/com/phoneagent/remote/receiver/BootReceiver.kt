package com.phoneagent.remote.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phoneagent.remote.core.RemoteControlService
import com.phoneagent.remote.data.ConfigRepository
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * 开机自启接收器
 * 替代 Termux:Boot
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.tag(TAG).i("Device booted, checking config...")
            
            // 异步检查配置
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val configRepo = ConfigRepository(context)
                    val config = configRepo.getConfig()
                    
                    if (config.isConfigured() && config.autoStart) {
                        Timber.tag(TAG).i("Auto-starting RemoteControlService...")
                        RemoteControlService.start(context)
                    } else {
                        Timber.tag(TAG).w("Config not found or auto-start disabled")
                    }
                    
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to start service on boot")
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "BootReceiver"
    }
}

