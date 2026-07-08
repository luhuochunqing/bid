<template>
  <div class="obs-upload-progress" v-if="visible">
    <div class="obs-upload-progress__header">
      <span class="obs-upload-progress__filename">{{ fileName }}</span>
      <span class="obs-upload-progress__status" :class="statusClass">{{ statusText }}</span>
    </div>
    <el-progress
      :percentage="progressPercent"
      :status="progressStatus"
      :stroke-width="8"
      :show-text="true"
    />
    <div class="obs-upload-progress__info">
      <span>{{ formattedSize }}</span>
      <el-button v-if="uploading" text size="small" @click="onCancel">取消</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  fileName: { type: String, default: '' },
  fileSize: { type: Number, default: 0 },
  progressPercent: { type: Number, default: 0 },
  uploading: { type: Boolean, default: false },
  hasError: { type: Boolean, default: false },
})

const emit = defineEmits(['cancel'])

const progressStatus = computed(() => {
  if (props.hasError) return 'exception'
  if (props.progressPercent >= 100) return 'success'
  return undefined
})

const statusClass = computed(() => {
  if (props.hasError) return 'is-error'
  if (props.progressPercent >= 100) return 'is-success'
  if (props.uploading) return 'is-uploading'
  return ''
})

const statusText = computed(() => {
  if (props.hasError) return '上传失败'
  if (props.progressPercent >= 100) return '完成'
  if (props.uploading) return '上传中'
  return '等待中'
})

const formattedSize = computed(() => {
  if (!props.fileSize) return ''
  const mb = props.fileSize / (1024 * 1024)
  if (mb < 1) return `${(props.fileSize / 1024).toFixed(1)} KB`
  if (mb < 1024) return `${mb.toFixed(1)} MB`
  return `${(mb / 1024).toFixed(2)} GB`
})

function onCancel() {
  emit('cancel')
}
</script>

<style scoped>
.obs-upload-progress {
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-bg-color);
}

.obs-upload-progress__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.obs-upload-progress__filename {
  font-size: 14px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}

.obs-upload-progress__status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}

.obs-upload-progress__status.is-uploading {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.obs-upload-progress__status.is-success {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.obs-upload-progress__status.is-error {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.obs-upload-progress__info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
