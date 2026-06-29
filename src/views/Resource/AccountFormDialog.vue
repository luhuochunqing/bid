<template>
  <el-dialog v-model="visible" :title="isEdit ? '编辑账户' : '新增平台'" width="620px" @open="onOpen">
    <el-form :model="form" label-width="110px">
      <el-form-item label="平台名称" required :error="accountNameDup ? '平台名称已存在' : ''">
        <el-input v-model="form.accountName" placeholder="请输入投标平台名称" maxlength="100" @blur="checkAccountNameUnique" />
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
            <UserPicker
              v-model="form.contactPerson"
              mode="search"
              placeholder="模糊搜索选择联系人"
              :initial-options="contactPersonInitialOptions"
              style="width: 100%"
              @select="onContactPersonSelected"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="绑定手机" required>
            <el-input v-model="form.contactPhone" placeholder="选择联系人后自动带入" maxlength="20" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="绑定邮箱" required>
            <el-input v-model="form.contactEmail" placeholder="选择联系人后自动带入" maxlength="200" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="平台类型">
            <el-select v-model="form.platformType" placeholder="请选择" style="width:100%">
              <el-option label="投标平台" value="BIDDING_PLATFORM" />
              <el-option label="采购平台" value="CONSTRUCTION_PLATFORM" />
              <el-option label="政府平台" value="GOV_PROCUREMENT" />
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
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { resourcesApi } from '@/api'
import UserPicker from '@/components/common/UserPicker.vue'

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
  accountName: '', platformType: 'BIDDING_PLATFORM', username: '', password: '',
  url: '', contactPerson: null, contactPhone: '', contactEmail: '',
  hasCa: false, remarks: ''
})

const form = ref(emptyForm())
const accountNameDup = ref(false)

// 编辑态回显已选联系人：从 editRow.contactPersonLabel 构造 initialOptions，
// 让 UserPicker 在未搜索时也能正确展示已选联系人的"姓名（工号）"标签。
const contactPersonInitialOptions = computed(() => {
  const r = props.editRow?.raw || props.editRow || {}
  if (r.contactPerson && r.contactPersonLabel) {
    return [{ id: r.contactPerson, name: r.contactPersonLabel }]
  }
  return []
})

// CO-390: 选择联系人后联动回填 phone/email（保持与联系人资料一致）
const onContactPersonSelected = (user) => {
  if (!user) return
  if (user.phone) form.value.contactPhone = user.phone
  if (user.email) form.value.contactEmail = user.email
}

// IJTHNN 修复：accountName 失焦去重校验
const checkAccountNameUnique = async () => {
  const name = (form.value.accountName || '').trim()
  if (!name) { accountNameDup.value = false; return }
  if (isEdit.value && props.editRow?.accountName === name) {
    accountNameDup.value = false; return
  }
  try {
    const res = await resourcesApi.accounts.getList({ accountName: name })
    const list = Array.isArray(res?.data) ? res.data : []
    accountNameDup.value = list.some(a => a.accountName === name)
  } catch { accountNameDup.value = false }
}

const onOpen = () => {
  const r = props.editRow?.raw || props.editRow || {}
  if (r.id) {
    form.value = {
      accountName: r.accountName || r.platform || '',
      platformType: r.platformType || 'BIDDING_PLATFORM',
      username: r.username || '', password: '',
      url: r.url || '', contactPerson: r.contactPerson || null,
      contactPhone: r.contactPhone || '', contactEmail: r.contactEmail || '',
      hasCa: r.hasCa || false,
      remarks: r.remarks || '' }
  } else {
    form.value = emptyForm()
  }
  accountNameDup.value = false
}

const submit = async () => {
  const f = form.value
  if (!isEdit.value) {
    await checkAccountNameUnique()
    if (accountNameDup.value) {
      ElMessage.error('平台名称已存在，请使用其他名称')
      return
    }
  }
  const payload = {
    accountName: f.accountName.trim(), platformType: f.platformType,
    username: f.username.trim(), url: f.url.trim(),
    contactPerson: f.contactPerson, contactPhone: f.contactPhone.trim(),
    contactEmail: f.contactEmail.trim(), hasCa: f.hasCa,
    remarks: f.remarks?.trim() || '' }
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
    if (/Account name already exists/i.test(res?.msg || '')) {
      accountNameDup.value = true
    }
    ElMessage.error(res?.msg || (isEdit.value ? '编辑失败' : '新增失败'))
    return
  }
  ElMessage.success(isEdit.value ? '账户已更新' : '账户已新增')
  visible.value = false
  emit('saved')
}
</script>
