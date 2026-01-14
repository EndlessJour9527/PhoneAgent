/*
 * Copyright © 2025 tmwgsicp
 * Licensed under AGPL v3
 */

package com.phoneagent.remote.termux

import android.content.Context
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.ErrnoException
import com.termux.terminal.JNI

/**
 * Termux 命令执行器
 * 
 * 负责在 Termux 环境中执行命令
 * 使用 Termux 官方的 JNI 接口（libtermux.so）
 */
class TermuxExecutor(private val context: Context) {
    
    companion object {
        private const val TAG = "TermuxExecutor"
        
        init {
            try {
                Class.forName("com.termux.terminal.JNI")
                Timber.tag(TAG).d("JNI class loaded")
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to load JNI class")
                throw e
            }
        }
    }
    
    private val bootstrap = TermuxBootstrapManager(context)
    
    /**
     * 执行命令并等待结果
     * 
     * 使用 Termux 官方的 JNI 接口 (fork + execvp) 来绕过 Android W^X 限制
     */
    suspend fun execute(
        command: String,
        workingDir: File? = null,
        timeoutSeconds: Long = 30
    ): Result<ExecutionResult> = withContext(Dispatchers.IO) {
        try {
            if (!bootstrap.isInitialized()) {
                return@withContext Result.failure(
                    Exception("Termux not initialized")
                )
            }
            
            val envInfo = bootstrap.getEnvironmentInfo()
            val env = bootstrap.getEnvironmentVariables()
            val dir = workingDir ?: File(envInfo.home)
            
            Timber.tag(TAG).d("Executing: $command")
            Timber.tag(TAG).d("Working dir: ${dir.absolutePath}")
            
            // 使用 Termux shell（通过 execvp 的 PATH 查找机制）
            // 关键：cmd 参数传递 "sh"（命令名），而不是完整路径
            // execvp() 会在 PATH 中查找 "sh"，找到 Termux 的 sh 并执行
            val shellCommand = "sh"  // 命令名，不是路径
            Timber.tag(TAG).d("Using shell command: $shellCommand (will be found via PATH)")
            
            val args = arrayOf(
                shellCommand,   // argv[0]: sh（命令名）
                "-c",           // argv[1]
                command         // argv[2]
            )
            
            val envArray = env.map { (key, value) -> "$key=$value" }.toTypedArray()
            val processId = IntArray(1)
            
            // 调用 Termux JNI 创建子进程
            // 关键：cmd 参数是命令名（"sh"），JNI 内部的 execvp() 会在 PATH 中查找
            val fd = JNI.createSubprocess(
                shellCommand,       // cmd: "sh"（命令名，execvp 会在 PATH 中查找）
                dir.absolutePath,   // cwd
                args,               // args
                envArray,           // envVars
                processId,          // processId
                80,                 // rows
                24,                 // columns
                8,                  // cellWidth
                16                  // cellHeight
            )
            
            if (fd < 0) {
                throw Exception("Failed to create subprocess via JNI")
            }
            
            Timber.tag(TAG).d("Process started, PID: ${processId[0]}, FD: $fd")
            
            // 读取输出
            val output = StringBuilder()
            val outputJob = launch {
                try {
                    // 使用反射创建 FileDescriptor，不转移所有权
                    val fileDescriptor = FileDescriptor()
                    val descriptorField = FileDescriptor::class.java.getDeclaredField("descriptor")
                    descriptorField.isAccessible = true
                    descriptorField.setInt(fileDescriptor, fd)
                    
                    val fileInputStream = FileInputStream(fileDescriptor)
                    val buffer = ByteArray(4096)
                    while (true) {
                        val bytesRead = fileInputStream.read(buffer)
                        if (bytesRead == -1) break
                        val text = String(buffer, 0, bytesRead)
                        output.append(text)
                        Timber.tag(TAG).v(text.trim())
                    }
                    // 不关闭 fileInputStream，避免关闭 FD
                } catch (e: Exception) {
                    Timber.tag(TAG).d("Output stream closed: ${e.message}")
                }
            }
            
            // 等待进程完成（带超时）
            val exitCode = withTimeoutOrNull(timeoutSeconds * 1000) {
                JNI.waitFor(processId[0])
            }
            
            if (exitCode == null) {
                // 超时，杀死进程
                try {
                    Os.kill(processId[0], OsConstants.SIGKILL)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to kill process ${processId[0]}")
                }
                outputJob.cancel()
                JNI.close(fd)
                throw Exception("Command execution timed out after $timeoutSeconds seconds")
            }
            
            // 等待输出读取完成
            outputJob.join()
            JNI.close(fd)
            
            val outputStr = output.toString()
            Timber.tag(TAG).d("Command completed with exit code: $exitCode")
            
            Result.success(ExecutionResult(exitCode, outputStr))
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Command execution failed")
            Result.failure(e)
        }
    }
    
