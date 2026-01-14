package com.phoneagent.remote.core

/**
 * 资源加载策略配置
 * 
 * @author tmwgsicp
 */
object ResourceConfig {
    /**
     * 资源加载策略
     */
    enum class LoadStrategy {
        /** 从 APK assets 加载（离线，APK 体积大） */
        FROM_ASSETS,
        
        /** 从 OSS 下载（在线，APK 体积小） */
        FROM_OSS,
        
        /** 混合：优先 assets，失败则 OSS（推荐） */
        HYBRID
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 全局策略配置（修改这里即可切换策略）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * 当前使用的加载策略
     * 
     * 建议：
     * - 开发/测试：FROM_ASSETS（快速，无需网络）
     * - 生产环境：FROM_OSS（APK 小，易更新）
     * - 兼容模式：HYBRID（优先离线，自动降级）
     */
    val CURRENT_STRATEGY = LoadStrategy.FROM_ASSETS  // ✅ 预装模式：APK ~70MB，安装即用
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // OSS 配置
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    private const val OSS_BASE_URL = "https://phoneagent.oss-cn-beijing.aliyuncs.com"
    
    /**
     * Termux Bootstrap OSS URL
     */
    fun getTermuxBootstrapUrl(arch: String): String {
        return when (arch) {
            "aarch64", "arm64" -> "$OSS_BASE_URL/packages/bootstrap-aarch64.zip"
            "arm", "armv7l" -> "$OSS_BASE_URL/packages/bootstrap-arm.zip"
            else -> throw IllegalArgumentException("Unsupported architecture: $arch")
        }
    }
    
    /**
     * FRP 客户端 OSS URL
     */
    fun getFrpUrl(arch: String): String {
        return when (arch) {
            "arm64" -> "$OSS_BASE_URL/packages/frp_0.65.0_android_arm64_frpc"
            "arm" -> "$OSS_BASE_URL/packages/frp_0.65.0_linux_arm_frpc"
            else -> throw IllegalArgumentException("Unsupported architecture: $arch")
        }
    }
    
    /**
     * yadb OSS URL
     */
    const val YADB_URL = "$OSS_BASE_URL/packages/yadb"
    const val YADB_MD5 = "29a0cd3b3adea92350dd5a25594593df"
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Assets 路径配置
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Termux Bootstrap Assets 路径
     */
    fun getTermuxBootstrapAssetPath(arch: String): String {
        return when (arch) {
            "aarch64", "arm64" -> "termux/bootstrap-aarch64.zip"
            "arm", "armv7l" -> "termux/bootstrap-arm.zip"
            else -> throw IllegalArgumentException("Unsupported architecture: $arch")
        }
    }
    
    /**
     * FRP 客户端 Assets 路径
     */
    fun getFrpAssetPath(arch: String): String {
        return when (arch) {
            "arm64" -> "frp/frpc_arm64"
            "arm" -> "frp/frpc_arm"
            else -> throw IllegalArgumentException("Unsupported architecture: $arch")
        }
    }
    
    /**
     * yadb Assets 路径
     */
    const val YADB_ASSET_PATH = "yadb/yadb"
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 文件大小验证（用于判断文件是否完整）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /** Termux Bootstrap 最小大小（15 MB） */
    const val MIN_BOOTSTRAP_SIZE = 15 * 1024 * 1024L
    
    /** FRP 最小大小（10 MB） */
    const val MIN_FRP_SIZE = 10 * 1024 * 1024L
    
    /** yadb 最小大小（50 KB） */
    const val MIN_YADB_SIZE = 50 * 1024L
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 网络配置
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /** 连接超时（秒） */
    const val CONNECT_TIMEOUT = 30
    
    /** 读取超时（秒） */
    const val READ_TIMEOUT = 120
    
    /** 下载重试次数 */
    const val MAX_RETRY = 3
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 预估 APK 大小（供参考）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * 各策略对应的 APK 大小估算
     */
    fun getEstimatedApkSize(strategy: LoadStrategy): String {
        return when (strategy) {
            LoadStrategy.FROM_ASSETS -> "~70 MB (通用) / ~45 MB (单架构)"
            LoadStrategy.FROM_OSS -> "~5 MB (仅代码和 JNI)"
            LoadStrategy.HYBRID -> "~70 MB (包含 assets 备用)"
        }
    }
    
    /**
     * 首次启动时间估算
     */
    fun getEstimatedFirstLaunchTime(strategy: LoadStrategy): String {
        return when (strategy) {
            LoadStrategy.FROM_ASSETS -> "5-10 秒（解压 bootstrap）"
            LoadStrategy.FROM_OSS -> "30-60 秒（下载 + 解压，取决于网速）"
            LoadStrategy.HYBRID -> "5-10 秒（优先使用 assets）"
        }
    }
}

