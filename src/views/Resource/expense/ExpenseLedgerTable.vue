<template>
  <el-card class="table-card">
    <template #header>
      <div class="card-header">
        <span>费用台账</span>
        <div class="actions">
          <el-button type="primary" @click="$emit('open-apply')">费用申请</el-button>
          <el-button @click="$emit('export')">导出</el-button>
        </div>
      </div>
    </template>

    <el-table :data="items" stripe v-loading="loading">
      <el-table-column type="index" label="序号" width="80" />
      <el-table-column label="项目" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.project || row.projectName || '-' }}</template>
      </el-table-column>
      <el-table-column label="部门" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.department || row.departmentName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="type" label="费用类型" width="120" />
      <el-table-column prop="amount" label="金额(元)" width="130" align="right">
        <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="date" label="发生日期" width="120" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.approvalStatus === 'pending'" type="warning">待审批</el-tag>
          <el-tag v-else-if="row.status === 'returned'" type="success">已退还</el-tag>
          <el-tag v-else-if="row.backendStatus === 'RETURN_REQUESTED'" type="danger">退还中</el-tag>
          <el-tag v-else-if="row.approvalStatus === 'rejected'" type="info">已驳回</el-tag>
          <el-tag v-else-if="row.backendStatus === 'PAID'" type="success">已支付</el-tag>
          <el-tag v-else type="success">待支付</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="approvalComment" label="审批意见" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="$emit('detail', row)">详情</el-button>
          <el-button
            v-if="row.type === '保证金' && row.backendStatus === 'PAID'"
            link
            type="success"
            size="small"
            @click="$emit('return', row)"
          >
            申请退还
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
// Input: normalized expense ledger items from backend
// Output: expense ledger main table with detail/return actions
// Pos: src/views/Resource/expense/ - Expense ledger table
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

defineEmits(['detail', 'return', 'open-apply', 'export'])
</script>

<style scoped>
.table-card {
  margin-bottom: 20px;
}

.card-header,
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-header {
  justify-content: space-between;
}
</style>