    /**
     * 启动后台进程（不等待结果）
     * 
     * 使用 Termux 官方的 JNI 接口 (fork + execvp) 来绕过 Android W^X 限制
     */
    fun startBackground(
        command: String,
        workingDir: File? = null
    ): Result<Process> {
        return try {
            android.util.Log.e(TAG, "━━━━━━ startBackground() BEGIN ━━━━━━")
            android.util.Log.e(TAG, "Command: $command")
            
            if (!bootstrap.isInitialized()) {
                android.util.Log.e(TAG, "❌ Termux not initialized")
                return Result.failure(
                    Exception("Termux not initialized")
                )
            }
            
            val envInfo = bootstrap.getEnvironmentInfo()
            val env = bootstrap.getEnvironmentVariables()
            val dir = workingDir ?: File(envInfo.home)
            
            Timber.tag(TAG).d("Starting background process: $command")
            
            val shellCommand = "sh"
            val args = arrayOf(shellCommand, "-c", command)
            val envArray = env.map { (key, value) -> "$key=$value" }.toTypedArray()
            
            val processId = IntArray(1)
            val fd = try {
                JNI.createSubprocess(
                    shellCommand,
                    dir.absolutePath,
                    args,
                    envArray,
                    processId,
                    80, 24, 8, 16
                )
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to create subprocess")
                throw e
            }
            
            if (fd < 0) {
                throw Exception("Invalid file descriptor: $fd")
            }
            
            Timber.tag(TAG).i("Process started: PID=${processId[0]}")
            
            val process = TermuxProcess(processId[0], fd)
            Result.success(process)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start background process")
            Result.failure(e)
        }
    }
    
