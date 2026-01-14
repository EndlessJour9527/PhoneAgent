/*
 * Copyright © 2025 tmwgsicp
 * Licensed under AGPL v3
 */

package com.phoneagent.remote.util

import timber.log.Timber
import java.io.File

/**
 * Kotlin 扩展函数
 */

// ============ File 扩展 ============

/**
 * 安全删除文件(如果存在)
 */
fun File.deleteIfExists(): Boolean {
    return if (exists()) {
        delete()
    } else {
        true
    }
}

/**
 * 安全重命名文件
 */
fun File.renameSafely(dest: File): Boolean {
    return try {
        renameTo(dest)
    } catch (e: Exception) {
        Timber.e(e, "Failed to rename file: $absolutePath -> ${dest.absolutePath}")
        false
    }
}

/**
 * 确保父目录存在
 */
fun File.ensureParentDirs(): Boolean {
    val parent = parentFile ?: return true
    return if (!parent.exists()) {
        parent.mkdirs()
    } else {
        true
    }
}

// ============ Result 扩展 ============

/**
 * 记录失败日志
 */
fun <T> Result<T>.logError(tag: String, message: String): Result<T> {
    if (isFailure) {
        Timber.tag(tag).e(exceptionOrNull(), message)
    }
    return this
}

/**
 * 记录成功日志
 */
fun <T> Result<T>.logSuccess(tag: String, message: String): Result<T> {
    if (isSuccess) {
        Timber.tag(tag).i(message)
    }
    return this
}

/**
 * 转换为可空值
 */
fun <T> Result<T>.getOrNull(): T? {
    return if (isSuccess) getOrNull() else null
}

// ============ String 扩展 ============

/**
 * 安全截断字符串(用于日志)
 */
fun String.truncate(maxLength: Int = 100, suffix: String = "..."): String {
    return if (length <= maxLength) {
        this
    } else {
        substring(0, maxLength - suffix.length) + suffix
    }
}

/**
 * 掩码敏感信息(如token)
 */
fun String.maskSensitive(visibleChars: Int = 4): String {
    return if (length <= visibleChars) {
        "*".repeat(length)
    } else {
        take(visibleChars) + "*".repeat(length - visibleChars)
    }
}

// ============ 性能监控扩展 ============

/**
 * 测量执行时间
 */
inline fun <T> measureTime(tag: String, operation: String, block: () -> T): T {
    val startTime = System.currentTimeMillis()
    try {
        return block()
    } finally {
        val duration = System.currentTimeMillis() - startTime
        if (duration > AppConstants.SLOW_OPERATION_THRESHOLD_MS) {
            Timber.tag(tag).w("Slow operation: $operation took ${duration}ms")
        } else {
            Timber.tag(tag).d("$operation took ${duration}ms")
        }
    }
}

