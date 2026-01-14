package com.phoneagent.remote.keepalive

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.phoneagent.remote.R
import com.phoneagent.remote.core.RemoteControlService
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * 守护进程服务
 * 
 * 运行在独立进程中，监控主服务状态
 * 如果主服务被杀，立即重启
 * 
 * 保活策略：
 * - 独立进程运行（:guard）
 * - 前台服务保活
 * - 每 10 秒检查主服务
 * - START_STICKY 自动重启
 */
class GuardService : Service() {
    
    companion object {
        private const val TAG = "GuardService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "guard_service"
        private const val CHANNEL_NAME = "守护服务"
        
        private const val CHECK_INTERVAL = 10_000L // 10 秒检查一次
        
        fun start(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            context.stopService(intent)
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).i("🛡️ GuardService onCreate (守护进程启动)")
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 启动前台服务
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            Timber.tag(TAG).i("✅ GuardService started as foreground")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to start foreground")
        }
        
        // 开始监控主服务
        startMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).d("GuardService onStartCommand")
        
        // 确保监控任务在运行
        if (monitorJob?.isActive != true) {
            startMonitoring()
        }
        
        // START_STICKY: 被杀后系统会尝试重启
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).w("⚠️ GuardService onDestroy (守护进程被杀)")
        
        // 取消监控任务
        monitorJob?.cancel()
        serviceScope.cancel()
        
        // 尝试重启自己（如果是异常退出）
        try {
            val restartIntent = Intent(this, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to restart guard service")
        }
    }
    
    /**
     * 开始监控主服务
     */
    private fun startMonitoring() {
        Timber.tag(TAG).i("🔍 Starting to monitor main service...")
        
        monitorJob = serviceScope.launch {
            var checkCount = 0
            
            while (isActive) {
                try {
                    checkCount++
                    
                    // 检查主服务是否运行
                    val isMainServiceRunning = isServiceRunning(
                        this@GuardService,
                        RemoteControlService::class.java
                    )
                    
                    if (!isMainServiceRunning) {
                        Timber.tag(TAG).w("⚠️ Main service not running! Restarting... (check #$checkCount)")
                        restartMainService()
                    } else {
                        // 每 10 次检查记录一次日志（避免日志过多）
                        if (checkCount % 10 == 0) {
                            Timber.tag(TAG).d("✅ Main service is running (check #$checkCount)")
                        }
                    }
                    
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error during service check")
                }
                
                // 等待下次检查
                delay(CHECK_INTERVAL)
            }
        }
    }
    
    /**
     * 检查服务是否正在运行
     */
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            manager.getRunningServices(Integer.MAX_VALUE).any {
                it.service.className == serviceClass.name
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to check service status")
            false
        }
    }
    
    /**
     * 重启主服务
     */
    private fun restartMainService() {
        try {
            val intent = Intent(this, RemoteControlService::class.java).apply {
                action = RemoteControlService.ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            Timber.tag(TAG).i("✅ Main service restart initiated")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to restart main service")
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "守护服务保持运行"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 构建通知
     */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneAgent 守护")
            .setContentText("守护进程运行中")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}

