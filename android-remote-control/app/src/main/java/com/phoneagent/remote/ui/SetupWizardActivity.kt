package com.phoneagent.remote.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.phoneagent.remote.R
import com.phoneagent.remote.core.RemoteControlService
import com.phoneagent.remote.data.Config
import com.phoneagent.remote.data.ConfigRepository
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 配置向导界面
 * 引导用户完成首次配置
 * 
 * 参考: client/install_termux.sh (第595-639行)
 * 
 * ⚠️ 架构说明（参考 docs/standard/DEPLOYMENT_ARCHITECTURE_STANDARD.md）：
 * 
 * PhoneAgent使用双WebSocket架构：
 * 1. 前端WebSocket (8000端口) - 前端网页 ↔ API服务器
 * 2. 设备WebSocket (9999端口) - 设备客户端 ↔ WebSocket服务器 ← 本应用连接这个
 * 
 * 本应用作为设备客户端，需要连接到【9999端口的WebSocket服务器】！
 * 
 * 两种连接模式：
 * 1. 【直连IP模式】(默认，推荐)
 *    格式: ws://{SERVER_IP}:9999/ws/device/{REMOTE_PORT}
 *    说明: 设备直接连接后端服务器的9999端口
 *    适用: 内网环境、VPN连接、开发测试
 * 
 * 2. 【域名代理模式】(可选)
 *    格式: wss://{DOMAIN}/device-ws/device/{REMOTE_PORT}
 *    说明: 通过域名+Nginx反向代理连接，支持HTTPS
 *    适用: 生产环境、需要SSL加密、跨网络访问
 * 
 * 必填参数:
 * 1. SERVER_IP - 后端服务器IP（FRP服务器所在IP）
 * 2. FRP_TOKEN - FRP连接令牌
 * 3. WS_MODE - WebSocket连接方式（1=直连IP, 2=域名代理）
 * 4. WS_DOMAIN - 域名（仅域名代理模式需要）
 * 
 * 可选参数:
 * 1. DEVICE_NAME - 设备名称（默认自动生成）
 */
class SetupWizardActivity : AppCompatActivity() {
    
    private lateinit var configRepository: ConfigRepository
    
    // UI 组件
    private lateinit var etServerIp: TextInputEditText       // 后端服务器IP (SERVER_IP)
    private lateinit var etFrpToken: TextInputEditText       // FRP Token
    private lateinit var rgWsMode: RadioGroup                // WebSocket连接方式
    private lateinit var rbDirectIp: RadioButton             // 直连IP模式
    private lateinit var rbDomainProxy: RadioButton          // 域名代理模式
    private lateinit var tilWsDomain: TextInputLayout        // 域名输入框布局
    private lateinit var etWsDomain: TextInputEditText       // 域名
    private lateinit var etRemotePort: TextInputEditText     // 远程端口（FRP分配的端口）
    private lateinit var etDeviceName: TextInputEditText     // 设备名称（可选）
    private lateinit var btnFinish: Button
    
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
        
        try {
            setContentView(R.layout.activity_setup_wizard)
            
            Timber.tag(TAG).d("SetupWizardActivity created")
            
            configRepository = ConfigRepository(this)
            
            initViews()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in onCreate")
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun initViews() {
        etServerIp = findViewById(R.id.et_server_ip)
        etFrpToken = findViewById(R.id.et_frp_token)
        rgWsMode = findViewById(R.id.rg_ws_mode)
        rbDirectIp = findViewById(R.id.rb_direct_ip)
        rbDomainProxy = findViewById(R.id.rb_domain_proxy)
        tilWsDomain = findViewById(R.id.til_ws_domain)
        etWsDomain = findViewById(R.id.et_ws_domain)
        etRemotePort = findViewById(R.id.et_remote_port)
        etDeviceName = findViewById(R.id.et_device_name)
        btnFinish = findViewById(R.id.btn_finish)
        
        // 默认选择直连IP模式
        rbDirectIp.isChecked = true
        tilWsDomain.visibility = android.view.View.GONE
        
        // 监听模式切换
        rgWsMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_direct_ip -> {
                    // 直连IP模式：隐藏域名输入框
                    tilWsDomain.visibility = android.view.View.GONE
                }
                R.id.rb_domain_proxy -> {
                    // 域名代理模式：显示域名输入框
                    tilWsDomain.visibility = android.view.View.VISIBLE
                }
            }
        }
        
        btnFinish.setOnClickListener { onFinishClicked() }
        
