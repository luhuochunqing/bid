<template>
  <el-dialog :model-value="modelValue" title="费用详情" width="720px" @update:model-value="$emit('update:modelValue', $event)">
    <div v-loading="loading">
      <el-descriptions v-if="expense" :column="2" border>
        <el-descriptions-item label="项目名称">{{ expense.project }}</el-descriptions-item>
        <el-descriptions-item label="费用类型">{{ expense.type }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ Number(expense.amount || 0).toFixed(2) }}万元</el-descriptions-item>
        <el-descriptions-item label="发生日期">{{ expense.date || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ expense.backendStatus || expense.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批状态">{{ expense.approvalStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ expense.createdBy || expense.applicant || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ expense.approvedBy || expense.approver || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批意见" :span="2">{{ expense.approvalComment || '-' }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ expense.paidAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="支付人">{{ expense.paidBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ expense.description || expense.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider>支付记录</el-divider>

      <el-empty v-if="!paymentRecords.length" description="暂无支付记录" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="record in paymentRecords"
          :key="record.id || `${record.paidAt}-${record.paymentReference}`"
          :timestamp="record.paidAt || record.createdAt || '-'"
          placement="top"
        >
          <div class="payment-item">
            <div>金额：¥{{ Number(record.amount || 0).toFixed(2) }}万元</div>
            <div>支付人：{{ record.paidBy || '-' }}</div>
            <div>支付方式：{{ record.paymentMethod || '-' }}</div>
            <div>流水号：{{ record.paymentReference || '-' }}</div>
            <div>备注：{{ record.remark || '-' }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  expense: {
    type: Object,
    default: null
  },
  paymentRecords: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue'])
</script>

<style scoped lang="scss">
.payment-item {
  display: grid;
  gap: 6px;
  line-height: 1.5;
}
</style>
