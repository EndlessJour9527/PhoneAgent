package com.phoneagent.remote.core

import android.content.Context
import android.os.Build
import com.phoneagent.remote.termux.TermuxBootstrapManager
import com.phoneagent.remote.termux.TermuxExecutor
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File

/**
 * FRP 管理器
 * 负责管理 FRP 客户端进程
 * 使用 Termux 环境运行 FRP，绕过 SELinux 限制
 */
class FrpManager(private val context: Context) {
    
    private val frpcBinary: File
    private val frpcConfig: File
    private var frpcProcess: Process? = null
    private var logReaderJob: Job? = null
    
    // Termux 集成
    private val termuxBootstrap = TermuxBootstrapManager(context)
    private val termuxExecutor = TermuxExecutor(context)
    
    /**
     * 获取 Termux Bootstrap 管理器（供其他组件使用）
     */
    fun getTermuxBootstrap(): TermuxBootstrapManager = termuxBootstrap
    
    /**
     * 获取 Termux Executor（供其他组件使用）
     */
    fun getTermuxExecutor(): TermuxExecutor = termuxExecutor
    
    // 当前设备架构
    private val deviceArch: String = when {
        Build.SUPPORTED_ABIS[0].contains("arm64") -> "arm64"
        Build.SUPPORTED_ABIS[0].contains("armeabi") -> "arm"
        else -> throw RuntimeException("Unsupported CPU architecture: ${Build.SUPPORTED_ABIS[0]}")
    }
    
    init {
        val envInfo = termuxBootstrap.getEnvironmentInfo()
        val phoneAgentDir = File(envInfo.home, ".phoneagent")
        
        frpcBinary = File(phoneAgentDir, "frpc")
        frpcConfig = File(phoneAgentDir, "frpc.ini")
        
        Timber.tag(TAG).d("FrpManager initialized: arch=$deviceArch")
    }
    
    
    /**
     * 确保 Termux 环境和 FRP 二进制文件可用
     */
    suspend fun ensureFrpcAvailable(
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            // 1. 确保 Termux 环境已初始化
            if (!termuxBootstrap.isInitialized()) {
                Timber.tag(TAG).i("Initializing Termux environment...")
                onProgress?.invoke(0, 100)
                
                val initResult = termuxBootstrap.initialize()
                if (initResult.isFailure) {
                    return@withContext Result.failure(
                        initResult.exceptionOrNull() ?: Exception("Failed to initialize Termux")
                    )
                }
                
                Timber.tag(TAG).i("✅ Termux environment initialized")
                onProgress?.invoke(50, 100)
            } else {
                Timber.tag(TAG).i("✅ Termux environment already initialized")
            }
            
            // 2. 检查 FRP 是否已安装
            if (frpcBinary.exists() && frpcBinary.length() > 10_000_000) {
                Timber.tag(TAG).i("✅ FRP binary already installed: ${frpcBinary.absolutePath}")
                
                // 🔧 确保 FRP 也存在于 Termux bin 目录（用于 execvp）
                val termuxBinDir = File(context.filesDir, "termux/bin")
                val frpcInBin = File(termuxBinDir, "frpc")
                if (!frpcInBin.exists() || frpcInBin.length() != frpcBinary.length()) {
                    try {
                        termuxBinDir.mkdirs()
                        frpcBinary.copyTo(frpcInBin, overwrite = true)
                        frpcInBin.setExecutable(true, false)
                        frpcInBin.setReadable(true, false)
                        Timber.tag(TAG).i("✅ FRP binary synced to Termux bin: ${frpcInBin.absolutePath}")
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "❌ Failed to sync FRP to Termux bin")
                        return@withContext Result.failure(e)
                    }
                } else {
                    Timber.tag(TAG).i("✅ FRP binary already in Termux bin")
                }
                
                return@withContext Result.success(Unit)
            }
            
