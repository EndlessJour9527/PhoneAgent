/*
 * Copyright © Termux Contributors
 * Copyright © 2025 tmwgsicp
 * 
 * This file is based on Termux (GPLv3).
 * Modified for PhoneAgent Remote and licensed under AGPL v3.
 * 
 * Original: https://github.com/termux/termux-app
 */

package com.phoneagent.remote.termux

import android.content.Context
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Termux Bootstrap 管理器
 * 
 * 负责：
 * 1. 检查 Termux 环境是否已初始化
 * 2. 从 assets 解压 bootstrap
 * 3. 设置正确的文件权限
 * 4. 提供环境信息
 */
class TermuxBootstrapManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TermuxBootstrap"
        
        // Termux 目录结构
        private const val TERMUX_PREFIX_DIR_NAME = "termux"
        private const val TERMUX_HOME_DIR_NAME = "home"
        
        // Bootstrap 文件名
        private const val BOOTSTRAP_ARM64 = "bootstrap-aarch64.zip"
        private const val BOOTSTRAP_ARM = "bootstrap-arm.zip"
    }
    
    // Termux 根目录
    private val termuxDir: File = File(context.filesDir, TERMUX_PREFIX_DIR_NAME)
    
    // Termux usr 目录 (PREFIX)
    // 注意：实际的 bootstrap 可能直接解压到 termux/ 而不是 termux/usr/
    private val prefixDir: File get() {
        // 检查实际的目录结构
        val usrDir = File(termuxDir, "usr")
        return if (usrDir.exists() && usrDir.isDirectory) {
            usrDir
        } else {
            // Bootstrap 直接解压到 termux/
            termuxDir
        }
    }
    
    // Termux home 目录
    private val homeDir: File = File(termuxDir, TERMUX_HOME_DIR_NAME)
    
    // Shell 可执行文件（动态查找实际存在的）
    private val shellFile: File get() {
        val possiblePaths = listOf(
            File(termuxDir, "bin/sh"),
            File(termuxDir, "bin/dash"),
            File(termuxDir, "bin/bash"),
            File(termuxDir, "usr/bin/sh"),
            File(termuxDir, "usr/bin/dash"),
            File(termuxDir, "usr/bin/bash")
        )
        // 返回第一个实际存在且可执行的 shell
        return possiblePaths.firstOrNull { it.exists() && it.canExecute() }
            ?: possiblePaths[0]  // 如果都不存在，返回默认值
    }
    
    /**
     * 检查 Termux 环境是否已初始化
     */
    fun isInitialized(): Boolean {
        android.util.Log.e(TAG, "Checking if Termux is initialized...")
        android.util.Log.e(TAG, "Termux dir: ${termuxDir.absolutePath}")
        
        if (!termuxDir.exists()) {
            android.util.Log.e(TAG, "❌ Termux dir doesn't exist")
            Timber.tag(TAG).d("Termux initialized: false (termux dir doesn't exist)")
            return false
        }
        
        // 尝试多个可能的 shell 文件位置
        // 注意：sh 是 dash 的符号链接，可能需要检查 dash
        val possibleShellFiles = listOf(
            File(termuxDir, "bin/sh"),           // 符号链接（如果创建成功）
            File(termuxDir, "bin/dash"),         // 实际的 shell
            File(termuxDir, "bin/bash"),         // 备选 shell
            File(termuxDir, "usr/bin/sh"),       // 标准 Termux 结构
            File(termuxDir, "usr/bin/bash")      // 备选
        )
        
        val actualShellFile = possibleShellFiles.firstOrNull { 
            val exists = it.exists()
            val canExec = it.canExecute()
            if (exists) {
                Timber.tag(TAG).d("Found shell candidate: ${it.absolutePath}, executable: $canExec")
            }
            exists && canExec
        }
        
        val initialized = actualShellFile != null
        
        android.util.Log.e(TAG, "Termux initialized: $initialized")
        Timber.tag(TAG).d("Termux initialized: $initialized")
        Timber.tag(TAG).d("Termux dir: ${termuxDir.absolutePath}")
        
        if (actualShellFile != null) {
            android.util.Log.e(TAG, "✅ Shell file found: ${actualShellFile.absolutePath}")
            Timber.tag(TAG).i("✅ Shell file found: ${actualShellFile.absolutePath}")
        } else {
            android.util.Log.e(TAG, "❌ Shell file not found in any expected location")
            Timber.tag(TAG).w("❌ Shell file not found in any expected location")
            possibleShellFiles.forEach { file ->
                android.util.Log.e(TAG, "  Checked: ${file.absolutePath}, exists: ${file.exists()}, executable: ${file.canExecute()}")
                Timber.tag(TAG).d("  Checked: ${file.absolutePath}, exists: ${file.exists()}, executable: ${file.canExecute()}")
            }
        }
        
        return initialized
    }
    
    /**
     * 初始化 Termux 环境
     * 
     * 从 assets 解压 bootstrap 到 filesDir/termux
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized()) {
                Timber.tag(TAG).i("Termux already initialized")
                return@withContext Result.success(Unit)
            }
            
            Timber.tag(TAG).i("Initializing Termux environment...")
            Timber.tag(TAG).d("Target directory: ${termuxDir.absolutePath}")
            
            // 获取设备架构
            val arch = getDeviceArch()
            Timber.tag(TAG).i("Device architecture: $arch")
            
            // 确定 bootstrap 文件名
            val bootstrapAsset = when (arch) {
                "aarch64", "arm64-v8a" -> BOOTSTRAP_ARM64
                "arm", "armeabi-v7a" -> BOOTSTRAP_ARM
                else -> {
                    Timber.tag(TAG).e("Unsupported architecture: $arch")
                    return@withContext Result.failure(
                        Exception("Unsupported architecture: $arch")
                    )
                }
            }
            
            Timber.tag(TAG).i("Using bootstrap: $bootstrapAsset")
            
            // 解压 bootstrap
            extractBootstrap(bootstrapAsset)
            
            // 创建 home 目录
            if (!homeDir.exists()) {
                homeDir.mkdirs()
                Timber.tag(TAG).d("Created home directory: ${homeDir.absolutePath}")
            }
            
            // 设置权限
            setPermissions()
            
            // 验证安装
            if (!isInitialized()) {
                throw Exception("Bootstrap installation verification failed")
            }
            
            Timber.tag(TAG).i("✅ Termux environment initialized successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to initialize Termux")
            Result.failure(e)
        }
    }
    
    /**
     * 从 assets 解压 bootstrap
     */
    private fun extractBootstrap(bootstrapAsset: String) {
        Timber.tag(TAG).i("Extracting bootstrap from assets/$TERMUX_PREFIX_DIR_NAME/$bootstrapAsset...")
        
        // 清理旧的安装
        if (termuxDir.exists()) {
            Timber.tag(TAG).d("Cleaning old installation...")
            termuxDir.deleteRecursively()
        }
        
        // 创建 termux 目录
        termuxDir.mkdirs()
        
        // 解压 ZIP
        context.assets.open("$TERMUX_PREFIX_DIR_NAME/$bootstrapAsset").use { inputStream ->
            ZipInputStream(inputStream).use { zipInput ->
                var entry = zipInput.nextEntry
                var fileCount = 0
                
                while (entry != null) {
                    val targetFile = File(termuxDir, entry.name)
                    
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        // 确保父目录存在
                        targetFile.parentFile?.mkdirs()
                        
                        // 写入文件
                        FileOutputStream(targetFile).use { output ->
                            zipInput.copyTo(output)
                        }
                        
                        fileCount++
                        if (fileCount % 100 == 0) {
                            Timber.tag(TAG).d("Extracted $fileCount files...")
                        }
                    }
                    
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                }
                
                Timber.tag(TAG).i("Extracted $fileCount files total")
            }
        }
        
        // 手动创建关键符号链接
        // sh -> dash (符号链接在 Android 上需要手动创建)
        createSymlink("bin/dash", "bin/sh")
    }
    
    /**
     * 创建符号链接的替代方案：复制文件
     * Android 上符号链接权限受限，直接复制更可靠
     */
    private fun createSymlink(source: String, target: String) {
        val sourceFile = File(termuxDir, source)
        val targetFile = File(termuxDir, target)
        
        if (sourceFile.exists() && !targetFile.exists()) {
            try {
                // 方法1: 尝试创建硬链接（Java NIO）
                try {
                    java.nio.file.Files.createLink(
                        targetFile.toPath(),
                        sourceFile.toPath()
                    )
                    Timber.tag(TAG).d("Created hard link: $target -> $source")
                    return
                } catch (e: Exception) {
                    // 硬链接失败，使用复制
                    Timber.tag(TAG).d("Hard link failed, copying file instead: ${e.message}")
                }
                
                // 方法2: 直接复制文件
                sourceFile.copyTo(targetFile, overwrite = false)
                Timber.tag(TAG).i("✅ Created symlink substitute by copying: $target -> $source")
            } catch (e: Exception) {
                Timber.tag(TAG).w("Failed to create symlink for $target: ${e.message}")
            }
        }
    }
    
    /**
     * 设置文件权限
     */
    private fun setPermissions() {
        Timber.tag(TAG).d("Setting file permissions...")
        
        // 找到所有可能的 bin 目录
        val possibleBinDirs = listOf(
            File(prefixDir, "bin"),
            File(termuxDir, "bin"),
            File(termuxDir, "usr/bin")
        )
        
        var binCount = 0
        var shFound = false
        for (binDir in possibleBinDirs) {
            if (binDir.exists() && binDir.isDirectory) {
                Timber.tag(TAG).d("Found bin directory: ${binDir.absolutePath}")
                binDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val success = file.setExecutable(true, false) && file.setReadable(true, false)
                        if (success) {
                            binCount++
                            if (file.name == "sh") {
                                shFound = true
                                Timber.tag(TAG).i("✅ Set executable: ${file.absolutePath}")
                                // 立即验证
                                Timber.tag(TAG).d("   Immediately checking: exists=${file.exists()}, canExecute=${file.canExecute()}")
                            }
                        }
                    }
                }
            }
        }
        Timber.tag(TAG).d("Set executable permissions for $binCount binaries")
        if (!shFound) {
            Timber.tag(TAG).w("⚠️ Shell file 'sh' not found in any bin directory!")
        }
        
        // 设置 lib 目录下所有 .so 文件为可读可执行
        val possibleLibDirs = listOf(
            File(prefixDir, "lib"),
            File(termuxDir, "lib"),
            File(termuxDir, "usr/lib")
        )
        
        var libCount = 0
        for (libDir in possibleLibDirs) {
            if (libDir.exists() && libDir.isDirectory) {
                Timber.tag(TAG).d("Found lib directory: ${libDir.absolutePath}")
                libDir.walkTopDown().forEach { file ->
                    if (file.isFile && file.name.endsWith(".so")) {
                        if (file.setReadable(true, false) && file.setExecutable(true, false)) {
                            libCount++
                        }
                    }
                }
            }
        }
        Timber.tag(TAG).d("Set permissions for $libCount libraries")
    }
    
    /**
     * 获取设备架构
     */
    private fun getDeviceArch(): String {
        val arch = System.getProperty("os.arch") ?: ""
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        
        return when {
            abi.contains("arm64") || abi.contains("aarch64") -> "aarch64"
            abi.contains("armeabi") -> "arm"
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("arm") -> "arm"
            else -> abi
        }
    }
    
    /**
     * 获取 bin 目录
     */
    fun getBinDir(): File {
        val possibleBinDirs = listOf(
            File(prefixDir, "bin"),
            File(termuxDir, "bin"),
            File(termuxDir, "usr/bin")
        )
        
        return possibleBinDirs.firstOrNull { it.exists() && it.isDirectory }
            ?: File(termuxDir, "bin").also { it.mkdirs() }
    }
    
    /**
     * 获取环境信息
     */
    fun getEnvironmentInfo(): TermuxEnvironmentInfo {
        return TermuxEnvironmentInfo(
            prefix = prefixDir.absolutePath,
            home = homeDir.absolutePath,
            shell = shellFile.absolutePath,
            termuxDir = termuxDir.absolutePath,
            isInitialized = isInitialized()
        )
    }
    
    /**
     * 获取环境变量
     */
    fun getEnvironmentVariables(): Map<String, String> {
        val prefix = prefixDir.absolutePath
        val home = homeDir.absolutePath
        
        return mapOf(
            "HOME" to home,
            "PREFIX" to prefix,
            "TMPDIR" to "$prefix/tmp",
            "PATH" to "$prefix/bin:$prefix/usr/bin:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH" to "$prefix/lib:$prefix/usr/lib:/system/lib64:/system/lib",
            "SHELL" to shellFile.absolutePath,
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
            "COLORTERM" to "truecolor"
        )
    }
}

/**
 * Termux 环境信息
 */
data class TermuxEnvironmentInfo(
    val prefix: String,
    val home: String,
    val shell: String,
    val termuxDir: String,
    val isInitialized: Boolean
)

