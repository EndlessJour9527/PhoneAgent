package com.phoneagent.remote.keepalive

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import com.phoneagent.remote.core.RemoteControlService
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 保活 Worker
 * 
 * 定期检查服务是否运行,如果未运行则重启
 * 
 * 使用 WorkManager 实现定期检查,即使应用被杀死也能重启
 */
class KeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "KeepAliveWorker"
        private const val WORK_NAME = "keep_alive_worker"
        
        /**
         * 调度保活任务
         */
        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .build()
                
                val workRequest = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                    15, TimeUnit.MINUTES  // 每 15 分钟执行一次
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        10, TimeUnit.SECONDS
                    )
                    .build()
                
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest
                    )
                
                Timber.tag(TAG).i("✅ WorkManager task scheduled successfully")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to schedule WorkManager task")
            }
        }
        
        /**
         * 取消保活任务
         */
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                Timber.tag(TAG).i("WorkManager task cancelled")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to cancel WorkManager task")
            }
        }
    }
    
    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("Checking service status...")
        
        return try {
            // 检查服务是否运行
            if (!isServiceRunning(applicationContext, RemoteControlService::class.java)) {
                Timber.tag(TAG).w("⚠️ Service not running, restarting...")
                restartService()
            } else {
                Timber.tag(TAG).d("✅ Service is running")
            }
            
            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Keep alive check failed")
            Result.failure()
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
     * 重启服务
     */
    private fun restartService() {
        try {
            val intent = Intent(applicationContext, RemoteControlService::class.java).apply {
                action = RemoteControlService.ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            
            Timber.tag(TAG).i("✅ Service restart initiated")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to restart service")
        }
    }
}

