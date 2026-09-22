<template>
  <el-card class="project-expense-card">
    <template #header>
      <div class="card-title">
        <div class="title-main">
          <el-icon><Coin /></el-icon>
          <span>费用汇总</span>
        </div>
        <el-button link type="primary" @click="$emit('manage')">费用管理</el-button>
      </div>
    </template>

    <div class="summary-metrics">
      <div class="metric-item">
        <span class="metric-label">费用总额</span>
        <span class="metric-value">¥{{ formatAmount(summary.totalAmount) }}万</span>
      </div>
      <div class="metric-item">
        <span class="metric-label">已支付</span>
        <span class="metric-value success">¥{{ formatAmount(summary.paidAmount) }}万</span>
      </div>
      <div class="metric-item">
        <span class="metric-label">待支付</span>
        <span class="metric-value warning">¥{{ formatAmount(summary.pendingAmount) }}万</span>
      </div>
      <div class="metric-item">
        <span class="metric-label">已退还</span>
        <span class="metric-value info">¥{{ formatAmount(summary.returnedAmount) }}万</span>
      </div>
    </div>

    <el-alert
      v-if="error"
      :title="error"
      type="warning"
      :closable="false"
      show-icon
      class="expense-alert"
    />

    <el-table v-loading="loading" :data="expenses" stripe size="small" empty-text="暂无费用归集记录">
      <el-table-column prop="type" label="费用类型" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="getExpenseTypeColor(row.type)">
            {{ row.type || '未分类' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="amount" label="金额" width="130" align="right">
        <template #default="{ row }">
          ¥{{ formatAmount(row.amount) }}万
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="getExpenseStatusType(row.status)">
            {{ getExpenseStatusLabel(row.status, row.backendStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="date" label="申请日期" width="120" />
      <el-table-column label="支付信息" min-width="220">
        <template #default="{ row }">
          <div class="payment-info">
            <span>{{ formatPaymentInfo(row) }}</span>
            <el-tag v-if="row.paymentMethod" size="small" effect="plain">{{ row.paymentMethod }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="说明" min-width="160">
        <template #default="{ row }">
          {{ row.remark || row.description || '-' }}
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { Coin } from '@element-plus/icons-vue'

defineEmits(['manage'])

defineProps({
  expenses: {
    type: Array,
    default: () => [],
  },
  summary: {
    type: Object,
    default: () => ({
      totalAmount: 0,
      paidAmount: 0,
      pendingAmount: 0,
      returnedAmount: 0,
    }),
  },
  loading: {
    type: Boolean,
    default: false,
  },
  error: {
    type: String,
    default: '',
  },
})

function formatAmount(value) {
  return Number(value || 0).toFixed(2)
}

function getExpenseTypeColor(type) {
  const map = {
    保证金: 'warning',
    标书费: 'success',
    差旅费: 'info',
    制作费: 'primary',
    公证费: 'danger',
    其他: '',
  }
  return map[type] || ''
}

function getExpenseStatusType(status) {
  const map = {
    paid: 'success',
    pending: 'warning',
    returned: 'info',
  }
  return map[status] || 'info'
}

function getExpenseStatusLabel(status, backendStatus) {
  const map = {
    paid: '已支付',
    pending: '待支付',
    returned: '已退还',
  }
  return map[status] || backendStatus || status || '-'
}

function formatPaymentInfo(row) {
  const parts = []
  if (row.paidAt) {
    parts.push(row.paidAt)
  }
  if (row.paymentReference) {
    parts.push(`流水:${row.paymentReference}`)
  }
  return parts.length > 0 ? parts.join(' / ') : '-'
}
</script>

<style scoped>
.project-expense-card {
  margin-bottom: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.title-main {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.metric-label {
  font-size: 12px;
  color: var(--gray-650);
}

.metric-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--gray-950);
}

.metric-value.success {
  color: var(--color-success-dark);
}

.metric-value.warning {
  color: #d97706;
}

.metric-value.info {
  color: #2563eb;
}

.expense-alert {
  margin-bottom: 16px;
}

.payment-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .summary-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .summary-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