            // 3. 根据策略加载 FRP
            when (ResourceConfig.CURRENT_STRATEGY) {
                ResourceConfig.LoadStrategy.FROM_ASSETS -> {
                    Timber.tag(TAG).i("Loading FRP from assets...")
                    loadFromAssets(onProgress)
                }
                
                ResourceConfig.LoadStrategy.FROM_OSS -> {
                    Timber.tag(TAG).i("Downloading FRP from OSS...")
                    downloadFromOSS(onProgress)
                }
                
                ResourceConfig.LoadStrategy.HYBRID -> {
                    Timber.tag(TAG).i("Loading FRP (hybrid mode: assets first, OSS fallback)...")
                    val assetsResult = loadFromAssets(onProgress)
                    if (assetsResult.isFailure) {
                        Timber.tag(TAG).w("Assets loading failed, trying OSS...")
                        downloadFromOSS(onProgress)
                    } else {
                        assetsResult
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to ensure FRP binary")
            Result.failure(e)
        }
    }
    
    /**
     * 从 assets 加载 FRP
     */
    private suspend fun loadFromAssets(
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val assetPath = ResourceConfig.getFrpAssetPath(deviceArch)
            onProgress?.invoke(60, 100)
            
            // 创建 .phoneagent 目录
            frpcBinary.parentFile?.mkdirs()
            
            // 从 assets 复制到 Termux 环境
            context.assets.open(assetPath).use { input ->
                frpcBinary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytes = 0L
                    var bytes: Int
                    
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        totalBytes += bytes
                        
                        // 更新进度
                        val progress = 60 + (totalBytes * 30 / ResourceConfig.MIN_FRP_SIZE).toInt()
                        onProgress?.invoke(progress.toLong(), 100)
                    }
                }
            }
            
            onProgress?.invoke(90, 100)
            
            // 设置可执行权限
            frpcBinary.setExecutable(true, false)
            frpcBinary.setReadable(true, false)
            
