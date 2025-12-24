<template>
  <div class="diagnostic-page">
    <!-- 导航栏 -->
    <TopNavigation />
    
    <!-- 页面头部 -->
    <PageHeader title="性能诊断" subtitle="实时监控前后端性能指标，快速定位性能瓶颈">
      <template #actions>
        <el-button @click="exportReport" type="primary" :icon="Download">
          导出报告
        </el-button>
        <el-button @click="refreshAll" :icon="Refresh" circle />
      </template>
    </PageHeader>
    
    <!-- 主内容区 -->
    <div class="page-container">
      <!-- 摘要信息卡片 -->
      <div class="summary-section">
        <el-row :gutter="24">
          <el-col :span="6">
            <el-card shadow="never" class="stat-card-unified">
              <div class="stat-card-content">
                <el-icon :size="32" :style="{color: 'var(--primary-color)'}"><Document /></el-icon>
                <div class="stat-info">
                  <div class="stat-value">{{ frontendStats.totalRequests }}</div>
                  <div class="stat-label">总请求数</div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card-unified">
              <div class="stat-card-content">
                <el-icon :size="32" :style="{color: 'var(--success-color)'}"><Loading /></el-icon>
                <div class="stat-info">
                  <div class="stat-value">{{ frontendStats.activeRequests }}</div>
                  <div class="stat-label">活跃请求</div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card-unified">
              <div class="stat-card-content">
                <el-icon :size="32" :style="{color: 'var(--warning-color)'}"><Warning /></el-icon>
                <div class="stat-info">
                  <div class="stat-value">{{ slowEndpointsCount }}</div>
                  <div class="stat-label">慢端点</div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card-unified">
              <div class="stat-card-content">
                <el-icon :size="32" :style="{color: 'var(--error-color)'}"><Timer /></el-icon>
                <div class="stat-info">
                  <div class="stat-value">{{ totalTimeoutCount }}</div>
                  <div class="stat-label">超时次数</div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 详细统计卡片 -->
      <el-card class="unified-card stats-card" shadow="never">
        <el-tabs v-model="activeTab" class="diagnostic-tabs">
          <!-- 前端统计 -->
          <el-tab-pane label="前端统计" name="frontend">
            <div class="tab-content">
              <div class="actions-bar">
                <el-button size="small" type="danger" @click="clearFrontendStats" :icon="Delete">
                  清除统计
                </el-button>
              </div>
              
              <el-table :data="frontendEndpoints" stripe class="unified-table" max-height="500">
                <el-table-column prop="endpoint" label="端点" min-width="200" />
                <el-table-column prop="totalRequests" label="请求数" width="100" />
                <el-table-column label="平均耗时" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getTimeTagType(row.averageTime)" size="small">
                      {{ row.averageTime }}ms
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="maxTime" label="最大耗时" width="100">
                  <template #default="{ row }">{{ row.maxTime }}ms</template>
                </el-table-column>
                <el-table-column label="慢请求率" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getSlowRateTagType(row.slowRate)" size="small">
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
              
              <el-table :data="backendEndpoints" stripe class="unified-table" max-height="500">
                <el-table-column prop="endpoint" label="端点" min-width="200" />
                <el-table-column prop="total_requests" label="请求数" width="100" />
                <el-table-column label="平均耗时" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getTimeTagType(row.average_time * 1000)" size="small">
                      {{ (row.average_time * 1000).toFixed(0) }}ms
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="最大耗时" width="100">
                  <template #default="{ row }">{{ (row.max_time * 1000).toFixed(0) }}ms</template>
                </el-table-column>
                <el-table-column label="慢请求率" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getSlowRateTagType(row.slow_rate)" size="small">
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
                class="warning-alert"
              >
                <template #title>
                  <div class="alert-title">
                    <el-icon><Warning /></el-icon>
                    <span>慢端点识别标准</span>
                  </div>
                </template>
                慢请求率 ≥ 10% 或 平均耗时 > 5秒的端点将被标记为慢端点
              </el-alert>

              <div class="slow-endpoints-grid" v-if="slowEndpoints.length > 0">
                <el-card 
                  v-for="endpoint in slowEndpoints" 
                  :key="endpoint.endpoint" 
                  shadow="never" 
                  class="list-item-card"
                >
                  <template #header>
                    <div class="endpoint-header">
                      <span class="endpoint-path">{{ endpoint.endpoint }}</span>
                      <el-tag type="danger" size="small">{{ endpoint.slowRate }}%</el-tag>
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

              <el-empty v-else description="暂无慢端点" :image-size="100">
                <template #image>
                  <el-icon :size="80" :style="{color: 'var(--text-tertiary)'}"><Select /></el-icon>
                </template>
              </el-empty>
            </div>
          </el-tab-pane>

          <!-- 活跃请求 -->
          <el-tab-pane label="活跃请求" name="active">
            <div class="tab-content">
              <el-table 
                v-if="activeRequests.length > 0"
                :data="activeRequests" 
                stripe 
                class="unified-table" 
                max-height="500"
              >
                <el-table-column prop="requestId" label="请求ID" width="180" />
                <el-table-column prop="method" label="方法" width="80" />
                <el-table-column prop="url" label="URL" min-width="200" />
                <el-table-column label="持续时间" width="120">
                  <template #default="{ row }">
                    <el-tag 
                      :type="row.isTimeout ? 'danger' : row.isSlow ? 'warning' : 'info'"
                      size="small"
                    >
                      {{ row.duration }}ms
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag v-if="row.isTimeout" type="danger" size="small">超时</el-tag>
                    <el-tag v-else-if="row.isSlow" type="warning" size="small">慢请求</el-tag>
                    <el-tag v-else type="info" size="small">正常</el-tag>
                  </template>
                </el-table-column>
              </el-table>

              <el-empty v-else description="暂无活跃请求" :image-size="100">
                <template #image>
                  <el-icon :size="80" :style="{color: 'var(--text-tertiary)'}"><Connection /></el-icon>
                </template>
              </el-empty>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { 
  Document, Loading, Warning, Timer, Refresh, Delete, Download,
  DataAnalysis, Select, Connection
} from '@element-plus/icons-vue'
import TopNavigation from '@/components/TopNavigation.vue'
import PageHeader from '@/components/PageHeader.vue'
import { requestMonitor } from '@/api/request-monitor'
import { request } from '@/api/index'

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
  ElMessage.success('统计数据已清除')
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

