<template>
  <el-card class="task-kanban" shadow="never">
    <template #header>
      <div class="kanban-header">
        <span class="kanban-title">任务看板</span>
        <div class="kanban-actions">
          <el-button v-if="canUseAI" size="small" @click="emit('openScoreParse')">
            AI 评分标准解析
            <sup v-if="scoreRiskCount > 0" style="display:inline-block;background:#dc2626;color:#fff;border-radius:10px;font-size:10px;padding:1px 5px;margin-left:3px;line-height:1.3;">{{ scoreRiskCount > 99 ? '99+' : scoreRiskCount }}</sup>
          </el-button>
          <el-button v-if="canUseAI" size="small" @click="emit('openDecompose')">AI 自动拆解任务</el-button>
          <el-button type="primary" size="small" @click="showCreateDialog = true">新建任务</el-button>
        </div>
      </div>
    </template>

    <div class="kanban-container">
      <div v-for="col in columns" :key="col.status" class="kanban-col">
        <div class="col-header">
          <span>{{ col.label }}</span>
          <el-tag size="small" :type="col.tag" effect="plain">{{ grouped[col.status]?.length || 0 }}</el-tag>
        </div>
        <div class="col-body">
          <div v-for="task in (grouped[col.status] || [])"
            :key="task.id"
            class="task-card"
          >
            <div class="card-title">{{ task.title }}</div>
            <div class="card-info">
              <span>创建人：{{ task.creatorName || '-' }}</span>
              <span>截止：{{ task.dueDate ? task.dueDate.slice(0, 10) : '-' }}</span>
            </div>
            <div class="card-info">
              <span>执行人：{{ task.assigneeName || '-' }}</span>
              <span>
                交付物：
                <span v-if="hasDeliverable(task)" style="color:#2E7659">已上传</span>
                <span v-else style="color:#e65100">* 必填</span>
              </span>
            </div>
            <div v-if="task.completionNotes" class="card-info" style="margin-top:4px;">
              <span>完成情况：{{ task.completionNotes }}</span>
            </div>
            <!-- TODO / IN_PROGRESS 列：交付物上传 + 提交（仅任务执行人本人可操作） -->
            <div v-if="col.status === 'TODO' || col.status === 'IN_PROGRESS'" class="card-actions">
              <el-button size="small" :disabled="!isTaskAssignee(task)" @click="openDeliverableUpload(task)">交付物上传</el-button>
              <el-button size="small" type="primary" :disabled="!isTaskAssignee(task) || !hasDeliverable(task)" @click="openSubmitDialog(task)">提交</el-button>
            </div>
            <!-- REVIEW 列：驳回/通过 -->
            <div v-if="col.status === 'REVIEW'" class="card-actions">
              <el-button size="small" type="danger" plain @click="rejectTask(task)">驳回</el-button>
              <el-button size="small" type="success" @click="approveTask(task)">通过</el-button>
            </div>
          </div>
          <el-empty v-if="!grouped[col.status]?.length" :description="`暂无${col.label}`" :image-size="60" />
        </div>
      </div>
    </div>

    <!-- 新建任务对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建任务" width="520px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" label-width="100px" :rules="createRules">
        <el-form-item label="任务名称" prop="title">
          <el-input v-model="createForm.title" placeholder="输入任务名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="详细描述" prop="description" required>
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="输入详细描述" />
        </el-form-item>
        <el-form-item label="任务附件">
          <el-upload :auto-upload="false" :file-list="createFileList" multiple>
            <el-button size="small">选择文件</el-button>
            <template #tip><span style="font-size:11px;color:#909399">可选，上传相关文件</span></template>
          </el-upload>
        </el-form-item>
        <el-form-item label="执行人" prop="assigneeId">
          <UserPicker v-model="createForm.assigneeId" mode="search" placeholder="搜索人员" style="width:100%" />
        </el-form-item>
        <el-form-item label="截止日期" prop="dueDate" required>
          <el-date-picker v-model="createForm.dueDate" type="date" placeholder="选择截止日期" style="width:100%" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 交付物上传 + 提交对话框 -->
    <el-dialog v-model="showSubmitDialog" :title="'提交任务 - ' + (submittingTask?.title || '')" width="480px" :close-on-click-modal="false">
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

    <!-- 驳回原因对话框 -->
    <el-dialog v-model="showRejectDialog" title="驳回任务" width="420px" :close-on-click-modal="false">
      <el-form label-width="0">
        <el-form-item :label="'驳回：' + (rejectingTask?.title || '')" />
        <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
      </el-form>
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="danger" :loading="rejectingLoading" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { projectsApi } from '@/api/modules/projects.js'
import { useUserStore } from '@/stores/user.js'
import UserPicker from '@/components/common/UserPicker.vue'
const props = defineProps({
  projectId: { type: [String, Number], required: true },
  canUseAI: Boolean,
  scoreRiskCount: { type: Number, default: 0 },
})
const emit = defineEmits(['openScoreParse', 'openDecompose'])
const userStore = useUserStore()

