package com.phoneagent.remote.core

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.phoneagent.remote.R
import com.phoneagent.remote.data.Config
import com.phoneagent.remote.data.ConfigRepository
import com.phoneagent.remote.termux.TermuxBootstrapManager
import com.phoneagent.remote.termux.TermuxExecutor
import com.phoneagent.remote.ui.MainActivity
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * 远程控制服务
 * 核心前台服务，负责：
 * 1. 启动和管理 FRP 客户端
 * 2. 启动和管理 WebSocket 客户端
 * 3. 启动和管理 ADB Server
 * 4. 安装和管理 yadb 工具
 * 5. 进程监控和自动重启
 * 6. 前台通知保活
 * 7. 🆕 WakeLock 保持 CPU 运行
 */
class RemoteControlService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private lateinit var frpManager: FrpManager
    private var wsManager: WebSocketManager? = null
    
    private var processMonitorJob: Job? = null
    private var config: Config? = null
    
    private var serviceStartTime: Long = 0
    private var statusBroadcastJob: Job? = null
    
    // 🆕 WakeLock 防止 CPU 休眠
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        android.util.Log.e(TAG, "━━━━━━ RemoteControlService onCreate() ━━━━━━")
        Timber.tag(TAG).e("RemoteControlService onCreate")
        
        // 🆕 获取 WakeLock
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PhoneAgent::RemoteControlWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            android.util.Log.e(TAG, "🔋 WakeLock acquired")
            Timber.tag(TAG).i("🔋 WakeLock acquired - CPU will not sleep")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "⚠️ Failed to acquire WakeLock", e)
            Timber.tag(TAG).w(e, "⚠️ Failed to acquire WakeLock")
        }
        
        // 初始化 FRP 管理器
        try {
            android.util.Log.e(TAG, "Initializing FrpManager...")
            frpManager = FrpManager(this)
            android.util.Log.e(TAG, "✅ FrpManager initialized")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌❌❌ FATAL: Failed to initialize FrpManager", e)
            throw e
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START -> {
                    Timber.tag(TAG).i("Starting remote control service...")
                    try {
                        startForeground(NOTIFICATION_ID, buildNotification("正在启动..."))
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "❌ Failed to start foreground service")
                        // 尝试不使用前台服务继续（降级处理）
                    }
                    
                    // 启动保活组件
                    startKeepAliveComponents()
                    
                    // 启动主服务
                    startServices()
                }
                ACTION_STOP -> {
                    Timber.tag(TAG).i("Stopping remote control service...")
                    stopServices()
                    stopSelf()
                }
                ACTION_RESTART -> {
                    Timber.tag(TAG).i("Restarting remote control service...")
                    stopServices()
                    startServices()
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Error in onStartCommand")
        }
        
        // START_STICKY: 被杀后自动重启
        return START_STICKY
    }
    
    /**
     * 启动保活组件
     * 🆕 增加 WorkManager 作为补充保活机制
     */
    private fun startKeepAliveComponents() {
        try {
            Timber.tag(TAG).i("🛡️ Starting keep-alive components...")
            
            // 1. 启动守护进程（独立进程）
            com.phoneagent.remote.keepalive.GuardService.start(this)
            Timber.tag(TAG).d("  ✅ Guard service started")
            
            // 2. 启动 JobScheduler（系统调度）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                com.phoneagent.remote.keepalive.KeepAliveJobService.schedule(this)
                Timber.tag(TAG).d("  ✅ JobScheduler scheduled")
            }
            
            // 🆕 3. 启动 WorkManager（长期保活）
            com.phoneagent.remote.keepalive.KeepAliveWorker.schedule(this)
            Timber.tag(TAG).d("  ✅ WorkManager scheduled")
            
            Timber.tag(TAG).i("✅ All keep-alive components started (Guard + JobScheduler + WorkManager)")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to start keep-alive components")
        }
    }
    
    /**
     * 启动所有服务
     */
    private fun startServices() {
        serviceScope.launch {
            try {
                android.util.Log.e(TAG, "━━━━━━ startServices() BEGIN ━━━━━━")
                
                // 加载配置
                android.util.Log.e(TAG, "Loading configuration...")
                val configRepo = ConfigRepository(this@RemoteControlService)
                config = configRepo.getConfig()
                
                // 🔍 详细打印配置信息
                Timber.tag(TAG).e("━━━━━━ CONFIG LOADED ━━━━━━")
                Timber.tag(TAG).e("  Device ID: ${config?.deviceId}")
                Timber.tag(TAG).e("  Device Name: ${config?.deviceName}")
                Timber.tag(TAG).e("  Server IP: ${config?.serverIp}")
                Timber.tag(TAG).e("  Server Port: ${config?.serverPort}")
                Timber.tag(TAG).e("  Remote Port: ${config?.remotePort}")
                Timber.tag(TAG).e("  FRP Token: ${config?.frpToken}")
                Timber.tag(TAG).e("  WebSocket URL: ${config?.wsServerUrl}")
                Timber.tag(TAG).e("━━━━━━━━━━━━━━━━━━━━━━━━")
                
                if (config?.isConfigured() != true) {
                    android.util.Log.e(TAG, "❌ Config not found, stopping service")
                    Timber.tag(TAG).e("❌ Config not found, stopping service")
                    updateNotification("配置未完成")
                    stopSelf()
                    return@launch
                }
                
                android.util.Log.e(TAG, "✅ Config loaded: ${config?.deviceId}")
                Timber.tag(TAG).e("Config loaded: ${config?.deviceId}")
                
                // 启动 FRP 客户端（直接映射 adbd TCP 端口 5555）
                // 注意: 需要提前通过 USB 执行 `adb tcpip 5555` 启用 adbd 的 TCP 模式
                android.util.Log.e(TAG, "🚀 Starting FRP client...")
                Timber.tag(TAG).e("🚀 Starting FRP client...")
                Timber.tag(TAG).e("   Mapping adbd port 5555 → remote port ${config!!.remotePort}")
                
                val frpResult = frpManager.start(
                    serverIp = config!!.serverIp,
                    serverPort = config!!.serverPort,
                    token = config!!.frpToken,
                    localPort = 5555,  // adbd TCP 端口（需提前通过 USB 执行 adb tcpip 5555）
                    remotePort = config!!.remotePort,
                    deviceName = config!!.deviceName
                )
                
                if (frpResult.isFailure) {
                    val error = frpResult.exceptionOrNull()
                    android.util.Log.e(TAG, "❌ FRP start failed: ${error?.message}", error)
                    Timber.tag(TAG).e(error, "❌ FRP start failed")
                    updateNotification("FRP启动失败")
                    return@launch
                }
                
                android.util.Log.e(TAG, "✅ FRP client started successfully")
                delay(2000)
                
                // 2. 安装 yadb 工具（用于强制截图等功能）
                Timber.tag(TAG).i("2️⃣ Installing yadb tool...")
                try {
                    val termuxBootstrap = frpManager.getTermuxBootstrap()
                    val termuxExecutor = frpManager.getTermuxExecutor()
                    val yadbInstaller = YadbInstaller(this@RemoteControlService, termuxBootstrap, termuxExecutor)
                    
                    val yadbResult = yadbInstaller.ensureInstalled()
                    if (yadbResult.isSuccess) {
                        Timber.tag(TAG).i("✅ yadb installed successfully")
                    } else {
                        Timber.tag(TAG).w("⚠️ yadb installation failed: ${yadbResult.exceptionOrNull()?.message}")
                        // yadb 安装失败不影响主流程，继续运行
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "⚠️ yadb installation error")
                    // 继续运行，yadb 不是必需的
                }
                
                // 3. 启动 WebSocket 客户端（如果启用）
                if (config!!.enableWebSocket) {
                    Timber.tag(TAG).i("2️⃣ Starting WebSocket client...")
                    wsManager = WebSocketManager(
                        context = this@RemoteControlService,
                        serverUrl = config!!.getWebSocketUrl(),
                        deviceId = config!!.deviceId,
                        frpPort = config!!.remotePort,
                        deviceName = config!!.deviceName
                    ).apply {
                        // 设置状态变化回调
                        onStatusChanged = { status ->
                            broadcastCurrentStatus()
                        }
                    }
                    wsManager?.connect()
                    delay(1000)
                }
                
                // 记录启动时间
                serviceStartTime = System.currentTimeMillis()
                
                // 启动状态广播
                startStatusBroadcast()
                
                // 4. 启动进程监控
                Timber.tag(TAG).i("4️⃣ Starting process monitor...")
                startProcessMonitor()
                
                // 更新通知 - 使用用户友好的文本
                updateNotification("服务运行中，设备已连接")
                
                android.util.Log.e(TAG, "━━━━━━ startServices() SUCCESS ━━━━━━")
                Timber.tag(TAG).e("✅ All services started successfully")
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "━━━━━━ startServices() FAILED ━━━━━━", e)
                android.util.Log.e(TAG, "Error type: ${e.javaClass.name}")
                android.util.Log.e(TAG, "Error message: ${e.message}")
                Timber.tag(TAG).e(e, "❌ Failed to start services")
                updateNotification("启动失败")
            }
        }
    }
    
    /**
     * 停止所有服务
     */
    private fun stopServices() {
        statusBroadcastJob?.cancel()
        statusBroadcastJob = null
        
        processMonitorJob?.cancel()
        processMonitorJob = null
        
        wsManager?.disconnect()
        wsManager = null
        
        frpManager.stop()
        
        Timber.tag(TAG).i("✅ All services stopped")
    }
    
    /**
     * 启动进程监控
     * 🆕 优化：提高监控频率，增加守护服务检查
     */
    private fun startProcessMonitor() {
        processMonitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 1. 检查 FRP 进程
                    if (!frpManager.isRunning()) {
                        Timber.tag(TAG).w("⚠️ FRP process died, restarting...")
                        config?.let {
                            frpManager.start(
                                serverIp = it.serverIp,
                                serverPort = it.serverPort,
                                token = it.frpToken,
                                localPort = 5555,  // adbd TCP 端口
                                remotePort = it.remotePort,
                                deviceName = it.deviceName
                            )
                        }
                    }
                    
                    // 2. 检查 WebSocket 连接
                    if (config?.enableWebSocket == true && wsManager?.isConnected() != true) {
                        Timber.tag(TAG).w("⚠️ WebSocket disconnected, reconnecting...")
                        wsManager?.connect()
                    }
                    
                    // 🆕 3. 检查 GuardService 是否存活
                    if (!isGuardServiceRunning()) {
                        Timber.tag(TAG).w("⚠️ GuardService died, restarting...")
                        com.phoneagent.remote.keepalive.GuardService.start(this@RemoteControlService)
                    }
                    
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Process monitor error")
                }
                
                // 🆕 每 15 秒检查一次（从 30 秒改为 15 秒，更快发现问题）
                delay(15_000)
            }
        }
        
        Timber.tag(TAG).i("✅ Process monitor started (interval: 15s)")
    }
    
    /**
     * 🆕 检查守护服务是否运行
     */
    private fun isGuardServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (com.phoneagent.remote.keepalive.GuardService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    /**
     * 构建通知
     * 🆕 优化：提高优先级，确保服务不被杀死
     */
    private fun buildNotification(text: String): Notification {
        createNotificationChannel()
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneAgent 远程控制")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // 🆕 持续通知，不可清除
            .setAutoCancel(false)  // 🆕 点击后不消失
            .setPriority(NotificationCompat.PRIORITY_MAX)  // 🆕 提高为最高优先级
            .setCategory(NotificationCompat.CATEGORY_SERVICE)  // 🆕 服务类别
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // 🆕 锁屏可见
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)  // 🆕 立即显示
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }
    
    /**
     * 创建通知渠道
     * 🆕 优化：提高重要性级别
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "远程控制服务",
                NotificationManager.IMPORTANCE_HIGH  // 🆕 提高为 HIGH（保证不被静默）
            ).apply {
                description = "保持远程控制连接，允许 AI 控制手机"
                setShowBadge(false)  // 🆕 不显示角标
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC  // 🆕 锁屏可见
                setSound(null, null)  // 🆕 静音（避免骚扰）
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 启动状态广播
     * 每5秒广播一次当前状态
     */
    private fun startStatusBroadcast() {
        statusBroadcastJob = serviceScope.launch {
            while (isActive) {
                try {
                    broadcastCurrentStatus()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to broadcast status")
                }
                delay(5000) // 每5秒广播一次
            }
        }
    }
    
    /**
     * 广播当前状态
     */
    private fun broadcastCurrentStatus() {
        val frpStatus = if (frpManager.isRunning()) {
            ServiceStatusBroadcaster.STATUS_RUNNING
        } else {
            ServiceStatusBroadcaster.STATUS_STOPPED
        }
        
        val wsStatus = when {
            wsManager == null -> ServiceStatusBroadcaster.STATUS_STOPPED
            wsManager?.isConnected() == true -> ServiceStatusBroadcaster.STATUS_RUNNING
            else -> ServiceStatusBroadcaster.STATUS_CONNECTING
        }
        
        val uptime = if (serviceStartTime > 0) {
            System.currentTimeMillis() - serviceStartTime
        } else {
            0L
        }
        
        ServiceStatusBroadcaster.broadcastStatus(
            context = this,
            frpStatus = frpStatus,
            wsStatus = wsStatus,
            uptime = uptime
        )
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        // 🆕 释放 WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                android.util.Log.e(TAG, "🔋 WakeLock released")
                Timber.tag(TAG).i("🔋 WakeLock released")
            }
        }
        wakeLock = null
        
        super.onDestroy()
        serviceScope.cancel()
        stopServices()
        Timber.tag(TAG).i("RemoteControlService onDestroy")
    }
    
    companion object {
        private const val TAG = "RemoteControlService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "remote_control_service"
        
        const val ACTION_START = "com.phoneagent.remote.ACTION_START"
        const val ACTION_STOP = "com.phoneagent.remote.ACTION_STOP"
        const val ACTION_RESTART = "com.phoneagent.remote.ACTION_RESTART"
        
        /**
         * 启动服务（便捷方法）
         */
        fun start(context: Context) {
            val intent = Intent(context, RemoteControlService::class.java)
            intent.action = ACTION_START
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 停止服务（便捷方法）
         */
        fun stop(context: Context) {
            val intent = Intent(context, RemoteControlService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }
    }
}

