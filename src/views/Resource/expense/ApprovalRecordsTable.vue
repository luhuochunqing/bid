<template>
  <el-card>
    <template #header>
      <span>审批记录</span>
    </template>
    <el-table :data="items" stripe>
      <el-table-column label="项目" min-width="180">
        <template #default="{ row }">{{ row.project || row.projectName || '-' }}</template>
      </el-table-column>
      <el-table-column label="部门" width="140">
        <template #default="{ row }">{{ row.department || row.departmentName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="type" label="费用类型" width="120" />
      <el-table-column prop="amount" label="金额(元)" width="130" align="right">
        <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="applicant" label="申请人" width="120" />
      <el-table-column prop="applyTime" label="申请日期" width="160" />
      <el-table-column prop="approver" label="审批人" width="120" />
      <el-table-column label="审批状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.approvalStatus === 'pending'" type="warning">待审批</el-tag>
          <el-tag v-else-if="row.approvalStatus === 'rejected'" type="danger">已拒绝</el-tag>
          <el-tag v-else type="success">已通过</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button
            v-if="row.approvalStatus === 'pending'"
            link
            type="primary"
            size="small"
            @click="$emit('approve', row)"
          >
            审批
          </el-button>
          <span v-else class="text-muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
// Input: expense approval rows merged from ledger and approval records
// Output: approval records table with approve action
// Pos: src/views/Resource/expense/ - Approval records table
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

defineProps({
  items: { type: Array, default: () => [] }
})

defineEmits(['approve'])
</script>

<style scoped>
.text-muted {
  color: var(--text-muted);
  font-size: 12px;
}
</style>
