/*
 * Copyright © 2025 tmwgsicp
 * Licensed under AGPL v3
 */

package com.phoneagent.remote.util

/**
 * 应用常量
 */
object AppConstants {
    
    // ============ 超时设置 ============
    
    /** FRP 启动超时 (毫秒) */
    const val FRP_STARTUP_TIMEOUT_MS = 30_000L
    
    /** 命令执行超时 (毫秒) */
    const val COMMAND_EXECUTION_TIMEOUT_MS = 30_000L
    
    /** WebSocket 连接超时 (毫秒) */
    const val WEBSOCKET_CONNECT_TIMEOUT_MS = 10_000L
    
    /** WebSocket 心跳间隔 (毫秒) */
    const val WEBSOCKET_PING_INTERVAL_MS = 30_000L
    
    // ============ 重试设置 ============
    
    /** 最大重试次数 */
    const val MAX_RETRY_ATTEMPTS = 3
    
    /** 重试延迟 (毫秒) */
    const val RETRY_DELAY_MS = 1000L
    
    // ============ 状态更新 ============
    
    /** 状态更新间隔 (毫秒) */
    const val STATUS_UPDATE_INTERVAL_MS = 5000L
    
    /** 健康检查间隔 (毫秒) */
    const val HEALTH_CHECK_INTERVAL_MS = 60_000L
    
    // ============ 文件路径 ============
    
    /** yadb 设备路径 */
    const val YADB_DEVICE_PATH = "/data/local/tmp/yadb"
    
    /** FRP 配置文件名 */
    const val FRPC_CONFIG_NAME = "frpc.ini"
    
    /** FRP 日志文件名 */
    const val FRPC_LOG_NAME = "frpc.log"
    
    // ============ 网络设置 ============
    
    /** 默认 FRP 服务器端口 */
    const val DEFAULT_FRP_PORT = 7000
    
    /** 默认 WebSocket 端口 */
    const val DEFAULT_WS_PORT = 9999
    
    /** 默认远程端口起始值 */
    const val DEFAULT_REMOTE_PORT_START = 6100
    
    // ============ 日志设置 ============
    
    /** 日志文件最大大小 (字节) */
    const val MAX_LOG_FILE_SIZE = 10 * 1024 * 1024  // 10MB
    
    /** 日志文件保留数量 */
    const val MAX_LOG_FILES = 3
    
    // ============ 性能阈值 ============
    
    /** 慢操作阈值 (毫秒) */
    const val SLOW_OPERATION_THRESHOLD_MS = 1000L
    
    /** 内存警告阈值 (字节) */
    const val MEMORY_WARNING_THRESHOLD = 100 * 1024 * 1024  // 100MB
}

