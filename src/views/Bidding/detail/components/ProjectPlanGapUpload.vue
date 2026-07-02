<template>
  <div class="gap-upload-wrapper">
    <textarea
      v-if="disabled"
      v-autosize
      :value="localGap || '-'"
      readonly
      class="readonly-textarea"
    />
    <el-input
      v-else
      v-model="localGap"
      type="textarea"
      :autosize="{ minRows: 3, maxRows: 10 }"
      placeholder="请填写项目计划差距（可选）"
      maxlength="5000"
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
        :key="file.id || file.fileUrl"
        class="gap-file-item"
      >
        <el-icon><Document /></el-icon>
        <el-link
          v-if="file.fileUrl"
          type="primary"
          :href="resolveFileUrl(file.fileUrl)"
          target="_blank"
          rel="noopener noreferrer"
          :title="file.name || file.fileName"
          class="gap-file-name"
        >
          {{ file.name || file.fileName || 'GAP附件' }}
        </el-link>
        <span v-else class="gap-file-name" :title="file.name || file.fileName">
          {{ file.name || file.fileName }}
        </span>
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

/**
 * CO-262: 解析附件 URL，支持外部绝对 URL（http/https）和相对路径（/api/...）。
 * - 绝对 URL（如 CRM 返回的 https://image-c.ehsy.com/...）：直接返回
 * - 相对路径（如 /api/tenders/.../documents/.../download）：拼接当前 origin
 * - 其他协议（javascript:、data: 等）：返回 '#' 防止 XSS
 */
function resolveFileUrl(fileUrl) {
  if (!fileUrl) return '#'
  if (/^https?:\/\//i.test(fileUrl)) return fileUrl
  if (fileUrl.startsWith('/')) return `${window.location.origin}${fileUrl}`
  // 防御深度：非 http(s):// 且非相对路径的 URL 一律拒绝，避免 javascript: 等协议注入
  return '#'
}

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

/* CO-262: el-link 在 flex 容器中需要限制宽度以触发省略号 */
.gap-file-name.el-link {
  max-width: 100%;
}

.gap-file-name :deep(.el-link__inner) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
  max-width: 100%;
}

.readonly-textarea {
  width: 100%;
  min-height: 72px;
  padding: 5px 11px;
  border: 1px solid var(--gray-100, #E8E8E8);
  border-radius: 6px;
  font-family: inherit;
  font-size: inherit;
  line-height: 1.5;
  color: var(--text-primary-ui, #303133);
  background: var(--bg-subtle, #F5F7FA);
  resize: vertical;
  overflow-y: auto;
}
</style>
