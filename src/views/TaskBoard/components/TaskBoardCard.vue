<template>
  <div
    class="task-card"
    :class="{ 'task-high': item.priority === 'HIGH', 'task-review': item.type === 'BID_REVIEW' }"
  >
    <div class="task-header">
      <div class="header-tags">
        <el-tag v-if="item.type" :type="item.type === 'BID_REVIEW' ? 'danger' : 'info'" size="small" class="type-tag">
          {{ item.type === 'BID_REVIEW' ? '标书审核' : '任务' }}
        </el-tag>
        <el-tag v-if="item.priority" :type="priorityType" size="small">
          {{ priorityText }}
        </el-tag>
        <el-tag v-if="item.type === 'TASK' && hasDeliverable(item)" type="success" size="small">已上传交付物</el-tag>
        <el-tag v-else-if="item.type === 'TASK' && !hasDeliverable(item)" type="warning" size="small">交付物必填</el-tag>
      </div>
    </div>
    <div class="task-name">{{ item.title }}</div>
    <div class="task-desc" v-if="item.description">{{ item.description }}</div>
    <div class="task-meta">
      <div class="task-owner" v-if="item.assigneeName || item.submitterName">
        <el-icon><User /></el-icon>
        <span>{{ item.assigneeName || item.submitterName }}</span>
      </div>
      <div class="task-deadline" :class="{ 'deadline-urgent': isUrgent }" v-if="item.dueDate">
        <el-icon><Calendar /></el-icon>
        <span>{{ formattedDate }}</span>
      </div>
    </div>
    <div class="task-project" v-if="item.projectName">
      <el-icon><OfficeBuilding /></el-icon>
      <span>{{ item.projectName }}</span>
    </div>

    <!-- BID_REVIEW：标书文件列表（只读下载） -->
    <ProjectDocumentTable v-if="item.type === 'BID_REVIEW' && item.projectId" :project-id="item.projectId" readonly />

    <!-- TASK：交付物操作（复用 TaskKanban 逻辑） -->
    <div v-if="item.type === 'TASK'" class="card-actions">
      <el-button size="small" :disabled="!isTaskAssignee(item)" @click="openDeliverableUpload(item)">交付物上传</el-button>
      <el-button size="small" type="primary" :disabled="!isTaskAssignee(item) || !hasDeliverable(item)" @click="openSubmitDialog(item)">提交</el-button>
    </div>

    <!-- BID_REVIEW：审核操作 -->
    <div v-if="item.type === 'BID_REVIEW'" class="card-actions">
      <el-button size="small" type="danger" plain @click="openRejectDialog(item)">驳回</el-button>
      <el-button size="small" type="success" @click="handleApproveBid(item)">通过审核</el-button>
    </div>

    <!-- 交付物上传 + 提交对话框（TASK） -->
    <el-dialog v-model="showSubmitDialog" :title="'提交任务 - ' + (submittingTask?.title || '')" width="480px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="交付物" required>
          <el-upload ref="deliverableUploadRef" :auto-upload="false" :file-list="deliverableFileList" :limit="1" accept=".pdf,.doc,.docx,.xlsx,.jpg,.png">
            <el-button size="small">选择文件</el-button>
            <template #tip><span style="font-size:11px;color:#909399">上传交付物（PDF/Word/Excel/图片）</span></template>
          </el-upload>
        </el-form-item>
        <el-form-item label="完成情况说明">
          <el-input v-model="submitNotes" type="textarea" :rows="3" placeholder="填写完成情况说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmitDialog = false">取消</el-button>
        <el-button type="primary" :loading="submittingTaskLoading" @click="confirmSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 驳回原因对话框（BID_REVIEW） -->
    <el-dialog v-model="showRejectDialog" title="驳回标书审核" width="420px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="0">
        <el-form-item :label="'驳回：' + (rejectingItem?.title || '')" />
        <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
      </el-form>
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="danger" :loading="rejectingLoading" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Calendar, OfficeBuilding } from '@element-plus/icons-vue'
import { projectsApi } from '@/api/modules/projects.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { useUserStore } from '@/stores/user.js'
import ProjectDocumentTable from '@/views/Project/stages/components/ProjectDocumentTable.vue'

const props = defineProps({
  item: { type: Object, required: true },
  availableStatuses: { type: Array, required: true }
})
const emit = defineEmits(['status-change', 'deliverable-changed'])
const userStore = useUserStore()

const PRIORITY_TYPE_MAP = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
const PRIORITY_TEXT_MAP = { HIGH: '高', MEDIUM: '中', LOW: '低' }
const priorityType = computed(() => PRIORITY_TYPE_MAP[props.item.priority] || 'info')
const priorityText = computed(() => PRIORITY_TEXT_MAP[props.item.priority] || props.item.priority)

