package com.phoneagent.remote.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通用资源下载器
 * 支持从 OSS 下载资源文件，并提供进度回调
 * 
 * @author tmwgsicp
 */
class ResourceDownloader(
    private val context: Context
) {
    companion object {
        private const val TAG = "tmwgsicp.ResourceDownloader"
    }
    
    /**
     * 下载文件到指定位置
     * 
     * @param url 下载 URL
     * @param targetFile 目标文件
     * @param minExpectedSize 最小期望大小（用于验证）
     * @param onProgress 进度回调 (已下载字节, 总字节, 当前速度 KB/s)
     * @return 下载是否成功
     */
    suspend fun download(
        url: String,
        targetFile: File,
        minExpectedSize: Long = 0,
        onProgress: ((Long, Long, Double) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var retryCount = 0
        
        while (retryCount < ResourceConfig.MAX_RETRY) {
            try {
                Timber.tag(TAG).i("Downloading from: $url")
                Timber.tag(TAG).i("Target: ${targetFile.absolutePath}")
                Timber.tag(TAG).i("Retry: $retryCount/${ResourceConfig.MAX_RETRY}")
                
                // 创建父目录
                targetFile.parentFile?.mkdirs()
                
                // 建立连接
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = ResourceConfig.CONNECT_TIMEOUT * 1000
                connection.readTimeout = ResourceConfig.READ_TIMEOUT * 1000
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP error: $responseCode")
                }
                
                val totalBytes = connection.contentLengthLong
                Timber.tag(TAG).d("Content length: ${totalBytes / 1024 / 1024} MB")
                
                // 下载到临时文件
                val tempFile = File(targetFile.absolutePath + ".tmp")
                var downloadedBytes = 0L
                var lastReportTime = System.currentTimeMillis()
                var lastReportBytes = 0L
                
                try {
                    connection.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytes: Int
                            
                            while (input.read(buffer).also { bytes = it } != -1) {
                                output.write(buffer, 0, bytes)
                                downloadedBytes += bytes
                                
                                // 更新进度（每 500ms 报告一次）
                                val now = System.currentTimeMillis()
                                if (now - lastReportTime >= 500) {
                                    val elapsedMs = now - lastReportTime
                                    val downloadedSinceLastReport = downloadedBytes - lastReportBytes
                                    val speedKBps = (downloadedSinceLastReport / elapsedMs.toDouble()) * 1000 / 1024
                                    
                                    onProgress?.invoke(downloadedBytes, totalBytes, speedKBps)
                                    
                                    lastReportTime = now
                                    lastReportBytes = downloadedBytes
                                }
                            }
                        }
                    }
                    
                    // 最终进度报告
                    onProgress?.invoke(downloadedBytes, totalBytes, 0.0)
                    
                    // 验证大小
                    if (minExpectedSize > 0 && downloadedBytes < minExpectedSize) {
                        throw Exception("Downloaded file too small: $downloadedBytes < $minExpectedSize")
                    }
                    
                    // 移动到目标位置
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (!tempFile.renameTo(targetFile)) {
                        throw Exception("Failed to rename temp file")
                    }
                    
                    Timber.tag(TAG).i("Download completed: ${downloadedBytes / 1024 / 1024} MB")
                    return@withContext Result.success(Unit)
                    
                } finally {
                    // 确保清理临时文件
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
                
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Download failed (attempt ${retryCount + 1})")
                retryCount++
                
                if (retryCount >= ResourceConfig.MAX_RETRY) {
                    return@withContext Result.failure(e)
                }
                
                // 等待后重试
                kotlinx.coroutines.delay(2000L * retryCount)
                
            } finally {
                connection?.disconnect()
            }
        }
        
        Result.failure(Exception("Download failed after $retryCount retries"))
    }
    
    /**
     * 下载文本文件
     */
    suspend fun downloadText(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = ResourceConfig.CONNECT_TIMEOUT * 1000
            connection.readTimeout = ResourceConfig.READ_TIMEOUT * 1000
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: $responseCode")
            }
            
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            Result.success(text)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to download text from: $url")
            Result.failure(e)
        }
    }
    
    /**
     * 格式化下载速度
     */
    fun formatSpeed(speedKBps: Double): String {
        return when {
            speedKBps >= 1024 -> "%.2f MB/s".format(speedKBps / 1024)
            else -> "%.2f KB/s".format(speedKBps)
        }
    }
    
    /**
     * 格式化文件大小
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / 1024.0 / 1024.0)
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

