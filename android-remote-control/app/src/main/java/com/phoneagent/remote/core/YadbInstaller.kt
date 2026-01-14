package com.phoneagent.remote.core

import android.content.Context
import com.phoneagent.remote.termux.TermuxBootstrapManager
import com.phoneagent.remote.termux.TermuxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * yadb 安装管理器
 * 
 * 职责:
 * - 从 assets/yadb/yadb 复制到 /data/local/tmp/yadb
 * - 设置可执行权限
 * - 验证安装
 * 
 * 注意:
 * - yadb 是一个 DEX 文件,通过 app_process 执行
 * - 服务器端通过 ADB 命令调用 yadb (不需要推送)
 * - Android app 只负责预装 yadb 到设备
 */
class YadbInstaller(
    private val context: Context,
    private val termuxBootstrap: TermuxBootstrapManager,
    private val termuxExecutor: TermuxExecutor
) {
    companion object {
        private const val TAG = "YadbInstaller"
        
        /** yadb 在 assets 中的路径 */
        private const val YADB_ASSET_PATH = "yadb/yadb"
        
        /** yadb 在设备上的目标路径 */
        private const val YADB_DEVICE_PATH = "/data/local/tmp/yadb"
        
        /** yadb 文件的预期 MD5 (官方版本) */
        private const val YADB_MD5 = "29a0cd3b3adea92350dd5a25594593df"
        
        /** yadb 最小文件大小 (10 KB) */
        private const val MIN_YADB_SIZE = 10 * 1024L
    }
    
    /**
     * 检查 yadb 是否已安装
     */
    suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val envInfo = termuxBootstrap.getEnvironmentInfo()
            
            // 检查文件是否存在
            val checkCmd = "test -f $YADB_DEVICE_PATH && echo 'exists' || echo 'not_found'"
            val result = termuxExecutor.execute(checkCmd, workingDir = File(envInfo.home))
                .getOrNull()
            
            if (result?.output?.contains("exists") == true) {
                Timber.tag(TAG).d("yadb file exists at $YADB_DEVICE_PATH")
                
                // 检查文件大小
                val sizeCmd = "stat -c %s $YADB_DEVICE_PATH 2>/dev/null || stat -f %z $YADB_DEVICE_PATH 2>/dev/null"
                val sizeResult = termuxExecutor.execute(sizeCmd, workingDir = File(envInfo.home))
                    .getOrNull()
                
                val size = sizeResult?.output?.trim()?.toLongOrNull() ?: 0L
                if (size < MIN_YADB_SIZE) {
                    Timber.tag(TAG).w("yadb file too small: $size bytes (min: $MIN_YADB_SIZE)")
                    return@withContext false
                }
                
                // 检查权限
                val permCmd = "ls -l $YADB_DEVICE_PATH"
                val permResult = termuxExecutor.execute(permCmd, workingDir = File(envInfo.home))
                    .getOrNull()
                Timber.tag(TAG).d("yadb permissions: ${permResult?.output}")
                
                Timber.tag(TAG).i("✅ yadb already installed: $size bytes")
                return@withContext true
            }
            
            Timber.tag(TAG).w("⚠️ yadb not found at $YADB_DEVICE_PATH")
            false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to check yadb installation")
            false
        }
    }
    
    /**
     * 安装 yadb 到设备
     * 
     * 流程:
     * 1. 从 assets 复制到临时文件
     * 2. 使用 Termux 环境将文件移动到 /data/local/tmp/
     * 3. 设置可执行权限
     * 4. 验证安装
     */
    suspend fun install(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.tag(TAG).i("━━━━━━ yadb Installation START ━━━━━━")
            Timber.tag(TAG).i("Installing yadb from assets...")
            
            val envInfo = termuxBootstrap.getEnvironmentInfo()
            val tempFile = File(envInfo.home, "yadb.tmp")
            
            try {
                // 步骤 1: 从 assets 复制到临时文件
                Timber.tag(TAG).i("Step 1: Copying yadb from assets...")
                Timber.tag(TAG).d("  Source: $YADB_ASSET_PATH")
                Timber.tag(TAG).d("  Temp: ${tempFile.absolutePath}")
                
                context.assets.open(YADB_ASSET_PATH).use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // 验证临时文件
                if (!tempFile.exists()) {
                    throw IllegalStateException("Temp file not created: ${tempFile.absolutePath}")
                }
                
                val tempSize = tempFile.length()
                if (tempSize < MIN_YADB_SIZE) {
                    throw IllegalStateException("Temp file too small: $tempSize bytes (min: $MIN_YADB_SIZE)")
                }
                
                Timber.tag(TAG).i("  ✅ Copied yadb: $tempSize bytes")
                
                // 步骤 2: 移动到 /data/local/tmp/ (需要使用 Termux 环境)
                Timber.tag(TAG).i("Step 2: Moving yadb to $YADB_DEVICE_PATH")
                val moveCmd = "cp ${tempFile.absolutePath} $YADB_DEVICE_PATH"
                Timber.tag(TAG).d("  Command: $moveCmd")
                
                val moveResult = termuxExecutor.execute(moveCmd, workingDir = File(envInfo.home))
                if (moveResult.isFailure) {
                    throw moveResult.exceptionOrNull() ?: Exception("Move command failed")
                }
                Timber.tag(TAG).i("  ✅ File moved")
                
                // 验证文件是否存在
                val checkCmd = "ls -l $YADB_DEVICE_PATH"
                val checkResult = termuxExecutor.execute(checkCmd, workingDir = File(envInfo.home))
                if (checkResult.isSuccess) {
                    Timber.tag(TAG).d("  File info: ${checkResult.getOrNull()?.output}")
                }
                
                // 步骤 3: 设置可执行权限
                Timber.tag(TAG).i("Step 3: Setting executable permission")
                val chmodCmd = "chmod 755 $YADB_DEVICE_PATH"
                Timber.tag(TAG).d("  Command: $chmodCmd")
                
                val chmodResult = termuxExecutor.execute(chmodCmd, workingDir = File(envInfo.home))
                if (chmodResult.isFailure) {
                    throw chmodResult.exceptionOrNull() ?: Exception("Chmod command failed")
                }
                Timber.tag(TAG).i("  ✅ Permission set")
                
                // 验证权限
                val permCmd = "ls -l $YADB_DEVICE_PATH"
                val permResult = termuxExecutor.execute(permCmd, workingDir = File(envInfo.home))
                if (permResult.isSuccess) {
                    Timber.tag(TAG).d("  Permissions: ${permResult.getOrNull()?.output}")
                }
                
                // 步骤 4: 验证安装
                Timber.tag(TAG).i("Step 4: Verifying installation")
                if (!isInstalled()) {
                    throw IllegalStateException("yadb installation verification failed")
                }
                
                Timber.tag(TAG).i("━━━━━━ yadb Installation SUCCESS ━━━━━━")
                Timber.tag(TAG).i("✅ yadb successfully installed to $YADB_DEVICE_PATH")
                Result.success(Unit)
                
            } finally {
                // 清理临时文件
                if (tempFile.exists()) {
                    tempFile.delete()
                    Timber.tag(TAG).d("Cleaned up temp file")
                }
            }
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to install yadb")
            Result.failure(e)
        }
    }
    
    /**
     * 确保 yadb 已安装
     * 
     * 如果未安装,则自动安装
     */
    suspend fun ensureInstalled(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInstalled()) {
                Timber.tag(TAG).d("yadb already installed")
                return@withContext Result.success(Unit)
            }
            
            Timber.tag(TAG).i("yadb not installed, installing...")
            install()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to ensure yadb installed")
            Result.failure(e)
        }
    }
    
    /**
     * 卸载 yadb
     * 
     * 仅用于测试或清理
     */
    suspend fun uninstall(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val envInfo = termuxBootstrap.getEnvironmentInfo()
            val removeCmd = "rm -f $YADB_DEVICE_PATH"
            termuxExecutor.execute(removeCmd, workingDir = File(envInfo.home))
                .getOrThrow()
            
            Timber.tag(TAG).i("yadb uninstalled")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to uninstall yadb")
            Result.failure(e)
        }
    }
}