    /**
     * 直接执行命令（不通过 shell）
     * 
     * 这个方法直接通过 JNI 执行命令，避免 shell 的子进程受 W^X 限制
     * 
     * @param command 命令名（会在 PATH 中查找）
     * @param args 命令参数
     * @param workingDir 工作目录
     * @param timeoutSeconds 超时时间（秒）
     */
    suspend fun executeDirect(
        command: String,
        args: List<String> = emptyList(),
        workingDir: File? = null,
        timeoutSeconds: Long = 30
    ): Result<ExecutionResult> = withContext(Dispatchers.IO) {
        try {
            if (!bootstrap.isInitialized()) {
                return@withContext Result.failure(
                    Exception("Termux not initialized")
                )
            }
            
            val envInfo = bootstrap.getEnvironmentInfo()
            val env = bootstrap.getEnvironmentVariables()
            val dir = workingDir ?: File(envInfo.home)
            
            Timber.tag(TAG).d("Executing directly: $command ${args.joinToString(" ")}")
            Timber.tag(TAG).d("Working dir: ${dir.absolutePath}")
            
            // 构建参数数组：[command, arg1, arg2, ...]
            val cmdArgs = arrayOf(command) + args.toTypedArray()
            
            val envArray = env.map { (key, value) -> "$key=$value" }.toTypedArray()
            val processId = IntArray(1)
            
            // 关键：直接执行命令，不通过 shell
            val fd = JNI.createSubprocess(
                command,            // cmd: 命令名（execvp 会在 PATH 中查找）
                dir.absolutePath,   // cwd
                cmdArgs,            // args: [command, arg1, arg2, ...]
                envArray,           // envVars
                processId,          // processId
                80,                 // rows
                24,                 // columns
                8,                  // cellWidth
                16                  // cellHeight
            )
            
            if (fd < 0) {
                throw Exception("Failed to create subprocess via JNI (fd=$fd)")
            }
            
            Timber.tag(TAG).d("Process started, PID: ${processId[0]}, FD: $fd")
            
            // 读取输出
            val output = StringBuilder()
            val outputJob = launch {
                try {
                    val fileDescriptor = FileDescriptor()
                    val descriptorField = FileDescriptor::class.java.getDeclaredField("descriptor")
                    descriptorField.isAccessible = true
                    descriptorField.setInt(fileDescriptor, fd)
                    
                    val fileInputStream = FileInputStream(fileDescriptor)
                    val buffer = ByteArray(4096)
                    while (true) {
                        val bytesRead = fileInputStream.read(buffer)
                        if (bytesRead == -1) break
                        val text = String(buffer, 0, bytesRead)
                        output.append(text)
                        Timber.tag(TAG).v(text.trim())
                    }
                    fileInputStream.close()
                } catch (e: Exception) {
                    Timber.tag(TAG).d("Output stream closed: ${e.message}")
                }
            }
            
            // 等待进程结束或超时
            val exitCode = withTimeoutOrNull(timeoutSeconds * 1000) {
                JNI.waitFor(processId[0])
            }
            
            if (exitCode == null) {
                try {
                    Os.kill(processId[0], OsConstants.SIGKILL)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to kill process ${processId[0]}")
                }
                outputJob.cancel()
                JNI.close(fd)
                throw Exception("Command execution timed out after $timeoutSeconds seconds")
            }
            
            outputJob.join()
            JNI.close(fd)
            
            val outputStr = output.toString()
            Timber.tag(TAG).d("Command completed with exit code: $exitCode")
            
            Result.success(ExecutionResult(exitCode, outputStr))
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Command execution failed")
            Result.failure(e)
        }
    }
    
    /**
     * 检查命令是否可用
     */
    suspend fun commandAvailable(command: String): Boolean {
        val result = execute("command -v $command", timeoutSeconds = 5)
        return result.isSuccess && result.getOrNull()?.exitCode == 0
    }
    
    /**
     * 直接启动后台进程（不通过 shell）
     * 
     * 这个方法直接通过 JNI 执行命令，避免 shell 的子进程受 W^X 限制
     * 
     * @param command 命令名（会在 PATH 中查找）
     * @param args 命令参数
     * @param workingDir 工作目录
     */
    suspend fun startBackgroundDirect(
        command: String,
        args: List<String> = emptyList(),
        workingDir: File? = null
    ): Result<Process> = withContext(Dispatchers.IO) {
        try {
            if (!bootstrap.isInitialized()) {
                return@withContext Result.failure(
                    Exception("Termux not initialized")
                )
            }
            
            val envInfo = bootstrap.getEnvironmentInfo()
            val env = bootstrap.getEnvironmentVariables()
            val dir = workingDir ?: File(envInfo.home)
            
            Timber.tag(TAG).i("Starting background process: $command ${args.joinToString(" ")}")
            Timber.tag(TAG).d("Working dir: ${dir.absolutePath}")
            
            // 构建参数数组：[command, arg1, arg2, ...]
            val cmdArgs = arrayOf(command) + args.toTypedArray()
            
            val envArray = env.map { (key, value) -> "$key=$value" }.toTypedArray()
            val processId = IntArray(1)
            
            // 关键：直接执行命令，不通过 shell
            // execvp() 会在 PATH 中查找命令，并在 Termux 进程上下文中执行
            val fd = try {
                JNI.createSubprocess(
                    command,            // cmd: 命令名（execvp 会在 PATH 中查找）
                    dir.absolutePath,   // cwd
                    cmdArgs,            // args: [command, arg1, arg2, ...]
                    envArray,           // envVars
                    processId,          // processId
                    80,                 // rows
                    24,                 // columns
                    8,                  // cellWidth
                    16                  // cellHeight
                )
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to create subprocess")
                throw e
            }
            
            if (fd < 0) {
                throw Exception("Invalid file descriptor: $fd")
            }
            
            Timber.tag(TAG).i("Process started: PID=${processId[0]}")
            
            val process = TermuxProcess(processId[0], fd)
            Result.success(process)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start background process directly")
            Result.failure(e)
        }
    }
    
