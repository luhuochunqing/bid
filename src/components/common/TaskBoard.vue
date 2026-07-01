<template>
  <div class="task-board">
    <div class="board-header">
      <div class="header-left">
        <span class="board-title">任务看板</span>
        <el-tag type="info" size="small">总进度: {{ progress }}%</el-tag>
      </div>
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
                    v-for="s in statuses"
                    :key="s.code"
                    :disabled="normalizeStatus(task.status) === s.code || !canTransitionToStatus(task, s.code)"
                    @click="onStatusMenuClick(task, s.code)"
                  >
                    设为{{ s.name }}
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleUploadDeliverable(task)" v-if="isTaskAssignee(task)">
                    <el-icon><Upload /></el-icon>
                    上传交付物
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleDeleteTask(task)" v-if="canDeleteTask(task)">
                    <el-icon><Delete /></el-icon>
                    <span class="delete-task-label">删除任务</span>
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
                <span>{{ task.deadline ? task.deadline.slice(0, 10) : '-' }}</span>
              </div>
            </div>
            <div v-if="task.deliverables && task.deliverables.length > 0" class="deliverables">
              <div class="deliverable-title">交付物:</div>
              <div v-for="del in task.deliverables" :key="del.id" class="deliverable-item">
                <el-tag size="small" :closable="isTaskAssignee(task)" @close="handleRemoveDeliverable(task, del)">
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

    <TaskDeliverableUploadDialog
      v-model="showUploadDialog"
      :task="currentTask"
      @save="handleSaveDeliverable"
    />

    <!-- CO-413: 驳回原因弹窗（REVIEW → TODO 必填） -->
    <el-dialog v-model="rejectDialogVisible" title="驳回任务" width="480px" :close-on-click-modal="false">
      <div v-if="rejectingTask" class="reject-task-name">任务：{{ rejectingTask.name }}</div>
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-position="top">
        <el-form-item label="驳回原因（必填）" prop="reason">
          <el-input
            v-model="rejectForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请填写驳回原因，执行人将看到此说明"
            maxlength="500"
            show-word-limit
            data-test="reject-reason-input"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelReject" data-test="reject-cancel">取消</el-button>
        <el-button type="danger" :loading="rejectSubmitting" @click="confirmReject" data-test="reject-confirm">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, User, Calendar, Document, Upload, DocumentAdd, Delete } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'

import { getPriorityType, getPriorityLabel as getPriorityText } from '@/views/Dashboard/workbench-formatters.js'
import { isTaskAssignee } from '@/utils/permission.js'
import { hexToSoftBackground } from '@/utils/color.js'
import { validateSubmitForReview } from '@/composables/useTaskSubmissionValidation.js'
import TaskDeliverableUploadDialog from './TaskDeliverableUploadDialog.vue'

