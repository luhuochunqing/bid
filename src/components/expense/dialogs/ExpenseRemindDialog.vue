<template>
  <el-dialog :model-value="modelValue" title="发送保证金归还提醒" width="500px" @update:model-value="$emit('update:modelValue', $event)">
    <div v-if="expense" class="remind-content">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="项目名称">{{ expense.project }}</el-descriptions-item>
        <el-descriptions-item label="保证金金额">¥{{ Number(expense.amount || 0).toFixed(2) }}万元</el-descriptions-item>
        <el-descriptions-item label="应退日期">{{ expense.expectedReturn }}</el-descriptions-item>
        <el-descriptions-item label="收款方">{{ expense.payee }}</el-descriptions-item>
      </el-descriptions>
      <div class="remind-message">
        <el-divider />
        <p><strong>提醒内容：</strong></p>
        <p>{{ expense.payee }}：</p>
        <p>您好！请及时退还{{ expense.project }}项目保证金（金额：¥{{ Number(expense.amount || 0).toFixed(2) }}万元），应退日期为{{ expense.expectedReturn }}。</p>
        <p v-if="isOverdue(expense.expectedReturn)" class="overdue-notice">
          该保证金已超期{{ overdueDays(expense.expectedReturn) }}天，请加急处理！
        </p>
      </div>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="$emit('submit')">发送提醒</el-button>
    </template>
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
  isOverdue: {
    type: Function,
    required: true
  },
  overdueDays: {
    type: Function,
    required: true
  }
})

defineEmits(['update:modelValue', 'submit'])
</script>

<style scoped lang="scss">
.remind-message {
  padding-top: 10px;

  p {
    margin: 8px 0;
    line-height: 1.6;
  }
}

.overdue-notice {
  color: #f56c6c;
  background: #fef0f0;
  padding: 10px;
  border-radius: 4px;
}
</style>
