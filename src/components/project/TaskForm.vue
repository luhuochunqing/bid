<template>
  <div class="task-form">
    <el-tabs v-model="activeTab" class="task-form-tabs">
      <el-tab-pane label="详情" name="detail">
        <el-form :model="localValue" label-width="110px" :disabled="readonly">
          <!-- CO-413: 驳回原因展示（只读，仅当存在 lastRejectReason 时显示） -->
          <el-alert
            v-if="localValue.extendedFields?.lastRejectReason"
            class="reject-reason-alert"
            type="warning"
            :closable="false"
            show-icon
            title="驳回原因"
            :description="localValue.extendedFields.lastRejectReason"
            data-test="reject-reason-alert"
          />
          <el-form-item label="任务名称" required>
            <el-input v-model="localValue.name" placeholder="请输入任务名称" />
          </el-form-item>

          <el-form-item label="详细描述">
            <el-input
              v-model="localValue.content"
              type="textarea"
              :rows="6"
              placeholder="支持 Markdown：# 标题、- 列表、**加粗** 等"
            />
          </el-form-item>

          <el-form-item label="任务附件">
            <el-upload
              v-if="!readonly"
              data-test="task-attachment-upload"
              :auto-upload="false"
              :file-list="attachmentFileList"
              accept=".doc,.docx,.pdf,.xls,.xlsx"
              multiple
              @change="handleAttachmentChange" @remove="handleAttachmentRemove"
              @preview="file => emit('attachment-preview', file)"
            >
              <el-button :icon="Upload">添加附件</el-button>
              <template #file="{ file }">
                <div class="task-attachment-file-row">
                  <a href="javascript:void(0)" class="upload-file-link" data-test="task-attachment-file-link" @click.prevent="handleDownloadAttachment(file)">{{ file.name }}</a>
                  <el-button
                    data-test="task-attachment-remove"
                    link
                    type="danger"
                    size="small"
                    @click.prevent="removeAttachment(file)"
                  >删除</el-button>
                </div>
              </template>
            </el-upload>
            <div v-if="readonly && savedAttachments.length" class="attachment-list" data-test="task-attachment-list">
              <a
                v-for="file in savedAttachments"
                :key="file.id"
                href="javascript:void(0)"
                class="attachment-link"
                data-test="task-attachment-file-link"
                @click.prevent="handleDownloadAttachment(file)"
              >{{ file.name }}</a>
            </div>
          </el-form-item>

          <el-form-item label="任务创建人">
            <el-input
              v-model="localValue.createdByName"
              data-test="task-creator-input"
              :disabled="true"
              placeholder="系统自动获取"
            />
          </el-form-item>

          <el-form-item label="任务执行人" required>
            <UserPicker
              v-model="localValue.assigneeId"
              data-test="task-owner-select"
              mode="search"
              placeholder="模糊搜索选择执行人"
              :disabled="readonly"
              :initial-options="assigneeOptions"
              @select="handleAssigneeSelect"
            />
          </el-form-item>

          <el-form-item label="截止日期" required>
            <el-date-picker v-model="localValue.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="请选择截止日期" />
          </el-form-item>

          <el-form-item label="交付物上传">
            <el-upload
              v-if="!readonly || canDeliver"
              data-test="task-deliverable-upload"
              :auto-upload="false"
              :file-list="deliverableFileList"
              :disabled="readonly && !canDeliver"
              multiple
              @change="handleDeliverableChange"
              @remove="handleDeliverableRemove"
            >
              <el-button :icon="Upload" :disabled="readonly && !canDeliver">上传交付物</el-button>
            </el-upload>
            <div v-if="readonly && localValue.deliverables?.length" class="deliverable-list">
              <a
                v-for="d in localValue.deliverables"
                :key="d.id"
                href="javascript:void(0)"
                class="deliverable-link"
                @click.prevent="downloadDeliverable(d)"
              >{{ d.name }}</a>
            </div>
          </el-form-item>

          <el-form-item label="完成情况说明">
            <el-input
              v-model="localValue.completionNotes"
              type="textarea"
              :rows="4"
              placeholder="请填写完成情况说明（可选）"
              :disabled="readonly && !canDeliver"
            />
          </el-form-item>

          <el-form-item label="优先级">
            <el-select v-model="localValue.priority" style="width: 100%">
              <el-option label="高" value="high" />
              <el-option label="中" value="medium" />
              <el-option label="低" value="low" />
            </el-select>
          </el-form-item>

          <el-form-item label="状态">
            <el-select v-model="localValue.status" style="width: 100%" :loading="loadingStatuses" :disabled="!canManageStatus">
              <el-option v-for="s in statuses" :key="s.code" :label="s.name" :value="s.code" />
            </el-select>
          </el-form-item>

          <el-alert v-if="validationMessage" type="warning" :closable="false" :title="validationMessage" />
        </el-form>

        <template v-if="extendedFieldSchema.length > 0">
          <el-divider>扩展字段</el-divider>
          <DynamicFormRenderer
            ref="extFormRef"
            :fields="extendedFieldSchema"
            v-model="localValue.extendedFields"
            :disabled="readonly"
          />
        </template>
      </el-tab-pane>

      <el-tab-pane v-if="localValue.id" label="动态" name="activity">
        <TaskActivityPanel :task-id="localValue.id" :readonly="readonly" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch, onMounted } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import { taskStatusDictApi } from '@/api/modules/taskStatusDict.js'
