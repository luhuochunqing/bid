<template>
  <div class="evidence-upload">
    <el-upload :with-credentials="true"
      v-model:file-list="fileList"
      :action="uploadUrl"
      :headers="uploadHeaders"
      :data="{ documentCategory: 'OPEN_LIST' }"
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
      <template #file="{ file }">
        <div class="evaluation-file-row">
          <a href="javascript:void(0)" class="upload-file-link" @click.prevent="handleDownloadFile(file)">{{ file.name }}</a>
          <el-button
            v-if="editable"
            link
            type="danger"
            size="small"
            @click.prevent="handleEvaluationFileRemove(file)"
          >删除</el-button>
        </div>
      </template>
    </el-upload>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user.js'
import { getApiUrl } from '@/api/config.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { downloadWithFilename } from '@/utils/download.js'
import { getDocuments } from '@/api/modules/projectDocuments.js'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  existingDocIds: { type: Array, default: () => [] },
  editable: { type: Boolean, default: false },
})

const emit = defineEmits(['attached'])

const userStore = useUserStore()
const fileList = ref([])
const fileIds = ref([])

// CO-408: existingDocIds 变化时根据 ids 拉取项目文档并回填 fileList，避免再次进入页面时文件名丢失
watch(() => props.existingDocIds, async (ids) => {
  if (!ids?.length) { fileList.value = []; return }
  try {
    const r = await getDocuments(props.projectId)
    const docs = r?.data || r || []
    const docMap = new Map(docs.map(d => [Number(d.id), d]))
    fileList.value = ids
      .map(id => docMap.get(Number(id)))
      .filter(Boolean)
      .map(doc => ({
        name: doc.name || '评标文件',
        url: doc.fileUrl || '',
        response: { data: doc },
        status: 'success',
      }))
  } catch (e) { console.error('回填评标文件失败:', e) }
}, { immediate: true })
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

function handleEvaluationFileRemove(file) { handleUploadRemove(file); fileList.value = fileList.value.filter((item) => item !== file) }

function handleUploadError(err) {
  const msg = err?.response?.data?.msg || err?.message || '上传失败'
  ElMessage.error('开标一览表上传失败: ' + msg)
}

// CO-375: 开标一览表文件下载
function handleDownloadFile(file) {
  const documentId = file.response?.data?.id
  if (!documentId) { ElMessage.warning('文件信息缺失，无法下载'); return }
  const url = `/api/projects/${props.projectId}/documents/${documentId}/download`
  downloadWithFilename(url, file.name || '开标一览表')
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
.upload-file-link { color: var(--el-color-primary); text-decoration: none; }
.upload-file-link:hover { text-decoration: underline; }
.evaluation-file-row { display: flex; align-items: center; gap: 8px; }
</style>
