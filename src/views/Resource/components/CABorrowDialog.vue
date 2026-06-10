<template>
  <el-dialog
    v-model="visible"
    title="申请借用 CA 证书"
    width="560px"
    destroy-on-close
    top="5vh"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" size="default">
      <el-form-item label="CA 证书">
        <el-input :model-value="caLabel" disabled />
      </el-form-item>

      <el-form-item label="使用目的" prop="purpose">
        <el-input
          v-model="form.purpose"
          type="textarea"
          :rows="2"
          placeholder="请简述使用目的（最多500字符）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="关联项目" prop="projectId">
        <el-select
          v-model="form.projectId"
          filterable
          clearable
          placeholder="选择已立项的项目（可选）"
          style="width: 100%"
        >
          <el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="借用期限" prop="borrowDurationType">
        <el-radio-group v-model="form.borrowDurationType">
          <el-radio value="SHORT_TERM">短期</el-radio>
          <el-radio value="LONG_TERM">长期</el-radio>
        </el-radio-group>
        <div class="form-help">
          短期需在预计归还日前归还；长期需上传盖章承诺书，审批通过后 CA 保管人直接变更为申请人。
        </div>
      </el-form-item>

      <el-form-item
        v-if="form.borrowDurationType === 'SHORT_TERM'"
        label="预计归还日期"
        prop="expectedReturnDate"
      >
        <el-date-picker
          v-model="form.expectedReturnDate"
          type="date"
          placeholder="请选择预计归还日期"
          value-format="YYYY-MM-DD"
          style="width: 100%"
          :disabled-date="disabledDate"
        />
      </el-form-item>

      <el-form-item
        v-if="form.borrowDurationType === 'LONG_TERM'"
        label="盖章承诺书"
        prop="commitmentLetterUrl"
      >
        <el-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          :show-file-list="false"
          :before-upload="beforeUpload"
          :on-success="onUploadSuccess"
          :on-error="onUploadError"
          accept=".pdf,.jpg,.jpeg,.png"
        >
          <el-button type="primary" plain>上传盖章承诺书</el-button>
        </el-upload>
        <div v-if="form.commitmentLetterUrl" class="form-help">
          ✓ 已上传：<a :href="form.commitmentLetterUrl" target="_blank">查看附件</a>
        </div>
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="2"
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { projectsApi } from '@/api'
import httpClient from '@/api/client'
import { useUserStore } from '@/stores/user'

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
const projectOptions = ref([])
const userStore = useUserStore()

const caLabel = computed(() => {
  const ca = props.ca
  if (!ca) return ''
  const platforms = Array.isArray(ca.platformIds) && ca.platformIds.length
    ? ca.platformIds.map(p => (typeof p === 'object' ? p.accountName : `#${p}`)).join(', ')
    : ''
  const seal = ca.sealTypeLabel || ca.sealType || ''
  const holder = ca.holderName || ''
  return [holder, platforms, seal].filter(Boolean).join(' / ')
})

const uploadUrl = computed(() => {
  const base = httpClient.defaults?.baseURL || ''
  return `${base}/api/uploads/commitment-letter`
})
const uploadHeaders = computed(() => ({
  Authorization: userStore.token ? `Bearer ${userStore.token}` : ''
}))

function createDefaultForm() {
  return {
    purpose: '',
    projectId: null,
    borrowDurationType: 'SHORT_TERM',
    expectedReturnDate: '',
    commitmentLetterUrl: '',
    remark: ''
  }
}

const form = reactive(createDefaultForm())

async function loadProjects() {
  try {
    const res = await projectsApi.getList({})
    const list = Array.isArray(res?.data) ? res.data : []
    projectOptions.value = list
      .map(p => ({ id: p.id, name: p.name || p.projectName || `项目#${p.id}` }))
      .filter(p => p.id != null)
  } catch { projectOptions.value = [] }
}

onMounted(loadProjects)

watch(() => props.modelValue, (v) => {
  if (v) {
    Object.assign(form, createDefaultForm())
    formRef.value?.resetFields?.()
  }
})

const rules = {
  purpose: [
    { required: true, message: '请填写使用目的', trigger: 'blur' }
  ],
  borrowDurationType: [
    { required: true, message: '请选择借用期限', trigger: 'change' }
  ],
  expectedReturnDate: [
    {
      validator: (_rule, _value, callback) => {
        if (form.borrowDurationType === 'SHORT_TERM' && !form.expectedReturnDate) {
          callback(new Error('短期借用必须选择预计归还日期'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  commitmentLetterUrl: [
    {
      validator: (_rule, _value, callback) => {
        if (form.borrowDurationType === 'LONG_TERM' && !form.commitmentLetterUrl) {
          callback(new Error('长期借用必须上传盖章承诺书'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

function disabledDate(date) {
  return date.getTime() < Date.now() - 8.64e7
}

function beforeUpload(file) {
  const maxMb = 10
  if (file.size / 1024 / 1024 > maxMb) {
    ElMessage.error(`文件大小不能超过 ${maxMb}MB`)
    return false
  }
  return true
}

function onUploadSuccess(response) {
  if (response?.success && response?.data?.url) {
    form.commitmentLetterUrl = response.data.url
    ElMessage.success('盖章承诺书已上传')
  } else {
    ElMessage.error(response?.msg || '上传失败')
  }
}

function onUploadError() {
  ElMessage.error('盖章承诺书上传失败，请重试')
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const submitData = {
    purpose: form.purpose,
    projectId: form.projectId || null,
    borrowDurationType: form.borrowDurationType,
    expectedReturnDate: form.borrowDurationType === 'SHORT_TERM' ? form.expectedReturnDate : null,
    commitmentLetterUrl: form.borrowDurationType === 'LONG_TERM' ? form.commitmentLetterUrl : null,
    remark: form.remark || undefined
  }

  emit('submit', submitData)
}
</script>

<style scoped>
.form-help {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
