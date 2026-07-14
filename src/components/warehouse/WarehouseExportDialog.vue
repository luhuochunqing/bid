<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="导出台账（含附件）"
    width="560px"
    :close-on-click-modal="false"
    :before-close="handleClose"
    data-testid="warehouse-export-dialog"
  >
    <div v-if="!taskId" class="export-init">
      <el-form :model="form" label-width="92px">
        <el-form-item label="导出范围">
          <el-radio-group v-model="form.scope" class="scope-group">
            <el-radio value="filter" class="scope-radio">
              <span>当前筛选结果</span>
              <el-tag size="small" type="info" class="scope-count">{{ filterCount }}</el-tag>
            </el-radio>
            <el-radio value="ids" :disabled="selectedIds.length === 0" class="scope-radio">
              <span>当前勾选的仓库</span>
              <el-tag size="small" type="info" class="scope-count">{{ selectedIds.length }}</el-tag>
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div class="attachment-scope-section">
        <div class="section-label">附件导出范围</div>
        <el-radio-group v-model="form.attachmentScope" class="scope-radio-group">
          <el-radio value="ALL">全部文件导出</el-radio>
          <el-radio value="PARTIAL">部分文件导出</el-radio>
        </el-radio-group>
        <el-checkbox-group v-if="form.attachmentScope === 'PARTIAL'" v-model="form.attachmentTypes" class="type-checkbox-group">
          <el-checkbox value="PROPERTY_CERTIFICATE">产权证</el-checkbox>
          <el-checkbox value="INVOICE">发票</el-checkbox>
          <el-checkbox value="PHOTOS">照片</el-checkbox>
          <el-checkbox value="LEASE_CONTRACT">租赁合同</el-checkbox>
        </el-checkbox-group>
        <div v-if="validation.message" class="scope-hint">
          {{ validation.message }}
        </div>
      </div>
      <div class="attachment-forms-section" data-testid="attachment-forms-section">
        <div class="section-label">附件组织形式</div>
        <el-checkbox-group v-model="form.attachmentForms" class="forms-checkbox-group">
          <el-checkbox value="ATTACHMENTS_FOLDER">附件文件夹</el-checkbox>
          <el-checkbox value="WORD_COMBINED">Word 合订本</el-checkbox>
        </el-checkbox-group>
      </div>
    </div>
    <div v-else class="export-task">
      <div v-if="isRunning" class="export-progress">
        <el-progress :percentage="status === 'PROCESSING' ? 60 : 20" :stroke-width="12" striped :striped-flow="true" />
        <p class="status-text">{{ status === 'PENDING' ? '导出任务排队中...' : '正在打包 ZIP（含附件），请稍候...' }}</p>
      </div>
      <div v-else-if="isCompleted" class="export-done">
        <el-result icon="success" title="📤 仓库信息导出包 — 完成" :sub-title="`共 ${totalCount} 条记录`">
          <template #extra>
            <el-button type="primary" @click="handleDownload"><el-icon><Download /></el-icon> 下载文件包</el-button>
          </template>
        </el-result>
        <div class="package-detail">
          <div class="detail-title">📦 ZIP 包内容</div>
          <ul class="detail-list">
            <li>仓库信息台账.xlsx（{{ totalCount }} 条，24 列含系统字段）</li>
            <li v-if="hasAttachments && form.attachmentForms.includes('ATTACHMENTS_FOLDER')">attachments/</li>
            <li v-if="form.attachmentForms.includes('ATTACHMENTS_FOLDER') && summary.propertyCertCount" class="indent">产权证 {{ summary.propertyCertCount }} 份</li>
            <li v-if="form.attachmentForms.includes('ATTACHMENTS_FOLDER') && summary.invoiceCount" class="indent">发票 {{ summary.invoiceCount }} 份</li>
            <li v-if="form.attachmentForms.includes('ATTACHMENTS_FOLDER') && summary.photosCount" class="indent">照片 {{ summary.photosCount }} 张</li>
            <li v-if="form.attachmentForms.includes('ATTACHMENTS_FOLDER') && summary.leaseContractCount" class="indent">租赁合同 {{ summary.leaseContractCount }} 份</li>
            <li v-if="form.attachmentForms.includes('WORD_COMBINED')">仓库附件合订本.docx</li>
          </ul>
          <div class="meta-row"><span class="meta-label">导出范围：</span><span>{{ summary.filterSummary || '—' }}</span></div>
          <div class="meta-row"><span class="meta-label">附件范围：</span><span>{{ summary.attachmentScope || '—' }}</span></div>
          <div class="meta-row"><span class="meta-label">处理耗时：</span><span>{{ formatElapsed(summary.elapsedMs) }}</span></div>
          <div class="meta-row"><span class="meta-label">包大小：</span><span>{{ formatBytes(summary.zipBytes) }}</span></div>
          <div class="meta-row"><span class="meta-label">链接有效期：</span><span>7 天</span></div>
        </div>
      </div>
      <div v-else-if="isFailed" class="export-failed">
        <el-result icon="error" title="导出失败" :sub-title="failureReason || '未知原因'">
          <template #extra>
            <el-button @click="handleRetry">重新导出</el-button>
          </template>
        </el-result>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <span v-if="!taskId" class="footer-hint">点击"开始导出"以提交导出任务</span>
        <span v-else-if="!isCompleted" class="footer-hint">关闭后仍可稍后在导出记录中下载</span>
        <el-button v-if="!taskId" type="primary" :disabled="!validation.valid" @click="handleStart">开始导出</el-button>
        <el-button @click="handleClose">{{ isCompleted ? '关闭' : '取消' }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { watch, computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import http from '@/api/client'
import { useAsyncTask } from '@/composables/useAsyncTask'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  filter: { type: Object, default: () => ({}) },
  filterCount: { type: Number, default: 0 },
  selectedIds: { type: Array, default: () => [] },
  defaultScope: { type: String, default: 'filter' }
})
const emit = defineEmits(['update:modelValue'])

