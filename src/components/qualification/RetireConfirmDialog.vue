<template>
  <el-dialog
    :model-value="modelValue"
    title="下架资质证书"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="onClose"
    @close="resetState"
  >
    <div class="retire-dialog__intro" data-testid="qual-retire-meta">
      <p class="retire-dialog__lead">确认下架以下证书？</p>
      <ul class="retire-dialog__meta">
        <li data-testid="qual-retire-meta-name"><span class="retire-dialog__label">证书名称：</span><span class="retire-dialog__value">{{ certificate?.name || '—' }}</span></li>
        <li data-testid="qual-retire-meta-no"><span class="retire-dialog__label">证书号：</span><span class="retire-dialog__value">{{ certificate?.certificateNo || '—' }}</span></li>
      </ul>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent>
      <el-form-item label="下架原因" prop="reason" :error="reasonError">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="请说明下架原因"
          resize="none"
          data-testid="qual-retire-reason"
        />
        <div class="retire-dialog__hint" data-testid="qual-retire-hint">必填，1-200 字符</div>
      </el-form-item>

      <el-form-item prop="confirmed">
        <el-checkbox v-model="form.confirmed" data-testid="qual-retire-confirm">我已确认该证书可以下架</el-checkbox>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button data-testid="qual-retire-cancel" @click="onClose">取消</el-button>
      <el-button
        type="danger"
        :disabled="!canConfirm"
        :loading="loading"
        data-testid="qual-retire-submit"
        @click="onConfirm"
      >确认下架</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  certificate: { type: Object, default: () => null },
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const formRef = ref(null)
const reasonError = ref('')
const form = reactive({ reason: '', confirmed: false })
const rules = {
  reason: [
    { required: true, message: '下架原因必填', trigger: 'blur' },
    { min: 1, max: 200, message: '下架原因长度需在 1-200 字符', trigger: 'blur' }
  ],
  confirmed: [
    {
      validator: (_rule, value, cb) => (value ? cb() : cb(new Error('请勾选确认'))),
      trigger: 'change'
    }
  ]
}

const canConfirm = computed(() => {
  const r = (form.reason || '').trim()
  return r.length >= 1 && r.length <= 200 && form.confirmed === true
})

const resetState = () => {
  form.reason = ''
  form.confirmed = false
  reasonError.value = ''
}

const onClose = () => {
  if (props.loading) return
  emit('update:modelValue', false)
}

const onConfirm = async () => {
  reasonError.value = ''
  const reason = (form.reason || '').trim()
  if (!reason) { reasonError.value = '下架原因必填'; return }
  if (reason.length > 200) { reasonError.value = '下架原因不能超过 200 字符'; return }
  if (!form.confirmed) { ElMessage.warning('请先勾选确认'); return }
  emit('confirm', reason)
}

watch(() => props.modelValue, (open) => {
  if (open) {
    resetState()
    formRef.value?.clearValidate()
  }
})
</script>

<style scoped>
.retire-dialog__intro { margin-bottom: 16px; }
.retire-dialog__lead { margin: 0 0 12px; color: var(--gray-950); font-size: 14px; }
.retire-dialog__meta { list-style: none; margin: 0; padding: 12px 16px; background: var(--bg-muted-2); border-radius: 6px; }
.retire-dialog__meta li { display: flex; gap: 8px; line-height: 1.8; }
.retire-dialog__label { color: var(--gray-650); min-width: 70px; }
.retire-dialog__value { color: var(--gray-950); font-weight: 500; word-break: break-all; }
.retire-dialog__hint { margin-top: 4px; font-size: 12px; color: var(--text-lighter); }
</style>
