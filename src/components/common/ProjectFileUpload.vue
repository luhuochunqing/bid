<template>
  <el-upload :with-credentials="true"
    ref="uploadRef"
    :action="uploadUrl"
    :headers="uploadHeaders"
    :accept="accept"
    :multiple="multiple"
    :limit="limit"
    :drag="drag"
    :disabled="disabled"
    :file-list="fileList"
    :auto-upload="autoUpload"
    :show-file-list="showFileList"
    :before-upload="handleBeforeUpload"
    :on-success="handleOnSuccess"
    :on-error="handleOnError"
    :on-remove="handleOnRemove"
    :on-exceed="handleOnExceed"
    v-bind="$attrs"
  >
    <template v-if="$slots.default">
      <slot />
    </template>
    <template v-else>
      <el-icon v-if="drag" class="el-icon--upload"><UploadFilled /></el-icon>
      <div v-if="drag" class="el-upload__text">
        拖拽文件到此处，或<em>点击上传</em>
      </div>
      <el-button v-else type="primary" :icon="Upload">上传文件</el-button>
    </template>
    <template #tip>
      <slot name="tip">
        <div v-if="tipText" class="el-upload__tip">{{ tipText }}</div>
      </slot>
    </template>
  </el-upload>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Upload, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getUploadUrl, getUploadHeaders } from '@/api/upload.js'

const props = defineProps({
  businessType: { type: String, required: true },
  businessId: { type: [String, Number], default: null },
  accept: { type: String, default: '.pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx' },
  multiple: { type: Boolean, default: true },
  limit: { type: Number, default: 5 },
  drag: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  maxSizeMb: { type: Number, default: 10 },
  autoUpload: { type: Boolean, default: true },
  showFileList: { type: Boolean, default: true },
  fileList: { type: Array, default: () => [] },
  allowedMimes: { type: Array, default: null },
})

const emit = defineEmits(['success', 'error', 'remove', 'before-upload'])
const uploadRef = ref(null)

const uploadUrl = computed(() => getUploadUrl(props.businessType, props.businessId))
const uploadHeaders = computed(() => getUploadHeaders())

const DEFAULT_MIME_MAP = {
  '.pdf': 'application/pdf', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.png': 'image/png', '.doc': 'application/msword',
  '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  '.xls': 'application/vnd.ms-excel',
  '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
}

const tipText = computed(() => {
  const labels = props.accept.split(',').map((e) => e.trim().toUpperCase().replace(/^\./, '')).join('/')
  return `支持 ${labels} 格式，单文件不超过 ${props.maxSizeMb}MB，最多 ${props.limit} 个`
})

function getMimeFromExt(filename) {
  const ext = '.' + (filename.split('.').pop() || '').toLowerCase()
  return DEFAULT_MIME_MAP[ext]
}

function handleBeforeUpload(file) {
  if (props.allowedMimes && props.allowedMimes.length > 0) {
    const fileMime = file.type || getMimeFromExt(file.name)
    if (!fileMime || !props.allowedMimes.includes(fileMime)) {
      ElMessage.error(`不支持的文件类型: ${file.name}`)
      return false
    }
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    ElMessage.error(`文件"${file.name}"超过 ${props.maxSizeMb}MB 限制`)
    return false
  }
  emit('before-upload', file)
  return true
}

function handleOnSuccess(response, file, fileList) { emit('success', response, file, fileList) }
function handleOnError(error, file, fileList) { console.error('[ProjectFileUpload]', error); ElMessage.error(`上传失败: ${file.name}`); emit('error', error, file, fileList) }
function handleOnRemove(uploadFile, uploadFileList) { emit('remove', uploadFile, uploadFileList) }
function handleOnExceed() { ElMessage.warning(`最多上传 ${props.limit} 个文件，已超出`) }
function getElUploadRef() { return uploadRef.value }

defineExpose({ getElUploadRef })
</script>
