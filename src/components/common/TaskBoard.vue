<template>
  <div class="task-board">
    <div class="board-header">
      <div class="header-left">
        <span class="board-title">任务看板</span>
        <el-tag type="info" size="small">总进度: {{ progress }}%</el-tag>
      </div>
      <el-button type="primary" size="small" @click="$emit('generate-tasks')" v-if="canGenerate">
        <el-icon><MagicStick /></el-icon>
        拆解任务
      </el-button>
    </div>
    <div class="board-columns-container">
      <div class="board-column" v-for="column in columns" :key="column.key">
      <div class="column-header" :style="getColumnHeaderStyle(column)">
        <span class="column-title">{{ column.title }}</span>
        <el-badge :value="getTaskCount(column.key)" class="badge" />
      </div>
      <draggable
        :model-value="getTasksByStatus(column.key)"
        :group="{ name: 'task-cards' }"
        :item-key="(t) => t.id"
        :disabled="isStatusTransitionInFlight"
        class="column-content"
        ghost-class="task-card-ghost"
        drag-class="task-card-dragging"
        :data-column="column.key"
        @mouseup="handleMouseDrop(column.key)"
        @change="(evt) => onDragChange(evt, column.key)"
      >
        <template #item="{ element: task }">
          <div
            class="task-card"
            :class="{ 'task-high': task.priority === 'high', 'task-review': column.category === 'REVIEW' }"
            @mousedown="handleMouseDragStart(task, $event)"
            @click="handleTaskClick(task)"
          >
            <div class="task-header">
              <el-tag
                :type="getPriorityType(task.priority)"
                size="small"
                v-if="task.priority"
              >
                {{ getPriorityText(task.priority) }}
              </el-tag>
              <el-tag v-if="task.hasDeliverable" type="success" size="small">有交付物</el-tag>
              <el-dropdown trigger="click" @click.stop>
                <el-icon class="more-icon"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-item
                    v-for="s in availableStatuses"
                    :key="s.code"
                    :disabled="normalizeStatus(task.status) === s.code"
                    @click="handleStatusChange(task, s.code)"
                  >
                    设为{{ s.name }}
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleUploadDeliverable(task)">
                    <el-icon><Upload /></el-icon>
                    上传交付物
                  </el-dropdown-item>
                </template>
              </el-dropdown>
            </div>
            <div class="task-name">{{ task.name }}</div>
            <div class="task-desc">{{ task.description }}</div>
            <div class="task-meta">
              <div class="task-owner">
                <el-icon><User /></el-icon>
                <span>{{ task.owner }}</span>
              </div>
              <div class="task-deadline" :class="{ 'deadline-urgent': isUrgent(task.deadline) }">
                <el-icon><Calendar /></el-icon>
                <span>{{ task.deadline }}</span>
              </div>
            </div>
            <div v-if="task.deliverables && task.deliverables.length > 0" class="deliverables">
              <div class="deliverable-title">交付物:</div>
              <div v-for="del in task.deliverables" :key="del.id" class="deliverable-item">
                <el-tag size="small" closable @close="handleRemoveDeliverable(task, del)">
                  <el-link :href="del.url" target="_blank" type="primary">
                    <el-icon><Document /></el-icon>
                    {{ del.name }}
                  </el-link>
                </el-tag>
              </div>
            </div>
          </div>
        </template>
        <template #footer>
          <el-empty v-if="getTasksByStatus(column.key).length === 0" description="暂无任务" :image-size="60" />
        </template>
      </draggable>
    </div>
    </div>

    <div class="submit-section" v-if="canSubmitToDocument && props.showSubmitButton">
      <el-button type="success" size="large" @click="handleSubmitToDocument" :disabled="!allTasksCompleted">
        <el-icon><DocumentAdd /></el-icon>
        提交至标书编写流程
      </el-button>
      <div class="submit-tip">所有任务已完成并审核通过，可开始标书编写</div>
    </div>

    <el-dialog v-model="showUploadDialog" title="上传交付物" width="500px">
      <el-form :model="deliverableForm" label-width="80px">
        <el-form-item label="交付物名称">
          <el-input v-model="deliverableForm.name" placeholder="请输入交付物名称" />
        </el-form-item>
        <el-form-item label="交付物类型">
          <el-select v-model="deliverableForm.type" placeholder="请选择类型">
            <el-option label="文档" value="document" />
            <el-option label="资质文件" value="qualification" />
            <el-option label="技术方案" value="technical" />
            <el-option label="报价单" value="quotation" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload
            class="upload-demo"
            drag
            action="#"
            :auto-upload="false"
            :on-change="handleFileChange"
            :file-list="fileList"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 doc/docx/pdf/xls/xlsx 格式</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveDeliverable" :disabled="!deliverableForm.name || !deliverableForm.file">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { MoreFilled, User, Calendar, Document, MagicStick, Upload, DocumentAdd, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import draggable from 'vuedraggable'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { useTaskBoardDrag } from './useTaskBoardDrag'