        // 自动生成默认设备名称
        val defaultDeviceName = "Android_${Build.MODEL.replace(" ", "_")}"
        etDeviceName.setText(defaultDeviceName)
        
        // 设置默认远程端口（可修改）
        etRemotePort.setText("6104")
        
        // 加载现有配置（如果有）
        lifecycleScope.launch {
            try {
                val config = configRepository.getConfig()
                if (config.isConfigured()) {
                    etServerIp.setText(config.serverIp)
                    etFrpToken.setText(config.frpToken)
                    etDeviceName.setText(config.deviceName)
                    etRemotePort.setText(config.remotePort.toString())
                    
                    // 解析WebSocket模式
                    val wsUrl = config.wsServerUrl
                    if (wsUrl.startsWith("wss://") && wsUrl.contains("/device-ws/")) {
                        // 域名代理模式
                        rbDomainProxy.isChecked = true
                        tilWsDomain.visibility = android.view.View.VISIBLE
                        
                        // 解析域名: wss://domain.com/device-ws/device/6100
                        try {
                            val domain = wsUrl.substringAfter("wss://").substringBefore("/")
                            etWsDomain.setText(domain)
                        } catch (e: Exception) {
                            Timber.tag(TAG).w(e, "Failed to parse domain from wsUrl")
                        }
                    } else {
                        // 直连IP模式
                        rbDirectIp.isChecked = true
                        tilWsDomain.visibility = android.view.View.GONE
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error loading config")
            }
        }
    }
    
    private fun onFinishClicked() {
        try {
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 设备WebSocket连接配置
            // 参考: client/install_termux.sh 第595-622行
            // 架构: docs/standard/DEPLOYMENT_ARCHITECTURE_STANDARD.md 第98-122行
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            
            // 1. 后端服务器IP（必填）
            // 作用: FRP服务器(7000) + WebSocket服务器(9999) 所在IP
            var serverIp = etServerIp.text?.toString()?.trim() ?: ""
            if (serverIp.isEmpty()) {
                Toast.makeText(this, "请输入服务器 IP 地址", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 清理 IP：移除端口（如果用户误输入了冒号+端口）
            // 兼容半角冒号和全角冒号: : 和 ：
            serverIp = serverIp.split(":")[0].split("：")[0].trim()
            
            // 基础验证：检查是否看起来像有效的 IP
            if (!isValidIpFormat(serverIp)) {
                Toast.makeText(this, "服务器 IP 格式无效，请只输入 IP 地址（不要输入端口）", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 2. FRP Token（必填）
            // 作用: FRP客户端连接认证
            val frpToken = etFrpToken.text?.toString()?.trim() ?: ""
            if (frpToken.isEmpty()) {
                Toast.makeText(this, "请输入 FRP Token", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 3. 远程端口（必填）- FRP分配的唯一端口
            // 注意: 这不是本地ADB端口5555，而是FRP服务器分配给每个设备的端口（如6100）
            val remotePortStr = etRemotePort.text?.toString()?.trim() ?: ""
            if (remotePortStr.isEmpty()) {
                Toast.makeText(this, "请输入远程端口", Toast.LENGTH_SHORT).show()
                return
            }
            val remotePort = try {
                remotePortStr.toInt()
            } catch (e: Exception) {
                Toast.makeText(this, "远程端口必须是数字", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 验证端口范围
            if (remotePort < 1024 || remotePort > 65535) {
                Toast.makeText(this, "端口号必须在 1024-65535 之间", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 4. 设备名称（可选）
            val deviceName = etDeviceName.text?.toString()?.trim() ?: ""
            
            // 5. 生成设备ID
            val deviceId = generateDeviceId()
            
            // 6. WebSocket连接方式
            // 根据文档第108-115行：设备WebSocket路径为 /ws/device/{frp_port}
            // frp_port 就是 remotePort（FRP分配的唯一端口）
            val wsMode = if (rbDirectIp.isChecked) 1 else 2
            val wsServerUrl: String
            
            when (wsMode) {
                1 -> {
                    // 模式1: 直连IP模式（推荐）
                    // 格式: ws://{SERVER_IP}:9999/ws/device/{REMOTE_PORT}
                    // 注意: REMOTE_PORT 是FRP分配的端口，不是5555
                    wsServerUrl = "ws://${serverIp}:9999/ws/device/${remotePort}"
                    
                    Timber.tag(TAG).i("✅ 使用直连IP模式")
                    Timber.tag(TAG).i("   清理后的服务器IP: $serverIp")
                    Timber.tag(TAG).i("   WebSocket URL: $wsServerUrl")
                    Timber.tag(TAG).i("   说明: 直接连接服务器9999端口")
                    Timber.tag(TAG).i("   FRP远程端口: $remotePort")
                }
                2 -> {
                    // 模式2: 域名代理模式
                    // 格式: wss://{DOMAIN}/device-ws/device/{REMOTE_PORT}
                    // Nginx会将 /device-ws/ 代理到 http://127.0.0.1:9999/ws/
                    val wsDomain = etWsDomain.text?.toString()?.trim() ?: ""
                    if (wsDomain.isEmpty()) {
                        Toast.makeText(this, "域名代理模式需要输入域名", Toast.LENGTH_SHORT).show()
                        return
                    }
                    
                    wsServerUrl = "wss://${wsDomain}/device-ws/device/${remotePort}"
                    
                    Timber.tag(TAG).i("✅ 使用域名代理模式")
                    Timber.tag(TAG).i("   WebSocket URL: $wsServerUrl")
                    Timber.tag(TAG).i("   说明: 通过域名+Nginx反向代理连接")
                    Timber.tag(TAG).i("   FRP远程端口: $remotePort")
                    Timber.tag(TAG).w("   注意: 需要配置Nginx反向代理，详见项目文档")
                }
                else -> {
                    Toast.makeText(this, "请选择连接方式", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            // 保存配置
            val config = Config(
                serverIp = serverIp,
                serverPort = 7001,  // FRP服务器端口固定为7000
                frpToken = frpToken,
                wsServerUrl = wsServerUrl,
                deviceId = deviceId,
                deviceName = deviceName.ifEmpty { "Android_${Build.MODEL}" },
                remotePort = remotePort  // 保存FRP分配的远程端口
            )
            
            Timber.tag(TAG).i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.tag(TAG).i("设备配置信息:")
            Timber.tag(TAG).i("  服务器IP: $serverIp")
            Timber.tag(TAG).i("  FRP服务器端口: 7000")
            Timber.tag(TAG).i("  FRP Token: ${frpToken.take(4)}...")
            Timber.tag(TAG).i("  设备ID: $deviceId")
            Timber.tag(TAG).i("  设备名称: ${config.deviceName}")
            Timber.tag(TAG).i("  本地ADB端口: 5555 (固定)")
            Timber.tag(TAG).i("  FRP远程端口: $remotePort (WebSocket路径使用此端口)")
            Timber.tag(TAG).i("  WebSocket URL: $wsServerUrl")
            Timber.tag(TAG).i("  连接模式: ${if (wsMode == 1) "直连IP模式" else "域名代理模式"}")
            Timber.tag(TAG).i("  连接目标: WebSocket服务器(9999端口)")
            Timber.tag(TAG).i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            
            lifecycleScope.launch {
                try {
                    configRepository.saveConfig(config)
                    
                    Toast.makeText(this@SetupWizardActivity, "配置保存成功", Toast.LENGTH_SHORT).show()
                    
                    // 启动服务
                    RemoteControlService.start(this@SetupWizardActivity)
                    
                    // 跳转到主界面
                    val intent = Intent(this@SetupWizardActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error saving config")
                    Toast.makeText(this@SetupWizardActivity, "保存配置失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in onFinishClicked")
            Toast.makeText(this, "操作失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 验证 IP 地址格式（简单检查）
     * 接受: IPv4 (192.168.1.1) 和 IPv6 地址
     */
    private fun isValidIpFormat(ip: String): Boolean {
        // 移除所有空白
        val cleanIp = ip.trim()
        
        // IPv4 检查：应该包含数字和点，形如 x.x.x.x
        val ipv4Regex = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")
        if (ipv4Regex.matches(cleanIp)) {
            return true
        }
        
        // IPv6 检查：包含冒号（简单检查）
        if (cleanIp.contains(":") && !cleanIp.contains("：")) {
            // 有半角冒号，可能是 IPv6，简单接受
            // 更严格的验证可以在运行时尝试连接时发现
            return true
        }
        
        // 其他格式不接受
        return false
    }
    
    /**
     * 生成设备 ID
     * 格式: device_{last4DigitsOfAndroidId}
     */
    @SuppressLint("HardwareIds")
    private fun generateDeviceId(): String {
        return try {
            val androidId = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            
            val last4 = androidId.takeLast(4)
            "device_$last4"
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error generating device ID")
            // 备用方案：使用随机ID
            "device_${System.currentTimeMillis() % 10000}"
        }
    }
    
    companion object {
        private const val TAG = "SetupWizardActivity"
    }
}
