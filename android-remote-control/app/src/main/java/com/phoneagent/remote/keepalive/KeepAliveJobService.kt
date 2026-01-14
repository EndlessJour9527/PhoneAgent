package com.phoneagent.remote.keepalive

import android.app.ActivityManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.phoneagent.remote.core.RemoteControlService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * JobScheduler 保活服务
 * 
 * 使用系统的任务调度器定期检查服务状态
 * 
 * 优势：
 * - 系统原生支持
 * - 重启后自动恢复
 * - 省电优化
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class KeepAliveJobService : JobService() {
    
    companion object {
        private const val TAG = "KeepAliveJobService"
        private const val JOB_ID = 1001
        
        /**
         * 调度保活任务
         */
        fun schedule(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.tag(TAG).w("JobScheduler not available on this Android version")
                return
            }
            
            try {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                
                // 取消之前的任务
                jobScheduler.cancel(JOB_ID)
                
                val jobInfo = JobInfo.Builder(
                    JOB_ID,
                    ComponentName(context, KeepAliveJobService::class.java)
                ).apply {
                    // 每 15 分钟执行一次（系统最小间隔）
                    setPeriodic(15 * 60 * 1000L)
                    
                    // 不需要网络
                    setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    
                    // 设备重启后保持任务
                    setPersisted(true)
                    
                    // 不需要充电
                    setRequiresCharging(false)
                    
                    // 不需要设备空闲
                    setRequiresDeviceIdle(false)
                }.build()
                
                val result = jobScheduler.schedule(jobInfo)
                if (result == JobScheduler.RESULT_SUCCESS) {
                    Timber.tag(TAG).i("✅ JobScheduler task scheduled successfully")
                } else {
                    Timber.tag(TAG).e("❌ Failed to schedule JobScheduler task")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to schedule job")
            }
        }
        
        /**
         * 取消保活任务
         */
        fun cancel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                return
            }
            
            try {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.cancel(JOB_ID)
                Timber.tag(TAG).i("JobScheduler task cancelled")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to cancel job")
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onStartJob(params: JobParameters): Boolean {
        Timber.tag(TAG).d("onStartJob - checking service status...")
        
        // 在协程中执行检查
        scope.launch {
            try {
                checkAndRestartService()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during service check")
            } finally {
                // 通知系统任务完成
                jobFinished(params, false)
            }
        }
        
        // 返回 true 表示任务在后台执行
        return true
    }
    
    override fun onStopJob(params: JobParameters): Boolean {
        Timber.tag(TAG).d("onStopJob - job interrupted")
        // 返回 true 表示需要重新调度
        return true
    }
    
    /**
     * 检查并重启服务
     */
    private fun checkAndRestartService() {
        // 检查主服务
        if (!isServiceRunning(RemoteControlService::class.java)) {
            Timber.tag(TAG).w("⚠️ Main service not running, restarting...")
            restartService(RemoteControlService::class.java, RemoteControlService.ACTION_START)
        } else {
            Timber.tag(TAG).d("✅ Main service is running")
        }
        
        // 检查守护服务
        if (!isServiceRunning(GuardService::class.java)) {
            Timber.tag(TAG).w("⚠️ Guard service not running, restarting...")
            GuardService.start(this)
        } else {
            Timber.tag(TAG).d("✅ Guard service is running")
        }
    }
    
    /**
     * 检查服务是否运行
     */
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        return try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
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
    private fun restartService(serviceClass: Class<*>, action: String) {
        try {
            val intent = Intent(this, serviceClass).apply {
                this.action = action
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            Timber.tag(TAG).i("✅ Service restart initiated: ${serviceClass.simpleName}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to restart service")
        }
    }
}

