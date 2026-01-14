package com.phoneagent.remote.keepalive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import timber.log.Timber

/**
 * 厂商白名单引导
 * 
 * 针对不同厂商提供白名单设置指引
 * 帮助用户将应用添加到白名单，提高保活率
 */
object WhitelistGuide {
    
    private const val TAG = "WhitelistGuide"
    
    /**
     * 显示白名单设置引导
     */
    fun showGuide(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        val guide = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> getXiaomiGuide()
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> getHuaweiGuide()
            manufacturer.contains("oppo") -> getOppoGuide()
            manufacturer.contains("vivo") -> getVivoGuide()
            manufacturer.contains("samsung") -> getSamsungGuide()
            manufacturer.contains("oneplus") -> getOnePlusGuide()
            manufacturer.contains("meizu") -> getMeizuGuide()
            else -> getGenericGuide()
        }
        
        showGuideDialog(context, guide)
    }
    
    /**
     * 显示引导对话框
     */
    private fun showGuideDialog(context: Context, guide: GuideInfo) {
        try {
            AlertDialog.Builder(context)
                .setTitle(guide.title)
                .setMessage(guide.message)
                .setPositiveButton("去设置") { _, _ ->
                    try {
                        guide.openSettings(context)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to open settings")
                        // 降级：打开应用详情页
                        openAppDetails(context)
                    }
                }
                .setNegativeButton("稍后", null)
                .show()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to show guide dialog")
        }
    }
    
    /**
     * 打开应用详情页
     */
    private fun openAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open app details")
        }
    }
    
    // ==================== 各厂商引导 ====================
    
    private fun getXiaomiGuide(): GuideInfo {
        return GuideInfo(
            title = "小米手机保活设置",
            message = """
                为了保证应用稳定运行，请进行以下设置：
                
                1. 开启「自启动」权限
                2. 设置「省电策略」为「无限制」
                3. 在最近任务中「下拉锁定」应用
                4. 关闭「MIUI 优化」（可选）
                
                点击「去设置」将打开应用详情页
            """.trimIndent(),
            openSettings = { context ->
                // 尝试打开自启动设置
                val intent = Intent().apply {
                    action = "miui.intent.action.APP_PERM_EDITOR"
                    putExtra("extra_pkgname", context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        )
    }
    
    private fun getHuaweiGuide(): GuideInfo {
        return GuideInfo(
            title = "华为手机保活设置",
            message = """
                为了保证应用稳定运行，请进行以下设置：
                
                1. 开启「自启动」权限
                2. 设置「电池」→「应用启动管理」为「手动管理」
                3. 开启「允许后台活动」
                4. 开启「允许关联启动」
                5. 关闭「省电模式」（可选）
                
                点击「去设置」将打开应用详情页
            """.trimIndent(),
            openSettings = { context ->
                openAppDetails(context)
            }
        )
    }
    
    private fun getOppoGuide(): GuideInfo {
        return GuideInfo(
            title = "OPPO 手机保活设置",
            message = """
                为了保证应用稳定运行，请进行以下设置：
                
                1. 开启「自启动」权限
                2. 设置「电池」→「应用耗电管理」为「允许后台运行」
                3. 关闭「应用冻结」
                4. 在最近任务中「锁定」应用
                
                点击「去设置」将打开应用详情页
            """.trimIndent(),
            openSettings = { context ->
                openAppDetails(context)
            }
        )
    }
    
    private fun getVivoGuide(): GuideInfo {
        return GuideInfo(
            title = "vivo 手机保活设置",
            message = """
                为了保证应用稳定运行，请进行以下设置：
                
                1. 开启「自启动」权限
                2. 设置「后台高耗电」为「允许」
                3. 关闭「后台冻结」
                4. 在最近任务中「加锁」应用
                
                点击「去设置」将打开应用详情页
            """.trimIndent(),
            openSettings = { context ->
                openAppDetails(context)
            }
        )
    }
    
    private fun getSamsungGuide(): GuideInfo {
        return GuideInfo(
            title = "三星手机保活设置",
            message = """
                为了保证应用稳定运行，请进行以下设置：
                
                1. 关闭「电池优化」
                2. 设置「后台使用限制」为「不限制」
                3. 开启「允许后台活动」
                4. 关闭「自适应电池」（可选）
                
                点击「去设置」将打开应用详情页
            """.trimIndent(),
            openSettings = { context ->
                openAppDetails(context)
            }
        )
    }
    
    private fun getOnePlusGuide(): GuideInfo {
        return GuideInfo(
            title = "一加手机保活设置",
            message = """
                为了保证应用稳定运行，请进行以下设置：
                
                1. 开启「自启动」权限
                2. 设置「电池优化」为「不优化」
                3. 开启「允许后台运行」
                4. 在最近任务中「锁定」应用
                
                点击「去设置」将打开应用详情页
            """.trimIndent(),
            openSettings = { context ->
                openAppDetails(context)
            }
        )
    }
    
    private fun getMeizuGuide(): GuideInfo {
        return GuideInfo(
            title = "魅族手机保活设置",
            message = """
                为了保证应用稳定运行，请进行以下设置：
                
                1. 开启「自启动」权限
                2. 设置「待机耗电管理」为「允许后台运行」
                3. 关闭「智能后台冻结」
                4. 在最近任务中「锁定」应用
                
                点击「去设置」将打开应用详情页
            """.trimIndent(),
            openSettings = { context ->
                openAppDetails(context)
            }
        )
    }
    
    private fun getGenericGuide(): GuideInfo {
        return GuideInfo(
            title = "保活设置建议",
            message = """
                为了保证应用稳定运行，建议进行以下设置：
                
                1. 关闭「电池优化」
                2. 开启「自启动」权限（如有）
                3. 允许「后台运行」
                4. 在最近任务中「锁定」应用
                
                点击「去设置」将打开应用详情页
            """.trimIndent(),
            openSettings = { context ->
                openAppDetails(context)
            }
        )
    }
    
    /**
     * 引导信息
     */
    private data class GuideInfo(
        val title: String,
        val message: String,
        val openSettings: (Context) -> Unit
    )
}

