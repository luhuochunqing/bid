<template>
  <el-dialog
    v-model="visible"
    title="登记归还"
    width="500px"
    destroy-on-close
    top="10vh"
  >
    <el-alert
      title="归还确认"
      type="warning"
      :closable="false"
      show-icon
      class="return-alert"
    >
      <template #default>
        <p>确认归还该 CA 证书？请核实以下信息后提交。</p>
      </template>
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" size="default" class="return-form">
      <el-form-item label="CA 证书">
        <el-input :model-value="caLabel" disabled />
      </el-form-item>

      <el-form-item label="借用人">
        <el-input :model-value="props.ca?.currentBorrowerName || '-'" disabled />
      </el-form-item>

      <el-form-item label="借用申请" prop="applicationId">
        <el-select
          v-model="form.applicationId"
          placeholder="请选择要归还的借用记录"
          style="width: 100%"
        >
          <el-option
            v-for="app in activeApplications"
            :key="app.id"
            :label="`${app.purpose || '借用'} - ${app.borrowDate || ''}`"
            :value="app.id"
          />
        </el-select>
        <div v-if="activeApplications.length === 0" class="form-help">
          未找到进行中的借用记录。如已归还，请刷新页面查看。
        </div>
      </el-form-item>

      <el-form-item label="实际归还日期" prop="actualReturnDate">
        <el-date-picker
          v-model="form.actualReturnDate"
          type="date"
          placeholder="请选择实际归还日期"
          value-format="YYYY-MM-DD"
          style="width: 100%"
          :disabled-date="disabledDate"
        />
      </el-form-item>

      <el-form-item label="归还备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="归还备注（可选）"
          maxlength="300"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="activeApplications.length === 0"
        @click="handleSubmit"
      >确认归还</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  ca: { type: Object, default: null },
  borrowApplications: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const formRef = ref(null)

const caLabel = computed(() => {
  const ca = props.ca
  if (!ca) return ''
  // CO-466: 优先使用后端 enrich 的 caName（与审批/借用列表一致）
  if (ca.caName) return ca.caName
  // fallback 到拼装逻辑（与 CABorrowDialog caLabel 格式统一）
  const platforms = Array.isArray(ca.platformIds) && ca.platformIds.length
    ? ca.platformIds.map(p => (typeof p === 'object' ? p.accountName : `#${p}`)).join(', ')
    : ''
  const seal = ca.sealTypeLabel || ca.sealType || ''
  const holder = ca.holderName || ''
  return [holder, platforms, seal].filter(Boolean).join(' / ')
})

// Only show APPROVED borrow applications (PENDING/RETURNED excluded)
const activeApplications = computed(() => {
  return props.borrowApplications.filter(a => a.status === 'APPROVED')
})

function createDefaultForm() {
  return {
    applicationId: '',
    actualReturnDate: new Date().toISOString().slice(0, 10),
    remark: ''
  }
}

const form = reactive(createDefaultForm())

watch(() => props.modelValue, (v) => {
  if (v) {
    Object.assign(form, createDefaultForm())
    // Auto-select if only one active application
    if (activeApplications.value.length === 1) {
      form.applicationId = activeApplications.value[0].id
    }
    formRef.value?.resetFields?.()
  }
})

const rules = {
  applicationId: [
    { required: true, message: '请选择要归还的借用记录', trigger: 'change' }
  ],
  actualReturnDate: [
    { required: true, message: '请选择实际归还日期', trigger: 'change' }
  ]
}

function disabledDate(date) {
  // Cannot select future dates for actual return
  return date.getTime() > Date.now()
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const submitData = {
    applicationId: form.applicationId,
    actualReturnDate: form.actualReturnDate,
    remark: form.remark || undefined
  }

  emit('submit', submitData)
}
</script>

<style scoped>
.return-alert {
  margin-bottom: 20px;
}

.return-form {
  margin-top: 0;
}

.form-help {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
