<template>
  <div class="system-logs-page">
    <!-- 统一导航栏 -->
    <TopNavigation />

    <!-- 统一页面头部 -->
    <PageHeader title="系统日志" subtitle="查看和搜索系统运行日志">
      <template #actions>
        <el-button @click="clearLogs" :icon="Delete" type="danger" plain>清空日志</el-button>
        <el-button @click="refresh" :icon="Refresh" circle :loading="loading" />
      </template>
    </PageHeader>

    <div class="page-container">
      <!-- 筛选器 -->
      <el-card class="filter-card unified-card" shadow="never">
        <el-form :inline="true" :model="filterForm" class="filter-form">
          <el-form-item label="日志级别">
            <el-select v-model="filterForm.level" placeholder="全部" clearable style="width: 120px">
              <el-option label="全部" value="" />
              <el-option label="DEBUG" value="DEBUG" />
              <el-option label="INFO" value="INFO" />
              <el-option label="WARNING" value="WARNING" />
              <el-option label="ERROR" value="ERROR" />
              <el-option label="CRITICAL" value="CRITICAL" />
            </el-select>
          </el-form-item>

          <el-form-item label="搜索">
            <el-input
              v-model="filterForm.search"
              placeholder="搜索日志内容"
              clearable
              style="width: 300px"
              @keyup.enter="refresh"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="refresh" :icon="Search">搜索</el-button>
            <el-button @click="resetFilter">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 日志列表 -->
      <el-card class="logs-card unified-card" shadow="never" v-loading="loading">
        <div class="logs-header">
          <span>共 {{ total }} 条日志</span>
          <el-switch
            v-model="autoRefresh"
            active-text="自动刷新"
            inactive-text=""
            @change="toggleAutoRefresh"
          />
        </div>

        <div class="logs-container">
          <div
            v-for="(log, index) in logs"
            :key="index"
            class="log-entry"
            :class="getLevelClass(log.level)"
          >
            <div class="log-header">
              <el-tag :type="getLevelType(log.level)" size="small">
                {{ log.level }}
              </el-tag>
              <span class="log-time">{{ formatTime(log.timestamp) }}</span>
            </div>
            <div class="log-message">{{ log.message }}</div>
            <div class="log-meta" v-if="log.name">
              <span class="log-name">{{ log.name }}</span>
            </div>
          </div>

          <el-empty v-if="logs.length === 0 && !loading" description="暂无日志" />
        </div>

        <!-- 分页 -->
        <el-pagination
          v-if="total > 0"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[50, 100, 200, 500]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          class="pagination"
        />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete, Search } from '@element-plus/icons-vue'
import { request } from '@/api'
import TopNavigation from '@/components/TopNavigation.vue'
import PageHeader from '@/components/PageHeader.vue'

const loading = ref(false)
const logs = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(100)
const autoRefresh = ref(false)
let refreshTimer = null

const filterForm = ref({
  level: '',
  search: ''
})

// 获取日志
async function fetchLogs() {
  loading.value = true
  try {
    const params = {
      limit: pageSize.value,
      offset: (currentPage.value - 1) * pageSize.value
    }
    
    if (filterForm.value.level) {
      params.level = filterForm.value.level
    }
    
    if (filterForm.value.search) {
      params.search = filterForm.value.search
    }
    
    console.log('Fetching logs with params:', params)
    const response = await request.get('/logs', { params })
    console.log('Logs response:', response)
    
    logs.value = response.logs || []
    total.value = response.total || 0
    
    if (logs.value.length === 0) {
      console.warn('No logs returned from API')
    }
  } catch (error) {
    console.error('Failed to fetch logs:', error)
    ElMessage.error('获取日志失败: ' + (error.response?.data?.detail || error.message || '未知错误'))
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 刷新
function refresh() {
  currentPage.value = 1
  fetchLogs()
}

// 重置筛选
function resetFilter() {
  filterForm.value = {
    level: '',
    search: ''
  }
  refresh()
}

// 清空日志
async function clearLogs() {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有日志吗？日志会被备份到 logs_backup 目录。',
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await request.delete('/logs')
    ElMessage.success('日志已清空并备份')
    refresh()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to clear logs:', error)
      ElMessage.error('清空日志失败: ' + (error.message || '未知错误'))
    }
  }
}

