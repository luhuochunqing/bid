<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="导出台账"
    width="560px"
    :close-on-click-modal="false"
    :before-close="handleClose"
    data-testid="warehouse-export-dialog"
  >
    <div v-if="!taskId" class="export-init">
      <el-alert
        :title="mode === 'ids' ? `即将按勾选 ID 导出 ${selectedIds.length} 条仓库台账（含附件）` : '即将导出仓库台账 ZIP 包（含附件）'"
        :closable="false"
        type="info"
        show-icon
      />
      <div v-if="mode !== 'ids'" class="filter-summary">
        <span>当前筛选条件：</span>
        <el-tag v-if="!hasFilters" size="small">无（导出全部）</el-tag>
        <template v-else>
          <el-tag v-for="tag in filterTags" :key="tag" size="small" class="filter-tag">{{ tag }}</el-tag>
        </template>
      </div>
      <div v-else class="filter-summary">
        <el-tag size="small">勾选模式</el-tag>
        <el-tag size="small" type="info">共 {{ selectedIds.length }} 条</el-tag>
      </div>
    </div>
    <div v-else class="export-task">
      <div v-if="status === 'PENDING' || status === 'PROCESSING'" class="export-progress">
        <el-progress :percentage="status === 'PROCESSING' ? 60 : 20" :stroke-width="12" striped :striped-flow="true" />
        <p class="status-text">{{ status === 'PENDING' ? '导出任务排队中...' : '正在打包 ZIP（含附件），请稍候...' }}</p>
      </div>
      <div v-else-if="status === 'COMPLETED'" class="export-done">
        <el-result icon="success" title="📤 仓库信息导出包 — 完成" :sub-title="`共 ${totalCount} 条记录`">
          <template #extra>
            <el-button type="primary" @click="handleDownload"><el-icon><Download /></el-icon> 下载文件包</el-button>
          </template>
        </el-result>
        <div class="package-detail">
          <div class="detail-title">📦 ZIP 包内容</div>
          <ul class="detail-list">
            <li>仓库信息台账.xlsx（{{ totalCount }} 条，29 列含系统字段）</li>
            <li v-if="hasAttachments">attachments/</li>
            <li v-if="summary.propertyCertCount" class="indent">产权证 {{ summary.propertyCertCount }} 份</li>
            <li v-if="summary.invoiceCount" class="indent">发票 {{ summary.invoiceCount }} 份</li>
            <li v-if="summary.photosCount" class="indent">照片 {{ summary.photosCount }} 张</li>
          </ul>
          <div class="meta-row"><span class="meta-label">导出范围：</span><span>{{ summary.filterSummary || '—' }}</span></div>
          <div class="meta-row"><span class="meta-label">处理耗时：</span><span>{{ formatElapsed(summary.elapsedMs) }}</span></div>
          <div class="meta-row"><span class="meta-label">包大小：</span><span>{{ formatBytes(summary.zipBytes) }}</span></div>
          <div class="meta-row"><span class="meta-label">链接有效期：</span><span>7 天</span></div>
        </div>
      </div>
      <div v-else-if="status === 'FAILED'" class="export-failed">
        <el-result icon="error" title="导出失败" :sub-title="failureReason || '未知原因'">
          <template #extra>
            <el-button @click="handleRetry">重新导出</el-button>
          </template>
        </el-result>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <span v-if="status !== 'COMPLETED'" class="footer-hint">关闭后仍可稍后在导出记录中下载</span>
        <el-button @click="handleClose">{{ status === 'COMPLETED' ? '关闭' : '取消' }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed, onUnmounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import http from '@/api/client'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  filters: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'filter' },
  selectedIds: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])

const taskId = ref(null)
const status = ref('')
const totalCount = ref(0)
const failureReason = ref('')
const summary = ref({})
let pollTimer = null

const hasFilters = computed(() => {
  const f = props.filters
  return !!(f.keyword || f.types?.length || f.statuses?.length || f.province ||
    f.endDateFrom || f.endDateTo || f.hasPropertyCert || f.hasInvoice || f.hasPhotos || f.contactPersonKeyword)
})