const columns = [
  { status: 'TODO', label: '待办', tag: '' },
  { status: 'IN_PROGRESS', label: '进行中', tag: 'primary' },
  { status: 'REVIEW', label: '待审核', tag: 'warning' },
  { status: 'COMPLETED', label: '已完成', tag: 'success' },
]
const tasks = ref([])
const grouped = computed(() => {
  const g = {}
  for (const t of tasks.value) {
    const s = t.status || 'TODO'
    if (!g[s]) g[s] = []
    g[s].push(t)
  }
  return g
})
function isTaskAssignee(task) {
  const uid = userStore?.currentUser?.id
  return uid != null && task?.assigneeId != null && String(uid) === String(task.assigneeId)
}

async function loadTasks() {
  try {
    const r = await projectsApi.getTasks(props.projectId)
    tasks.value = Array.isArray(r?.data) ? r.data : []
  } catch { tasks.value = [] }
}
// Create task dialog
const showCreateDialog = ref(false)
const creating = ref(false)
const createFormRef = ref(null)
const createForm = reactive({ title: '', description: '', assigneeId: null, dueDate: '' })
const createFileList = ref([])
const createRules = { title: [{ required: true, message: '请输入任务名称', trigger: 'blur' }], description: [{ required: true, message: '请输入详细描述', trigger: 'blur' }], assigneeId: [{ required: true, message: '请选择执行人', trigger: 'change' }], dueDate: [{ required: true, message: '请选择截止日期', trigger: 'change' }] }

async function handleCreate() {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await projectsApi.createTask(props.projectId, {
      title: createForm.title,
      description: createForm.description,
      assigneeId: createForm.assigneeId,
      dueDate: createForm.dueDate || null,
      status: 'TODO'
    })
    ElMessage.success('任务已创建')
    showCreateDialog.value = false
    createForm.title = ''; createForm.description = ''; createForm.assigneeId = null; createForm.dueDate = ''
    createFileList.value = []
    createFormRef.value.resetFields()
    await loadTasks()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '创建失败') }
  finally { creating.value = false }
}
// Deliverable upload + submit dialog
const showSubmitDialog = ref(false)
const submittingTask = ref(null)
const submittingTaskLoading = ref(false)
const deliverableFileList = ref([])
const deliverableUploadRef = ref(null)
const submitNotes = ref('')

function hasDeliverable(task) {
  return !!(task.deliverableUrl || task.deliverableName || task.fileUrl)
}

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
    // Upload deliverable if selected
    if (deliverableUploadRef.value?.uploadFiles?.length > 0) {
      const formData = new FormData()
      formData.append('file', deliverableUploadRef.value.uploadFiles[0].raw)
      formData.append('taskId', submittingTask.value.id)
      await projectsApi.createTaskDeliverable(props.projectId, submittingTask.value.id, formData)
    }
    // Update completion notes and status
    if (submitNotes.value) {
      await projectsApi.updateTask(submittingTask.value.id, { completionNotes: submitNotes.value })
    }
    await projectsApi.updateTaskStatus(props.projectId, submittingTask.value.id, 'REVIEW')
    ElMessage.success('已提交审核')
    showSubmitDialog.value = false
    await loadTasks()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '提交失败') }
  finally { submittingTaskLoading.value = false; submittingTask.value = null }
}

async function approveTask(task) {
  try {
    await projectsApi.updateTaskStatus(props.projectId, task.id, 'COMPLETED')
    ElMessage.success('审核通过')
    await loadTasks()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '审核失败') }
}
// Reject with reason
const showRejectDialog = ref(false), rejectingTask = ref(null), rejectReason = ref(''), rejectingLoading = ref(false)
function rejectTask(task) { rejectingTask.value = task; rejectReason.value = ''; showRejectDialog.value = true }

async function confirmReject() {
  if (!rejectReason.value.trim()) return ElMessage.warning('请填写驳回原因')
  if (!rejectingTask.value) return
  rejectingLoading.value = true
  try {
    await projectsApi.updateTask(rejectingTask.value.id, { rejectionReason: rejectReason.value.trim() })
    await projectsApi.updateTaskStatus(props.projectId, rejectingTask.value.id, 'TODO')
    ElMessage.success('已驳回，任务退回待办')
    showRejectDialog.value = false
    await loadTasks()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '驳回失败') }
  finally { rejectingLoading.value = false; rejectingTask.value = null }
}

onMounted(loadTasks)
watch(() => props.projectId, (newId, oldId) => { if (newId && newId !== oldId) loadTasks() })
</script>

<style scoped>
.kanban-header { display: flex; justify-content: space-between; align-items: center; }
.kanban-title { font-size: 15px; font-weight: 600; }
.kanban-actions { display: flex; gap: 8px; }
.kanban-container { display: flex; gap: 16px; min-height: 300px; }
.kanban-col { flex: 1; background: #f5f7fa; border-radius: 8px; padding: 12px; min-width: 0; }
.col-header { font-size: 13px; font-weight: 600; color: #333; padding: 6px 8px; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; }
.col-body { display: flex; flex-direction: column; gap: 8px; }
.task-card { background: #fff; border: 1px solid #e4e7ed; border-radius: 6px; padding: 12px; }
.card-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 6px; }
.card-info { font-size: 11px; color: #909399; margin-bottom: 2px; display: flex; justify-content: space-between; }
.card-actions { margin-top: 8px; display: flex; gap: 6px; justify-content: flex-end; }
</style>
