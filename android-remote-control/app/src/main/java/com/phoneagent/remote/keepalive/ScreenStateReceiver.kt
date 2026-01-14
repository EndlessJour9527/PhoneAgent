package com.phoneagent.remote.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * 屏幕状态监听器
 * 
 * 监听屏幕开关事件，控制 1 像素保活 Activity
 */
class ScreenStateReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenStateReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                // 屏幕关闭，启动 1 像素 Activity
                Timber.tag(TAG).d("Screen OFF - starting 1-pixel activity")
                KeepAliveActivity.start(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                // 屏幕打开，1 像素 Activity 会自动关闭
                Timber.tag(TAG).d("Screen ON")
            }
        }
    }
}

