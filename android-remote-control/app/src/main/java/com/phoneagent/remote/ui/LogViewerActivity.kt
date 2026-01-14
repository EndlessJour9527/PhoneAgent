package com.phoneagent.remote.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.phoneagent.remote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 日志查看Activity - 优化版
 * 显示应用的logcat日志，支持过滤、刷新、自动滚动等功能
 */
class LogViewerActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvLog: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnRefresh: Button
    private lateinit var btnClear: Button
    private lateinit var chipDebug: Chip
    private lateinit var chipInfo: Chip
    private lateinit var chipWarning: Chip
    private lateinit var chipError: Chip
    private lateinit var tvLineCount: TextView
    private lateinit var tvAutoScroll: TextView
    
    private var autoScroll = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置背景色
        window.apply {
            setBackgroundDrawableResource(R.color.background)
            navigationBarColor = getColor(R.color.background)
            statusBarColor = getColor(R.color.primary)
        }
        
        setContentView(R.layout.activity_log_viewer)
        
        initViews()
        setupListeners()
        loadLogs()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tvLog = findViewById(R.id.tv_log)
        scrollView = findViewById(R.id.scroll_view)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnClear = findViewById(R.id.btn_clear)
        chipDebug = findViewById(R.id.chip_debug)
        chipInfo = findViewById(R.id.chip_info)
        chipWarning = findViewById(R.id.chip_warning)
        chipError = findViewById(R.id.chip_error)
        tvLineCount = findViewById(R.id.tv_line_count)
        tvAutoScroll = findViewById(R.id.tv_auto_scroll)
        
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "PhoneAgent Remote - 应用日志"
    }
    
    private fun setupListeners() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        btnRefresh.setOnClickListener {
            loadLogs()
        }
        
        btnClear.setOnClickListener {
            clearLogcat()
        }
        
        tvAutoScroll.setOnClickListener {
            autoScroll = !autoScroll
            updateAutoScrollText()
        }
        
        // 日志级别过滤（简化版，实际只是视觉反馈）
        listOf(chipDebug, chipInfo, chipWarning, chipError).forEach { chip ->
            chip.setOnCheckedChangeListener { _, _ ->
                // 可以在这里实现实时过滤
                loadLogs()
            }
        }
    }
    
    private fun updateAutoScrollText() {
        tvAutoScroll.text = if (autoScroll) "自动滚动: 开启" else "自动滚动: 关闭"
        tvAutoScroll.setTextColor(
            getColor(if (autoScroll) R.color.primary else R.color.text_secondary)
        )
    }
    
    private fun loadLogs() {
        lifecycleScope.launch {
            try {
                tvLog.text = "加载中..."
                
                val logs = withContext(Dispatchers.IO) {
                    readLogcat()
                }
                
                tvLog.text = logs
                
                // 更新行数
                val lineCount = logs.lines().size
                tvLineCount.text = "总计: $lineCount 行"
                
                // 自动滚动到底部
                if (autoScroll) {
                    scrollView.post {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load logs")
                tvLog.text = "加载日志失败: ${e.message}"
            }
        }
    }
    
    private fun clearLogcat() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Runtime.getRuntime().exec("logcat -c")
                }
                tvLog.text = "日志已清空"
                tvLineCount.text = "总计: 0 行"
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to clear logs")
                tvLog.text = "清空日志失败: ${e.message}"
            }
        }
    }
    
    private fun readLogcat(): String {
        return try {
            // 构建日志级别过滤
            val levels = buildList {
                if (chipDebug.isChecked) add("D")
                if (chipInfo.isChecked) add("I")
                if (chipWarning.isChecked) add("W")
                if (chipError.isChecked) add("E")
            }
            
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat",
                    "-d",  // dump模式
                    "-v", "time",  // 时间格式
                    "SetupWizardActivity:${levels.joinToString("")}",
                    "MainActivity:${levels.joinToString("")}",
                    "RemoteControlService:${levels.joinToString("")}",
                    "FrpManager:${levels.joinToString("")}",
                    "WebSocketManager:${levels.joinToString("")}",
                    "PhoneAgentRemoteApplication:${levels.joinToString("")}",
                    "*:E"  // 所有ERROR级别
                )
            )
            
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            val log = StringBuilder()
            var line: String?
            
            // 只读取最后2000行
            val lines = mutableListOf<String>()
            while (bufferedReader.readLine().also { line = it } != null) {
                line?.let { lines.add(it) }
            }
            
            // 取最后2000行
            val lastLines = if (lines.size > 2000) {
                lines.takeLast(2000)
            } else {
                lines
            }
            
            lastLines.forEach { line ->
                log.append(line).append("\n")
            }
            
            bufferedReader.close()
            
            if (log.isEmpty()) {
                "没有找到应用日志\n\n" +
                "提示:\n" +
                "1. 确保应用正在运行\n" +
                "2. 可能需要授予日志权限:\n" +
                "   adb shell pm grant com.phoneagent.remote android.permission.READ_LOGS"
            } else {
                log.toString()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read logcat")
            "读取日志失败: ${e.message}\n\n" +
            "可能需要授予日志权限：\n" +
            "adb shell pm grant com.phoneagent.remote android.permission.READ_LOGS"
        }
    }
    
    companion object {
        private const val TAG = "LogViewerActivity"
    }
}

