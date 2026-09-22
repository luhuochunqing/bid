<template>
  <el-card class="approval-card">
    <template #header><div class="card-header"><span>审批记录</span></div></template>
    <el-table :data="rows" stripe>
      <el-table-column prop="project" label="项目名称" min-width="150" />
      <el-table-column prop="type" label="费用类型" width="120"><template #default="{ row }"><el-tag size="small">{{ row.type }}</el-tag></template></el-table-column>
      <el-table-column prop="amount" label="金额(万元)" width="130" align="right"><template #default="{ row }">¥{{ row.amount.toFixed(2) }}</template></el-table-column>
      <el-table-column prop="applicant" label="申请人" width="110" />
      <el-table-column prop="applyTime" label="申请时间" width="200" />
      <el-table-column prop="approver" label="审批人" width="110" />
      <el-table-column prop="approvalStatus" label="审批状态" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.approvalStatus === 'pending'" type="warning" size="small">待审批</el-tag>
          <el-tag v-else-if="row.approvalStatus === 'approved'" type="success" size="small">已通过</el-tag>
          <el-tag v-else-if="row.approvalStatus === 'rejected'" type="danger" size="small">已拒绝</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button v-if="row.approvalStatus === 'pending'" size="small" type="primary" link @click="$emit('approve', row)">审批</el-button>
          <el-button v-else size="small" link disabled>已处理</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
defineProps({
  rows: { type: Array, required: true }
})

defineEmits(['approve'])
</script>