    /**
     * 安装文件到 Termux 环境
     * 
     * @param sourceFile 源文件
     * @param targetPath 目标路径（相对于 HOME）
     * @param executable 是否设置为可执行
     */
    suspend fun installFile(
        sourceFile: File,
        targetPath: String,
        executable: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!bootstrap.isInitialized()) {
                return@withContext Result.failure(
                    Exception("Termux not initialized")
                )
            }
            
            val envInfo = bootstrap.getEnvironmentInfo()
            val targetFile = File(envInfo.home, targetPath)
            
            // 确保目标目录存在
            targetFile.parentFile?.mkdirs()
            
            // 复制文件
            sourceFile.copyTo(targetFile, overwrite = true)
            
            // 设置权限
            targetFile.setReadable(true, false)
            if (executable) {
                targetFile.setExecutable(true, false)
            }
            
            Timber.tag(TAG).i("Installed file: ${targetFile.absolutePath}")
            Result.success(targetFile)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to install file")
            Result.failure(e)
        }
    }
}

/**
 * 命令执行结果
 */
data class ExecutionResult(
    val exitCode: Int,
    val output: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Termux 进程包装器
 * 
 * 包装通过 Termux JNI 创建的进程，提供 Process 接口
 */
class TermuxProcess(private val pid: Int, private val fd: Int) : Process() {
    
    private var exitValue: Int? = null
    
    override fun getOutputStream(): java.io.OutputStream {
        // 不支持向进程写入（FRP 不需要）
        return java.io.OutputStream.nullOutputStream()
    }
    
    override fun getInputStream(): java.io.InputStream {
        // 返回空流（FRP 的输出我们不需要读取）
        return java.io.ByteArrayInputStream(ByteArray(0))
    }
    
    override fun getErrorStream(): java.io.InputStream {
        return java.io.ByteArrayInputStream(ByteArray(0))
    }
    
    override fun waitFor(): Int {
        if (exitValue == null) {
            exitValue = JNI.waitFor(pid)
        }
        return exitValue!!
    }
    
    override fun exitValue(): Int {
        if (exitValue == null) {
            // 检查进程是否还活着
            try {
                // 尝试使用 Os.kill 发送信号 0 来检查进程是否存在
                Os.kill(pid, 0)
                // 如果成功，进程还活着
                throw IllegalThreadStateException("Process is still running")
            } catch (e: ErrnoException) {
                // kill 失败，进程已结束，获取退出码
                exitValue = JNI.waitFor(pid)
            }
        }
        return exitValue!!
    }
    
    override fun destroy() {
        // 发送 SIGTERM
        try {
            Os.kill(pid, OsConstants.SIGTERM)
        } catch (e: ErrnoException) {
            // 忽略错误
        }
        // 关闭文件描述符
        try {
            JNI.close(fd)
        } catch (e: Exception) {
            // 忽略错误
        }
    }
    
    override fun isAlive(): Boolean {
        if (exitValue != null) {
            return false
        }
        // 使用 Os.kill 发送信号 0 检查进程是否存在
        return try {
            Os.kill(pid, 0)
            true
        } catch (e: ErrnoException) {
            false
        }
    }
    
    fun getPid(): Int = pid
}

