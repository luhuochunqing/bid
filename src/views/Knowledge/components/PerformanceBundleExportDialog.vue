<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="val => $emit('update:visible', val)"
    title="导出业绩合订本"
    width="560px"
    :close-on-click-modal="false"
    :close-on-press-escape="!isRunning"
    :show-close="!isRunning"
  >
    <div class="bundle-export-dialog-body">
      <!-- 步骤 1：配置 -->
      <template v-if="!isRunning && !isCompleted && !isFailed">
        <div class="export-hint">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ exportHintText }}</span>
        </div>

        <div class="section-title">附件类型筛选（不勾选则导出全部类型）</div>
        <div class="select-all-row">
          <el-checkbox
            :model-value="selectAll"
            :indeterminate="indeterminate"
            @change="handleSelectAllChange"
          >
            全选
          </el-checkbox>
        </div>

        <el-checkbox-group v-model="checkedTypes" class="type-checkbox-group">
          <div v-for="item in ATTACHMENT_TYPES" :key="item.value" class="type-row">
            <el-checkbox :value="item.value">
              {{ item.label }}
            </el-checkbox>
            <el-tag v-if="item.required" size="small" type="danger" effect="light">必传</el-tag>
          </div>
        </el-checkbox-group>

        <el-alert
          type="info"
          :closable="false"
          class="structure-preview"
        >
          <template #title>
            <div class="structure-preview-title">合订本导航结构</div>
          </template>
          <div class="structure-preview-body">
            <div>H1 客户类型</div>
            <div class="indent-1">H2 集团名称</div>
            <div class="indent-2">H3 合同名称 / 附件类型标签</div>
            <div class="indent-3">H4 中标通知书</div>
            <div class="soe-note">央企同一集团+签约抬头下，关系证明/央企名录/品类页/商城截图只展示一次</div>
          </div>
        </el-alert>
      </template>

      <!-- 步骤 2：进行中 -->
      <template v-if="isRunning">
        <div class="status-block">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span class="status-text">正在生成业绩合订本（任务 #{{ taskId }}）...</span>
        </div>
        <el-progress
          :percentage="processingPercentage"
          :indeterminate="isIndeterminate"
          status="warning"
        />
        <div class="status-hint">
          300 DPI 高清渲染可能需要几分钟，请耐心等待。
          可关闭对话框，任务在后台继续执行，完成后会通过通知提醒。
        </div>
      </template>

      <!-- 步骤 3：完成 -->
      <template v-if="isCompleted">
        <el-result icon="success" title="合订本生成完成" sub-title="">
          <template #extra>
            <div class="result-summary">
              <div>记录数：{{ totalCount }} 条</div>
              <div v-if="summary.elapsedMs">耗时：{{ Math.round(summary.elapsedMs / 1000) }} 秒</div>
              <div v-if="summary.wordBytes">文件大小：{{ formatBytes(summary.wordBytes) }}</div>
            </div>
            <el-button type="primary" :loading="isDownloading" @click="handleDownload">
              下载 Word 文件
            </el-button>
          </template>
        </el-result>
      </template>

      <!-- 步骤 4：失败 -->
      <template v-if="isFailed">
        <el-result icon="error" title="合订本生成失败" :sub-title="failureReason">
          <template #extra>
            <el-button @click="handleRetry">重试</el-button>
          </template>
        </el-result>
      </template>
    </div>

    <template #footer>
      <el-button v-if="!isRunning" @click="handleCancel">关闭</el-button>
      <el-button
        v-if="!isRunning && !isCompleted && !isFailed"
        type="primary"
        :disabled="!canConfirm"
        @click="handleConfirm"
      >
        确认导出
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { InfoFilled, Loading } from '@element-plus/icons-vue'
import { useAsyncTask } from '@/composables/useAsyncTask'
import { performanceBundleExportApi } from '@/api/modules/performanceBundleExport'

