package com.phoneagent.remote.keepalive

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import timber.log.Timber

/**
 * 1 像素保活 Activity
 * 
 * 策略：
 * - 屏幕关闭时启动 1 像素窗口
 * - 屏幕打开时关闭窗口
 * - 提高进程优先级，降低被杀概率
 * 
 * 注意：
 * - 这是一个透明的 1x1 像素窗口
 * - 用户完全无感知
 * - 符合 Android 规范
 */
class KeepAliveActivity : Activity() {
    
    companion object {
        private const val TAG = "KeepAliveActivity"
        
        fun start(context: Context) {
            try {
                val intent = Intent(context, KeepAliveActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                           Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                           Intent.FLAG_ACTIVITY_NO_HISTORY
                }
                context.startActivity(intent)
                Timber.tag(TAG).d("1-pixel activity started")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to start 1-pixel activity")
            }
        }
    }
    
    private var screenReceiver: BroadcastReceiver? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).d("onCreate")
        
        // 设置为 1 像素窗口
        setupOnePixelWindow()
        
        // 监听屏幕状态
        registerScreenReceiver()
    }
    
    /**
     * 设置 1 像素窗口
     */
    private fun setupOnePixelWindow() {
        val window = window
        
        // 设置窗口位置在左上角
        window.setGravity(Gravity.START or Gravity.TOP)
        
        // 设置窗口大小为 1x1 像素
        val params = window.attributes
        params.x = 0
        params.y = 0
        params.width = 1
        params.height = 1
        window.attributes = params
        
        Timber.tag(TAG).d("1-pixel window configured")
    }
    
    /**
     * 注册屏幕状态监听
     */
    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        // 屏幕关闭，保持窗口
                        Timber.tag(TAG).d("Screen OFF - keeping 1-pixel window")
                    }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        // 屏幕打开，关闭窗口
                        Timber.tag(TAG).d("Screen ON - closing 1-pixel window")
                        finish()
                    }
                }
            }
        }
        
        registerReceiver(screenReceiver, filter)
        Timber.tag(TAG).d("Screen receiver registered")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("onDestroy")
        
        // 取消注册广播接收器
        try {
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to unregister receiver")
        }
    }
}

