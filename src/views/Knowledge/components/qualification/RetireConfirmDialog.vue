<template>
  <el-dialog
    v-model="visible"
    title="下架资质证书"
    width="520px"
    :close-on-click-modal="false"
    data-testid="qual-retire-dialog"
    @closed="handleClosed"
  >
    <div v-if="data" class="retire-dialog-body">
      <div class="confirm-info" data-testid="qual-retire-meta">
        <p class="confirm-title">确认下架以下证书？</p>
        <ul class="cert-info-list">
          <li data-testid="qual-retire-meta-name"><span class="label">证书名称：</span>{{ data.name || '—' }}</li>
          <li data-testid="qual-retire-meta-no"><span class="label">证书号：</span>{{ data.certificateNo || '—' }}</li>
        </ul>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent>
        <el-form-item
          label="下架原因（必填，4-200 字符）"
          :error="reasonError"
        >
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            placeholder="请说明下架原因"
            maxlength="200"
            show-word-limit
            data-testid="qual-retire-reason"
          />
          <div class="retire-hint" data-testid="qual-retire-hint">请输入 4-200 字符的下架原因</div>
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="form.confirmed" data-testid="qual-retire-confirm">
            我已确认该证书可以下架
          </el-checkbox>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button data-testid="qual-retire-cancel" @click="visible = false">取消</el-button>
      <el-button
        type="danger"
        :disabled="!canSubmit"
        :loading="submitting"
        data-testid="qual-retire-submit"
        @click="handleSubmit"
      >
        确认下架
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  data: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'confirm', 'closed'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const form = ref({ reason: '', confirmed: false })
const trimmedReason = computed(() => form.value.reason?.trim() || '')
const reasonError = computed(() => {
  if (trimmedReason.value.length > 0 && trimmedReason.value.length < 4) return '下架原因不少于4个字'
  if (trimmedReason.value.length > 200) return '下架原因不超过200字'
  return ''
})
const submitting = ref(false)

const canSubmit = computed(() => {
  return trimmedReason.value.length >= 4 && trimmedReason.value.length <= 200 && form.value.confirmed
})

watch(() => props.modelValue, (val) => {
  if (val) {
    form.value = { reason: '', confirmed: false }
    submitting.value = false
  }
})

const handleSubmit = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    await emit('confirm', { id: props.data?.id, reason: form.value.reason?.trim() })
    visible.value = false
  } finally {
    submitting.value = false
  }
}

const handleClosed = () => {
  emit('closed')
}
</script>

<style scoped lang="scss">
.retire-dialog-body {
  padding: 0 8px;
}
.confirm-info {
  margin-bottom: 20px;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.confirm-title {
  font-weight: 500;
  margin: 0 0 12px;
  color: var(--el-text-color-primary);
}
.cert-info-list {
  list-style: none;
  padding: 0;
  margin: 0;
  li {
    margin-bottom: 6px;
    color: var(--el-text-color-regular);
  }
  .label {
    color: var(--el-text-color-secondary);
  }
}
</style>