            // 🔧 关键修复：将 FRP 复制到 Termux bin 目录
            // 这样 execvp() 可以在 PATH 中找到它，绕过 SELinux 限制
            try {
                val termuxBinDir = File(context.filesDir, "termux/bin")
                if (!termuxBinDir.exists()) {
                    termuxBinDir.mkdirs()
                }
                val frpcInBin = File(termuxBinDir, "frpc")
                frpcBinary.copyTo(frpcInBin, overwrite = true)
                frpcInBin.setExecutable(true, false)
                frpcInBin.setReadable(true, false)
                Timber.tag(TAG).i("✅ FRP binary copied to Termux bin: ${frpcInBin.absolutePath}")
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "⚠️ Failed to copy FRP to Termux bin (non-critical)")
            }
            
            Timber.tag(TAG).i("✅ FRP binary installed successfully from assets")
            Timber.tag(TAG).i("   Location: ${frpcBinary.absolutePath}")
            Timber.tag(TAG).i("   Size: ${frpcBinary.length()} bytes")
            
            onProgress?.invoke(100, 100)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to extract FRP binary from assets")
            Result.failure(e)
        }
    }
    
    /**
     * 从 OSS 下载 FRP
     */
    private suspend fun downloadFromOSS(
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ossUrl = ResourceConfig.getFrpUrl(deviceArch)
            val downloader = ResourceDownloader(context)
            
            val result = downloader.download(
                url = ossUrl,
                targetFile = frpcBinary,
                minExpectedSize = ResourceConfig.MIN_FRP_SIZE,
                onProgress = { downloaded, total, speed ->
                    // 转换为百分比进度
                    val percentage = if (total > 0) (downloaded * 100 / total) else 0
                    onProgress?.invoke(percentage, 100)
                    
                    // 日志
                    if (percentage % 10 == 0L) {
                        Timber.tag(TAG).d("Downloading FRP: $percentage% (${downloader.formatSpeed(speed)})")
                    }
                }
            )
            
            if (result.isSuccess) {
                // 设置可执行权限
                frpcBinary.setExecutable(true, false)
                frpcBinary.setReadable(true, false)
                
                Timber.tag(TAG).i("✅ FRP binary downloaded successfully from OSS")
                Timber.tag(TAG).i("   Location: ${frpcBinary.absolutePath}")
                Timber.tag(TAG).i("   Size: ${frpcBinary.length()} bytes")
                
                onProgress?.invoke(100, 100)
                Result.success(Unit)
            } else {
                result
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to download FRP binary from OSS")
            Result.failure(e)
        }
    }
    
    
    
    /**
     * 启动 FRP 客户端（在 Termux 环境中）
     * 
     * @param localPort 本地端口，默认 5555 (adbd TCP 端口)
     * 注意：需要提前通过 USB 执行 `adb tcpip 5555` 启用 adbd 的 TCP 模式
     */
    suspend fun start(
        serverIp: String,
        serverPort: Int = 7000,
        token: String,
        localPort: Int = 5555,
        remotePort: Int,
        deviceName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.tag(TAG).i("Starting FRP: $deviceName -> $serverIp:$remotePort")
            
            val ensureResult = ensureFrpcAvailable()
            if (ensureResult.isFailure) {
                val error = ensureResult.exceptionOrNull() ?: Exception("Failed to prepare FRP")
                return@withContext Result.failure(error)
            }
            
            if (isRunning()) {
                Timber.tag(TAG).w("FRP already running, restarting")
                stop()
                delay(1000)
            }
            
            generateConfig(serverIp, serverPort, token, localPort, remotePort, deviceName)
            Timber.tag(TAG).d("FRP config generated: ${frpcConfig.absolutePath}")
            
            if (!frpcBinary.exists()) {
                throw Exception("FRP binary not found: ${frpcBinary.absolutePath}")
            }
            
            if (frpcBinary.length() < 1_000_000) {
                throw Exception("FRP binary is corrupted or incomplete: ${frpcBinary.length()} bytes")
            }
            
            // 🔧 确保 frpc 有执行权限（通过 chmod 命令）
            try {
                val chmodCmd = "chmod 755 ${frpcBinary.absolutePath}"
                val chmodResult = termuxExecutor.execute(chmodCmd)
                if (chmodResult.isSuccess) {
                    Timber.tag(TAG).i("✅ FRP binary permissions set: 755")
                } else {
                    Timber.tag(TAG).w("⚠️ Failed to set FRP permissions: ${chmodResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "⚠️ Error setting FRP permissions")
            }
            
            Timber.tag(TAG).i("Starting FRP client: $deviceName -> $serverIp:$remotePort")
            
            // 检查本地端口 5555 是否可用
            try {
                val checkPortCmd = "cat /proc/net/tcp | grep ':15B3'"  // 5555 的十六进制是 15B3
                val checkResult = termuxExecutor.execute(checkPortCmd)
                if (checkResult.isSuccess) {
                    val execResult = checkResult.getOrNull()
                    if (execResult?.output?.isEmpty() == true) {
                        Timber.tag(TAG).w("Port 5555 is NOT listening! Please run: adb tcpip 5555")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to check port 5555")
            }
            
            // 创建日志文件路径
            val logFile = File(frpcBinary.parentFile, "frpc.log")
            
            // 🔧 通过 Termux shell 执行 frpc
            // 注意：需要 targetSdk=28 来绕过 Android 10+ 的 W^X 限制
            // 详见：docs/technical/ANDROID_WX_RESTRICTION_TARGETSDK.md
            val frpcCommand = "frpc -c ${frpcConfig.absolutePath} > ${logFile.absolutePath} 2>&1"
            
            Timber.tag(TAG).d("FRP command: $frpcCommand")
            Timber.tag(TAG).d("Working dir: ${frpcBinary.parentFile?.absolutePath}")
            
            val processResult = try {
                withTimeout(30_000) {
                    termuxExecutor.startBackground(
                        command = frpcCommand,
                        workingDir = frpcBinary.parentFile
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Timber.tag(TAG).e("FRP startup timeout (SELinux issue?)")
                throw Exception("FRP startup timeout", e)
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to start FRP")
                throw e
            }
            
            if (processResult.isSuccess) {
                frpcProcess = processResult.getOrNull()
                val pid = (frpcProcess as? com.phoneagent.remote.termux.TermuxProcess)?.getPid()
                Timber.tag(TAG).i("FRP process started: PID=$pid")
                
                // 🆕 设置 FRP 进程优先级为最高（防止被系统杀死）
                if (pid != null) {
                    try {
                        // 方法1: 尝试设置 OOM_ADJ (可能需要root)
                        val result1 = termuxExecutor.execute("echo -17 > /proc/$pid/oom_adj")
                        if (result1.isSuccess) {
                            Timber.tag(TAG).i("✅ FRP process OOM_ADJ set to -17 (highest priority)")
                        } else {
                            // 方法2: 降级为 OOM_SCORE_ADJ (更通用)
                            val result2 = termuxExecutor.execute("echo -1000 > /proc/$pid/oom_score_adj")
                            if (result2.isSuccess) {
                                Timber.tag(TAG).i("✅ FRP process OOM_SCORE_ADJ set to -1000")
                            } else {
                                Timber.tag(TAG).w("⚠️ Failed to set FRP process OOM priority (may require root)")
                            }
                        }
                        
                        // 方法3: 使用 renice 提升进程调度优先级
                        val result3 = termuxExecutor.execute("renice -n -20 -p $pid")
                        if (result3.isSuccess) {
                            Timber.tag(TAG).i("✅ FRP process nice value set to -20 (highest CPU priority)")
                        }
                        
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "⚠️ Failed to optimize FRP process priority (non-critical)")
                        // 不影响主流程，继续运行
                    }
                }
                
                startLogReader()
                delay(2000)
                
                if (isRunning()) {
                    Timber.tag(TAG).i("FRP client started successfully")
                    Result.success(Unit)
                } else {
                    Timber.tag(TAG).e("⚠️ FRP process is not running! Check logs above for errors.")
                    throw Exception("FRP process died after start")
                }
            } else {
                val error = processResult.exceptionOrNull() ?: Exception("Failed to start FRP")
                throw error
            }
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start FRP client")
            Result.failure(e)
        }
    }
    
    /**
     * 生成 FRP 配置文件
     */
    private fun generateConfig(
        serverIp: String,
        serverPort: Int,
        token: String,
        localPort: Int,
        remotePort: Int,
        deviceName: String
    ) {
        val config = """
            [common]
            server_addr = $serverIp
            server_port = $serverPort
            token = $token
            
            [${deviceName}_adb]
            type = tcp
            local_ip = 127.0.0.1
            local_port = $localPort
            remote_port = $remotePort
        """.trimIndent()
        
        frpcConfig.parentFile?.mkdirs()
        frpcConfig.writeText(config)
        
        Timber.tag(TAG).d("Config generated: ${frpcConfig.absolutePath}")
    }
    
    /**
     * 启动日志读取线程
     */
    private fun startLogReader() {
        logReaderJob?.cancel()
        
        Timber.tag(TAG).d("Starting FRP log reader...")
        
        // 同时读取标准输出和错误输出
        logReaderJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // 启动两个协程分别读取 stdout 和 stderr
                val stdoutJob = launch {
                    try {
                        Timber.tag(TAG).d("Reading FRP stdout...")
                        frpcProcess?.inputStream?.bufferedReader()?.use { reader ->
                            var lineCount = 0
                            reader.lineSequence().forEach { line ->
                                lineCount++
                                if (line.isNotBlank()) {
                                    Timber.tag("FRP").i("[STDOUT] $line")
                                } else {
                                    Timber.tag("FRP").d("[STDOUT] <empty line>")
                                }
                            }
                            Timber.tag(TAG).d("FRP stdout closed after $lineCount lines")
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error reading FRP stdout")
                    }
                }
                
                val stderrJob = launch {
                    try {
                        Timber.tag(TAG).d("Reading FRP stderr...")
                        frpcProcess?.errorStream?.bufferedReader()?.use { reader ->
                            var lineCount = 0
                            reader.lineSequence().forEach { line ->
                                lineCount++
                                if (line.isNotBlank()) {
                                    Timber.tag("FRP").e("[STDERR] $line")
                                } else {
                                    Timber.tag("FRP").d("[STDERR] <empty line>")
                                }
                            }
                            Timber.tag(TAG).d("FRP stderr closed after $lineCount lines")
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error reading FRP stderr")
                    }
                }
                
                // 等待两个读取任务完成
                stdoutJob.join()
                stderrJob.join()
                Timber.tag(TAG).d("FRP log reader finished")
                
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Log reader stopped")
            }
        }
    }
    
    /**
     * 停止 FRP 客户端
     */
    fun stop() {
        try {
            logReaderJob?.cancel()
            logReaderJob = null
            
            frpcProcess?.destroy()
            frpcProcess = null
            
            Timber.tag(TAG).i("✅ FRP client stopped")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error stopping FRP")
        }
    }
    
    /**
     * 检查 FRP 进程是否运行
     */
    fun isRunning(): Boolean {
        return try {
            val process = frpcProcess ?: return false
            // 兼容 API 21+：尝试获取 exitValue，如果抛出异常说明进程还在运行
            process.exitValue()
            false  // 如果成功获取到 exitValue，说明进程已结束
        } catch (e: IllegalThreadStateException) {
            true  // 抛出异常说明进程还在运行
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查 FRP 二进制文件是否存在
     */
    fun isBinaryAvailable(): Boolean {
        return frpcBinary.exists() && frpcBinary.length() > 0
    }
    
    companion object {
        private const val TAG = "FrpManager"
    }
}