const props = defineProps({
  tasks: {
    type: Array,
    default: () => []
  },
  projectId: String,
  showSubmitButton: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['task-click', 'status-change', 'submit-to-document', 'add-deliverable', 'remove-deliverable', 'remove-task'])

const projectStore = useProjectStore()
const userStore = useUserStore()

// 交付物上传对话框
const showUploadDialog = ref(false)
const currentTask = ref(null)

const handleUploadDeliverable = (task) => {
  currentTask.value = task
  showUploadDialog.value = true
}

const handleSaveDeliverable = async (payload) => {
  if (!currentTask.value) return
  const typeMap = {
    document: 'DOCUMENT', qualification: 'QUALIFICATION',
    technical: 'TECHNICAL', quotation: 'QUOTATION', other: 'OTHER',
  }
  try {
    const saved = await projectStore.addDeliverable(props.projectId, currentTask.value.id, {
      name: payload.name,
      deliverableType: typeMap[payload.type] || 'DOCUMENT',
      size: payload.file ? `${(payload.file.size / 1024).toFixed(1)}KB` : null,
      fileType: payload.file?.type || null,
      file: payload.file,
      uploaderId: userStore.currentUser?.id ?? null,
      uploaderName: userStore.userName,
    })
    emit('add-deliverable', currentTask.value.id, saved)
    ElMessage.success('交付物已保存')
  } catch (error) {
    ElMessage.error(error?.message || '保存交付物失败')
  }
}

const statuses = computed(() => projectStore.taskStatuses)

onMounted(() => {
  if (!projectStore.taskStatusesLoaded) {
    projectStore.loadTaskStatuses()
  }
})

const normalizeStatus = (status) => String(status || '').toUpperCase()

const canReviewTask = (task) => {
  if (userStore.isBidManager) return true
  const project = projectStore.currentProject
  if (!project) return false
  const uid = userStore?.currentUser?.id
  if (uid == null) return false
  if (task.assigneeId != null && String(uid) === String(task.assigneeId)) return false
  return (project.primaryLeadUserId != null && String(uid) === String(project.primaryLeadUserId)) ||
    (project.secondaryLeadUserId != null && String(uid) === String(project.secondaryLeadUserId))
}

const canTransitionToStatus = (task, targetStatus) => {
  const currentStatus = normalizeStatus(task.status)
  const target = normalizeStatus(targetStatus)
  if (currentStatus === target) return false
  if (target === 'TODO') {
    if (currentStatus === 'REVIEW') return canReviewTask(task)
    return false
  }
  if (target === 'REVIEW') {
    if (currentStatus === 'TODO') {
      if (!isTaskAssignee(task)) return false
      return validateSubmitForReview({
        deliverables: task.deliverables,
        hasDeliverable: task.hasDeliverable,
        completionNotes: task.completionNotes
      }).valid
    }
    return false
  }
  if (target === 'COMPLETED') {
    if (currentStatus === 'REVIEW') return canReviewTask(task)
    return false
  }
  return false
}

// CO-387: 删除任务权限 — 仅 TODO 状态 + (管理员/组长 或 项目主/副负责人)
// 注意：任务执行人本身无权删除（避免执行人误删分配给自己的任务）
const canDeleteTask = (task) => {
  if (normalizeStatus(task.status) !== 'TODO') return false
  if (userStore.isBidManager) return true
  const project = projectStore.currentProject
  if (!project) return false
  const uid = userStore?.currentUser?.id
  return uid != null && (
    (project.primaryLeadUserId != null && String(uid) === String(project.primaryLeadUserId)) ||
    (project.secondaryLeadUserId != null && String(uid) === String(project.secondaryLeadUserId))
  )
}

const columns = computed(() => statuses.value
  .map((s) => ({
    key: s.code,
    title: s.name,
    color: s.color,
    category: s.category,
    terminal: s.terminal
  })))

const terminalCodes = computed(() => new Set(statuses.value.filter((s) => s.terminal).map((s) => s.code)))

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

const getTasksByStatus = (code) => {
  return props.tasks.filter((t) => normalizeStatus(t.status) === code)
}

const getTaskCount = (code) => getTasksByStatus(code).length

const getColumnHeaderStyle = (column) => {
  const color = column?.color || 'var(--text-muted)'
  return {
    color,
    background: hexToSoftBackground(color)
  }
}

const isUrgent = (deadline) => {
  if (!deadline) return false
  const deadlineDate = new Date(deadline)
  const today = new Date()
  const diffDays = Math.ceil((deadlineDate - today) / (1000 * 60 * 60 * 24))
  return diffDays <= 3
}

const handleTaskClick = (task) => emit('task-click', task)

const handleStatusChange = (task, newStatus, reviewComment) => emit('status-change', task, newStatus, reviewComment)

// CO-413: REVIEW → TODO（驳回）时弹出驳回原因弹窗，其他状态走原直接 emit
const rejectDialogVisible = ref(false)
const rejectingTask = ref(null)
const rejectFormRef = ref(null)
const rejectForm = ref({ reason: '' })
const rejectSubmitting = ref(false)
const rejectRules = { reason: [{ required: true, message: '请填写驳回原因', trigger: 'blur' }] }

function onStatusMenuClick(task, newStatus) {
  if (normalizeStatus(task.status) === 'REVIEW' && normalizeStatus(newStatus) === 'TODO') {
    openRejectDialog(task)
    return
  }
  handleStatusChange(task, newStatus)
}

function openRejectDialog(task) {
  rejectingTask.value = task
  rejectForm.value = { reason: '' }
  rejectDialogVisible.value = true
}

function cancelReject() {
  rejectDialogVisible.value = false
  rejectingTask.value = null
}

async function confirmReject() {
  if (!rejectingTask.value) return
  // formRef 未挂载时（如 stub 环境）退化为手动校验非空
  if (rejectFormRef.value) {
    try {
      await rejectFormRef.value.validate()
    } catch {
      return
    }
  } else if (!rejectForm.value.reason?.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  rejectSubmitting.value = true
  try {
    handleStatusChange(rejectingTask.value, 'TODO', rejectForm.value.reason)
    rejectDialogVisible.value = false
    rejectingTask.value = null
  } finally {
    rejectSubmitting.value = false
  }
}

const isStatusTransitionInFlight = ref(false)
const draggingTask = ref(null)
const dragTransitionEmitted = ref(false)
let mouseDragCandidate = null

const cleanupMouseDrag = () => {
  window.removeEventListener('mousemove', handleWindowMouseMove)
  window.removeEventListener('mouseup', handleWindowMouseUp)
  mouseDragCandidate = null
  draggingTask.value = null
  dragTransitionEmitted.value = false
}

const emitDragStatusChange = (task, targetColumnKey) => {
  if (!task) return
  if (dragTransitionEmitted.value) return
  if (normalizeStatus(task.status) === targetColumnKey) return
  if (!canTransitionToStatus(task, targetColumnKey)) {
    ElMessage.warning('不允许直接将任务设为该状态，请按流程操作')
    return
  }
  dragTransitionEmitted.value = true
  handleStatusChange(task, targetColumnKey)
}

const onDragChange = (evt, targetColumnKey) => {
  if (!evt?.added) return
  emitDragStatusChange(evt.added.element, targetColumnKey)
}

function handleWindowMouseMove(event) {
  if (!mouseDragCandidate || mouseDragCandidate.active) return
  const deltaX = Math.abs(event.clientX - mouseDragCandidate.startX)
  const deltaY = Math.abs(event.clientY - mouseDragCandidate.startY)
  if (deltaX < 6 && deltaY < 6) return
  mouseDragCandidate.active = true
  draggingTask.value = mouseDragCandidate.task
}

function handleWindowMouseUp() {
  cleanupMouseDrag()
}

const handleMouseDragStart = (task, event) => {
  if (event?.button !== 0) return
  mouseDragCandidate = {
    task,
    startX: event.clientX,
    startY: event.clientY,
    active: false,
  }
  dragTransitionEmitted.value = false
  window.addEventListener('mousemove', handleWindowMouseMove)
  window.addEventListener('mouseup', handleWindowMouseUp)
}

const handleMouseDrop = (targetColumnKey) => {
  if (!mouseDragCandidate?.active) return
  emitDragStatusChange(mouseDragCandidate.task, targetColumnKey)
  cleanupMouseDrag()
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

const handleDeleteTask = async (task) => {
  const taskLabel = task.name || task.title || `#${task.id}`
  try {
    await ElMessageBox.confirm(
      `确定要删除任务「${taskLabel}」吗？此操作不可恢复。`,
      '删除任务',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await projectStore.removeTask(task.id)
    emit('remove-task', task.id)
    ElMessage.success('任务已删除')
  } catch (error) {
    ElMessage.error(error?.message || '删除任务失败')
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
.task-board { display: flex; flex-direction: column; gap: 16px; }
.board-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.board-title { font-size: 16px; font-weight: 600; color: var(--gray-750); }
.board-columns-container { display: flex; gap: 16px; min-height: 500px; }
.board-column { background: var(--bg-subtle); border-radius: 8px; overflow: hidden; display: flex; flex-direction: column; min-width: 260px; flex: 1; }
.column-header { padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; font-weight: 500; }
.column-title { font-size: 14px; }
.column-content { flex: 1; padding: 12px; overflow-y: auto; max-height: 500px; }
.task-card { background: white; border-radius: 6px; padding: 12px; margin-bottom: 12px; cursor: pointer; transition: all 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
.task-card:hover { box-shadow: 0 6px 16px rgba(0,0,0,0.15); }
.task-card-ghost { opacity: 0.5; background: var(--el-color-info-light-9) !important; border: 1px dashed var(--el-color-primary) !important; }
.task-card-dragging { opacity: 0.9; transform: rotate(2deg); box-shadow: 0 12px 24px rgba(0,0,0,0.2) !important; cursor: grabbing; }
.task-high { border-left: 3px solid var(--el-color-danger); }
.task-review { border-left: 3px solid var(--el-color-warning); }
.task-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; gap: 8px; }
.more-icon { color: var(--text-muted); cursor: pointer; font-size: 16px; }
.more-icon:hover { color: var(--el-color-primary); }
.task-name { font-size: 14px; color: var(--gray-750); margin-bottom: 8px; font-weight: 500; line-height: 1.4; }
.task-desc { font-size: 12px; color: var(--text-muted); margin-bottom: 12px; line-height: 1.4; }
.task-meta { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-muted); }
.task-owner, .task-deadline { display: flex; align-items: center; gap: 4px; }
.task-deadline.deadline-urgent { color: var(--el-color-danger); }
.deliverables { margin-top: 12px; padding-top: 12px; border-top: 1px dashed var(--el-border-color); }
.deliverable-title { font-size: 12px; color: var(--text-muted); margin-bottom: 8px; }
.deliverable-item { display: inline-flex; align-items: center; gap: 4px; margin: 0 8px 8px 0; }
.submit-section { margin-top: 16px; padding: 16px; background: var(--el-color-info-light-9); border: 1px solid var(--el-color-primary-light-8); border-radius: 8px; text-align: center; }
.submit-tip { margin-top: 8px; font-size: 12px; color: var(--el-color-primary); }
.badge :deep(.el-badge__content) { background-color: transparent; color: inherit; border: none; }
.delete-task-label { color: var(--el-color-danger); }
.reject-task-name { margin-bottom: 12px; padding: 8px 12px; background: var(--bg-subtle, #f5f7fa); border-radius: 4px; font-size: 14px; color: var(--gray-750, #303133); font-weight: 500; }
</style>
