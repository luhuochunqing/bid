<template>
  <el-dialog v-model="visible" title="登记归还" width="520px">
    <el-form :model="form" label-width="120px" :rules="rules" ref="formRef">
      <el-form-item label="平台信息">
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="平台名称">{{ account?.platform || '-' }}</el-descriptions-item>
          <el-descriptions-item label="网址">{{ account?.url || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag type="warning">使用中</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-form-item>
      <el-form-item label="实际归还时间">
        <el-date-picker v-model="form.actualReturnTime" type="datetime"
          placeholder="选择时间" disabled style="width:100%" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword" required>
        <el-input v-model="form.newPassword" type="password" show-password
          placeholder="请输入新密码（至少6位）" maxlength="50" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword" required>
        <el-input v-model="form.confirmPassword" type="password" show-password
          placeholder="请再次输入新密码" maxlength="50" />
      </el-form-item>
      <el-form-item label="归还备注" prop="remarks">
        <el-input v-model="form.remarks" type="textarea" :rows="2"
          placeholder="可选，填写归还说明" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit" :loading="submitting">确认归还</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { resourcesApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  account: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'submitted'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const formRef = ref(null)
const submitting = ref(false)

const empty = () => ({
  actualReturnTime: new Date(),
  newPassword: '',
  confirmPassword: '',
  remarks: ''
})
const form = ref(empty())

const rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.value.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

watch(() => props.account, () => {
  form.value = empty()
})

const submit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!props.account) return

  submitting.value = true
  try {
    const payload = {
      newPassword: form.value.newPassword,
      remarks: form.value.remarks.trim() || ''
    }
    const res = await resourcesApi.accounts.returnWithPassword(props.account.id, payload)
    if (!res?.success) {
      ElMessage.error(res?.msg || '归还失败')
      return
    }
    ElMessage.success('账号已归还，密码已更新')
    visible.value = false
    emit('submitted')
  } finally {
    submitting.value = false
  }
}
</script>