const hasAttachments = computed(() => {
  const s = summary.value || {}
  return (s.propertyCertCount || 0) + (s.invoiceCount || 0) + (s.photosCount || 0) > 0
})

const filterTags = computed(() => {
  const f = props.filters
  const tags = []
  if (f.keyword) tags.push(`关键词: ${f.keyword}`)
  if (f.types?.length) tags.push(`类型: ${f.types.join(',')}`)
  if (f.statuses?.length) tags.push(`状态: ${f.statuses.join(',')}`)
  if (f.province) tags.push(`省份: ${f.province}`)
  if (f.endDateFrom || f.endDateTo) tags.push(`到期: ${f.endDateFrom || '...'} ~ ${f.endDateTo || '...'}`)
  if (f.hasPropertyCert) tags.push('有产权证')
  if (f.hasInvoice) tags.push('有发票')
  if (f.hasPhotos) tags.push('有照片')
  if (f.contactPersonKeyword) tags.push(`联系人: ${f.contactPersonKeyword}`)
  return tags
})

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!taskId.value) return
    try {
      const { data } = await http.get(`/api/knowledge/warehouses/export/tasks/${taskId.value}/status`)
      status.value = data.status
      if (data.totalCount != null) totalCount.value = data.totalCount
      if (data.failureReason) failureReason.value = data.failureReason
      if (data.resultSummary) summary.value = data.resultSummary
      if (data.status === 'COMPLETED' || data.status === 'FAILED') {
        stopPolling()
      }
    } catch {
      stopPolling()
    }
  }, 2000)
}

const startExport = async () => {
  try {
    const payload = props.mode === 'ids'
      ? { ids: props.selectedIds }
      : props.filters
    const { data } = await http.post('/api/knowledge/warehouses/export', payload)
    taskId.value = data.taskId
    status.value = 'PENDING'
    totalCount.value = 0
    failureReason.value = ''
    summary.value = {}
    startPolling()
  } catch {
    emit('update:modelValue', false)
  }
}

const handleDownload = async () => {
  try {
    const response = await http.get(`/api/knowledge/warehouses/export/tasks/${taskId.value}/download`, {
      responseType: 'blob'
    })
    const blob = response.data
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const filename = (summary.value && summary.value.fileName)
      || `仓库信息导出包_${new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)}.zip`
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch {
    // error already handled by interceptor
  }
}

const handleRetry = () => {
  taskId.value = null
  status.value = ''
  totalCount.value = 0
  failureReason.value = ''
  summary.value = {}
  startExport()
}

const handleClose = () => {
  stopPolling()
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

watch(() => props.modelValue, (v) => {
  if (v) {
    taskId.value = null
    status.value = ''
    totalCount.value = 0
    failureReason.value = ''
    summary.value = {}
    startExport()
  } else {
    stopPolling()
  }
})

onUnmounted(stopPolling)
</script>

<style scoped>
.export-init { padding: 8px 0; }
.filter-summary { margin-top: 16px; font-size: 13px; color: var(--el-text-color-secondary); }
.filter-tag { margin: 4px 4px 4px 0; }
.export-progress { padding: 24px 0; text-align: center; }
.status-text { margin-top: 12px; color: var(--el-text-color-secondary); font-size: 14px; }
.export-done, .export-failed { padding: 8px 0; }
.package-detail { margin-top: 12px; padding: 14px; background: #f5f7fa; border-radius: 6px; font-size: 13px; }
.detail-title { font-weight: 600; color: #303133; margin-bottom: 8px; }
.detail-list { margin: 0 0 12px; padding-left: 18px; line-height: 1.9; }
.detail-list .indent { list-style: none; margin-left: -12px; color: var(--el-text-color-secondary); }
.meta-row { line-height: 1.9; color: var(--el-text-color-regular); }
.meta-label { display: inline-block; min-width: 88px; color: var(--el-text-color-secondary); }
.dialog-footer { display: flex; justify-content: space-between; align-items: center; }
.footer-hint { font-size: 12px; color: var(--el-text-color-placeholder); }
</style>
