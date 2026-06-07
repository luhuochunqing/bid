<template>
  <el-dialog v-model="visible" :title="isEdit ? '编辑账户' : '新增平台'" width="620px" @open="onOpen">
    <el-form :model="form" label-width="110px">
      <el-form-item label="平台名称" required>
        <el-input v-model="form.accountName" placeholder="请输入投标平台名称" maxlength="100" />
      </el-form-item>
      <el-form-item label="网址" required>
        <el-input v-model="form.url" placeholder="平台官网或登录入口 URL" maxlength="500" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="平台账号" required>
            <el-input v-model="form.username" placeholder="请输入平台账号" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="平台密码" required>
            <el-input v-model="form.password" type="password" show-password
              :placeholder="isEdit ? '留空则不修改密码' : '请输入平台密码'" maxlength="200" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="绑定联系人" required>
            <el-input v-model="form.contactPerson" placeholder="建议格式：姓名（工号）" maxlength="200" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="绑定手机" required>
            <el-input v-model="form.contactPhone" placeholder="自动带入员工资料" maxlength="20" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="绑定邮箱" required>
            <el-input v-model="form.contactEmail" placeholder="自动带入员工资料" maxlength="200" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="平台类型">
            <el-select v-model="form.platformType" placeholder="请选择" style="width:100%">
              <el-option label="投标平台" value="BID_PLATFORM" />
              <el-option label="采购平台" value="PROCUREMENT_PLATFORM" />
              <el-option label="政府平台" value="GOVERNMENT_PLATFORM" />
              <el-option label="其他平台" value="OTHER" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="是否有 CA">
            <el-switch v-model="form.hasCa" active-text="是" inactive-text="否" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="CA 保管人" required>
            <el-select v-model="form.caCustodian" placeholder="请选择投标部门人员"
              style="width:100%" filterable clearable>
              <el-option v-for="u in biddingUsers" :key="u.id" :label="u.name" :value="u.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="备注">
        <el-input v-model="form.remarks" type="textarea" :rows="2" placeholder="自由备注" maxlength="500" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { resourcesApi } from '@/api'
import httpClient from '@/api/client'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const isEdit = computed(() => !!props.editRow?.id)

const emptyForm = () => ({
  accountName: '', platformType: 'BID_PLATFORM', username: '', password: '',
  url: '', contactPerson: '', contactPhone: '', contactEmail: '',
  hasCa: false, caCustodian: null, remarks: ''
})

const form = ref(emptyForm())
const biddingUsers = ref([])

const loadBiddingUsers = async () => {
  try {
    const res = await httpClient.get('/api/admin/users')
    if (res?.data) {
      biddingUsers.value = (Array.isArray(res.data) ? res.data : [])
        .map(u => ({ id: u.id, name: u.fullName || u.username }))
    }
  } catch { /* silent */ }
}

const onOpen = () => {
  const r = props.editRow?.raw || props.editRow || {}
  if (r.id) {
    form.value = {
      accountName: r.accountName || r.platform || '',
      platformType: r.platformType || 'BID_PLATFORM',
      username: r.username || '', password: '',
      url: r.url || '', contactPerson: r.contactPerson || '',
      contactPhone: r.contactPhone || '', contactEmail: r.contactEmail || '',
      hasCa: r.hasCa || false, caCustodian: r.caCustodian || null,
      remarks: r.remarks || '' }
  } else {
    form.value = emptyForm()
  }
  loadBiddingUsers()
}

const submit = async () => {
  const f = form.value
  const payload = {
    accountName: f.accountName.trim(), platformType: f.platformType,
    username: f.username.trim(), url: f.url.trim(),
    contactPerson: f.contactPerson.trim(), contactPhone: f.contactPhone.trim(),
    contactEmail: f.contactEmail.trim(), hasCa: f.hasCa,
    caCustodian: f.caCustodian, remarks: f.remarks?.trim() || '' }
  if (f.password) payload.password = f.password

  if (!payload.accountName || !payload.username || !payload.url
      || !payload.contactPerson || !payload.contactPhone || !payload.contactEmail) {
    ElMessage.warning('请完整填写必填字段')
    return
  }
  if (!isEdit.value && !payload.password) {
    ElMessage.warning('请填写密码'); return
  }

  let res
  if (isEdit.value) {
    res = await resourcesApi.accounts.update(props.editRow.id, payload)
  } else {
    payload.password = f.password
    res = await resourcesApi.accounts.create(payload)
  }
  if (!res?.success) {
    ElMessage.error(res?.msg || (isEdit.value ? '编辑失败' : '新增失败'))
    return
  }
  ElMessage.success(isEdit.value ? '账户已更新' : '账户已新增')
  visible.value = false
  emit('saved')
}
</script>
