<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="关仓确认"
    width="520px"
    :close-on-click-modal="false"
    :before-close="handleClose"
    data-testid="warehouse-close-dialog"
  >
    <div v-if="warehouse" class="close-confirm">
      <div class="info-block">
        <div class="info-title">确认关闭以下仓库？</div>
        <ul class="info-list">
          <li><span class="info-label">仓库名称：</span><span class="info-value">{{ warehouse.name }}</span></li>
          <li><span class="info-label">所属区域：</span><span class="info-value">{{ warehouse.region || '—' }}</span></li>
          <li><span class="info-label">仓库类型：</span><span class="info-value">{{ typeLabel }}</span></li>
        </ul>
      </div>
      <el-alert
        title="关仓后该仓库将不再出现在默认列表中，且不再接收到期提醒。"
        type="warning"
        :closable="false"
        show-icon
        class="warning-alert"
      />
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" class="close-form">
        <el-form-item label="关仓原因" prop="reason" required>
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="如：合同到期不再续约、业务调整等"
          />
        </el-form-item>
        <el-form-item prop="confirmed">
          <el-checkbox v-model="form.confirmed">我已确认该仓库可以关闭</el-checkbox>
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="danger" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">确认关仓</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, reactive, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/client'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  warehouse: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'closed'])

const formRef = ref()
const submitting = ref(false)
const form = reactive({ reason: '', confirmed: false })

const rules = {
  reason: [
    { required: true, message: '关仓原因不能为空', trigger: 'blur' },
    { min: 1, max: 500, message: '关仓原因不能超过500字符', trigger: 'blur' }
  ],
  confirmed: [
    {
      validator: (_, value, cb) => value ? cb() : cb(new Error('请勾选确认')),
      trigger: 'change'
    }
  ]
}

const typeLabel = computed(() => {
  const t = props.warehouse?.type
  if (t === 'SELF_OPERATED') return '自营'
  if (t === 'CLOUD') return '云仓'
  return t || '—'
})

const canSubmit = computed(() => form.reason.trim().length > 0 && form.confirmed)

const reset = () => {
  form.reason = ''
  form.confirmed = false
  if (formRef.value) formRef.value.clearValidate()
}

const handleClose = () => {
  reset()
  emit('update:modelValue', false)
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!props.warehouse?.id) {
    ElMessage.error('仓库信息不完整')
    return
  }
  submitting.value = true
  try {
    await http.post(`/api/knowledge/warehouses/${props.warehouse.id}/close`, { reason: form.reason.trim() })
    ElMessage.success('已关仓')
    emit('closed', props.warehouse.id)
    handleClose()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '关仓失败')
  } finally {
    submitting.value = false
  }
}

watch(() => props.modelValue, (v) => {
  if (v) {
    reset()
    nextTick(() => formRef.value?.clearValidate())
  }
})
</script>

<style scoped>
.close-confirm { padding: 4px 0; }
.info-block { margin-bottom: 14px; }
.info-title { font-weight: 600; color: #303133; margin-bottom: 10px; }
.info-list { margin: 0; padding: 0; list-style: none; }
.info-list li { line-height: 1.9; font-size: 14px; }
.info-label { display: inline-block; min-width: 80px; color: var(--el-text-color-secondary); }
.info-value { color: var(--el-text-color-regular); font-weight: 500; }
.warning-alert { margin-bottom: 14px; }
.close-form { padding-top: 4px; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; }
:deep(.el-form-item.is-error .el-checkbox) { color: var(--el-color-danger); }
</style>
