<template>
  <div class="gap-upload-wrapper">
    <el-input
      v-model="localGap"
      type="textarea"
      :rows="3"
      placeholder="请填写项目计划差距（可选）"
      maxlength="5000"
      :readonly="disabled"
    />
    <div v-if="!disabled" class="gap-file-upload">
      <el-upload :with-credentials="true"
        :file-list="localFiles"
        :action="gapUploadUrl"
        :headers="gapUploadHeaders"
        :before-upload="beforeGapUpload"
        :on-success="(res) => handleGapFileSuccess(res)"
        :on-remove="(file) => handleGapFileRemove(file)"
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
      <div
        v-for="file in localFiles"
        :key="file.id"
        class="gap-file-item"
      >
        <el-icon><Document /></el-icon>
        <span class="gap-file-name" :title="file.name">{{ file.name || file.fileName }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user.js'
import { tendersApi } from '@/api/index.js'

const props = defineProps({
  modelValue: { type: Object, required: true },
  tenderId: { type: Number, required: true },
  disabled: { type: Boolean, default: false },
})

const userStore = useUserStore()

const localGap = computed({
  get: () => props.modelValue.projectPlanGap ?? '',
  set: (v) => { props.modelValue.projectPlanGap = v },
})

const localFiles = computed(() => props.modelValue.projectPlanGapFiles ?? [])

const gapUploadUrl = computed(() => `/api/tenders/${props.tenderId}/evaluation/documents`)
const gapUploadHeaders = computed(() => {
  const token = userStore?.token
  return token ? { Authorization: `Bearer ${token}` } : {}
})

function handleGapFileSuccess(response) {
  if (response?.data) {
    props.modelValue.projectPlanGapFiles.push(response.data)
    ElMessage.success('附件上传成功')
  }
}

async function handleGapFileRemove(file) {
  const idx = (props.modelValue.projectPlanGapFiles || []).findIndex(
    (f) => f.id === file.id || f.uid === file.uid
  )
  if (idx !== -1) {
    const doc = props.modelValue.projectPlanGapFiles[idx]
    if (doc?.id) {
      try {
        await tendersApi.deleteEvaluationDocument(props.tenderId, doc.id)
        ElMessage.success('附件已删除')
      } catch {
        ElMessage.error('删除附件失败')
      }
    }
    props.modelValue.projectPlanGapFiles.splice(idx, 1)
  }
}

function beforeGapUpload(file) {
  const maxSize = 10 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}
</script>

<style scoped>
/* 根容器：统一宽度约束，和 BasicFieldsSection 里的数字输入框/textarea 保持一致 */
.gap-upload-wrapper {
  width: 100%;
  max-width: 360px;
}

/* 禁用态下也要让文字可读 */
.gap-upload-wrapper :deep(.el-textarea) {
  width: 100%;
}

.gap-upload-wrapper :deep(.el-textarea__inner) {
  min-height: 72px !important;
}

.gap-file-upload {
  margin-top: 8px;
}

.gap-upload {
  line-height: 1;
}

.gap-file-list {
  margin-top: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.gap-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}

.gap-file-item:last-child {
  border-bottom: none;
}

.gap-file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
