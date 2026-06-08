<template>
  <el-dialog
    v-model="visible"
    title="资质证书导入结果"
    width="680px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <div class="import-result-dialog">
      <!-- 统计卡片 -->
      <div class="stat-cards">
        <div class="stat-card total">
          <div class="stat-number">{{ data.total }}</div>
          <div class="stat-label">总条数</div>
        </div>
        <div class="stat-card success">
          <div class="stat-number">{{ data.success }}</div>
          <div class="stat-label">成功</div>
        </div>
        <div class="stat-card failed">
          <div class="stat-number">{{ data.failed }}</div>
          <div class="stat-label">失败</div>
        </div>
      </div>

      <!-- 失败明细 -->
      <div v-if="data.errors?.length" class="error-section">
        <div class="section-title">失败明细</div>
        <el-table :data="data.errors" size="small" border style="width: 100%">
          <el-table-column prop="row" label="行号" width="70" align="center" />
          <el-table-column prop="certificateNo" label="证书编号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="reason" label="失败原因" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>

      <!-- 全部成功提示 -->
      <div v-else class="all-success">
        <el-icon class="success-icon" :size="48"><CircleCheck /></el-icon>
        <p>全部导入成功</p>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="data.errors?.length" type="primary" @click="downloadCorrection">
          <el-icon><Download /></el-icon> 下载修正文件
        </el-button>
        <el-button @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { CircleCheck, Download } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  data: {
    type: Object,
    default: () => ({ total: 0, success: 0, failed: 0, errors: [] })
  }
})

const emit = defineEmits(['update:modelValue', 'closed'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const handleClosed = () => {
  emit('closed')
}

/**
 * 下载修正文件：生成仅含失败行的 Excel（11列 + 导入结果列）
 */
const downloadCorrection = () => {
  const errors = props.data.errors || []
  if (!errors.length) return

  const headers = [
    '证书名称', '等级', '认证机构', '证书编号', '发证日期', '证书有效期',
    '代理机构', '代理联系方式', '认证范围', '证书审核提醒', '附件文件名', '导入结果'
  ]

  // 构建 CSV 内容
  const escapeCsv = (val) => {
    if (val == null) return ''
    const str = String(val)
    if (str.includes(',') || str.includes('"') || str.includes('\n')) {
      return '"' + str.replace(/"/g, '""') + '"'
    }
    return str
  }

  const rows = [headers.map(escapeCsv).join(',')]
  errors.forEach((err) => {
    const row = new Array(11).fill('')
    row[3] = err.certificateNo || ''
    row[11] = err.reason || ''
    rows.push(row.map(escapeCsv).join(','))
  })

  const csvContent = '\uFEFF' + rows.join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `资质证书导入修正_${new Date().toISOString().slice(0, 10)}.csv`)
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
</script>

<style scoped lang="scss">
.import-result-dialog {
  .stat-cards {
    display: flex;
    gap: 16px;
    margin-bottom: 20px;
  }
  .stat-card {
    flex: 1;
    text-align: center;
    padding: 16px 12px;
    border-radius: 8px;
    background: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    &.success {
      border-color: var(--el-color-success-light);
      background: var(--el-color-success-light-9);
      .stat-number { color: var(--el-color-success); }
    }
    &.failed {
      border-color: var(--el-color-danger-light);
      background: var(--el-color-danger-light-9);
      .stat-number { color: var(--el-color-danger); }
    }
    .stat-number {
      font-size: 28px;
      font-weight: 600;
      line-height: 1.2;
      color: var(--el-text-color-primary);
    }
    .stat-label {
      margin-top: 4px;
      font-size: 13px;
      color: var(--el-text-color-secondary);
    }
  }
  .error-section {
    .section-title {
      font-weight: 500;
      margin-bottom: 8px;
      color: var(--el-text-color-primary);
    }
  }
  .all-success {
    text-align: center;
    padding: 32px 0;
    .success-icon {
      margin-bottom: 12px;
    }
    p {
      color: var(--el-text-color-secondary);
      font-size: 14px;
    }
  }
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
