<template>
  <el-dialog
    v-model="visible"
    title="性能诊断"
    width="90%"
    :close-on-click-modal="false"
    class="diagnostic-dialog"
  >
    <div class="diagnostic-panel">
      <!-- 摘要信息 -->
      <el-row :gutter="20" class="summary-cards">
        <el-col :span="6">
          <el-card shadow="never" class="stat-card-unified">
            <div class="stat-card">
              <el-icon :size="32" color="#409eff"><Document /></el-icon>
              <div class="stat-content">
                <div class="stat-value">{{ frontendStats.totalRequests }}</div>
                <div class="stat-label">总请求数</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card-unified">
            <div class="stat-card">
              <el-icon :size="32" color="#67c23a"><Loading /></el-icon>
              <div class="stat-content">
                <div class="stat-value">{{ frontendStats.activeRequests }}</div>
                <div class="stat-label">活跃请求</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card-unified">
            <div class="stat-card">
              <el-icon :size="32" color="#e6a23c"><Warning /></el-icon>
              <div class="stat-content">
                <div class="stat-value">{{ slowEndpointsCount }}</div>
                <div class="stat-label">慢端点</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card-unified">
            <div class="stat-card">
              <el-icon :size="32" color="#f56c6c"><Timer /></el-icon>
              <div class="stat-content">
                <div class="stat-value">{{ totalTimeoutCount }}</div>
                <div class="stat-label">超时次数</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- Tabs -->
      <el-tabs v-model="activeTab" class="diagnostic-tabs">
        <!-- 前端统计 -->
        <el-tab-pane label="前端统计" name="frontend">
          <div class="tab-content">
            <div class="actions">
              <el-button size="small" @click="refreshFrontendStats">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
              <el-button size="small" type="danger" @click="clearFrontendStats">
                <el-icon><Delete /></el-icon>
                清除统计
              </el-button>
            </div>
            
            <el-table :data="frontendEndpoints" stripe style="margin-top: 16px;" max-height="400">
              <el-table-column prop="endpoint" label="端点" min-width="200" />
              <el-table-column prop="totalRequests" label="请求数" width="100" />
              <el-table-column label="平均耗时" width="120">
                <template #default="{ row }">
                  <el-tag :type="getTimeTagType(row.averageTime)">
                    {{ row.averageTime }}ms
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="maxTime" label="最大耗时" width="100">
                <template #default="{ row }">{{ row.maxTime }}ms</template>
              </el-table-column>
              <el-table-column label="慢请求率" width="120">
                <template #default="{ row }">
                  <el-tag :type="getSlowRateTagType(row.slowRate)">
                    {{ row.slowRate }}%
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="timeoutCount" label="超时次数" width="100" />
              <el-table-column prop="errorCount" label="错误次数" width="100" />
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 后端统计 -->
        <el-tab-pane label="后端统计" name="backend">
          <div class="tab-content">
            <div class="actions">
              <el-button size="small" @click="refreshBackendStats">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
            
            <el-table :data="backendEndpoints" stripe style="margin-top: 16px;" max-height="400">
              <el-table-column prop="endpoint" label="端点" min-width="200" />
              <el-table-column prop="total_requests" label="请求数" width="100" />
              <el-table-column label="平均耗时" width="120">
                <template #default="{ row }">
                  <el-tag :type="getTimeTagType(row.average_time * 1000)">
                    {{ (row.average_time * 1000).toFixed(0) }}ms
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="最大耗时" width="100">
                <template #default="{ row }">{{ (row.max_time * 1000).toFixed(0) }}ms</template>
              </el-table-column>
              <el-table-column label="慢请求率" width="120">
                <template #default="{ row }">
                  <el-tag :type="getSlowRateTagType(row.slow_rate)">
                    {{ row.slow_rate }}%
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="slow_requests" label="慢请求" width="100" />
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 慢端点分析 -->
        <el-tab-pane label="慢端点分析" name="slow">
          <div class="tab-content">
            <el-alert
              type="warning"
              :closable="false"
              style="margin-bottom: 20px;"
            >
              <template #title>
                <div style="display: flex; align-items: center; gap: 8px;">
                  <el-icon><Warning /></el-icon>
                  <span style="font-weight: 600;">慢端点识别标准</span>
                </div>
              </template>
              慢请求率 ≥ 10% 或 平均耗时 > 5秒的端点将被标记为慢端点
            </el-alert>

            <div class="slow-endpoints-grid">
              <el-card v-for="endpoint in slowEndpoints" :key="endpoint.endpoint" shadow="never" class="list-item-card">
                <template #header>
                  <div class="endpoint-header">
                    <span class="endpoint-path">{{ endpoint.endpoint }}</span>
                    <el-tag type="danger">{{ endpoint.slowRate }}%</el-tag>
                  </div>
                </template>
                <div class="endpoint-stats">
                  <div class="stat-item">
                    <span class="label">平均耗时:</span>
                    <span class="value">{{ endpoint.averageTime }}ms</span>
                  </div>
                  <div class="stat-item">
                    <span class="label">慢请求:</span>
                    <span class="value">{{ endpoint.slowRequests }}/{{ endpoint.totalRequests }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="label">超时次数:</span>
                    <span class="value">{{ endpoint.timeoutCount }}</span>
                  </div>
                </div>
              </el-card>
            </div>

            <el-empty v-if="slowEndpoints.length === 0" description="暂无慢端点" />
          </div>
        </el-tab-pane>

        <!-- 活跃请求 -->
        <el-tab-pane label="活跃请求" name="active">
          <div class="tab-content">
            <el-table :data="activeRequests" stripe max-height="400">
              <el-table-column prop="requestId" label="请求ID" width="180" />
              <el-table-column prop="method" label="方法" width="80" />
              <el-table-column prop="url" label="URL" min-width="200" />
              <el-table-column label="持续时间" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.isTimeout ? 'danger' : row.isSlow ? 'warning' : 'info'">
                    {{ row.duration }}ms
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag v-if="row.isTimeout" type="danger">超时</el-tag>
                  <el-tag v-else-if="row.isSlow" type="warning">慢请求</el-tag>
                  <el-tag v-else type="info">正常</el-tag>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-if="activeRequests.length === 0" description="暂无活跃请求" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" @click="exportReport">
        <el-icon><Download /></el-icon>
        导出报告
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { 
  Document, Loading, Warning, Timer, Refresh, Delete, Download 
} from '@element-plus/icons-vue'
import { requestMonitor } from '@/api/request-monitor'
import { request } from '@/api/index'

const visible = defineModel('visible', { type: Boolean, default: false })

const activeTab = ref('frontend')

// 前端统计数据
const frontendStats = ref({
  endpoints: {},
  activeRequests: 0,
  totalRequests: 0
})

// 后端统计数据
const backendStats = ref({
  endpoints: {},
  active_requests: 0
})

// 计算属性
const frontendEndpoints = computed(() => {
  return Object.entries(frontendStats.value.endpoints).map(([endpoint, stats]) => ({
    endpoint,
    ...stats
  }))
})

const backendEndpoints = computed(() => {
  return Object.entries(backendStats.value.endpoints).map(([endpoint, stats]) => ({
    endpoint,
    ...stats
  }))
})

const slowEndpoints = computed(() => {
  return requestMonitor.getSlowEndpoints(10)
})

const slowEndpointsCount = computed(() => slowEndpoints.value.length)

const totalTimeoutCount = computed(() => {
  return frontendEndpoints.value.reduce((sum, ep) => sum + (ep.timeoutCount || 0), 0)
})

const activeRequests = computed(() => {
  return requestMonitor.getActiveRequests()
})

// 刷新前端统计
const refreshFrontendStats = () => {
  frontendStats.value = requestMonitor.getStats()
  ElMessage.success('前端统计已刷新')
}

// 清除前端统计
const clearFrontendStats = () => {
  requestMonitor.clearStats()
  frontendStats.value = requestMonitor.getStats()
}

// 刷新后端统计
const refreshBackendStats = async () => {
  try {
    const stats = await request.get('/diagnostics/request-stats')
    backendStats.value = stats
    ElMessage.success('后端统计已刷新')
  } catch (error) {
    console.error('获取后端统计失败:', error)
    ElMessage.error('获取后端统计失败')
  }
}

// 标签类型判断
const getTimeTagType = (time) => {
  if (time > 30000) return 'danger'
  if (time > 5000) return 'warning'
  return 'success'
}

const getSlowRateTagType = (rate) => {
  const numRate = parseFloat(rate)
  if (numRate >= 50) return 'danger'
  if (numRate >= 20) return 'warning'
  if (numRate >= 10) return 'info'
  return 'success'
}

// 导出报告
const exportReport = () => {
  const report = {
    timestamp: new Date().toISOString(),
    frontend: frontendStats.value,
    backend: backendStats.value,
    slowEndpoints: slowEndpoints.value,
    activeRequests: activeRequests.value
  }
  
  const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `diagnostic-report-${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
  
  ElMessage.success('报告已导出')
}

// 监听对话框打开，自动刷新数据
watch(visible, (newVal) => {
  if (newVal) {
    refreshFrontendStats()
    refreshBackendStats()
  }
})
</script>

<style scoped>
/* 对话框样式 */
:deep(.diagnostic-dialog) {
  border-radius: var(--radius-large, 12px);
}

:deep(.el-dialog__header) {
  padding: var(--space-lg, 24px);
  border-bottom: 1px solid var(--border-light, #e5e7eb);
  background: var(--bg-secondary, #f9fafb);
}

:deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary, #1f2937);
}

:deep(.el-dialog__body) {
  padding: var(--space-lg, 24px);
  background: var(--bg-primary, #ffffff);
}

:deep(.el-dialog__footer) {
  padding: var(--space-lg, 24px);
  border-top: 1px solid var(--border-light, #e5e7eb);
  background: var(--bg-secondary, #f9fafb);
}

.diagnostic-panel {
  padding: 0;
}

/* 摘要卡片 */
.summary-cards {
  margin-bottom: var(--space-xl, 32px);
}

.summary-cards :deep(.el-card) {
  border-radius: var(--radius-base, 8px);
  border: 1px solid var(--border-light, #e5e7eb);
  transition: all 0.3s ease;
}

.summary-cards :deep(.el-card:hover) {
  transform: translateY(-2px);
  box-shadow: var(--shadow-base, 0 4px 12px rgba(0, 0, 0, 0.1));
}

.summary-cards :deep(.el-card__body) {
  padding: var(--space-lg, 20px);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-md, 16px);
}

.stat-card .el-icon {
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary, #1f2937);
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary, #6b7280);
  margin-top: 4px;
  font-weight: 500;
}

/* Tabs样式 */
.diagnostic-tabs {
  margin-top: 0;
}

.diagnostic-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--space-lg, 24px);
  border-bottom: 2px solid var(--border-light, #e5e7eb);
}

.diagnostic-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 500;
  padding: 0 var(--space-lg, 20px);
  height: 48px;
  line-height: 48px;
}

.diagnostic-tabs :deep(.el-tabs__item.is-active) {
  color: var(--primary-color, #409eff);
  font-weight: 600;
}

.diagnostic-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  background: var(--primary-color, #409eff);
}

.tab-content {
  padding: var(--space-md, 16px) 0;
  min-height: 400px;
}

/* 操作按钮 */
.actions {
  display: flex;
  gap: var(--space-sm, 12px);
  justify-content: flex-end;
  margin-bottom: var(--space-md, 16px);
}

/* 表格样式优化 */
.tab-content :deep(.el-table) {
  border-radius: var(--radius-base, 8px);
  overflow: hidden;
  border: 1px solid var(--border-light, #e5e7eb);
}

.tab-content :deep(.el-table th) {
  background: var(--bg-secondary, #f9fafb);
  color: var(--text-primary, #1f2937);
  font-weight: 600;
}

.tab-content :deep(.el-table__body tr:hover > td) {
  background: var(--info-bg) !important;
}

/* 慢端点网格 */
.slow-endpoints-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-lg, 20px);
}

.slow-endpoints-grid :deep(.el-card) {
  border-radius: var(--radius-base, 8px);
  border: 1px solid var(--border-light, #e5e7eb);
  transition: all 0.3s ease;
}

.slow-endpoints-grid :deep(.el-card:hover) {
  transform: translateY(-4px);
  box-shadow: var(--shadow-base, 0 8px 16px rgba(0, 0, 0, 0.1));
  border-color: var(--primary-color, #409eff);
}

.slow-endpoints-grid :deep(.el-card__header) {
  padding: var(--space-md, 16px);
  background: var(--bg-secondary, #f9fafb);
  border-bottom: 1px solid var(--border-light, #e5e7eb);
}

.slow-endpoints-grid :deep(.el-card__body) {
  padding: var(--space-md, 16px);
}

.endpoint-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm, 12px);
}

.endpoint-path {
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
  color: var(--text-primary, #1f2937);
  flex: 1;
}

.endpoint-stats {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm, 10px);
}

.stat-item {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  padding: var(--space-xs, 8px) 0;
  border-bottom: 1px solid var(--border-lighter, #f3f4f6);
}

.stat-item:last-child {
  border-bottom: none;
}

.stat-item .label {
  color: var(--text-secondary, #6b7280);
  font-weight: 500;
}

.stat-item .value {
  font-weight: 600;
  color: var(--text-primary, #1f2937);
}

/* Alert样式 */
.tab-content :deep(.el-alert) {
  border-radius: var(--radius-base, 8px);
  border: none;
  background: var(--warning-bg);
  color: var(--warning-color);
}

.tab-content :deep(.el-alert__title) {
  font-weight: 600;
}

/* Empty状态 */
.tab-content :deep(.el-empty) {
  padding: var(--space-xl, 60px) 0;
}

.tab-content :deep(.el-empty__description) {
  color: var(--text-secondary, #6b7280);
  font-size: 15px;
}

/* Tag样式优化 */
:deep(.el-tag) {
  border-radius: var(--radius-sm, 6px);
  font-weight: 500;
  padding: 4px 12px;
}

/* 响应式 */
@media (max-width: 768px) {
  .summary-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .slow-endpoints-grid {
    grid-template-columns: 1fr;
  }
  
  .stat-value {
    font-size: 24px;
  }
}
</style>

