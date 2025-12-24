<template>
  <el-dialog
    v-model="visible"
    title="快捷指令管理"
    width="90%"
    :fullscreen="isMobile"
    @close="handleClose"
  >
    <!-- 头部操作栏 -->
    <div class="shortcuts-header">
      <div class="header-left">
        <el-segmented v-model="activeCategory" :options="categoryOptions" />
      </div>
      <el-button type="primary" @click="showCreateDialog" :icon="Plus">
        新建快捷指令
      </el-button>
    </div>

    <!-- 快捷指令列表 -->
    <div class="shortcuts-list" v-loading="loading">
      <el-empty v-if="filteredShortcuts.length === 0" description="暂无快捷指令" />
      
      <div v-else class="shortcuts-grid">
        <el-card
          v-for="shortcut in filteredShortcuts"
          :key="shortcut.id"
          class="shortcut-item list-item-card"
          :class="{ 'system-shortcut': shortcut.is_system }"
          shadow="never"
        >
          <template #header>
            <div class="shortcut-header">
              <div class="shortcut-title-row">
                <span class="shortcut-title">{{ shortcut.title }}</span>
                <el-tag v-if="shortcut.is_system" type="info" size="small">系统</el-tag>
                <el-tag v-else type="success" size="small">自定义</el-tag>
              </div>
              <el-tag type="warning" size="small">{{ shortcut.category }}</el-tag>
            </div>
          </template>

          <div class="shortcut-content">
            <p class="shortcut-description">{{ shortcut.instruction }}</p>
            
            <div class="shortcut-keywords" v-if="shortcut.voice_keywords.length > 0">
              <el-icon><Microphone /></el-icon>
              <div class="keywords-wrapper">
                <el-tag
                  v-for="(keyword, index) in shortcut.voice_keywords"
                  :key="index"
                  size="small"
                  type="info"
                  effect="plain"
                >
                  {{ keyword }}
                </el-tag>
              </div>
            </div>
          </div>

          <template #footer>
            <div class="shortcut-actions">
              <el-button
                type="primary"
                size="small"
                @click="executeShortcut(shortcut)"
                :icon="VideoPlay"
              >
                执行
              </el-button>
              <el-button
                size="small"
                @click="editShortcut(shortcut)"
                :icon="Edit"
              >
                编辑
              </el-button>
              <el-button
                v-if="!shortcut.is_system"
                type="danger"
                size="small"
                @click="deleteShortcut(shortcut)"
                :icon="Delete"
              >
                删除
              </el-button>
              <el-button
                size="small"
                @click="useInHome(shortcut)"
                :icon="CopyDocument"
              >
                使用
              </el-button>
            </div>
          </template>
        </el-card>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      :title="editingShortcut ? '编辑快捷指令' : '新建快捷指令'"
      width="600px"
      append-to-body
    >
      <el-form :model="shortcutForm" label-width="100px" :rules="formRules" ref="formRef">
        <el-form-item label="指令标题" prop="title">
          <el-input
            v-model="shortcutForm.title"
            placeholder="例如：查看微信消息"
            clearable
          />
        </el-form-item>

        <el-form-item label="指令内容" prop="instruction">
          <el-input
            v-model="shortcutForm.instruction"
            type="textarea"
            :rows="3"
            placeholder="例如：打开微信，查看最新的未读消息"
            clearable
          />
        </el-form-item>

        <el-form-item label="分类" prop="category">
          <el-select v-model="shortcutForm.category" placeholder="选择分类" style="width: 100%">
            <el-option label="社交" value="社交" />
            <el-option label="娱乐" value="娱乐" />
            <el-option label="生活" value="生活" />
            <el-option label="支付" value="支付" />
            <el-option label="购物" value="购物" />
            <el-option label="出行" value="出行" />
            <el-option label="工具" value="工具" />
            <el-option label="自定义" value="自定义" />
          </el-select>
        </el-form-item>

        <el-form-item label="语音关键词">
          <el-tag
            v-for="(keyword, index) in shortcutForm.voice_keywords"
            :key="index"
            closable
            @close="removeKeyword(index)"
            style="margin-right: 8px; margin-bottom: 8px;"
          >
            {{ keyword }}
          </el-tag>
          <el-input
            v-if="showKeywordInput"
            ref="keywordInputRef"
            v-model="newKeyword"
            size="small"
            style="width: 120px; margin-right: 8px;"
            @keyup.enter="addKeyword"
            @blur="addKeyword"
          />
          <el-button v-else size="small" @click="showKeywordInput = true" :icon="Plus">
            添加关键词
          </el-button>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveShortcut" :loading="saving">
          {{ editingShortcut ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Edit,
  Delete,
  Document,
  Microphone,
  View,
  VideoPlay,
  CopyDocument
} from '@element-plus/icons-vue'
import { shortcutApi } from '@/api'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'use-shortcut'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isMobile = computed(() => window.innerWidth < 768)

// 数据
const shortcuts = ref([])
const loading = ref(false)
const activeCategory = ref('全部')
const categoryOptions = ref(['全部', '社交', '娱乐', '生活', '支付', '购物', '出行', '工具', '自定义'])

// 编辑对话框
const editDialogVisible = ref(false)
const editingShortcut = ref(null)
const saving = ref(false)
const formRef = ref(null)
const shortcutForm = ref({
  title: '',
  instruction: '',
  category: '自定义',
  voice_keywords: []
})

// 关键词输入
const showKeywordInput = ref(false)
const newKeyword = ref('')
const keywordInputRef = ref(null)

// 表单验证规则
const formRules = {
  title: [
    { required: true, message: '请输入指令标题', trigger: 'blur' },
    { min: 2, max: 20, message: '标题长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  instruction: [
    { required: true, message: '请输入指令内容', trigger: 'blur' },
    { min: 5, max: 200, message: '指令长度在 5 到 200 个字符', trigger: 'blur' }
  ],
  category: [
    { required: true, message: '请选择分类', trigger: 'change' }
  ]
}

// 筛选后的快捷指令
const filteredShortcuts = computed(() => {
  if (activeCategory.value === '全部') {
    return shortcuts.value
  }
  return shortcuts.value.filter(s => s.category === activeCategory.value)
})

// 加载快捷指令
async function loadShortcuts() {
  loading.value = true
  try {
    const response = await shortcutApi.list()
    shortcuts.value = response.shortcuts || []
  } catch (error) {
    console.error('Failed to load shortcuts:', error)
    ElMessage.error('加载快捷指令失败')
  } finally {
    loading.value = false
  }
}

// 显示创建对话框
function showCreateDialog() {
  editingShortcut.value = null
  shortcutForm.value = {
    title: '',
    instruction: '',
    category: '自定义',
    voice_keywords: []
  }
  editDialogVisible.value = true
}

// 编辑快捷指令
function editShortcut(shortcut) {
  editingShortcut.value = shortcut
  shortcutForm.value = {
    title: shortcut.title,
    instruction: shortcut.instruction,
    category: shortcut.category,
    voice_keywords: [...shortcut.voice_keywords]
  }
  editDialogVisible.value = true
}

// 保存快捷指令
async function saveShortcut() {
  try {
    await formRef.value.validate()
    
    saving.value = true
    
    if (editingShortcut.value) {
      // 更新
      await shortcutApi.update(editingShortcut.value.id, shortcutForm.value)
      ElMessage.success('快捷指令已更新')
    } else {
      // 创建
      await shortcutApi.create(shortcutForm.value)
      ElMessage.success('快捷指令已创建')
    }
    
    editDialogVisible.value = false
    await loadShortcuts()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to save shortcut:', error)
      ElMessage.error('保存失败：' + (error.message || '未知错误'))
    }
  } finally {
    saving.value = false
  }
}

// 删除快捷指令
async function deleteShortcut(shortcut) {
  try {
    await ElMessageBox.confirm(
      `确定要删除快捷指令"${shortcut.title}"吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await shortcutApi.delete(shortcut.id)
    ElMessage.success('快捷指令已删除')
    await loadShortcuts()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to delete shortcut:', error)
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }
}

// 执行快捷指令
async function executeShortcut(shortcut) {
  try {
    const result = await shortcutApi.execute(shortcut.id)
    ElMessage.success(`快捷指令"${shortcut.title}"已开始执行`)
    // 可以跳转到任务详情页
  } catch (error) {
    console.error('Failed to execute shortcut:', error)
    ElMessage.error('执行失败：' + (error.message || '未知错误'))
  }
}

// 在首页使用
function useInHome(shortcut) {
  emit('use-shortcut', shortcut)
  visible.value = false
}

// 添加关键词
function addKeyword() {
  if (newKeyword.value && !shortcutForm.value.voice_keywords.includes(newKeyword.value)) {
    shortcutForm.value.voice_keywords.push(newKeyword.value)
  }
  newKeyword.value = ''
  showKeywordInput.value = false
}

// 移除关键词
function removeKeyword(index) {
  shortcutForm.value.voice_keywords.splice(index, 1)
}

// 监听关键词输入框显示
watch(showKeywordInput, async (val) => {
  if (val) {
    await nextTick()
    keywordInputRef.value?.focus()
  }
})

// 监听对话框打开
watch(visible, (val) => {
  if (val) {
    loadShortcuts()
  }
})

// 关闭对话框
function handleClose() {
  visible.value = false
}
</script>

<style scoped>
.shortcuts-header {
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

.shortcuts-list {
  max-height: 600px;
  overflow-y: auto;
}

.shortcuts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.shortcut-item {
  transition: all 0.3s;
}

.shortcut-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.shortcut-item.system-shortcut {
  border-left: 3px solid #909399;
}

.shortcut-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.shortcut-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.shortcut-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.shortcut-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.shortcut-description {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.shortcut-keywords {
  display: flex;
  gap: 8px;
  padding: var(--space-sm);
  background: var(--bg-tertiary);
  border-radius: var(--radius-small);
  border-left: 3px solid #409eff;
}

.shortcut-keywords .el-icon {
  color: var(--primary-color);
  flex-shrink: 0;
  margin-top: 2px;
}

.keywords-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
}

.shortcut-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 768px) {
  .shortcuts-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-left {
    width: 100%;
  }

  .shortcuts-grid {
    grid-template-columns: 1fr;
  }
}
</style>

