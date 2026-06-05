<template>
  <el-dialog :model-value="modelValue" title="登记支付" width="680px" @update:model-value="$emit('update:modelValue', $event)">
    <div v-if="expense">
      <el-descriptions :column="2" border class="payment-summary">
        <el-descriptions-item label="项目名称">{{ expense.project }}</el-descriptions-item>
        <el-descriptions-item label="费用类型">{{ expense.type }}</el-descriptions-item>
        <el-descriptions-item label="审批状态">{{ expense.approvalStatus }}</el-descriptions-item>
        <el-descriptions-item label="待支付金额">¥{{ Number(expense.amount || 0).toFixed(2) }}万元</el-descriptions-item>
      </el-descriptions>

      <el-divider>支付信息</el-divider>

      <el-form :model="form" label-width="100px">
        <el-form-item label="支付金额">
          <el-input-number v-model="form.amount" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="支付日期">
          <el-date-picker v-model="form.paidAt" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="支付人">
          <el-input v-model="form.paidBy" />
        </el-form-item>
        <el-form-item label="支付方式">
          <el-select v-model="form.paymentMethod">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
            <el-option label="支票" value="CHECK" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="流水号">
          <el-input v-model="form.paymentReference" placeholder="请输入支付流水或凭证号" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <el-divider>历史支付记录</el-divider>

      <el-table :data="records" size="small" max-height="240">
        <el-table-column prop="paidAt" label="支付时间" min-width="160" />
        <el-table-column prop="amount" label="金额(万元)" width="130" align="right">
          <template #default="{ row }">
            ¥{{ Number(row.amount || 0).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="paidBy" label="支付人" width="120" />
        <el-table-column prop="paymentMethod" label="支付方式" width="120" />
        <el-table-column prop="paymentReference" label="流水号" min-width="140" />
      </el-table>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')">确认登记</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineModel('form', { type: Object, required: true })

defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  expense: {
    type: Object,
    default: null
  },
  records: {
    type: Array,
    default: () => []
  },
  submitting: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue', 'submit'])
</script>

<style scoped lang="scss">
.payment-summary {
  margin-bottom: 12px;
}
</style>