const props = defineProps({
  tasks: {
    type: Array,
    default: () => []
  },
  projectId: String,
  canGenerate: {
    type: Boolean,
    default: true
  },
  showSubmitButton: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['task-click', 'status-change', 'generate-tasks', 'submit-to-document', 'add-deliverable', 'remove-deliverable'])

const projectStore = useProjectStore()
const userStore = useUserStore()

const showUploadDialog = ref(false)
const currentTask = ref(null)
const fileList = ref([])

const deliverableForm = ref({
  name: '',
  type: 'document',
  file: null
})

const statuses = computed(() => projectStore.taskStatuses)

const availableStatuses = computed(() => statuses.value.filter((s) => s.code !== 'IN_PROGRESS'))

onMounted(() => {
  if (!projectStore.taskStatusesLoaded) {
    projectStore.loadTaskStatuses()
  }
})

const normalizeStatus = (status) => String(status || '').toUpperCase()

const columns = computed(() => availableStatuses.value
  .map((s) => ({
    key: s.code,
    title: s.name,
    color: s.color,
    category: s.category,
    terminal: s.terminal
  })))

const terminalCodes = computed(() => new Set(
  statuses.value.filter((s) => s.terminal).map((s) => s.code)
))

const progress = computed(() => {
  if (props.tasks.length === 0) return 0
  const doneCount = props.tasks.filter((t) => terminalCodes.value.has(normalizeStatus(t.status))).length
  return Math.round((doneCount / props.tasks.length) * 100)
})

const allTasksCompleted = computed(() => {
  if (props.tasks.length === 0) return false
  return props.tasks.every((t) => terminalCodes.value.has(normalizeStatus(t.status)))
})

const canSubmitToDocument = computed(() => allTasksCompleted.value)

// [DIAGNOSTIC]
watch(() => props.tasks?.length, (len) => {
  console.log('[TaskBoard] props.tasks.length:', len, 'statuses:', props.tasks?.map(t => `${t.id}:${t.status}`))
}, { immediate: true })
watch(columns, (cols) => {
  console.log('[TaskBoard] columns:', cols?.map(c => c.key))
}, { immediate: true })

const getTasksByStatus = (code) => {
  return props.tasks.filter((t) => normalizeStatus(t.status) === code)
}

const getTaskCount = (code) => {
  return getTasksByStatus(code).length
}

const hexToSoftBackground = (hex) => {
  if (typeof hex !== 'string' || !/^#([\da-f]{3}|[\da-f]{6})$/i.test(hex)) {
    return 'var(--bg-subtle)'
  }
  let normalized = hex.replace('#', '')
  if (normalized.length === 3) {
    normalized = normalized.split('').map((c) => c + c).join('')
  }
  const r = parseInt(normalized.slice(0, 2), 16)
  const g = parseInt(normalized.slice(2, 4), 16)
  const b = parseInt(normalized.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, 0.12)`
}

const getColumnHeaderStyle = (column) => {
  const color = column?.color || 'var(--text-muted)'
  return {
    color,
    background: hexToSoftBackground(color)
  }
}

const getPriorityType = (priority) => {
  const typeMap = {
    high: 'danger',
    medium: 'warning',
    low: 'info'
  }
  return typeMap[priority] || 'info'
}

const getPriorityText = (priority) => {
  const textMap = {
    high: '高',
    medium: '中',
    low: '低'
  }
  return textMap[priority] || priority
}

const isUrgent = (deadline) => {
  if (!deadline) return false
  const deadlineDate = new Date(deadline)
  const today = new Date()
  const diffDays = Math.ceil((deadlineDate - today) / (1000 * 60 * 60 * 24))
  return diffDays <= 3
}

const handleTaskClick = (task) => {
  emit('task-click', task)
}

const handleStatusChange = (task, newStatus) => {
  emit('status-change', task, newStatus)
}

const {
  isStatusTransitionInFlight, onDragChange, handleMouseDragStart, handleMouseDrop,
} = useTaskBoardDrag({
  normalizeStatus,
  emitStatusChange: handleStatusChange,
})

const handleUploadDeliverable = (task) => {
  currentTask.value = task
  showUploadDialog.value = true
  deliverableForm.value = {
    name: '',
    type: 'document',
    file: null
  }
  fileList.value = []
}

const handleFileChange = (file) => {
  deliverableForm.value.file = file.raw
}

const handleSaveDeliverable = async () => {
  if (!currentTask.value || !deliverableForm.value.name) {
    ElMessage.warning('请填写交付物名称')
    return
  }

  try {
    const typeMap = {
      document: 'DOCUMENT',
      qualification: 'QUALIFICATION',
      technical: 'TECHNICAL',
      quotation: 'QUOTATION',
      other: 'OTHER'
    }

    const savedDeliverable = await projectStore.addDeliverable(props.projectId, currentTask.value.id, {
      name: deliverableForm.value.name,
      deliverableType: typeMap[deliverableForm.value.type] || 'DOCUMENT',
      size: deliverableForm.value.file ? `${(deliverableForm.value.file.size / 1024).toFixed(1)}KB` : null,
      fileType: deliverableForm.value.file?.type || null,
      file: deliverableForm.value.file,
      uploaderId: userStore.currentUser?.id ?? null,
      uploaderName: userStore.userName,
    })

    emit('add-deliverable', currentTask.value.id, savedDeliverable)

    showUploadDialog.value = false
    deliverableForm.value = { name: '', type: 'document', file: null }
    fileList.value = []

    ElMessage.success('交付物已保存')
  } catch (error) {
    ElMessage.error(error?.message || '交付物上传失败')
  }
}

const handleRemoveDeliverable = async (task, deliverable) => {
  try {
    await projectStore.removeDeliverable(props.projectId, task.id, deliverable.id)
    emit('remove-deliverable', task.id, deliverable.id)
    ElMessage.success('交付物已删除')
  } catch (error) {
    ElMessage.error(error?.message || '交付物删除失败')
  }
}

const handleSubmitToDocument = async () => {
  try {
    const result = await projectStore.submitToBidDocument(props.projectId)
    if (result?.data?.accepted) {
      ElMessage.success(result.data.msg || '已提交至标书编写流程')
      emit('submit-to-document', props.projectId)
    } else {
      ElMessage.warning(result?.data?.msg || '提交校验未通过')
    }
  } catch (error) {
    ElMessage.error(error?.message || '提交失败')
  }
}
</script>

<style scoped>
.task-board {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.board-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.board-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
}

.board-columns-container {
  display: flex;
  gap: 16px;
  min-height: 500px;
}

.board-column {
  background: var(--bg-subtle);
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-width: 260px;
  flex: 1;
}

.column-header {
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
}

.column-title {
  font-size: 14px;
}

.column-content {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  max-height: 500px;
}

.task-card {
  background: white;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.task-card:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

.task-card-ghost {
  opacity: 0.5;
  background: #eef5ff !important;
  border: 1px dashed #409eff !important;
}

.task-card-dragging {
  opacity: 0.9;
  transform: rotate(2deg);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.2) !important;
  cursor: grabbing;
}

.task-high {
  border-left: 3px solid #f56c6c;
}

.task-review {
  border-left: 3px solid #e6a23c;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  gap: 8px;
}

.more-icon {
  color: var(--text-muted);
  cursor: pointer;
  font-size: 16px;
}

.more-icon:hover {
  color: #409eff;
}

.task-name {
  font-size: 14px;
  color: var(--gray-750);
  margin-bottom: 8px;
  font-weight: 500;
  line-height: 1.4;
}

.task-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 12px;
  line-height: 1.4;
}

.task-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
}

.task-owner,
.task-deadline {
  display: flex;
  align-items: center;
  gap: 4px;
}

.task-deadline.deadline-urgent {
  color: #f56c6c;
}

.deliverables {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #dcdfe6;
}

.deliverable-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.deliverable-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0 8px 8px 0;
}

.submit-section {
  margin-top: 16px;
  padding: 16px;
  background: #f0f9ff;
  border: 1px solid #b3e8ff;
  border-radius: 8px;
  text-align: center;
}

.submit-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #409eff;
}

.badge :deep(.el-badge__content) {
  background-color: transparent;
  color: inherit;
  border: none;
}
</style>
