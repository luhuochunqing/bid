<template>
  <el-dialog v-model="visible" title="申请使用" width="540px">
    <el-form :model="form" label-width="110px">
      <el-form-item label="平台信息">
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="平台名称">{{ account?.platform || '-' }}</el-descriptions-item>
          <el-descriptions-item label="网址">{{ account?.url || '-' }}</el-descriptions-item>
          <el-descriptions-item label="账号保管员">{{ account?.custodian || account?.caCustodianName || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-form-item>
      <el-form-item label="使用目的" required>
        <el-input v-model="form.purpose" type="textarea" :rows="2"
          placeholder="例：参与北京医院 IT 项目投标" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="关联项目">
        <el-select v-model="form.projectId" placeholder="请选择已立项的项目" clearable style="width:100%">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="预计归还日期" required>
        <el-date-picker v-model="form.returnDate" type="date"
          placeholder="选择日期" :disabled-date="d => d < new Date(new Date().setHours(0,0,0,0))" style="width:100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { resourcesApi } from '@/api'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  account: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'submitted'])

const userStore = useUserStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const projects = ref([])

const empty = () => ({ purpose: '', projectId: null, returnDate: '' })
const form = ref(empty())

watch(() => props.account, async (acc) => {
  form.value = empty()
  if (acc) {
    try {
      const res = await resourcesApi.accounts.getList({})
      if (res?.data) {
        projects.value = res.data.filter(p => p.projectId).map(p => ({
          id: p.projectId, name: p.project || `项目#${p.projectId}`
        }))
      }
    } catch { projects.value = [] }
  }
})

const submit = async () => {
  if (!form.value.purpose.trim()) { ElMessage.warning('请填写使用目的'); return }
  if (!form.value.returnDate) { ElMessage.warning('请选择预计归还日期'); return }
  if (!props.account) return

  const payload = {
    borrowedBy: Number(userStore.currentUser?.id || 0),
    purpose: form.value.purpose.trim(),
    projectId: form.value.projectId,
    expectedReturnDate: form.value.returnDate
  }
  const res = await resourcesApi.accounts.borrow(props.account.id, payload)
  if (!res?.success) {
    ElMessage.error(res?.msg || '申请提交失败'); return
  }
  ElMessage.success('申请已提交，等待保管员审批')
  visible.value = false
  emit('submitted')
}
</script>