import { projectsApi } from '@/api/modules/projects.js'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import DynamicFormRenderer from '@/components/common/DynamicFormRenderer.vue'
import UserPicker from '@/components/common/UserPicker.vue'
import TaskActivityPanel from '@/components/project/TaskActivityPanel.vue'
import { useTaskAssigneePicker } from './useTaskAssigneePicker.js'
import { ElMessage } from 'element-plus'
import { getTaskDeliverableDownloadUrl } from '@/api/modules/taskDeliverables.js'
import { downloadWithFilename } from '@/utils/download.js'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'create' }, // create | edit | view
})
const emit = defineEmits(['submit', 'submit-review', 'update:modelValue', 'attachment-preview'])
const projectStore = useProjectStore()
const userStore = useUserStore()
const localValue = reactive({ ...props.modelValue })
if (!localValue.extendedFields) localValue.extendedFields = {}
if (!Array.isArray(localValue.attachments)) localValue.attachments = []
const statuses = ref([])
const loadingStatuses = ref(false)
const validationMessage = ref('')
const extFormRef = ref(null)
const activeTab = ref('detail')
const readonly = computed(() => props.mode === 'view')
// 状态仅分配人可改
const canManageStatus = computed(() => {
  if (props.mode === 'create') return true
  if (props.mode === 'view') return false
  const currentUserId = userStore.currentUser?.id
  if (currentUserId == null) return false
  const taskAssigneeId = localValue.assigneeId
  if (taskAssigneeId == null) return true
  return String(currentUserId) !== String(taskAssigneeId)
})
/** 执行人可在任务TODO状态下填写交付物和完成情况说明，并提交审核。 */
const canDeliver = computed(() => {
  if (props.mode !== 'view') return false
  const currentUserId = userStore.currentUser?.id
  if (currentUserId == null) return false
  const taskAssigneeId = localValue.assigneeId
  if (taskAssigneeId == null) return false
  const taskStatus = String(localValue.status || '').toLowerCase()
  return String(taskAssigneeId) === String(currentUserId) && taskStatus === 'todo'
})
// 只读模式下已保存的附件（含 id），交给模板渲染为独立可下载链接。
// 历史问题：把已保存附件塞进 el-upload 的 file-list 后，el-upload 的 :disabled 会
// 把整个 #file 插槽（含 <a> 链接）置为 aria-disabled，点击不触发下载。
// 与交付物（.deliverable-link）保持同一思路：已保存的移出 el-upload，独立渲染。
const savedAttachments = computed(() =>
  (localValue.attachments || []).filter((file) => file?.id != null).map((file, i) => ({
    ...file,
    name: file?.name || `附件${i + 1}`,
    url: (file?.projectId || localValue.projectId) && file?.id
      ? `/api/projects/${file.projectId || localValue.projectId}/documents/${file.id}/download`
      : file?.url,
  }))
)
// el-upload 的 file-list 仅保留待上传的新文件（raw File），避免禁用态把已保存附件一起锁死。
const attachmentFileList = computed(() =>
  (localValue.attachments || [])
    .filter((file) => file instanceof File || file?.raw)
    .map((file, i) => ({
      name: file?.name || `附件${i + 1}`,
      raw: file instanceof File ? file : file?.raw,
    }))
)
// === 交付物下载 ===
function getDeliverableDownloadUrl(deliverable) {
  if (!deliverable?.id) return ''
  const projectId = deliverable.projectId || localValue.projectId
  const taskId = deliverable.taskId || localValue.id
  return getTaskDeliverableDownloadUrl(projectId, taskId, deliverable.id)
}

async function downloadDeliverable(deliverable) {
  const url = getDeliverableDownloadUrl(deliverable)
  if (!url) {
    ElMessage.info('文件地址不可用')
    return
  }
  await downloadWithFilename(url, deliverable?.name || 'download')
}

function handleDownloadAttachment(file) {
  if (!file?.id) {
    ElMessage.warning('文件信息缺失，无法下载')
    return
  }
  const projectId = file?.projectId || localValue.projectId
  downloadWithFilename(`/api/projects/${projectId}/documents/${file.id}/download`, file?.name || '附件')
}

// === 交付物表单 ===
const deliverableFileList = ref([])

function rebuildFileList() {
  const files = localValue.deliverableFiles
  if (files?.length) {
    const list = files.map((file, i) => ({ name: file?.name || `交付物${i + 1}`, raw: file }))
    const oldJson = JSON.stringify(deliverableFileList.value)
    const newJson = JSON.stringify(list)
    if (oldJson !== newJson) deliverableFileList.value = list
    return
  }
  if (deliverableFileList.value.length) deliverableFileList.value = []
}