const props = defineProps({
  visible: { type: Boolean, default: false },
  selectedIds: { type: Array, default: () => [] },
  totalCount: { type: Number, default: 0 },
  criteria: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:visible'])

const ATTACHMENT_TYPES = [
  { value: 'CONTRACT_AGREEMENT', label: '合同协议', required: true },
  { value: 'MALL_SCREENSHOT', label: '商城截图' },
  { value: 'SOE_DIRECTORY', label: '央企名录' },
  { value: 'RELATIONSHIP_PROOF', label: '关系证明' },
  { value: 'CATEGORY_PAGE', label: '品类页' },
  { value: 'BID_NOTICE', label: '中标通知书' },
  { value: 'OTHER', label: '其他附件' }
]

const ALL_VALUES = ATTACHMENT_TYPES.map(t => t.value)

const checkedTypes = ref([])

const selectAll = computed(() => checkedTypes.value.length === ALL_VALUES.length)
const indeterminate = computed(() => {
  const len = checkedTypes.value.length
  return len > 0 && len < ALL_VALUES.length
})
const canConfirm = computed(() => checkedTypes.value.length > 0)

const exportHintText = computed(() => {
  if (props.selectedIds.length > 0) {
    return `将导出选中的 ${props.selectedIds.length} 条业绩生成 Word 合订本`
  }
  return `将导出全部 ${props.totalCount} 条业绩生成 Word 合订本`
})

// 异步任务
const {
  taskId,
  status,
  totalCount: taskTotalCount,
  failureReason,
  summary,
  isRunning,
  isCompleted,
  isFailed,
  isDownloading,
  startTask,
  reset,
  downloadFile
} = useAsyncTask({
  statusUrl: '/api/knowledge/performance/bundle-export/tasks/:id/status',
  downloadUrl: '/api/knowledge/performance/bundle-export/tasks/:id/download',
  submitFn: async (payload) => {
    const res = await performanceBundleExportApi.triggerExport(payload)
    return res?.data
  }
})

// ElProgress percentage 必须是 0-100 的数字，indeterminate 独立控制
const isIndeterminate = computed(() => isRunning.value)
const processingPercentage = computed(() => isRunning.value ? 0 : 0)

function handleSelectAllChange(val) {
  checkedTypes.value = val ? [...ALL_VALUES] : []
}

function handleCancel() {
  reset()
  emit('update:visible', false)
}

async function handleConfirm() {
  if (!canConfirm.value) return
  const payload = {
    attachmentTypes: [...checkedTypes.value]
  }
  if (props.selectedIds.length > 0) {
    payload.ids = [...props.selectedIds]
  } else {
    payload.criteria = props.criteria || {}
  }
  await startTask(payload)
}

async function handleDownload() {
  await downloadFile(taskId.value, (s) => {
    const ts = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    return `业绩合订本_${ts}.docx`
  })
}

function handleRetry() {
  reset()
  handleConfirm()
}

function formatBytes(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

watch(() => props.visible, (val) => {
  if (val) {
    checkedTypes.value = [...ALL_VALUES]
    reset()
  }
})
</script>

<style scoped lang="scss">
.bundle-export-dialog-body { padding: 0 4px; }
.export-hint {
  margin-bottom: 16px; padding: 10px 12px;
  background: var(--el-color-primary-light-9);
  border-radius: 6px; font-size: 13px;
  color: var(--el-color-primary);
  display: flex; align-items: center; gap: 6px;
}
.section-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; color: var(--el-text-color-primary); }
.select-all-row { padding: 8px 0; border-bottom: 1px solid var(--el-border-color-lighter); margin-bottom: 8px; }
.type-checkbox-group .type-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; }
.structure-preview {
  margin-top: 16px;
  &-title { font-weight: 600; }
  &-body {
    font-size: 12px; line-height: 1.8;
    .indent-1 { padding-left: 16px; }
    .indent-2 { padding-left: 32px; }
    .indent-3 { padding-left: 48px; }
    .soe-note { margin-top: 8px; padding: 6px 8px; background: var(--el-color-warning-light-9); border-radius: 4px; color: var(--el-color-warning-dark-2); }
  }
}
.status-block {
  display: flex; align-items: center; gap: 8px; margin-bottom: 16px; font-size: 14px;
  .status-text { color: var(--el-color-primary); }
}
.status-hint { margin-top: 12px; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.6; }
.result-summary { margin-bottom: 16px; font-size: 13px; color: var(--el-text-color-regular); line-height: 1.8; }
</style>
