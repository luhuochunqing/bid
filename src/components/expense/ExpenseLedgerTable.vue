<template>
  <el-table :data="expenses" stripe v-loading="loading">
    <el-table-column type="index" label="序号" width="100" />
    <el-table-column prop="project" label="项目名称" min-width="150" />
    <el-table-column prop="type" label="费用类型" width="120">
      <template #default="{ row }">
        <el-tag :type="expenseTypeTone(row.type)">{{ row.type }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="amount" label="金额(万元)" width="130" align="right">
      <template #default="{ row }">
        <span class="amount">¥{{ Number(row.amount || 0).toFixed(2) }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="status" label="状态" width="140">
      <template #default="{ row }">
        <el-tag :type="expenseStatus(row).type">{{ expenseStatus(row).label }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="approvalStatus" label="审批状态" width="140">
      <template #default="{ row }">
        <el-tag :type="approvalStatus(row.approvalStatus).type">{{ approvalStatus(row.approvalStatus).label }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="date" label="发生日期" width="160" />
    <el-table-column prop="paidAt" label="支付日期" width="180">
      <template #default="{ row }">
        <span>{{ row.paidAt || '-' }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="returnDate" label="预计退还日期" width="170">
      <template #default="{ row }">
        <span v-if="row.returnDate" :class="{ 'warning-text': isReturnOverdue(row.returnDate) }">
          {{ row.returnDate }}
        </span>
        <span v-else>-</span>
      </template>
    </el-table-column>
    <el-table-column label="操作" min-width="220" fixed="right">
      <template #default="{ row }">
        <el-button link type="primary" size="small" @click="$emit('detail', row)">详情</el-button>
        <el-button
          v-if="row.approvalStatus === 'approved' && row.status === 'pending'"
          link
          type="success"
          size="small"
          @click="$emit('pay', row)"
        >
          登记支付
        </el-button>
        <el-button
          v-if="row.status === 'paid' && row.type === '保证金'"
          link
          type="warning"
          size="small"
          @click="$emit('request-return', row)"
        >
          申请退还
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
defineProps({
  expenses: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['detail', 'pay', 'request-return'])

function expenseTypeTone(type) {
  if (type === '保证金') return 'warning'
  if (type === '标书费') return 'success'
  if (type === '差旅费') return 'info'
  return ''
}

function expenseStatus(row) {
  if (row.approvalStatus === 'pending') return { label: '待审批', type: 'info' }
  if (row.status === 'pending') return { label: '待支付', type: 'warning' }
  if (row.status === 'paid') return { label: '已支付', type: 'success' }
  if (row.status === 'returned') return { label: '已退还', type: 'info' }
  return { label: row.backendStatus || row.status || '-', type: '' }
}

function approvalStatus(status) {
  if (status === 'pending') return { label: '待审批', type: 'warning' }
  if (status === 'approved') return { label: '已通过', type: 'success' }
  if (status === 'rejected') return { label: '已拒绝', type: 'danger' }
  return { label: status || '-', type: '' }
}

function isReturnOverdue(dateText) {
  if (!dateText) return false
  const date = new Date(dateText)
  if (Number.isNaN(date.getTime())) return false
  return date < new Date()
}
</script>

<style scoped lang="scss">
.amount {
  font-weight: 700;
  color: #409eff;
}

.warning-text {
  color: #f56c6c;
}
</style>
