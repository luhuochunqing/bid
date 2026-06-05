<template>
  <el-card class="approval-card">
    <template #header>
      <div class="card-header">
        <span>审批记录</span>
      </div>
    </template>

    <el-table :data="records" stripe v-loading="loading">
      <el-table-column prop="project" label="项目名称" min-width="150" />
      <el-table-column prop="type" label="费用类型" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="amount" label="金额(万元)" width="130" align="right">
        <template #default="{ row }">
          ¥{{ Number(row.amount || 0).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column prop="applicant" label="申请人" width="110" />
      <el-table-column prop="applyTime" label="申请时间" width="180" />
      <el-table-column prop="approver" label="审批人" width="110" />
      <el-table-column prop="approvalStatus" label="审批状态" width="140">
        <template #default="{ row }">
          <el-tag :type="approvalStatus(row.approvalStatus).type" size="small">
            {{ approvalStatus(row.approvalStatus).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button
            v-if="row.approvalStatus === 'pending'"
            size="small"
            type="primary"
            link
            @click="$emit('approve', row)"
          >
            审批
          </el-button>
          <el-button v-else size="small" link disabled>已处理</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
defineProps({
  records: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['approve'])

function approvalStatus(status) {
  if (status === 'pending') return { label: '待审批', type: 'warning' }
  if (status === 'approved') return { label: '已通过', type: 'success' }
  if (status === 'rejected') return { label: '已拒绝', type: 'danger' }
  return { label: status || '-', type: '' }
}
</script>

<style scoped lang="scss">
.approval-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
