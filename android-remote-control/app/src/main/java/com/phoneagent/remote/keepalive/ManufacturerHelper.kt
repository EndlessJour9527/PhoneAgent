package com.phoneagent.remote.keepalive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import timber.log.Timber

/**
 * 厂商特殊处理辅助类
 * 
 * 针对不同手机厂商的自启动和后台运行设置
 */
object ManufacturerHelper {
    
    private const val TAG = "ManufacturerHelper"
    
    /**
     * 获取手机厂商
     */
    fun getManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }
    
    /**
     * 获取手机型号
     */
    fun getModel(): String {
        return Build.MODEL
    }
    
    /**
     * 打开自启动设置页面
     */
    fun openAutoStartSettings(context: Context) {
        val manufacturer = getManufacturer()
        Timber.tag(TAG).i("Opening auto-start settings for $manufacturer")
        
        val intent = when {
            manufacturer.contains("xiaomi") -> {
                // 小米
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                // 华为/荣耀
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
            }
            manufacturer.contains("oppo") -> {
                // OPPO
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            }
            manufacturer.contains("vivo") -> {
                // vivo
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
            }
            manufacturer.contains("meizu") -> {
                // 魅族
                Intent().apply {
                    component = ComponentName(
                        "com.meizu.safe",
                        "com.meizu.safe.security.SHOW_APPSEC"
                    )
                    putExtra("packageName", context.packageName)
                }
            }
            manufacturer.contains("samsung") -> {
                // 三星
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                }
            }
            else -> {
                // 其他厂商,打开应用详情页
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }
        
        try {
            context.startActivity(intent)
            Timber.tag(TAG).i("✅ Opened auto-start settings")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to open auto-start settings for $manufacturer, trying fallback")
            
            // 回退:打开应用详情页
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(fallbackIntent)
                Timber.tag(TAG).i("Opened app details as fallback")
            } catch (e2: Exception) {
                Timber.tag(TAG).e(e2, "Failed to open app details")
            }
        }
    }
    
    /**
     * 获取厂商特定的保活指南
     */
    fun getKeepAliveGuide(): String {
        val manufacturer = getManufacturer()
        
        return when {
            manufacturer.contains("xiaomi") -> {
                """
                小米手机保活设置:
                1. 设置 > 应用设置 > 应用管理 > PhoneAgent Remote
                2. 开启"自启动"
                3. 省电策略选择"无限制"
                4. 锁定后台(任务管理器中下拉锁定)
                """.trimIndent()
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                """
                华为/荣耀手机保活设置:
                1. 设置 > 应用 > 应用启动管理 > PhoneAgent Remote
                2. 关闭"自动管理",手动开启"允许自启动"、"允许后台活动"
                3. 设置 > 电池 > 应用启动管理 > PhoneAgent Remote > 手动管理
                """.trimIndent()
            }
            manufacturer.contains("oppo") -> {
                """
                OPPO 手机保活设置:
                1. 设置 > 应用管理 > PhoneAgent Remote
                2. 开启"允许自启动"
                3. 开启"允许后台运行"
                4. 任务管理器中锁定应用
                """.trimIndent()
            }
            manufacturer.contains("vivo") -> {
                """
                vivo 手机保活设置:
                1. i管家 > 应用管理 > 自启动 > PhoneAgent Remote
                2. 开启"允许自启动"
                3. 设置 > 电池 > 后台高耗电 > PhoneAgent Remote > 允许
                """.trimIndent()
            }
            manufacturer.contains("samsung") -> {
                """
                三星手机保活设置:
                1. 设置 > 应用程序 > PhoneAgent Remote
                2. 电池 > 优化电池使用 > 关闭
                3. 允许后台数据使用
                """.trimIndent()
            }
            else -> {
                """
                通用保活设置:
                1. 加入电池优化白名单
                2. 允许自启动
                3. 允许后台运行
                4. 在任务管理器中锁定应用
                """.trimIndent()
            }
        }
    }
}