// 全局刷新
const refreshAll = () => {
  refreshFrontendStats()
  refreshBackendStats()
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

// 页面加载时刷新数据
onMounted(() => {
  refreshFrontendStats()
  refreshBackendStats()
})
</script>

<style scoped>
.diagnostic-page {
  min-height: 100vh;
  background: var(--bg-tertiary);
}

/* 页面头部 - 简洁版（符合设计规范）*/
.page-header {
  background: var(--bg-primary);
  border-bottom: 2px solid var(--border-light);
  padding: var(--space-xl) 0;
  margin-bottom: var(--space-xl);
}

.page-header-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--space-lg);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.header-text {
  /* 移除白色文字，使用标准文本色 */
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 var(--space-xs) 0;
  color: var(--text-primary);
}

.page-subtitle {
  font-size: 14px;
  margin: 0;
  color: var(--text-secondary);
}

.header-actions {
  display: flex;
  gap: var(--space-sm);
}

/* 主容器 */
.page-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--space-lg) var(--space-xl);
}

/* 摘要卡片区域 */
.summary-section {
  margin-bottom: var(--space-xl);
}

.stat-card-unified {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  transition: all 0.3s ease;
}

.stat-card-unified:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-base);
  border-color: var(--primary-light);
}

.stat-card-unified :deep(.el-card__body) {
  padding: var(--space-lg);
}

.stat-card-content {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
  margin-bottom: var(--space-xs);
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

/* 统一卡片样式 */
.unified-card {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-large);
  box-shadow: var(--shadow-light);
  transition: all 0.3s ease;
}

.unified-card :deep(.el-card__body) {
  padding: 0;
}

/* Tabs样式 */
.diagnostic-tabs {
  margin-top: 0;
}

.diagnostic-tabs :deep(.el-tabs__header) {
  padding: 0 var(--space-lg);
  margin-bottom: 0;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-secondary);
}

.diagnostic-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.diagnostic-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 500;
  padding: 0 var(--space-lg);
  height: 56px;
  line-height: 56px;
  color: var(--text-secondary);
}

.diagnostic-tabs :deep(.el-tabs__item:hover) {
  color: var(--primary-color);
}

.diagnostic-tabs :deep(.el-tabs__item.is-active) {
  color: var(--primary-color);
  font-weight: 600;
}

.diagnostic-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  background: var(--primary-color);
}

.tab-content {
  padding: var(--space-lg);
  min-height: 400px;
}

/* 操作栏 */
.actions-bar {
  display: flex;
  gap: var(--space-sm);
  justify-content: flex-end;
  margin-bottom: var(--space-md);
}

/* 表格样式 */
.unified-table {
  border-radius: var(--radius-base);
  overflow: hidden;
  border: 1px solid var(--border-light);
}

.unified-table :deep(.el-table__header) {
  background: var(--bg-secondary);
}

.unified-table :deep(.el-table th) {
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-weight: 600;
}

.unified-table :deep(.el-table__body tr:hover > td) {
  background: var(--info-bg) !important;
}

/* Alert样式 */
.warning-alert {
  margin-bottom: var(--space-lg);
  border-radius: var(--radius-base);
  border: none;
  background: var(--warning-bg);
  color: var(--warning-color);
}

.warning-alert :deep(.el-alert__title) {
  font-weight: 600;
}

.alert-title {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

/* 慢端点网格 */
.slow-endpoints-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-lg);
}

.list-item-card {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  transition: all 0.3s ease;
}

.list-item-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-base);
  border-color: var(--primary-light);
}

.list-item-card :deep(.el-card__header) {
  padding: var(--space-md);
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-light);
}

.list-item-card :deep(.el-card__body) {
  padding: var(--space-md);
}

.endpoint-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm);
}

.endpoint-path {
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
  color: var(--text-primary);
  flex: 1;
}

.endpoint-stats {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.stat-item {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  padding: var(--space-xs) 0;
  border-bottom: 1px solid var(--border-light);
}

.stat-item:last-child {
  border-bottom: none;
}

.stat-item .label {
  color: var(--text-secondary);
  font-weight: 500;
}

.stat-item .value {
  font-weight: 600;
  color: var(--text-primary);
}

/* Empty状态 */
.tab-content :deep(.el-empty) {
  padding: var(--space-xl) 0;
}

.tab-content :deep(.el-empty__description) {
  color: var(--text-secondary);
  font-size: 15px;
}

/* 响应式 */
@media (max-width: 1280px) {
  .summary-section :deep(.el-col) {
    margin-bottom: var(--space-md);
  }
}

@media (max-width: 768px) {
  .page-header-content {
    flex-direction: column;
    gap: var(--space-md);
    align-items: flex-start;
  }
  
  .header-left {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .slow-endpoints-grid {
    grid-template-columns: 1fr;
  }
  
  .stat-value {
    font-size: 24px;
  }
}
</style>
