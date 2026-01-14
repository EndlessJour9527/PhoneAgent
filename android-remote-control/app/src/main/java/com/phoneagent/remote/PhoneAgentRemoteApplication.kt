package com.phoneagent.remote

import android.app.Application
import timber.log.Timber

/**
 * PhoneAgent Remote Application
 * 
 * PhoneAgent 项目的 Android 客户端，负责建立远程 ADB 连接，
 * 配合服务端实现 AI 智能手机控制。
 * 
 * @author tmwgsicp
 * @license AGPL-3.0
 * @see https://github.com/tmwgsicp/PhoneAgent
 */
class PhoneAgentRemoteApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 Timber 日志（在所有构建类型中都启用）
        // 这样即使是 Release 版本也能通过 logcat 查看日志
        Timber.plant(Timber.DebugTree())
        
        Timber.tag(TAG).e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.tag(TAG).e("  PhoneAgent Remote by tmwgsicp")
        Timber.tag(TAG).e("  https://github.com/tmwgsicp")
        Timber.tag(TAG).e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.tag(TAG).e("✅ Application started - Build: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
        Timber.tag(TAG).e("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Timber.tag(TAG).e("License: AGPL-3.0")
        
        android.util.Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.e(TAG, "  PhoneAgent Remote by tmwgsicp - Application onCreate()")
        android.util.Log.e(TAG, "  Build Type: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
        android.util.Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    companion object {
        private const val TAG = "PhoneAgentRemote"
    }
}

