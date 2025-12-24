<template>
  <div class="logs-page">
    <TopNavigation />
    <PageHeader title="系统日志" subtitle="查看和管理系统运行日志">
      <template #actions>
        <el-button type="danger" @click="clearLogs" :icon="Delete">清空日志</el-button>
        <el-button @click="loadLogs" :icon="Refresh" circle :loading="loading" />
      </template>
    </PageHeader>

    <div class="page-container">
      <!-- 筛选器 -->
      <el-card class="filter-card unified-card" shadow="never">
        <div class="filter-content">
          <el-select
            v-model="filterLevel"
            placeholder="日志级别"
            clearable
            style="width: 150px"
            @change="loadLogs"
          >
            <el-option label="全部" value="" />
            <el-option label="DEBUG" value="DEBUG" />
            <el-option label="INFO" value="INFO" />
            <el-option label="WARNING" value="WARNING" />
            <el-option label="ERROR" value="ERROR" />
          </el-select>

          <el-input
            v-model="searchKeyword"
            placeholder="搜索关键词"
            clearable
            style="flex: 1; max-width: 400px"
            @keyup.enter="loadLogs"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          
          <div class="log-count">
            <el-tag size="small" type="info">{{ logs.length }} 条记录</el-tag>
          </div>
        </div>
      </el-card>
      
      <!-- 日志列表 -->
      <el-card class="unified-card" shadow="never">
        <template #header>
          <div class="card-header-unified">
            <div class="card-title-content">
              <el-icon><Document /></el-icon>
              <span class="card-title-text">日志列表</span>
            </div>
          </div>
        </template>

        <!-- 日志表格 -->
        <el-table
          :data="logs"
          v-loading="loading"
          stripe
          class="logs-table"
          :max-height="600"
        >
          <el-table-column prop="timestamp" label="时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.timestamp) }}
            </template>
          </el-table-column>

          <el-table-column prop="level" label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="getLevelType(row.level)" size="small">
                {{ row.level }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="message" label="消息">
            <template #default="{ row }">
              <div class="log-message" :class="'level-' + row.level.toLowerCase()">
                {{ row.message }}
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button
                type="text"
                size="small"
                @click="viewDetail(row)"
              >
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[50, 100, 200, 500]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadLogs"
          @size-change="loadLogs"
          class="pagination-container"
        />
      </el-card>
    </div>

    <!-- 日志详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="日志详情"
      width="70%"
    >
      <el-descriptions :column="1" border v-if="selectedLog">
        <el-descriptions-item label="时间">
          {{ formatTime(selectedLog.timestamp) }}
        </el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag :type="getLevelType(selectedLog.level)">
            {{ selectedLog.level }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="消息">
          <pre class="log-message-pre">{{ selectedLog.message }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete } from '@element-plus/icons-vue'
import { request } from '@/api'
import TopNavigation from '@/components/TopNavigation.vue'
import PageHeader from '@/components/PageHeader.vue'

const logs = ref([])
const loading = ref(false)
const filterLevel = ref('')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(100)
const total = ref(0)
const detailDialogVisible = ref(false)
const selectedLog = ref(null)

async function loadLogs() {
  loading.value = true
  try {
    const params = {
      offset: (currentPage.value - 1) * pageSize.value,
      limit: pageSize.value
    }
    
    if (filterLevel.value) {
      params.level = filterLevel.value
    }
    
    if (searchKeyword.value) {
      params.search = searchKeyword.value
    }

    const response = await request.get('/logs', { params })
    logs.value = response.logs || []
    total.value = response.total || 0
  } catch (error) {
    ElMessage.error('加载日志失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

async function clearLogs() {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有日志吗？日志会被备份到 logs_backup 目录',
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await request.delete('/logs')
    ElMessage.success('日志已清空并备份')
    loadLogs()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('清空日志失败: ' + error.message)
    }
  }
}

function getLevelType(level) {
  const types = {
    DEBUG: 'info',
    INFO: 'success',
    WARNING: 'warning',
    ERROR: 'danger'
  }
  return types[level] || 'info'
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN')
}

function viewDetail(log) {
  selectedLog.value = log
  detailDialogVisible.value = true
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.logs-page {
  min-height: 100vh;
  background: var(--bg-tertiary);
}

/* 筛选卡片 */
.filter-card {
  margin-bottom: var(--space-lg);
}

.filter-content {
  display: flex;
  gap: var(--space-md);
  align-items: center;
  flex-wrap: wrap;
}

.log-count {
  margin-left: auto;
}

.log-message {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.level-error {
  color: var(--error-color);
}

.level-warning {
  color: var(--warning-color);
}

.level-info {
  color: var(--primary-color);
}

.level-debug {
  color: var(--text-tertiary);
}

.logs-table {
  width: 100%;
  margin-top: var(--space-md);
}

.pagination-container {
  margin-top: var(--space-md);
  justify-content: center;
}

.log-message-pre {
  white-space: pre-wrap;
  word-break: break-all;
}
</style>