rebuildFileList()
watch(() => localValue.deliverableFiles, rebuildFileList, { deep: true })

function handleDeliverableChange(file, fileList = []) {
  localValue.deliverableFiles = (Array.isArray(fileList) ? fileList : [fileList])
    .map((item) => item?.raw || item)
    .filter(Boolean)
}

function handleDeliverableRemove(_file, fileList = []) {
  localValue.deliverableFiles = (Array.isArray(fileList) ? fileList : [])
    .map((item) => item?.raw || item)
    .filter(Boolean)
}

let syncingFromModel = false

const { assigneeOptions, loadingAssignees, loadAssignees, ensureSelectedAssignee, handleAssigneeSelect } =
  useTaskAssigneePicker({ localValue, userStore })

const extendedFieldSchema = computed(() =>
  (projectStore.taskExtendedFields || []).map((f) => ({
    key: f.key,
    label: f.label,
    type: f.fieldType, // already lowercase from backend
    required: f.required,
    placeholder: f.placeholder,
    options: f.options, // already parsed array
  }))
)

watch(() => props.modelValue, (v) => {
  syncingFromModel = true
  Object.keys(localValue).forEach((k) => delete localValue[k])
  Object.assign(localValue, v || {})
  if (!localValue.extendedFields) localValue.extendedFields = {}
  if (!Array.isArray(localValue.attachments)) localValue.attachments = []
  ensureSelectedAssignee()
  nextTick(() => {
    syncingFromModel = false
  })
})

watch(localValue, () => {
  if (!syncingFromModel) {
    emit('update:modelValue', { ...localValue })
  }
}, { deep: true })

onMounted(() => {
  projectStore.loadTaskExtendedFields()
  ensureSelectedAssignee()
  // 创建模式时自动设置当前用户为创建人
  if (props.mode === 'create' && userStore.userName) {
    localValue.createdByName = userStore.userName
  }
  setTimeout(() => {
    loadAssignees()
  }, 0)
  loadStatuses()
})

async function loadStatuses() {
  loadingStatuses.value = true
  try {
    const res = await taskStatusDictApi.list()
    statuses.value = res?.data || []
    if (!localValue.status && statuses.value.length > 0) {
      const initialStatus = statuses.value.find((s) => s.initial) || statuses.value[0]
      localValue.status = initialStatus.code
    }
  } catch (err) {
    console.error('[TaskForm] Failed to load task status dict', err)
  } finally {
    loadingStatuses.value = false
  }
}
function validate() {
  if (!localValue.name || !String(localValue.name).trim()) {
    validationMessage.value = '请填写任务名称'
    return validationMessage.value
  }
  if (!localValue.assigneeId) {
    validationMessage.value = '请选择任务执行人'
    return validationMessage.value
  }
  if (!localValue.deadline) {
    validationMessage.value = '请选择截止日期'
    return validationMessage.value
  }
  validationMessage.value = ''
  return ''
}
function normalizeUploadFiles(fileList = []) {
  return (Array.isArray(fileList) ? fileList : [fileList])
    .map((item) => item?.raw || item)
    .filter(Boolean)
}
function handleAttachmentChange(file, fileList = []) {
  localValue.attachments = normalizeUploadFiles(fileList.length ? fileList : [file])
}
function handleAttachmentRemove(_file, fileList = []) {
  localValue.attachments = normalizeUploadFiles(fileList)
}

function removeAttachment(file) {
  const raw = file?.raw
  if (!raw) return
  localValue.attachments = localValue.attachments.filter((item) => item !== raw)
}

function submit() {
  const msg = validate()
  if (msg) return { valid: false, message: msg }
  // Extended fields — if any, validate via DynamicFormRenderer
  if (extendedFieldSchema.value.length > 0) {
    const extRes = extFormRef.value?.submit?.()
    if (extRes && extRes.valid === false) {
      return extRes // propagate {valid, message} from extended form
    }
  }
  emit('submit', { ...localValue })
  return { valid: true, data: { ...localValue } }
}

function submitForReview() {
  const msg = validate()
  if (msg) return { valid: false, message: msg }
  // 提交审核时强制目标状态为 REVIEW
  const data = { ...localValue, status: 'REVIEW' }
  emit('submit-review', data)
  return { valid: true, data }
}

defineExpose({ submit, submitForReview, validate, canDeliver })
</script>

<style scoped>
.task-form { width: 100%; }
.task-attachment-file-row { display: flex; align-items: center; gap: 8px; }
.deliverable-list { margin-top: 8px; }
.deliverable-link { display: block; color: #409eff; text-decoration: none; margin-bottom: 4px; }
.deliverable-link:hover { text-decoration: underline; }
.attachment-list { margin-top: 8px; }
.attachment-link { display: block; color: #409eff; text-decoration: none; margin-bottom: 4px; }
.attachment-link:hover { text-decoration: underline; }
.reject-reason-alert { margin-bottom: 16px; }
</style>
