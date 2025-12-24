<template>
  <el-dialog
    v-model="visible"
    title="提示词卡片管理"
    width="90%"
    :fullscreen="isMobile"
    @close="handleClose"
  >
    <!-- 头部操作栏 -->
    <div class="cards-header">
      <div class="header-left">
        <el-segmented v-model="activeCategory" :options="categoryOptions" />
      </div>
      <el-button type="primary" @click="showCreateDialog" :icon="Plus">
        新建提示词卡片
      </el-button>
    </div>

    <!-- 提示词卡片列表 -->
    <div class="cards-list" v-loading="loading">
      <el-empty v-if="filteredCards.length === 0" description="暂无提示词卡片" />
      
      <div v-else class="cards-grid">
        <el-card
          v-for="card in filteredCards"
          :key="card.id"
          class="card-item list-item-card"
          :class="{ 'system-card': card.is_system }"
          shadow="never"
        >
          <template #header>
            <div class="card-header">
              <div class="card-title-row">
                <span class="card-title">{{ card.title }}</span>
                <el-tag v-if="card.is_system" type="info" size="small">系统</el-tag>
                <el-tag v-else type="success" size="small">自定义</el-tag>
              </div>
              <el-tag type="warning" size="small">{{ card.category }}</el-tag>
            </div>
          </template>

          <div class="card-content">
            <p class="card-description">{{ card.description }}</p>
            <div class="card-prompt">
              <el-icon><ChatLineSquare /></el-icon>
              <p>{{ card.content }}</p>
            </div>
          </div>

          <template #footer>
            <div class="card-actions">
              <el-button
                type="primary"
                size="small"
                @click="useCard(card)"
                :icon="Select"
              >
                使用
              </el-button>
              <el-button
                size="small"
                @click="editCard(card)"
                :icon="Edit"
              >
                编辑
              </el-button>
              <el-button
                v-if="!card.is_system"
                type="danger"
                size="small"
                @click="deleteCard(card)"
                :icon="Delete"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-card>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑提示词卡片' : '新建提示词卡片'"
      width="600px"
      append-to-body
      @close="resetForm"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题" required>
          <el-input
            v-model="form.title"
            placeholder="输入提示词卡片标题"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="描述" required>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="简短描述这个提示词卡片的用途"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="分类" required>
          <el-select
            v-model="form.category"
            placeholder="选择分类"
            allow-create
            filterable
            style="width: 100%"
          >
            <el-option label="操作优化" value="操作优化" />
            <el-option label="速度控制" value="速度控制" />
            <el-option label="应用适配" value="应用适配" />
            <el-option label="安全提示" value="安全提示" />
            <el-option label="任务规划" value="任务规划" />
            <el-option label="输入优化" value="输入优化" />
            <el-option label="适应性" value="适应性" />
            <el-option label="通用" value="通用" />
          </el-select>
        </el-form-item>

        <el-form-item label="提示词内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="输入完整的提示词内容，这会被拼接到系统提示词中"
            maxlength="2000"
            show-word-limit
          />
          <div class="help-text">
            💡 提示词示例：请特别注意：1) 仔细识别界面元素；2) 确认点击位置；3) 避免误操作
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="saveCard"
          :loading="saving"
          :disabled="!isFormValid"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import {
  Plus, Edit, Delete, ChatLineSquare, Select
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/api/index'

const props = defineProps({
  modelValue: {
    type: Boolean,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'use-card'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isMobile = ref(window.innerWidth <= 768)

// 状态
const loading = ref(false)
const saving = ref(false)
const cards = ref([])
const activeCategory = ref('全部')
const dialogVisible = ref(false)
const isEditing = ref(false)
const form = ref({
  id: null,
  title: '',
  description: '',
  content: '',
  category: '通用'
})

// 分类选项
const categoryOptions = computed(() => {
  const categories = new Set(['全部'])
  cards.value.forEach(card => categories.add(card.category))
  return Array.from(categories)
})

// 过滤的卡片
const filteredCards = computed(() => {
  if (activeCategory.value === '全部') {
    return cards.value
  }
  return cards.value.filter(card => card.category === activeCategory.value)
})

// 表单验证
const isFormValid = computed(() => {
  return form.value.title.trim() !== '' &&
    form.value.description.trim() !== '' &&
    form.value.content.trim() !== '' &&
    form.value.category.trim() !== ''
})

// 加载提示词卡片
async function loadCards() {
  loading.value = true
  try {
    const response = await request.get('/prompt-cards')
    cards.value = response.cards || []
  } catch (error) {
    console.error('Failed to load prompt cards:', error)
    ElMessage.error('加载提示词卡片失败')
  } finally {
    loading.value = false
  }
}

// 显示创建对话框
function showCreateDialog() {
  isEditing.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑卡片
function editCard(card) {
  isEditing.value = true
  form.value = {
    id: card.id,
    title: card.title,
    description: card.description,
    content: card.content,
    category: card.category
  }
  dialogVisible.value = true
}

// 使用卡片
function useCard(card) {
  emit('use-card', card)
  ElMessage.success(`已选择提示词卡片：${card.title}`)
}

// 保存卡片
async function saveCard() {
  if (!isFormValid.value) {
    ElMessage.warning('请填写完整信息')
    return
  }

  saving.value = true
  try {
    if (isEditing.value) {
      await request.put(`/prompt-cards/${form.value.id}`, {
        title: form.value.title,
        description: form.value.description,
        content: form.value.content,
        category: form.value.category
      })
      ElMessage.success('更新成功')
    } else {
      await request.post('/prompt-cards', {
        title: form.value.title,
        description: form.value.description,
        content: form.value.content,
        category: form.value.category
      })
      ElMessage.success('创建成功')
    }

    dialogVisible.value = false
    await loadCards()
  } catch (error) {
    console.error('Failed to save prompt card:', error)
    ElMessage.error(isEditing.value ? '更新失败' : '创建失败')
  } finally {
    saving.value = false
  }
}

// 删除卡片
async function deleteCard(card) {
  try {
    await ElMessageBox.confirm(
      `确定要删除提示词卡片"${card.title}"吗？`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await request.delete(`/prompt-cards/${card.id}`)
    ElMessage.success('删除成功')
    await loadCards()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to delete prompt card:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 重置表单
function resetForm() {
  form.value = {
    id: null,
    title: '',
    description: '',
    content: '',
    category: '通用'
  }
}

// 关闭对话框
function handleClose() {
  activeCategory.value = '全部'
}

// 监听对话框打开，加载数据
watch(visible, (newVal) => {
  if (newVal) {
    loadCards()
  }
})

// 监听窗口大小变化
window.addEventListener('resize', () => {
  isMobile.value = window.innerWidth <= 768
})
</script>

<style scoped>
.cards-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.header-left {
  flex: 1;
}

.cards-list {
  max-height: 600px;
  overflow-y: auto;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.card-item {
  transition: all 0.3s;
}

.card-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.card-item.system-card {
  border-left: 3px solid #909399;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-description {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.card-prompt {
  display: flex;
  gap: 8px;
  padding: var(--space-sm);
  background: var(--bg-tertiary);
  border-radius: var(--radius-small);
  border-left: 3px solid #409eff;
}

.card-prompt .el-icon {
  color: var(--primary-color);
  flex-shrink: 0;
  margin-top: 2px;
}

.card-prompt p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.help-text {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.5;
}

@media (max-width: 768px) {
  .cards-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-left {
    width: 100%;
  }

  .cards-grid {
    grid-template-columns: 1fr;
  }
}
</style>

