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
          <el-form-item label="平台密码">
            <el-input v-model="form.password" type="password" show-password
              :placeholder="isEdit ? '留空则不修改密码' : '请输入平台密码（选填）'" maxlength="200"
              @input="onPasswordInput" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="账号保管员" required>
        <UserPicker
          v-model="form.contactPerson"
          mode="search"
          placeholder="模糊搜索选择联系人"
          :initial-options="contactPersonInitialOptions"
          style="width: 100%"
        />
      </el-form-item>
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
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="注册人">
            <el-input v-model="form.registrant" placeholder="注册人姓名" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="注册手机">
            <el-input v-model="form.registerPhone" placeholder="注册手机号" maxlength="20" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="注册邮箱">
            <el-input v-model="form.registerEmail" placeholder="注册邮箱" maxlength="200" />
          </el-form-item>
        </el-col>
      </el-row>
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
import { useUserStore } from '@/stores/user'
import { canRevealPassword, isCurrentUserContactPerson } from './accountActions.js'
import UserPicker from '@/components/common/UserPicker.vue'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const userStore = useUserStore()
const userRoleCode = computed(() => userStore.currentUser?.roleCode || userStore.currentUser?.role || '')
const isBidTeam = computed(() => userRoleCode.value === 'bid-Team')

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const isEdit = computed(() => !!props.editRow?.id)

// CO-522: 跟踪密码是否被用户主动修改。
// 编辑表单打开时会预填明文密码（CO-400 round5），仅靠 form.password 是否为空无法区分
// "预填的旧密码" 和 "用户输入的新密码"。用 dirty 标记确保只有用户真正改动才提交。
const passwordDirty = ref(false)
const onPasswordInput = () => { passwordDirty.value = true }

const emptyForm = () => ({
  accountName: '', username: '', password: '',
  url: '', contactPerson: null,
  hasCa: false, remarks: '',
  registrant: '', registerPhone: '', registerEmail: ''
})

const form = ref(emptyForm())

// 编辑态回显已选联系人：从 editRow.contactPersonLabel 构造 initialOptions，
// 让 UserPicker 在未搜索时也能正确展示已选联系人的"姓名（工号）"标签。
const contactPersonInitialOptions = computed(() => {
  const r = props.editRow?.raw || props.editRow || {}
  if (r.contactPerson && r.contactPersonLabel) {
    return [{ id: r.contactPerson, name: r.contactPersonLabel }]
  }
  return []
})

const onOpen = async () => {
  const r = props.editRow?.raw || props.editRow || {}
  if (r.id) {
    form.value = {
      accountName: r.accountName || r.platform || '',
      username: r.username || '', password: '',
      url: r.url || '', contactPerson: r.contactPerson || null,
      hasCa: r.hasCa || false,
      remarks: r.remarks || '',
      registrant: r.registrant || '',
      registerPhone: r.registerPhone || '',
      registerEmail: r.registerEmail || '' }
    // CO-400 round5 review: 改用 isCurrentUserContactPerson helper 统一判断逻辑
    // （helper 已处理 null/undefined/空字符串边界，避免本组件重复造轮子）
    const isContactPerson = isCurrentUserContactPerson(r, userStore.currentUser)
    const shouldLoadPassword = canRevealPassword({
      isManager: userStore.isBidManager,
      isBidTeam: isBidTeam.value,
      isContactPerson
    })
    if (shouldLoadPassword) {
      try {
        const pwdRes = await resourcesApi.accounts.getPassword(r.id)
        if (pwdRes?.success && pwdRes?.data?.password) {
          form.value.password = pwdRes.data.password
        }
      } catch (e) {
        console.error('Failed to load password for edit:', e)
      }
    }
  } else {
    form.value = emptyForm()
  }
  passwordDirty.value = false  // CO-522: 每次打开表单重置，避免上次编辑的 dirty 状态残留
}

const submit = async () => {
  const f = form.value
  const payload = {
    accountName: f.accountName.trim(),
    username: f.username.trim(), url: f.url.trim(),
    contactPerson: f.contactPerson, hasCa: f.hasCa,
    remarks: f.remarks?.trim() || '',
    registrant: f.registrant?.trim() || '',
    registerPhone: f.registerPhone?.trim() || '',
    registerEmail: f.registerEmail?.trim() || '' }
  if (passwordDirty.value && f.password) payload.password = f.password

  if (!payload.accountName || !payload.username || !payload.url
      || !payload.contactPerson) {
    ElMessage.warning('请完整填写必填字段')
    return
  }
  // CO-567: 平台密码改为非必填，创建时可不填写

  try {
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
  } catch (error) {
    // 429 已由全局 axios interceptor 展示友好提示，业务层不再重复弹窗
    notifyErrorUnlessRateLimit(error, isEdit.value ? '编辑失败' : '新增失败')
  }
}
</script>