// 切换自动刷新
function toggleAutoRefresh(value) {
  if (value) {
    // 开启自动刷新（每5秒）
    refreshTimer = setInterval(() => {
      fetchLogs()
    }, 5000)
    ElMessage.success('已开启自动刷新（5秒/次）')
  } else {
    // 关闭自动刷新
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }
}

// 分页
function handleSizeChange() {
  currentPage.value = 1
  fetchLogs()
}

function handleCurrentChange() {
  fetchLogs()
}

// 日志级别样式
function getLevelClass(level) {
  const classMap = {
    DEBUG: 'log-debug',
    INFO: 'log-info',
    WARNING: 'log-warning',
    ERROR: 'log-error',
    CRITICAL: 'log-critical'
  }
  return classMap[level] || 'log-info'
}

function getLevelType(level) {
  const typeMap = {
    DEBUG: 'info',
    INFO: 'success',
    WARNING: 'warning',
    ERROR: 'danger',
    CRITICAL: 'danger'
  }
  return typeMap[level] || 'info'
}

// 格式化时间
function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date
  
  // 1分钟内显示"刚刚"
  if (diff < 60000) {
    return '刚刚'
  }
  
  // 1小时内显示"X分钟前"
  if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  }
  
  // 今天显示"今天 HH:MM:SS"
  if (date.toDateString() === now.toDateString()) {
    return '今天 ' + date.toLocaleTimeString('zh-CN', { hour12: false })
  }
  
  // 其他显示完整日期时间
  return date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  fetchLogs()
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style scoped>
/* 遵循设计系统规范 */
.system-logs-page {
  min-height: 100vh;
  background: var(--bg-tertiary);
}

.page-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--space-lg) var(--space-lg);
}

/* 筛选卡片 */
.filter-card {
  margin-bottom: var(--space-lg);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
}

.filter-form {
  margin: 0;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

/* 日志卡片 */
.logs-card {
  min-height: 600px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-large);
  box-shadow: var(--shadow-light);
}

.logs-card :deep(.el-card__body) {
  padding: var(--space-lg);
}

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-md);
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--border-light);
  font-size: var(--font-sm);
  color: var(--text-secondary);
  font-weight: 500;
}

/* 日志容器 - 使用统一的系统字体 */
.logs-container {
  max-height: 70vh;
  overflow-y: auto;
  font-size: var(--font-sm);
  /* 使用设计系统的统一字体，而不是等宽字体 */
}

.log-entry {
  padding: var(--space-md);
  margin-bottom: var(--space-sm);
  border-radius: var(--radius-base);
  border-left: 3px solid var(--border-base);
  background: var(--bg-primary);
  transition: all var(--transition-base);
  border: 1px solid var(--border-light);
}

.log-entry:hover {
  background: var(--bg-secondary);
  box-shadow: var(--shadow-light);
  transform: translateX(2px);
}

/* 日志级别颜色 - 使用设计系统颜色 */
.log-debug {
  border-left-color: var(--text-tertiary);
}

.log-info {
  border-left-color: var(--success-color);
}

.log-warning {
  border-left-color: var(--warning-color);
}

.log-error,
.log-critical {
  border-left-color: var(--error-color);
}

.log-critical {
  background: var(--error-bg);
}

.log-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
}

.log-time {
  color: var(--text-tertiary);
  font-size: var(--font-xs);
  font-weight: 400;
}

.log-message {
  color: var(--text-primary);
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  font-size: var(--font-sm);
}

.log-meta {
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px dashed var(--border-light);
}

.log-name {
  color: var(--text-secondary);
  font-size: var(--font-xs);
  font-weight: 500;
}

/* 分页 */
.pagination {
  margin-top: var(--space-lg);
  display: flex;
  justify-content: center;
}

/* 滚动条样式 */
.logs-container::-webkit-scrollbar {
  width: 8px;
}

.logs-container::-webkit-scrollbar-track {
  background: var(--bg-secondary);
  border-radius: var(--radius-small);
}

.logs-container::-webkit-scrollbar-thumb {
  background: var(--border-dark);
  border-radius: var(--radius-small);
}

.logs-container::-webkit-scrollbar-thumb:hover {
  background: var(--text-tertiary);
}
</style>

