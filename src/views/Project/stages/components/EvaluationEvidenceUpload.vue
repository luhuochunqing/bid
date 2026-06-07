<template>
  <div class="evidence-upload">
    <div class="upload-header">
      <span class="upload-label">开标一览表</span>
      <span class="upload-tag-hint">打上标签：开标一览表</span>
    </div>
    <el-upload
      v-model:file-list="fileList"
      :action="uploadUrl"
      :headers="uploadHeaders"
      :accept="acceptedTypes"
      :before-upload="beforeUpload"
      multiple
      :limit="5"
      :on-success="handleUploadSuccess"
      :on-remove="handleUploadRemove"
      :on-error="handleUploadError"
      :disabled="!editable"
    >
      <el-button type="primary" :disabled="!editable">上传开标一览表</el-button>
      <template #tip>
        <div class="el-upload__tip">支持 Word/PDF/Excel/图片等格式，单文件不超过 10MB，最多 5 个</div>
      </template>
    </el-upload>
    <el-button
      v-if="!hideAttachButton && fileIds.length > 0 && editable"
      type="primary"
      size="small"
      :loading="attaching"
      style="margin-top: 12px"
      @click="handleAttach"
    >
      保存文件关联
    </el-button>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user.js'
import { getApiUrl } from '@/api/config.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  existingDocIds: { type: Array, default: () => [] },
  editable: { type: Boolean, default: false },
  hideAttachButton: { type: Boolean, default: false }
})

const emit = defineEmits(['attached'])

const userStore = useUserStore()
const fileList = ref([])
const fileIds = ref([])
const attaching = ref(false)
const uploadUrl = computed(() => getApiUrl(`/api/projects/${props.projectId}/documents`))
const acceptedTypes = '.pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png'
const MAX_FILE_SIZE_MB = 10
const ALLOWED_MIMES = [
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'image/jpeg', 'image/jpg', 'image/png'
]

const uploadHeaders = computed(() => {
  const token = userStore?.token
  return token ? { Authorization: `Bearer ${token}` } : {}
})

function beforeUpload(file) {
  if (!ALLOWED_MIMES.includes(file.type)) {
    ElMessage.error(`不支持的文件类型: ${file.type || '未知'}`)
    return false
  }
  if (file.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
    ElMessage.error(`文件不能超过 ${MAX_FILE_SIZE_MB}MB`)
    return false
  }
  return true
}

function handleUploadSuccess(response) {
  if (response?.data?.id) {
    fileIds.value.push(response.data.id)
  }
}

function handleUploadRemove(uploadFile) {
  const idx = fileIds.value.indexOf(uploadFile.response?.data?.id)
  if (idx > -1) fileIds.value.splice(idx, 1)
}

function handleUploadError(err) {
  const msg = err?.response?.data?.msg || err?.message || '上传失败'
  ElMessage.error('开标一览表上传失败: ' + msg)
}

async function handleAttach() {
  if (!fileIds.value.length) return ElMessage.warning('请先上传文件')
  attaching.value = true
  try {
    await projectLifecycleApi.attachEvaluationEvidence(props.projectId, {
      fileIds: fileIds.value
    })
    ElMessage.success('评标文件已关联')
    emit('attached')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '文件关联失败')
  } finally {
    attaching.value = false
  }
}

function getPendingFileIds() {
  return fileIds.value
}

function clearPendingFileIds() {
  fileIds.value = []
}

defineExpose({ getPendingFileIds, clearPendingFileIds })
</script>

<style scoped>
.evidence-upload { text-align: center; }
.upload-header {
  margin-bottom: 12px;
}
.upload-label {
  font-size: 13px;
  font-weight: 500;
  color: #333;
}
.upload-tag-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 8px;
}
</style>
