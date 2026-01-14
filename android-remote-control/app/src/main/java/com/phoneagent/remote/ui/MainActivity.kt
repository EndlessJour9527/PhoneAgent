package com.phoneagent.remote.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.phoneagent.remote.R
import com.phoneagent.remote.core.FrpManager
import com.phoneagent.remote.core.RemoteControlService
import com.phoneagent.remote.core.ServiceStatusBroadcaster
import com.phoneagent.remote.data.ConfigRepository
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 主界面 - 指挥中心风格
 * 显示设备状态、服务状态和快速操作
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var configRepository: ConfigRepository
    private lateinit var frpManager: FrpManager
    
    // UI 组件
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var tvRemotePort: TextView
    private lateinit var tvUptime: TextView
    private lateinit var tvFrpStatus: TextView
    private lateinit var tvWsStatus: TextView
    private lateinit var statusIndicator: View
    private lateinit var statusFrp: View
    private lateinit var statusWs: View
    private lateinit var btnStop: Button
    private lateinit var btnRestart: Button
    private lateinit var btnViewLogs: Button
    private lateinit var btnReconfigure: Button
    
    // 状态广播接收器
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ServiceStatusBroadcaster.ACTION_STATUS_UPDATE) {
                val frpStatus = intent.getStringExtra(ServiceStatusBroadcaster.EXTRA_FRP_STATUS)
                val wsStatus = intent.getStringExtra(ServiceStatusBroadcaster.EXTRA_WS_STATUS)
                val uptime = intent.getLongExtra(ServiceStatusBroadcaster.EXTRA_UPTIME, 0)
                
                updateServiceStatus(frpStatus, wsStatus, uptime)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ⭐ 强制设置 Window 背景和导航栏 - 必须在 setContentView 之前
        window.apply {
            // 设置 Window 背景色
            setBackgroundDrawableResource(R.color.background)
            // 设置导航栏颜色
            navigationBarColor = getColor(R.color.background)
            // 设置状态栏颜色
            statusBarColor = getColor(R.color.primary)
        }
        
        Timber.tag(TAG).e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.tag(TAG).e("PhoneAgent Remote by tmwgsicp")
        Timber.tag(TAG).e("MainActivity onCreate START")
        Timber.tag(TAG).e("Android Version: ${Build.VERSION.SDK_INT}")
        Timber.tag(TAG).e("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Timber.tag(TAG).e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // 🆕 请求电池优化豁免（延迟执行，避免阻塞初始化）
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1000)  // 延迟1秒
            requestBatteryOptimizationExemption()
        }
        
        try {
            Timber.tag(TAG).d("Step 1: setContentView")
            setContentView(R.layout.activity_main)
            
            Timber.tag(TAG).d("Step 2: initialize repositories")
            configRepository = ConfigRepository(this)
            frpManager = FrpManager(this)
            
            Timber.tag(TAG).d("Step 3: initViews")
            initViews()
            
            Timber.tag(TAG).d("Step 4: check configuration")
            // FRP 客户端已打包在 APK 中，无需下载
            // 直接检查配置状态
            lifecycleScope.launch {
                try {
                    Timber.tag(TAG).d("Step 4.1: verifying FRP binary in APK")
                    // 验证 FRP 二进制文件（应该已在 APK 中）
                    val verifyResult = frpManager.ensureFrpcAvailable()
                    if (verifyResult.isFailure) {
                        Timber.tag(TAG).e("❌ FRP binary not found in APK!")
                        Toast.makeText(
                            this@MainActivity,
                            "应用打包错误：FRP 客户端缺失，请重新安装",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return@launch
                    }
                    
                    Timber.tag(TAG).d("Step 4.2: loading config")
                    // 检查配置
                    val config = configRepository.getConfig()
                    
                    if (!config.isConfigured()) {
                        // 未配置，启动配置向导
                        Timber.tag(TAG).i("Config not found, starting setup wizard")
                        startSetupWizard()
                    } else {
                        // 已配置，加载数据并启动服务
                        Timber.tag(TAG).i("Config found: ${config.deviceId}")
                        Timber.tag(TAG).d("Step 4.3: loading device info")
                        loadDeviceInfo(config)
                        
                        Timber.tag(TAG).d("Step 4.4: checking notification permission")
                        // 检查并请求通知权限后启动服务
                        if (checkNotificationPermission()) {
                            Timber.tag(TAG).d("Step 4.5: starting service")
                            startRemoteControlService()
                            
                            // 显示白名单引导（首次启动）
                            showWhitelistGuideIfNeeded()
                        } else {
                            Timber.tag(TAG).w("Notification permission not granted yet")
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "❌ Error in onCreate coroutine")
                    Toast.makeText(this@MainActivity, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            Timber.tag(TAG).e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.tag(TAG).e("MainActivity onCreate SUCCESS")
            Timber.tag(TAG).e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌❌❌ FATAL: Error in onCreate")
            Timber.tag(TAG).e("Exception type: ${e.javaClass.name}")
            Timber.tag(TAG).e("Exception message: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    /**
     * 初始化视图
     */
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        tvStatus = findViewById(R.id.tv_status)
        tvDeviceId = findViewById(R.id.tv_device_id)
        tvDeviceName = findViewById(R.id.tv_device_name)
        tvRemotePort = findViewById(R.id.tv_remote_port)
        tvUptime = findViewById(R.id.tv_uptime)
        tvFrpStatus = findViewById(R.id.tv_frp_status)
        tvWsStatus = findViewById(R.id.tv_ws_status)
        statusIndicator = findViewById(R.id.status_indicator)
        statusFrp = findViewById(R.id.status_frp)
        statusWs = findViewById(R.id.status_ws)
        btnStop = findViewById(R.id.btn_stop)
        btnRestart = findViewById(R.id.btn_restart)
        btnViewLogs = findViewById(R.id.btn_view_logs)
        btnReconfigure = findViewById(R.id.btn_reconfigure)
        
        // 设置点击事件
        btnStop.setOnClickListener { onStopClicked() }
        btnRestart.setOnClickListener { onRestartClicked() }
        btnViewLogs.setOnClickListener { onViewLogsClicked() }
        btnReconfigure.setOnClickListener { onReconfigureClicked() }
        
        // 关于按钮
        findViewById<Button>(R.id.btn_about).setOnClickListener {
            Timber.tag(TAG).i("About button clicked")
            showAboutDialog()
        }
    }
    
    /**
     * 加载设备信息
     */
    private fun loadDeviceInfo(config: com.phoneagent.remote.data.Config) {
        try {
            tvDeviceId.text = config.deviceId.ifEmpty { "未设置" }
            tvDeviceName.text = config.deviceName.ifEmpty { "未设置" }
            tvRemotePort.text = "${config.serverIp}:${config.remotePort}"
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading device info")
            Toast.makeText(this, "加载设备信息失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 更新服务状态（从广播接收）
     */
    private fun updateServiceStatus(frpStatus: String?, wsStatus: String?, uptime: Long) {
        // 更新 FRP 状态
        when (frpStatus) {
            ServiceStatusBroadcaster.STATUS_RUNNING -> {
                tvFrpStatus.text = "运行中"
                tvFrpStatus.setTextColor(getColor(R.color.status_online))
                statusFrp.setBackgroundResource(R.drawable.status_indicator_online)
            }
            ServiceStatusBroadcaster.STATUS_STOPPED -> {
                tvFrpStatus.text = "已停止"
                tvFrpStatus.setTextColor(getColor(R.color.status_error))
                statusFrp.setBackgroundResource(R.drawable.status_indicator_offline)
            }
            else -> {
                tvFrpStatus.text = "未知"
                tvFrpStatus.setTextColor(getColor(R.color.status_unknown))
                statusFrp.setBackgroundResource(R.drawable.status_indicator_unknown)
            }
        }
        
        // 更新 WebSocket 状态
        when (wsStatus) {
            ServiceStatusBroadcaster.STATUS_RUNNING -> {
                tvWsStatus.text = "已连接"
                tvWsStatus.setTextColor(getColor(R.color.status_online))
                statusWs.setBackgroundResource(R.drawable.status_indicator_online)
            }
            ServiceStatusBroadcaster.STATUS_CONNECTING -> {
                tvWsStatus.text = "连接中..."
                tvWsStatus.setTextColor(getColor(R.color.status_warning))
                statusWs.setBackgroundResource(R.drawable.status_indicator_unknown)
            }
            ServiceStatusBroadcaster.STATUS_ERROR -> {
                tvWsStatus.text = "连接失败"
                tvWsStatus.setTextColor(getColor(R.color.status_error))
                statusWs.setBackgroundResource(R.drawable.status_indicator_offline)
            }
            ServiceStatusBroadcaster.STATUS_STOPPED -> {
                tvWsStatus.text = "未连接"
                tvWsStatus.setTextColor(getColor(R.color.text_secondary))
                statusWs.setBackgroundResource(R.drawable.status_indicator_unknown)
            }
        }
        
        // 更新运行时间和整体状态
        val isFrpRunning = frpStatus == ServiceStatusBroadcaster.STATUS_RUNNING
        val isWsConnected = wsStatus == ServiceStatusBroadcaster.STATUS_RUNNING
        
        when {
            isFrpRunning && isWsConnected -> {
                // FRP 和 WebSocket 都正常
                tvStatus.text = "设备在线"
                tvStatus.setTextColor(getColor(R.color.status_online))
                statusIndicator.setBackgroundResource(R.drawable.status_indicator_online)
                
                if (uptime > 0) {
                    val hours = uptime / 3600000
                    val minutes = (uptime % 3600000) / 60000
                    tvUptime.text = "运行中 ${hours}小时${minutes}分钟"
                } else {
                    tvUptime.text = "刚刚启动"
                }
            }
            isFrpRunning && !isWsConnected -> {
                // FRP 正常但 WebSocket 未连接
                tvStatus.text = "部分在线"
                tvStatus.setTextColor(getColor(R.color.status_warning))
                statusIndicator.setBackgroundResource(R.drawable.status_indicator_unknown)
                tvUptime.text = "等待连接服务器..."
            }
            !isFrpRunning -> {
                // FRP 未运行
                tvStatus.text = "设备离线"
                tvStatus.setTextColor(getColor(R.color.status_error))
                statusIndicator.setBackgroundResource(R.drawable.status_indicator_offline)
                tvUptime.text = "服务未启动"
            }
            else -> {
                // 其他情况
                tvStatus.text = "状态未知"
                tvStatus.setTextColor(getColor(R.color.status_unknown))
                statusIndicator.setBackgroundResource(R.drawable.status_indicator_unknown)
                tvUptime.text = "检查中..."
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 注册状态广播接收器
        val filter = IntentFilter(ServiceStatusBroadcaster.ACTION_STATUS_UPDATE)
        registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
    }
    
    override fun onPause() {
        super.onPause()
        // 取消注册广播接收器
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to unregister receiver")
        }
    }
    
    
    /**
     * 启动配置向导
     */
    private fun startSetupWizard() {
        val intent = Intent(this, SetupWizardActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    /**
     * 停止服务
     */
    private fun onStopClicked() {
        Timber.tag(TAG).i("Stop button clicked")
        
        RemoteControlService.stop(this)
        
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 重启服务
     */
    private fun onRestartClicked() {
        Timber.tag(TAG).i("Restart button clicked")
        
        val intent = Intent(this, RemoteControlService::class.java)
        intent.action = RemoteControlService.ACTION_RESTART
        startService(intent)
        
        Toast.makeText(this, "服务重启中...", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 查看日志
     */
    private fun onViewLogsClicked() {
        Timber.tag(TAG).i("View logs button clicked")
        
        val intent = Intent(this, LogViewerActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 重新配置
     */
    private fun onReconfigureClicked() {
        Timber.tag(TAG).i("Reconfigure button clicked")
        
        val intent = Intent(this, SetupWizardActivity::class.java)
        intent.putExtra("reconfigure", true)
        startActivity(intent)
    }
    
    /**
     * 检查并请求通知权限
     * @return true 如果已有权限或不需要权限，false 如果需要请求
     */
    private fun checkNotificationPermission(): Boolean {
        // Android 13 (API 33) 及以上需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.tag(TAG).i("Requesting notification permission")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
                return false
            }
        }
        return true
    }
    
    /**
     * 启动远程控制服务
     */
    private fun startRemoteControlService() {
        try {
            RemoteControlService.start(this@MainActivity)
            Timber.tag(TAG).i("✅ Remote control service started")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to start service")
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 显示白名单引导（首次启动）
     */
    private fun showWhitelistGuideIfNeeded() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hasShownGuide = prefs.getBoolean("has_shown_whitelist_guide", false)
        
        if (!hasShownGuide) {
            // 延迟 2 秒显示，避免干扰启动流程
            lifecycleScope.launch {
                kotlinx.coroutines.delay(2000)
                
                try {
                    com.phoneagent.remote.keepalive.WhitelistGuide.showGuide(this@MainActivity)
                    
                    // 标记已显示
                    prefs.edit().putBoolean("has_shown_whitelist_guide", true).apply()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to show whitelist guide")
                }
            }
        }
    }
    
    /**
     * 处理权限请求结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Timber.tag(TAG).i("✅ Notification permission granted")
                    startRemoteControlService()
                } else {
                    Timber.tag(TAG).w("⚠️ Notification permission denied")
                    Toast.makeText(
                        this,
                        "需要通知权限以保持后台服务运行",
                        Toast.LENGTH_LONG
                    ).show()
                    // 即使没有权限也尝试启动（降级处理）
                    startRemoteControlService()
                }
            }
        }
    }
    
    /**
     * 显示关于对话框（使用 BottomSheet 设计）
     */
    private fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        
        // 创建 BottomSheet 对话框
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_about, null)
        
        // 设置版本号
        view.findViewById<TextView>(R.id.tv_version).text = "v$version"
        
        // 作者主页点击事件
        view.findViewById<TextView>(R.id.tv_github_profile).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(getString(R.string.brand_github)))
            startActivity(intent)
        }
        
        // 商务合作点击事件（复制微信号）
        view.findViewById<TextView>(R.id.tv_business_contact).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("WeChat", "SZJishere")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "微信号已复制：SZJishere", Toast.LENGTH_LONG).show()
        }
        
        // Star CTA 点击事件
        view.findViewById<TextView>(R.id.tv_star_cta).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(getString(R.string.brand_project_url)))
            startActivity(intent)
        }
        
        // GitHub 按钮点击事件
        view.findViewById<View>(R.id.btn_github).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(getString(R.string.brand_project_url)))
            startActivity(intent)
            bottomSheet.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_close).setOnClickListener {
            bottomSheet.dismiss()
        }
        
        bottomSheet.setContentView(view)
        bottomSheet.show()
    }
    
    /**
     * 🆕 请求电池优化豁免
     * 确保服务在后台能够持续运行
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val packageName = packageName
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    Timber.tag(TAG).i("Requesting battery optimization exemption...")
                    
                    // 使用 BatteryOptimizationHelper 显示引导
                    com.phoneagent.remote.keepalive.BatteryOptimizationHelper.showOptimizationDialog(this)
                } else {
                    Timber.tag(TAG).i("✅ Battery optimization already disabled")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to request battery optimization exemption")
            }
        }
    }
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}
