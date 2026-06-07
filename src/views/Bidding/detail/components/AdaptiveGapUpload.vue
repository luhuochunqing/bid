<template>
  <div>
    <el-input
      v-model="localGap"
      type="textarea"
      :rows="3"
      placeholder="请填写项目计划差距（可选）"
      maxlength="5000"
    />
    <div v-if="gapUploadUrl" class="gap-file-upload">
      <el-upload
        :file-list="localFiles"
        :action="gapUploadUrl"
        :headers="gapUploadHeaders"
        :before-upload="beforeGapUpload"
        :on-success="handleGapFileSuccess"
        :on-remove="handleGapFileRemove"
        multiple
        drag
        accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
        :limit="5"
      >
        <el-button size="small" type="primary">上传附件</el-button>
        <template #tip>
          <div class="el-upload__tip">支持拖拽上传，最多5个文件，单个不超过10MB</div>
        </template>
      </el-upload>
    </div>
    <div v-else-if="localFiles.length" class="gap-file-list">
      <div v-for="file in localFiles" :key="file.id" class="gap-file-item">
        <el-icon><Document /></el-icon>
        <span>{{ file.name || file.fileName || '附件' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user.js'

const props = defineProps({
  modelValue: { type: Object, required: true },
  tenderId: { type: Number, default: null },
})

const userStore = useUserStore()

const localGap = computed({
  get: () => props.modelValue.projectPlanGap ?? '',
  set: (v) => { props.modelValue.projectPlanGap = v },
})

const localFiles = computed(() => props.modelValue.projectPlanGapFiles ?? [])

const gapUploadUrl = computed(() =>
  props.tenderId ? `/api/tenders/${props.tenderId}/evaluation/documents` : ''
)
const gapUploadHeaders = computed(() => {
  const token = userStore?.token
  return token ? { Authorization: `Bearer ${token}` } : {}
})

function handleGapFileSuccess(response) {
  if (response?.data) {
    if (!Array.isArray(props.modelValue.projectPlanGapFiles)) {
      props.modelValue.projectPlanGapFiles = []
    }
    props.modelValue.projectPlanGapFiles.push(response.data)
  }
}

function handleGapFileRemove(file) {
  if (!Array.isArray(props.modelValue.projectPlanGapFiles)) return
  const idx = props.modelValue.projectPlanGapFiles.findIndex(
    (f) => f.id === file.id || f.uid === file.uid
  )
  if (idx !== -1) props.modelValue.projectPlanGapFiles.splice(idx, 1)
}

function beforeGapUpload(file) {
  const maxSize = 10 * 1024 * 1024
  if (file.size > maxSize) {
    import('element-plus').then(({ ElMessage }) => ElMessage.error('文件大小不能超过 10MB'))
    return false
  }
  return true
}
</script>

<style scoped>
.gap-file-upload {
  margin-top: 8px;
}

.gap-file-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.gap-file-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
