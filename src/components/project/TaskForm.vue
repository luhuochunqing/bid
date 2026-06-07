<template>
  <div class="task-form">
    <el-tabs v-model="activeTab" class="task-form-tabs">
      <el-tab-pane label="详情" name="detail">
        <el-form :model="localValue" label-width="110px" :disabled="readonly">
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
              data-test="task-attachment-upload"
              :auto-upload="false"
              :file-list="attachmentFileList"
              :disabled="readonly"
              accept=".doc,.docx,.pdf,.xls,.xlsx"
              multiple
              @change="handleAttachmentChange"
              @remove="handleAttachmentRemove"
            >
              <el-button :icon="Upload" :disabled="readonly">添加附件</el-button>
              <template #tip>
                <div class="attachment-tip">保存任务后上传到该任务</div>
              </template>
            </el-upload>
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
            <el-select
              v-model="localValue.assigneeId"
              data-test="task-owner-select"
              filterable
              style="width: 100%"
              placeholder="请选择任务执行人"
              :loading="loadingAssignees"
              @change="handleAssigneeChange"
            >
              <el-option
                v-for="person in assigneeOptions"
                :key="person.userId"
                :label="assigneeLabel(person)"
                :value="person.userId"
              >
                <div class="assignee-option">
                  <span>{{ person.name }}</span>
                  <small>{{ person.deptName || '未配置部门' }} · {{ person.roleName || '未配置角色' }}</small>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="截止日期">
            <el-date-picker v-model="localValue.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>

          <el-form-item label="交付物上传">
            <el-upload
              data-test="task-deliverable-upload"
              :auto-upload="false"
              :file-list="deliverableFileList"
              :disabled="readonly"
              multiple
              @change="handleDeliverableChange"
              @remove="handleDeliverableRemove"
            >
              <el-button :icon="Upload" :disabled="readonly">上传交付物</el-button>
              <template #tip>
                <div class="attachment-tip">任务执行人上传交付物</div>
              </template>
            </el-upload>
          </el-form-item>

          <el-form-item label="完成情况说明">
            <el-input
              v-model="localValue.completionNote"
              type="textarea"
              :rows="4"
              placeholder="请填写完成情况说明（可选）"
              :disabled="readonly"
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
            <el-select v-model="localValue.status" style="width: 100%" :loading="loadingStatuses">
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
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import DynamicFormRenderer from '@/components/common/DynamicFormRenderer.vue'
import TaskActivityPanel from '@/components/project/TaskActivityPanel.vue'
import { useTaskAssigneeOptions } from './useTaskAssigneeOptions.js'
import { useTaskDeliveryForm } from './useTaskDeliveryForm.js'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'create' }, // create | edit | view
})
const emit = defineEmits(['submit', 'update:modelValue'])

const projectStore = useProjectStore()
const userStore = useUserStore()

const localValue = reactive({ ...props.modelValue })
if (!localValue.extendedFields) {
  localValue.extendedFields = {}
}
if (!Array.isArray(localValue.attachments)) {
  localValue.attachments = []
}
const statuses = ref([])
const loadingStatuses = ref(false)
const validationMessage = ref('')
const extFormRef = ref(null)
const activeTab = ref('detail')
const readonly = computed(() => props.mode === 'view')
const attachmentFileList = computed(() => localValue.attachments.map((file, index) => ({
  name: file?.name || `附件${index + 1}`,
  raw: file,
})))

const { deliverableFileList, handleDeliverableChange, handleDeliverableRemove } =
  useTaskDeliveryForm(localValue, readonly)

let syncingFromModel = false
const { assigneeOptions, loadingAssignees, loadAssignees, ensureSelectedAssignee, handleAssigneeChange, assigneeLabel } =
  useTaskAssigneeOptions({ localValue, isCreateMode: () => props.mode === 'create', userStore })

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
  if (!localValue.extendedFields) {
    localValue.extendedFields = {}
  }
  if (!Array.isArray(localValue.attachments)) {
    localValue.attachments = []
  }
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

defineExpose({ submit, validate })
</script>

<style scoped>
.task-form { width: 100%; }

.assignee-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.assignee-option small {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.4;
}
</style>
