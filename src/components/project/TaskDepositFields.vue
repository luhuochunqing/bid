<template>
  <div class="task-deposit-fields" data-test="task-deposit-fields">
    <!-- CO-448: 仅在「缴纳投标保证金」任务上显示，保证金金额和截止日期始终只读 -->
    <el-form-item label="保证金金额（元）" label-width="130px">
      <el-input
        :model-value="extendedFields.depositAmount ?? ''"
        disabled
        data-test="deposit-amount-input"
      />
    </el-form-item>

    <el-form-item label="保证金缴纳截止日期" label-width="150px">
      <el-date-picker
        :model-value="extendedFields.depositDeadline ?? ''"
        type="date"
        value-format="YYYY-MM-DD"
        disabled
        data-test="deposit-deadline-picker"
      />
    </el-form-item>

    <el-form-item label="收款方" :required="isAssigneeSubmitting" :error="errors.payee">
      <el-input
        v-model="local.payee"
        :disabled="!isAssigneeSubmitting"
        placeholder="请填写收款方"
        data-test="deposit-payee-input"
        @update:model-value="emitUpdate('payee', $event)"
      />
    </el-form-item>

    <el-form-item label="收款账号" :required="isAssigneeSubmitting" :error="errors.payeeAccount">
      <el-input
        v-model="local.payeeAccount"
        :disabled="!isAssigneeSubmitting"
        placeholder="请填写收款账号"
        data-test="deposit-payee-account-input"
        @update:model-value="emitUpdate('payeeAccount', $event)"
      />
    </el-form-item>

    <el-form-item label="实际缴纳日期" :required="isAssigneeSubmitting" :error="errors.actualPaymentDate">
      <el-date-picker
        v-model="local.actualPaymentDate"
        type="date"
        value-format="YYYY-MM-DD"
        :disabled="!isAssigneeSubmitting"
        placeholder="请选择实际缴纳日期"
        data-test="deposit-actual-payment-date-picker"
        @update:model-value="emitUpdate('actualPaymentDate', $event)"
      />
    </el-form-item>

    <el-form-item label="预计归还日期" :required="isAssigneeSubmitting" :error="errors.expectedRefundDate">
      <el-date-picker
        v-model="local.expectedRefundDate"
        type="date"
        value-format="YYYY-MM-DD"
        :disabled="!isAssigneeSubmitting"
        placeholder="请选择预计归还日期"
        data-test="deposit-expected-refund-date-picker"
        @update:model-value="emitUpdate('expectedRefundDate', $event)"
      />
    </el-form-item>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  extendedFields: { type: Object, default: () => ({}) },
  isAssigneeSubmitting: { type: Boolean, default: false },
})
const emit = defineEmits(['update:extendedFields'])

// 仅 4 个执行人填写字段进入本地可编辑状态；
// 保证金金额和保证金缴纳截止日期始终从 extendedFields 只读读取，不进入 v-model 双向绑定。
const local = reactive({
  payee: props.extendedFields.payee ?? '',
  payeeAccount: props.extendedFields.payeeAccount ?? '',
  actualPaymentDate: props.extendedFields.actualPaymentDate ?? '',
  expectedRefundDate: props.extendedFields.expectedRefundDate ?? '',
})

// 父组件切换任务或外部更新时，同步本地 4 字段
watch(
  () => props.extendedFields,
  (v) => {
    const ef = v || {}
    local.payee = ef.payee ?? ''
    local.payeeAccount = ef.payeeAccount ?? ''
    local.actualPaymentDate = ef.actualPaymentDate ?? ''
    local.expectedRefundDate = ef.expectedRefundDate ?? ''
    clearAllErrors()
  },
  { deep: true }
)

const FIELD_LABELS = {
  payee: '收款方',
  payeeAccount: '收款账号',
  actualPaymentDate: '实际缴纳日期',
  expectedRefundDate: '预计归还日期',
}
const errors = reactive({
  payee: '',
  payeeAccount: '',
  actualPaymentDate: '',
  expectedRefundDate: '',
})

function clearAllErrors() {
  Object.keys(errors).forEach((k) => { errors[k] = '' })
}

function emitUpdate(field, value) {
  // 从 $event 取新值，从 props.extendedFields 取其余字段做合并，保持其他键不变
  emit('update:extendedFields', { ...props.extendedFields, [field]: value })
}

function validate() {
  clearAllErrors()
  // 非执行人提交场景：4 字段不需要校验
  if (!props.isAssigneeSubmitting) {
    return { valid: true }
  }
  let firstError = ''
  for (const key of Object.keys(FIELD_LABELS)) {
    const value = local[key]
    if (value == null || String(value).trim() === '') {
      errors[key] = `请填写${FIELD_LABELS[key]}`
      if (!firstError) firstError = errors[key]
    }
  }
  return firstError ? { valid: false, message: firstError } : { valid: true }
}

defineExpose({ validate })
</script>

<style scoped>
.task-deposit-fields { width: 100%; }
</style>
