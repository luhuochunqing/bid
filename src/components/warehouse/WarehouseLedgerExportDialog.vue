<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="导出仓库台账"
    width="520px"
    :close-on-click-modal="false"
    :before-close="handleClose"
    data-testid="warehouse-ledger-export-dialog"
  >
    <div v-if="!taskId" class="ledger-init">
      <el-form :model="form" label-width="92px">
        <el-form-item label="导出范围">
          <el-radio-group v-model="form.scope" class="scope-group">
            <el-radio value="filter" class="scope-radio">
              <span>当前筛选结果</span>
              <el-tag size="small" type="info" class="scope-count">{{ filterCount }}</el-tag>
            </el-radio>
            <el-radio value="all_in_use" class="scope-radio">
              <span>全部使用中仓库</span>
              <el-tag size="small" type="info" class="scope-count">{{ allInUseCount }}</el-tag>
            </el-radio>
            <el-radio value="ids" :disabled="selectedIds.length === 0" class="scope-radio">
              <span>当前勾选的仓库</span>
              <el-tag size="small" type="info" class="scope-count">{{ selectedIds.length }}</el-tag>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="导出内容">
          <el-checkbox-group v-model="form.sections" class="section-group">
            <el-checkbox value="BASIC">仓库基础信息</el-checkbox>
            <el-checkbox value="LEASE">租约/服务信息</el-checkbox>
            <el-checkbox value="DOC">资料核验状态</el-checkbox>
            <el-checkbox value="META">创建信息</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="导出格式">
          <span class="format-text">Excel（.xlsx）</span>
        </el-form-item>
      </el-form>
      <el-alert
        title="本导出仅含台账数据。如需附件，请在仓库详情页单独下载。"
        type="info"
        :closable="false"
        show-icon
        class="hint-alert"
      />
    </div>
    <div v-else class="ledger-task">
      <div v-if="status === 'PENDING' || status === 'PROCESSING'" class="ledger-progress">
        <el-progress :percentage="status === 'PROCESSING' ? 55 : 20" :stroke-width="12" striped :striped-flow="true" />
        <p class="status-text">{{ status === 'PENDING' ? '导出任务排队中...' : '正在生成台账...' }}</p>
      </div>
      <div v-else-if="status === 'COMPLETED'" class="ledger-done">
        <el-result icon="success" title="📤 仓库台账导出 — 完成" :sub-title="`共 ${totalCount} 条记录`">
          <template #extra>
            <el-button type="primary" @click="handleDownload"><el-icon><Download /></el-icon> 下载台账</el-button>
          </template>
        </el-result>
        <div class="ledger-meta">
          <div class="meta-row"><span class="meta-label">导出范围：</span><span>{{ summaryScope }}</span></div>
          <div class="meta-row"><span class="meta-label">处理耗时：</span><span>{{ formatElapsed(summary.elapsedMs) }}</span></div>
          <div class="meta-row"><span class="meta-label">链接有效期：</span><span>7 天</span></div>
        </div>
      </div>
      <div v-else-if="status === 'FAILED'" class="ledger-failed">
        <el-result icon="error" title="导出失败" :sub-title="failureReason || '未知原因'">
          <template #extra>
            <el-button @click="handleRetry">重新导出</el-button>
          </template>
        </el-result>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">{{ taskId ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!taskId" type="primary" @click="handleStart">开始导出</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed, reactive, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import http from '@/api/client'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  filter: { type: Object, default: () => ({}) },
  filterCount: { type: Number, default: 0 },
  allInUseCount: { type: Number, default: 0 },
  selectedIds: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])

const taskId = ref(null)
const status = ref('')
const totalCount = ref(0)
const failureReason = ref('')
const summary = ref({})
let pollTimer = null

const form = reactive({
  scope: 'filter',
  sections: ['BASIC', 'LEASE', 'DOC', 'META']
})

const summaryScope = computed(() => {
  if (!summary.value) return '—'
  return summary.value.scope === 'ids' ? '当前勾选'
    : summary.value.scope === 'all_in_use' ? '全部使用中' : '当前筛选'
})

const reset = () => {
  taskId.value = null
  status.value = ''
  totalCount.value = 0
  failureReason.value = ''
  summary.value = {}
  form.scope = 'filter'
  form.sections = ['BASIC', 'LEASE', 'DOC', 'META']
}

const handleStart = async () => {
  if (form.sections.length === 0) {
    ElMessage.warning('请至少选择 1 个导出内容')
    return
  }
  if (form.scope === 'ids' && (!props.selectedIds || props.selectedIds.length === 0)) {
    ElMessage.warning('请先在列表中勾选要导出的仓库')
    return
  }
  try {
    const payload = {
      scope: form.scope,
      sections: form.sections
    }
    if (form.scope === 'filter') {
      Object.assign(payload, props.filter || {})
    } else if (form.scope === 'ids') {
      payload.ids = props.selectedIds
    }
    const { data } = await http.post('/api/knowledge/warehouses/export/ledger', payload)
    taskId.value = data.taskId
    status.value = 'PENDING'
    startPolling()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '创建导出任务失败')
  }
}

const stopPolling = () => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!taskId.value) return
    try {
      const { data } = await http.get(`/api/knowledge/warehouses/export/tasks/${taskId.value}/status`)
      status.value = data.status
      if (data.totalCount != null) totalCount.value = data.totalCount
      if (data.failureReason) failureReason.value = data.failureReason
      if (data.resultSummary) summary.value = data.resultSummary
      if (data.status === 'COMPLETED' || data.status === 'FAILED') stopPolling()
    } catch {
      stopPolling()
    }
  }, 2000)
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
    const ts = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)
    a.download = `仓库台账-${ts}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}

const handleRetry = () => { reset(); handleStart() }
const handleClose = () => { stopPolling(); reset(); emit('update:modelValue', false) }

const formatElapsed = (ms) => {
  if (!ms || ms <= 0) return '—'
  if (ms < 1000) return `${ms} 毫秒`
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s} 秒`
  const m = Math.floor(s / 60)
  return `${m} 分 ${s % 60} 秒`
}

watch(() => props.modelValue, (v) => { if (v) reset() })
onUnmounted(stopPolling)
</script>

<style scoped>
.ledger-init { padding: 4px 0; }
.scope-group { display:flex; flex-direction:column; gap:8px; }
.scope-radio { white-space:nowrap; margin-right:0; display:flex; align-items:center; gap:8px; }
.scope-radio :deep(.el-radio__label) { display:flex; align-items:center; gap:8px; padding-left:0; }
.scope-count { font-weight:500; }
.section-group { display:flex; flex-direction:column; gap:6px; }
.section-group :deep(.el-checkbox) { margin-right:0; }
.format-text { color: var(--el-text-color-regular); font-weight:500; }
.hint-alert { margin-top: 8px; }
.ledger-progress { padding: 24px 0; text-align: center; }
.status-text { margin-top: 12px; color: var(--el-text-color-secondary); font-size: 14px; }
.ledger-meta { margin-top: 12px; padding: 12px 14px; background: #f5f7fa; border-radius: 6px; font-size: 13px; }
.meta-row { line-height: 1.9; }
.meta-label { display: inline-block; min-width: 88px; color: var(--el-text-color-secondary); }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>
