<template>
  <el-card class="table-card">
    <template #header>
      <div class="card-header">
        <span>保证金归还跟踪</span>
        <el-tag :type="overdueCount > 0 ? 'danger' : 'success'">
          {{ overdueCount > 0 ? `${overdueCount}笔超期` : '无超期' }}
        </el-tag>
      </div>
    </template>

    <el-table :data="items" stripe>
      <el-table-column label="项目" min-width="180">
        <template #default="{ row }">{{ row.project || row.projectName || '-' }}</template>
      </el-table-column>
      <el-table-column label="部门" width="140">
        <template #default="{ row }">{{ row.department || row.departmentName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="amount" label="金额(元)" width="130" align="right">
        <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="date" label="缴纳日期" width="120" />
      <el-table-column prop="expectedReturn" label="应退日期" width="120" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'returned'" type="success">已退还</el-tag>
          <el-tag v-else-if="row.isOverdue" type="danger">超期未退</el-tag>
          <el-tag v-else type="warning">待退还</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="$emit('remind', row)">提醒</el-button>
          <el-button
            v-if="row.status !== 'returned'"
            link
            size="small"
            type="success"
            @click="$emit('confirm-return', row)"
          >
            确认退还
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
// Input: deposit-only ledger items
// Output: deposit return tracking table
// Pos: src/views/Resource/expense/ - Deposit tracking table
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

defineProps({
  items: { type: Array, default: () => [] },
  overdueCount: { type: Number, default: 0 }
})

defineEmits(['remind', 'confirm-return'])
</script>

<style scoped>
.table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