const isUrgent = computed(() => {
  if (!props.item.dueDate) return false
  const diff = new Date(props.item.dueDate) - new Date()
  return diff > 0 && diff < 3 * 24 * 60 * 60 * 1000
})

const formattedDate = computed(() => {
  const d = new Date(props.item.dueDate)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

// 复用 TaskKanban 的交付物逻辑
function isTaskAssignee(task) {
  const uid = userStore?.currentUser?.id
  return uid != null && task?.assigneeId != null && String(uid) === String(task.assigneeId)
}
function hasDeliverable(task) {
  return !!(task.deliverableUrl || task.deliverableName || task.fileUrl)
}

// 交付物上传 + 提交对话框（复用 TaskKanban）
const showSubmitDialog = ref(false)
const submittingTask = ref(null)
const submittingTaskLoading = ref(false)
const deliverableFileList = ref([])
const deliverableUploadRef = ref(null)
const submitNotes = ref('')

function openDeliverableUpload(task) {
  submittingTask.value = task
  showSubmitDialog.value = true
  deliverableFileList.value = task.deliverableUrl ? [{ name: task.deliverableName || '已上传文件', url: task.deliverableUrl }] : []
  submitNotes.value = task.completionNotes || ''
}

function openSubmitDialog(task) {
  if (!hasDeliverable(task)) {
    ElMessage.warning('请先上传交付物')
    return
  }
  submittingTask.value = task
  showSubmitDialog.value = true
  deliverableFileList.value = task.deliverableUrl ? [{ name: task.deliverableName || '已上传文件', url: task.deliverableUrl }] : []
  submitNotes.value = task.completionNotes || ''
}

async function confirmSubmit() {
  if (!submittingTask.value) return
  submittingTaskLoading.value = true
  try {
    if (deliverableUploadRef.value?.uploadFiles?.length > 0) {
      const formData = new FormData()
      formData.append('file', deliverableUploadRef.value.uploadFiles[0].raw)
      formData.append('taskId', submittingTask.value.id)
      await projectsApi.createTaskDeliverable(submittingTask.value.projectId, submittingTask.value.id, formData)
    }
    if (submitNotes.value) {
      await projectsApi.updateTask(submittingTask.value.id, { completionNotes: submitNotes.value })
    }
    await projectsApi.updateTaskStatus(submittingTask.value.projectId, submittingTask.value.id, 'REVIEW')
    ElMessage.success('已提交审核')
    showSubmitDialog.value = false
    emit('deliverable-changed', submittingTask.value)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '提交失败')
  } finally {
    submittingTaskLoading.value = false
    submittingTask.value = null
  }
}

// BID_REVIEW 审核操作
async function handleApproveBid(item) {
  try {
    await projectLifecycleApi.approveBid(item.projectId, { comment: '' })
    ElMessage.success('审核已通过')
    emit('deliverable-changed', item)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '审核通过失败')
  }
}

const showRejectDialog = ref(false)
const rejectingItem = ref(null)
const rejectReason = ref('')
const rejectingLoading = ref(false)

function openRejectDialog(item) {
  rejectingItem.value = item
  rejectReason.value = ''
  showRejectDialog.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) return ElMessage.warning('请填写驳回原因')
  if (!rejectingItem.value) return
  rejectingLoading.value = true
  try {
    await projectLifecycleApi.rejectBid(rejectingItem.value.projectId, { reason: rejectReason.value.trim() })
    ElMessage.success('已驳回')
    showRejectDialog.value = false
    emit('deliverable-changed', rejectingItem.value)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '驳回失败')
  } finally {
    rejectingLoading.value = false
    rejectingItem.value = null
  }
}
</script>

<style scoped lang="scss">
.task-card {
  background: #fff;
  border-radius: 6px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  transition: box-shadow 0.2s ease;
  &:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12); }
  &.task-high { border-left: 3px solid #f56c6c; }
  &.task-review { border-left: 3px solid #e6a23c; }
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  .header-tags { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
  .type-tag { flex-shrink: 0; }
}

.task-name { font-size: 14px; font-weight: 500; margin-bottom: 6px; color: #303133; }
.task-desc { font-size: 12px; color: #909399; margin-bottom: 8px; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  .task-owner, .task-deadline { display: flex; align-items: center; gap: 4px; }
  .deadline-urgent { color: #f56c6c; }
}

.task-project { display: flex; align-items: center; gap: 4px; margin-top: 8px; font-size: 12px; color: #909399; .el-icon { flex-shrink: 0; } }
.card-actions { margin-top: 8px; display: flex; gap: 6px; justify-content: flex-end; }
:deep(.project-documents) { margin-top: 8px; .el-card__header { padding: 8px 12px; } .el-card__body { padding: 8px; } }
</style>
