<template>
  <el-card class="deposit-tracking-card">
    <template #header>
      <div class="card-header">
        <span>保证金归还跟踪</span>
        <el-tag :type="overdueCount > 0 ? 'danger' : 'success'">
          {{ overdueCount > 0 ? `${overdueCount}笔超期未退` : '无超期' }}
        </el-tag>
      </div>
    </template>

    <el-table :data="deposits" stripe v-loading="loading">
      <el-table-column prop="project" label="项目名称" min-width="150" />
      <el-table-column prop="amount" label="金额(万元)" width="130" align="right">
        <template #default="{ row }">
          <span class="amount">¥{{ Number(row.amount || 0).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="paidAt" label="缴纳日期" width="180">
        <template #default="{ row }">
          <span>{{ row.paidAt || row.date || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="expectedReturn" label="应退日期" width="160">
        <template #default="{ row }">
          <span :class="{ 'overdue-text': isOverdue(row.expectedReturn) && row.status !== 'returned' }">
            {{ row.expectedReturn || '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="depositStatus(row).type">{{ depositStatus(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="payee" label="收款人" min-width="150" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'returned'"
            link
            type="primary"
            size="small"
            @click="$emit('remind', row)"
          >
            提醒
          </el-button>
          <el-button
            v-if="row.status !== 'returned'"
            link
            type="success"
            size="small"
            @click="$emit('confirm-return', row)"
          >
            确认退还
          </el-button>
          <span v-else class="text-muted">已完成</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
defineProps({
  deposits: {
    type: Array,
    default: () => []
  },
  overdueCount: {
    type: Number,
    default: 0
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['remind', 'confirm-return'])

function isOverdue(dateText) {
  if (!dateText) return false
  const date = new Date(dateText)
  if (Number.isNaN(date.getTime())) return false
  return date < new Date()
}

function depositStatus(row) {
  if (row.status === 'returned') return { label: '已退还', type: 'success' }
  if (isOverdue(row.expectedReturn)) return { label: '超期未退', type: 'danger' }
  return { label: '待退还', type: 'warning' }
}
</script>

<style scoped lang="scss">
.deposit-tracking-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.amount {
  font-weight: 700;
  color: #409eff;
}

.overdue-text {
  color: #f56c6c;
  font-weight: 700;
}

.text-muted {
  color: var(--text-muted);
  font-size: 12px;
}
</style>
