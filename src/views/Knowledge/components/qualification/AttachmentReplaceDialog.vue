<template>
  <el-dialog
    v-model="visible"
    :title="currentFileName ? '替换附件' : '上传附件'"
    width="520px"
    @close="handleClose"
    data-testid="att-replace-dialog"
  >
    <div class="replace-body">
      <div v-if="currentFileName" class="current-file-card" data-testid="current-file">
        <el-icon><Document /></el-icon>
        <span class="file-name">{{ currentFileName }}</span>
      </div>

      <el-upload
        v-if="!selectedFile"
        ref="uploadRef"
        drag
        action="#"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleFileChange"
        accept=".pdf,.jpg,.jpeg,.png"
        class="replace-upload"
        data-testid="replace-upload"
      >
        <el-icon :size="32"><Upload /></el-icon>
        <div class="upload-text">
          <span>点击选择文件</span>
          <span class="upload-or">或拖拽到此处</span>
        </div>
      </el-upload>

      <div v-else class="selected-file-card" data-testid="selected-file">
        <el-icon><Document /></el-icon>
        <span class="file-name">{{ selectedFile.name }}</span>
        <el-button link type="danger" size="small" @click="clearFile">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>

      <el-alert
        v-if="selectedFile"
        title="确认后，原附件将被替换并归档至操作日志，无法在前台恢复"
        type="warning"
        :closable="false"
        show-icon
        class="replace-warn"
        data-testid="replace-warn"
      />
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :disabled="!selectedFile"
        :loading="submitting"
        data-testid="replace-confirm"
        @click="handleConfirm"
      >
        确认替换
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Upload, Close } from '@element-plus/icons-vue'
import http from '@/api/client'

const props = defineProps({
  modelValue: Boolean,
  qualificationId: { type: [String, Number], default: null },
  currentFileName: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = ref(false)
const selectedFile = ref(null)
const submitting = ref(false)
const uploadRef = ref(null)

watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { if (!v) emit('update:modelValue', false) })

const handleFileChange = (f) => {
  if (!f.raw) return
  if (f.raw.size > 10485760) {
    ElMessage.error('附件不能超过10MB')
    return
  }
  selectedFile.value = f.raw
}

const clearFile = () => {
  selectedFile.value = null
  if (uploadRef.value) uploadRef.value.clearFiles()
}

const handleClose = () => {
  selectedFile.value = null
  visible.value = false
}

const handleConfirm = async () => {
  if (!selectedFile.value || !props.qualificationId) return
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    await http.post(`/api/knowledge/qualifications/${props.qualificationId}/upload`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('附件替换成功')
    emit('success')
    handleClose()
  } catch {
    ElMessage.error('附件替换失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.replace-body { padding: 8px 0; }
.current-file-card {
  display: flex; align-items: center; gap: 8px;
  padding: 12px; border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px; margin-bottom: 16px; background: var(--el-fill-color-light);
  .file-name { font-size: 14px; color: var(--el-text-color-primary); }
}
.replace-upload {
  :deep(.el-upload-dragger) {
    padding: 32px 0;
    .upload-text { margin-top: 8px; display: flex; flex-direction: column; gap: 4px; }
    .upload-or { font-size: 12px; color: var(--el-text-color-secondary); }
  }
}
.selected-file-card {
  display: flex; align-items: center; gap: 8px;
  padding: 12px; border: 1px solid var(--el-color-primary-light-7);
  border-radius: 6px; margin-bottom: 16px; background: var(--el-color-primary-light-9);
  .file-name { flex: 1; font-size: 14px; color: var(--el-text-color-primary); }
}
.replace-warn { margin-top: 8px; }
</style>
