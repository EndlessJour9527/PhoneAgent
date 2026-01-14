package com.phoneagent.remote.keepalive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import timber.log.Timber

/**
 * 电池优化辅助类
 * 
 * 帮助应用加入电池优化白名单,确保后台运行不被系统限制
 */
object BatteryOptimizationHelper {
    
    private const val TAG = "BatteryOptimization"
    
    /**
     * 检查是否在电池优化白名单中
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to check battery optimization status")
            false
        }
    }
    
    /**
     * 显示电池优化对话框
     */
    fun showOptimizationDialog(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        
        if (isIgnoringBatteryOptimizations(activity)) {
            Timber.tag(TAG).i("✅ Already ignoring battery optimizations")
            return
        }
        
        AlertDialog.Builder(activity)
            .setTitle("需要后台运行权限")
            .setMessage("""
                为了确保远程控制功能持续工作,需要将应用加入电池优化白名单。
                
                这样即使应用在后台,也能随时保持连接。
                
                类似系统服务的工作方式。
            """.trimIndent())
            .setPositiveButton("去设置") { _, _ ->
                requestIgnoreBatteryOptimizations(activity)
            }
            .setNegativeButton("稍后") { _, _ ->
                Timber.tag(TAG).w("⚠️ User declined battery optimization exemption")
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 请求加入电池优化白名单
     */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Timber.tag(TAG).d("Battery optimization not available on this Android version")
            return
        }
        
        if (isIgnoringBatteryOptimizations(activity)) {
            Timber.tag(TAG).d("Already ignoring battery optimizations")
            return
        }
        
        try {
            // 方案 1: 直接请求豁免
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
            Timber.tag(TAG).i("Requested battery optimization exemption")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to request battery optimization exemption, trying fallback")
            
            // 方案 2: 打开电池优化设置页面
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                activity.startActivity(intent)
                Timber.tag(TAG).i("Opened battery optimization settings")
            } catch (e2: Exception) {
                Timber.tag(TAG).e(e2, "Failed to open battery optimization settings")
            }
        }
    }
}

