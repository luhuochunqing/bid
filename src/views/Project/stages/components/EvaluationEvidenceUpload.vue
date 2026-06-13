<template>
  <div class="evidence-upload">
    <el-upload
      v-model:file-list="fileList"
      :action="uploadUrl"
      :headers="uploadHeaders"
      :accept="acceptedTypes"
      :before-upload="beforeUpload"
      drag
      multiple
      :limit="5"
      :on-success="handleUploadSuccess"
      :on-remove="handleUploadRemove"
      :on-error="handleUploadError"
      :disabled="!editable"
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">拖拽文件到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">开标一览表，支持 Word/PDF/Excel/图片等格式，单文件不超过 10MB，最多 5 个</div>
      </template>
    </el-upload>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user.js'
import { getApiUrl } from '@/api/config.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  existingDocIds: { type: Array, default: () => [] },
  editable: { type: Boolean, default: false },
})

const emit = defineEmits(['attached'])

const userStore = useUserStore()
const fileList = ref([])
const fileIds = ref([])
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

async function attachFile(fileId) {
  try {
    await projectLifecycleApi.attachEvaluationEvidence(props.projectId, { fileIds: [fileId] })
  } catch (e) {
    console.error('文件关联失败:', e)
  }
}

function handleUploadSuccess(response) {
  if (response?.data?.id) {
    fileIds.value.push(response.data.id)
    attachFile(response.data.id)
    emit('attached')
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
.evidence-upload :deep(.el-upload__tip) { text-align: left; }
</style>