const form = reactive({
  scope: props.defaultScope,
  attachmentScope: 'ALL',
  attachmentTypes: [],
  attachmentForms: ['WORD_COMBINED']
})

const {
  taskId, status, totalCount, failureReason, summary,
  isRunning, isCompleted, isFailed,
  startTask, reset: resetTask, retry, downloadFile, stopPolling
} = useAsyncTask({
  submitFn: async () => {
    const payload = form.scope === 'ids'
      ? { ids: props.selectedIds }
      : { ...props.filter }
    payload.attachmentScope = form.attachmentScope
    if (form.attachmentScope === 'PARTIAL') {
      payload.attachmentTypes = [...form.attachmentTypes]
    }
    payload.attachmentForms = [...form.attachmentForms]
    const { data } = await http.post('/api/knowledge/warehouses/export', payload)
    return data
  },
  statusUrl: '/api/knowledge/warehouses/export/tasks/:id/status',
  downloadUrl: '/api/knowledge/warehouses/export/tasks/:id/download',
  httpGet: http.get
})

const hasAttachments = computed(() => {
  const s = summary.value || {}
  return (s.propertyCertCount || 0) + (s.invoiceCount || 0) + (s.photosCount || 0) + (s.leaseContractCount || 0) > 0
})

const validation = computed(() => {
  if (form.attachmentForms.length === 0) {
    return { valid: false, message: '请至少选择一种附件组织形式' }
  }
  if (form.attachmentScope === 'PARTIAL' && form.attachmentTypes.length === 0) {
    return { valid: false, message: '请至少选择一种附件类型' }
  }
  if (form.scope === 'ids' && (!props.selectedIds || props.selectedIds.length === 0)) {
    return { valid: false, message: '请先在列表中勾选要导出的仓库' }
  }
  return { valid: true, message: '' }
})

const resetForm = () => {
  resetTask()
  form.scope = props.defaultScope
  form.attachmentScope = 'ALL'
  form.attachmentTypes = []
  form.attachmentForms = ['WORD_COMBINED']
}

const handleStart = async () => {
  try {
    await startTask()
  } catch {
    ElMessage.error('创建导出任务失败')
  }
}

const handleDownload = () => {
  downloadFile(summary.value?.fileName, () => {
    return `仓库信息导出包_${new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)}.zip`
  }).catch(() => {
    ElMessage.error('下载失败')
  })
}

const handleRetry = () => retry()

const handleClose = () => {
  stopPolling()
  resetForm()
  emit('update:modelValue', false)
}

const formatElapsed = (ms) => {
  if (!ms || ms <= 0) return '—'
  if (ms < 1000) return `${ms} 毫秒`
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s} 秒`
  const m = Math.floor(s / 60)
  return `${m} 分 ${s % 60} 秒`
}

const formatBytes = (bytes) => {
  if (!bytes || bytes <= 0) return '—'
  const units = ['B', 'KB', 'MB', 'GB']
  let v = bytes
  let i = 0
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++ }
  return `${v.toFixed(i > 0 ? 2 : 0)} ${units[i]}`
}

watch(() => props.modelValue, (v) => { if (v) resetForm() })
</script>

<style scoped>
.export-init { padding: 8px 0; }
.scope-group { display:flex; flex-direction:column; gap:8px; }
.scope-radio { white-space:nowrap; margin-right:0; display:flex; align-items:center; gap:8px; }
.scope-radio :deep(.el-radio__label) { display:flex; align-items:center; gap:8px; padding-left:0; }
.scope-count { font-weight:500; }
.export-progress { padding: 24px 0; text-align: center; }
.status-text { margin-top: 12px; color: var(--el-text-color-secondary); font-size: 14px; }
.export-done, .export-failed { padding: 8px 0; }
.package-detail { margin-top: 12px; padding: 14px; background: var(--gray-50); border-radius: 6px; font-size: 13px; }
.detail-title { font-weight: 600; color: var(--text-primary-ui); margin-bottom: 8px; }
.detail-list { margin: 0 0 12px; padding-left: 18px; line-height: 1.9; }
.detail-list .indent { list-style: none; margin-left: -12px; color: var(--el-text-color-secondary); }
.meta-row { line-height: 1.9; color: var(--el-text-color-regular); }
.meta-label { display: inline-block; min-width: 88px; color: var(--el-text-color-secondary); }
.dialog-footer { display: flex; justify-content: space-between; align-items: center; }
.footer-hint { font-size: 12px; color: var(--el-text-color-placeholder); }
.attachment-scope-section { margin-top: 20px; padding: 14px; background: var(--gray-50); border-radius: 6px; }
.attachment-forms-section { margin-top: 14px; padding: 14px; background: var(--gray-50); border-radius: 6px; }
.section-label { font-size: 13px; font-weight: 600; color: var(--text-primary-ui); margin-bottom: 10px; }
.scope-radio-group { display: flex; flex-direction: column; gap: 8px; }
.type-checkbox-group { margin-top: 10px; padding-left: 8px; display: flex; flex-direction: column; gap: 6px; }
.forms-checkbox-group { display: flex; flex-direction: column; gap: 6px; }
.scope-hint { margin-top: 8px; font-size: 12px; color: var(--el-color-danger); }
</style>
