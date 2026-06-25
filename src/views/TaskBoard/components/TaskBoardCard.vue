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

    <!-- TASK：操作按钮（交付物上传 → 独立弹窗，提交 → 提审弹窗） -->
    <div v-if="item.type === 'TASK'" class="card-actions">
      <el-button size="small" :disabled="!isTaskAssignee(item)" @click="openUploadDialog(item)">上传交付物</el-button>
      <el-button size="small" type="primary" :disabled="!isTaskAssignee(item) || !hasDeliverable(item)" @click="openSubmitDialog(item)">提交</el-button>
    </div>

    <!-- BID_REVIEW：审核操作 -->
    <div v-if="item.type === 'BID_REVIEW'" class="card-actions">
      <el-button size="small" type="danger" plain :disabled="!isBidReviewer(item)" @click="openRejectDialog(item)">驳回</el-button>
      <el-button size="small" type="success" :disabled="!isBidReviewer(item)" @click="handleApproveBid(item)">通过审核</el-button>
    </div>

    <!-- 上传交付物弹窗（与项目详情页统一：名称+类型+拖拽+保存） -->
    <el-dialog v-model="uploadDialogVisible" title="上传交付物" width="500px" :close-on-click-modal="false" append-to-body>
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
          <el-upload drag action="#" :auto-upload="false" :on-change="handleFileChange" :file-list="uploadFileList">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处或<em>点击上传</em></div>
            <template #tip><div class="el-upload__tip">支持 doc/docx/pdf/xls/xlsx 格式</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!deliverableForm.name || !deliverableForm.file" :loading="uploadingLoading" @click="handleSaveDeliverable">保存</el-button>
      </template>
    </el-dialog>

    <!-- 提交任务弹窗（含完成情况说明 + 提交审核） -->
    <el-dialog v-model="showSubmitDialog" :title="'提交任务 - ' + (submittingTask?.title || '')" width="480px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="100px">
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
import { User, Calendar, OfficeBuilding, UploadFilled } from '@element-plus/icons-vue'
import { projectsApi } from '@/api/modules/projects.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { isTaskAssignee, isBidReviewer } from '@/utils/permission.js'
import { getPriorityType, getPriorityLabel } from '@/views/Dashboard/workbench-formatters.js'
import { useDeliverableUpload } from '@/composables/useDeliverableUpload.js'
import ProjectDocumentTable from '@/views/Project/stages/components/ProjectDocumentTable.vue'

const props = defineProps({
  item: { type: Object, required: true },
  availableStatuses: { type: Array, required: true }
})
const emit = defineEmits(['status-change', 'deliverable-changed'])

const priorityType = computed(() => getPriorityType(props.item.priority))
const priorityText = computed(() => getPriorityLabel(props.item.priority))

const isUrgent = computed(() => {
  if (!props.item.dueDate) return false
  const diff = new Date(props.item.dueDate) - new Date()
  return diff > 0 && diff < 3 * 24 * 60 * 60 * 1000
})

const formattedDate = computed(() => {
  const d = new Date(props.item.dueDate)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

function hasDeliverable(task) { return task.deliverables && task.deliverables.length > 0 }

// 上传交付物弹窗（独立，与项目详情页 TaskBoard.vue 统一）
const {
  dialogVisible: uploadDialogVisible,
  currentTask: uploadingTask,
  fileList: uploadFileList,
  form: deliverableForm,
  saving: uploadingLoading,
  openDialog: openUploadDialog,
  handleFileChange,
  save: handleSaveDeliverable,
} = useDeliverableUpload({
  onSave: async (task, payload) => {
    const typeMap = { document: 'DOCUMENT', qualification: 'QUALIFICATION', technical: 'TECHNICAL', quotation: 'QUOTATION', other: 'OTHER' }
    const formData = new FormData()
    formData.append('file', payload.file)
    formData.append('taskId', task.id)
    formData.append('name', payload.name)
    formData.append('deliverableType', typeMap[payload.type] || 'DOCUMENT')
    await projectsApi.createTaskDeliverable(task.projectId, task.id, formData)
    emit('deliverable-changed', task)
  },
})

// === 提交任务弹窗（仅完成情况说明 + 提交审核，交付物已在独立弹窗上传） ===
const showSubmitDialog = ref(false)
const submittingTask = ref(null)
const submittingTaskLoading = ref(false)
const submitNotes = ref('')

function openSubmitDialog(task) {
  if (!hasDeliverable(task)) {
    ElMessage.warning('请先上传交付物')
    return
  }
  submittingTask.value = task
  showSubmitDialog.value = true
  submitNotes.value = task.completionNotes || ''
}

async function confirmSubmit() {
  if (!submittingTask.value) return
  submittingTaskLoading.value = true
  try {
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
.task-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.header-tags { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.type-tag { flex-shrink: 0; }
.task-name { font-size: 14px; font-weight: 500; margin-bottom: 6px; color: #303133; }
.task-desc { font-size: 12px; color: #909399; margin-bottom: 8px; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-meta { display: flex; justify-content: space-between; font-size: 12px; color: #606266; }
.task-owner, .task-deadline { display: flex; align-items: center; gap: 4px; }
.deadline-urgent { color: #f56c6c; }
.task-project { display: flex; align-items: center; gap: 4px; margin-top: 8px; font-size: 12px; color: #909399; }
.card-actions { margin-top: 8px; display: flex; gap: 6px; justify-content: flex-end; }
</style>