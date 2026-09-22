<template>
  <el-dialog :model-value="modelValue" title="费用审批" width="500px" @update:model-value="$emit('update:modelValue', $event)">
    <div v-if="expense" class="approval-content">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="项目名称">{{ expense.project }}</el-descriptions-item>
        <el-descriptions-item label="费用类型">{{ expense.type }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ Number(expense.amount || 0).toFixed(2) }}万元</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ expense.applicant || expense.createdBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请说明">{{ expense.remark || expense.description || '无' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider />
      <el-form :model="form" label-width="80px">
        <el-form-item label="审批结果">
          <el-radio-group v-model="form.result">
            <el-radio value="approved">通过</el-radio>
            <el-radio value="rejected">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="form.comment" type="textarea" :rows="3" placeholder="请输入审批意见" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')">提交审批</el-button>
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
  submitting: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue', 'submit'])
</script>
