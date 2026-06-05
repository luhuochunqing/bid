<template>
  <el-dialog
    v-model="visible"
    title="申请借用 CA 证书"
    width="500px"
    destroy-on-close
    top="10vh"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" size="default">
      <el-form-item label="CA 证书">
        <el-input :model-value="caLabel" disabled />
      </el-form-item>

      <el-form-item label="借用用途" prop="purpose">
        <el-select v-model="form.purpose" placeholder="请选择借用用途" style="width: 100%">
          <el-option label="投标使用" value="投标使用" />
          <el-option label="签章使用" value="签章使用" />
          <el-option label="资质审核" value="资质审核" />
          <el-option label="合同签署" value="合同签署" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="form.purpose === '其他'" label="其他用途" prop="purposeOther">
        <el-input
          v-model="form.purposeOther"
          placeholder="请描述借用用途"
          maxlength="200"
        />
      </el-form-item>

      <el-form-item label="关联项目" prop="projectId">
        <el-input
          v-model="form.projectId"
          placeholder="项目ID或名称（可选）"
          maxlength="100"
        />
      </el-form-item>

      <el-form-item label="预计归还日期" prop="expectedReturnDate">
        <el-date-picker
          v-model="form.expectedReturnDate"
          type="date"
          placeholder="请选择预计归还日期"
          value-format="YYYY-MM-DD"
          style="width: 100%"
          :disabled-date="disabledDate"
        />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="备注信息（可选）"
          maxlength="300"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  ca: { type: Object, default: null },
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
  const platforms = Array.isArray(ca.platformIds) && ca.platformIds.length
    ? ca.platformIds.join(', ')
    : ''
  const seal = ca.sealTypeLabel || ''
  return [platforms, seal].filter(Boolean).join(' - ')
})

function createDefaultForm() {
  return {
    purpose: '',
    purposeOther: '',
    projectId: '',
    expectedReturnDate: '',
    remark: ''
  }
}

const form = reactive(createDefaultForm())

watch(() => props.modelValue, (v) => {
  if (v) {
    Object.assign(form, createDefaultForm())
    formRef.value?.resetFields?.()
  }
})

const rules = {
  purpose: [
    { required: true, message: '请选择借用用途', trigger: 'change' }
  ],
  purposeOther: [
    { required: true, message: '请描述借用用途', trigger: 'blur' }
  ],
  expectedReturnDate: [
    { required: true, message: '请选择预计归还日期', trigger: 'change' }
  ]
}

function disabledDate(date) {
  // Cannot select dates before today
  return date.getTime() < Date.now() - 8.64e7
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const submitData = {
    purpose: form.purpose === '其他' ? form.purposeOther : form.purpose,
    projectId: form.projectId || undefined,
    expectedReturnDate: form.expectedReturnDate,
    remark: form.remark || undefined
  }

  emit('submit', submitData)
}
</script>
