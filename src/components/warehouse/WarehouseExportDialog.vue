<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="导出台账"
    width="520px"
    :close-on-click-modal="false"
    :before-close="handleClose"
    data-testid="warehouse-export-dialog"
  >
    <div v-if="!taskId" class="export-init">
      <el-alert title="即将导出仓库台账（Excel）" :closable="false" type="info" show-icon />
      <div class="filter-summary">
        <span>当前筛选条件：</span>
        <el-tag v-if="!hasFilters" size="small">无（导出全部）</el-tag>
        <template v-else>
          <el-tag v-for="tag in filterTags" :key="tag" size="small" class="filter-tag">{{ tag }}</el-tag>
        </template>
      </div>
    </div>
    <div v-else class="export-task">
      <div v-if="status === 'PENDING' || status === 'PROCESSING'" class="export-progress">
        <el-progress :percentage="status === 'PROCESSING' ? 60 : 20" :stroke-width="12" striped :striped-flow="true" />
        <p class="status-text">{{ status === 'PENDING' ? '导出任务排队中...' : '正在导出，请稍候...' }}</p>
      </div>
      <div v-else-if="status === 'COMPLETED'" class="export-done">
        <el-result icon="success" title="导出完成" :sub-title="`共导出 ${totalCount} 条记录`">
          <template #extra>
            <el-button type="primary" @click="handleDownload"><el-icon><Download /></el-icon> 下载 Excel</el-button>
          </template>
        </el-result>
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
  filters: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue'])

const taskId = ref(null)
const status = ref('')
const totalCount = ref(0)
const failureReason = ref('')
let pollTimer = null

const hasFilters = computed(() => {
  const f = props.filters
  return !!(f.keyword || f.types?.length || f.statuses?.length || f.province ||
    f.endDateFrom || f.endDateTo || f.hasPropertyCert || f.hasInvoice || f.hasPhotos || f.contactPersonKeyword)
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
    const { data } = await http.post('/api/knowledge/warehouses/export', props.filters)
    taskId.value = data.taskId
    status.value = 'PENDING'
    totalCount.value = 0
    failureReason.value = ''
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
    a.download = `仓库台账_${taskId.value}.xlsx`
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
  startExport()
}

const handleClose = () => {
  stopPolling()
  emit('update:modelValue', false)
}

watch(() => props.modelValue, (v) => {
  if (v) {
    taskId.value = null
    status.value = ''
    totalCount.value = 0
    failureReason.value = ''
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
.dialog-footer { display: flex; justify-content: space-between; align-items: center; }
.footer-hint { font-size: 12px; color: var(--el-text-color-placeholder); }
</style>
