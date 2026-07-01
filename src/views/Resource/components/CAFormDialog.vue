<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑 CA 证书' : '新增 CA 证书'"
    width="600px"
    destroy-on-close
    top="5vh"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" size="default">
      <el-form-item label="关联平台" prop="platformIds">
        <el-select
          v-model="form.platformIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          :max-collapse-tags="3"
          :loading="platformOptionsLoading"
          :remote-method="searchPlatforms"
          remote
          reserve-keyword
          placeholder="请选择已存在的投标平台（可多选）"
          style="width: 100%"
        >
          <el-option
            v-for="p in platformOptions"
            :key="p.id"
            :label="`${p.accountName || p.platform}（#${p.id}）`"
            :value="p.id"
          />
        </el-select>
        <div class="form-help">支持多选（公章CA可在多个平台共用）；只能选择已有的投标平台账号。</div>
      </el-form-item>

      <el-form-item label="CA 类型" prop="caType">
        <el-select v-model="form.caType" placeholder="请选择 CA 类型" style="width: 100%">
          <el-option label="实体CA" value="ENTITY_CA" />
          <el-option label="电子CA" value="ELECTRONIC_CA" />
        </el-select>
      </el-form-item>

      <el-form-item label="印章类型" prop="sealType">
        <el-select v-model="form.sealType" placeholder="请选择印章类型" style="width: 100%">
          <el-option label="公章" value="OFFICIAL_SEAL" />
          <el-option label="法人章" value="LEGAL_PERSON_SEAL" />
          <el-option label="法人签字" value="LEGAL_SIGN" />
          <el-option label="联系人签字" value="CONTACT_SIGN" />
        </el-select>
      </el-form-item>

      <el-form-item
        v-if="form.caType === 'ELECTRONIC_CA'"
        label="电子账号"
        prop="electronicAccount"
      >
        <el-input
          v-model="form.electronicAccount"
          placeholder="请输入电子CA账号"
          maxlength="200"
        />
      </el-form-item>

      <el-form-item label="CA 密码" prop="caPassword">
        <div class="password-input-wrapper">
          <el-input
            v-model="form.caPassword"
            :type="passwordRevealed ? 'text' : 'password'"
            placeholder="请输入 CA 密码（将加密存储）"
            maxlength="100"
            :show-password="!canViewPassword"
            style="flex: 1"
          />
          <el-button v-if="canViewPassword" class="password-toggle-btn" link type="primary" :loading="passwordLoading" @click="handleRevealPassword">
            <el-icon><View v-if="!passwordRevealed" /><Hide v-else /></el-icon>
          </el-button>
        </div>
        <div class="form-help">{{ isEdit ? '留空则不修改密码；点击眼睛图标可查看当前密码' : '请输入 CA 密码（将加密存储）' }}</div>
      </el-form-item>

      <el-form-item label="有效期至" prop="expiryDate">
        <el-date-picker
          v-model="form.expiryDate"
          type="date"
          placeholder="请选择有效期"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="颁发机构" prop="issuer">
        <el-input v-model="form.issuer" placeholder="请输入颁发机构" maxlength="100" />
      </el-form-item>

      <el-form-item label="持有人" prop="holderName">
        <el-input v-model="form.holderName" placeholder="请输入持有人姓名" maxlength="100" />
      </el-form-item>

      <el-form-item label="平台地址/APP" prop="caPlatformUrl">
        <el-input
          v-model="form.caPlatformUrl"
          placeholder="平台URL或APP名称"
          maxlength="500"
        />
      </el-form-item>

      <el-form-item label="保管员" prop="custodianId">
        <!-- CO-451: 编辑模式下传入 initialOptions 以显示"姓名（工号）"格式 -->
        <UserPicker
          v-model="form.custodianId"
          mode="search"
          :initial-options="custodianInitialOptions"
          placeholder="搜索选择保管员"
          clearable
          style="width: 100%"
          @select="onCustodianSelect"
        />
      </el-form-item>

      <el-form-item label="备注" prop="remarks">
        <el-input
          v-model="form.remarks"
          type="textarea"
          :rows="3"
          placeholder="备注信息（可选）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="状态" prop="status">
        <template v-if="!isEdit">
          <span class="form-help">新建默认有效，系统自动计算到期状态</span>
        </template>
        <template v-else>
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="有效" value="ACTIVE" />
            <el-option label="已下架" value="INACTIVE" />
          </el-select>
        </template>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { usePlatformAccountSearch } from '@/composables/usePlatformAccountSearch.js'
import UserPicker from '@/components/common/UserPicker.vue'
import { View, Hide } from '@element-plus/icons-vue'
import { useCaPasswordReveal } from '../composables/useCaPasswordReveal'

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

const isEdit = computed(() => !!props.ca?.id)
const formRef = ref(null)
const { platformOptions, platformOptionsLoading, searchPlatforms } = usePlatformAccountSearch()

function createDefaultForm() {
  return {
    platformIds: [],
    caType: 'ENTITY_CA',
    sealType: 'OFFICIAL_SEAL',
    electronicAccount: '',
    caPassword: '',
    expiryDate: '',
    issuer: '',
    holderName: '',
    caPlatformUrl: '',
    custodianName: '',
    custodianId: '',
    // CO-451: 保管人工号，用于构造 initialOptions
    custodianEmployeeNumber: '',
    status: 'ACTIVE',
    remarks: ''
  }
}
const form = reactive(createDefaultForm())

const { passwordRevealed, passwordLoading, canViewPassword, resetPasswordState, handleRevealPassword } =
  useCaPasswordReveal(isEdit, computed(() => props.ca), form)

function onCustodianSelect(user) {
  if (user) {
    form.custodianName = user.name || user.fullName || ''
  }
}

// Watch external data changes
watch(() => props.ca, (ca) => {
  resetPasswordState()  // 重置密码显示状态
  if (ca) {
    form.id = ca.id
    form.platformIds = Array.isArray(ca.platformIds)
      ? ca.platformIds.map(v => Number(v)).filter(v => Number.isFinite(v))
      : []
    // Pre-populate the dropdown with the linked platforms so the labels render
    if (form.platformIds.length) {
      platformOptions.value = form.platformIds.map(id => ({ id, accountName: ca.platformNamesById?.[id] || `平台 #${id}` }))
    }
    form.caType = ca.caType || 'ENTITY_CA'
    form.sealType = ca.sealType || 'OFFICIAL_SEAL'
    form.electronicAccount = ca.electronicAccount || ''
    form.caPassword = ''
    form.expiryDate = ca.expiryDate || ''
    form.issuer = ca.issuer || ''
    form.holderName = ca.holderName || ''
    form.caPlatformUrl = ca.caPlatformUrl || ''
    form.custodianId = ca.custodianId || ''
    form.custodianName = ca.custodianName || ''
    // CO-451: 填充保管人工号
    form.custodianEmployeeNumber = ca.custodianEmployeeNumber || ''
    form.status = ca.status || 'ACTIVE'
    form.remarks = ca.remarks || ca.remark || ''
  } else {
    Object.assign(form, createDefaultForm())
    delete form.id
    platformOptions.value = []
  }
  if (visible.value) searchPlatforms('')
}, { immediate: true })

watch(visible, (open) => {
  if (open) searchPlatforms('')
})

// CO-451: 编辑模式下为 UserPicker 提供 initialOptions（用于显示"姓名（工号）"格式）
const custodianInitialOptions = computed(() => {
  if (!props.ca?.custodianId) return []
  return [{
    id: props.ca.custodianId,
    name: props.ca.custodianName,
    fullName: props.ca.custodianName,
    employeeNumber: props.ca.custodianEmployeeNumber || ''
  }]
})

// When CA type changes to ENTITY_CA, clear electronic account
watch(() => form.caType, (val) => {
  if (val !== 'ELECTRONIC_CA') {
    form.electronicAccount = ''
  }
})

const rules = computed(() => ({
  caType: [{ required: true, message: '请选择 CA 类型', trigger: 'change' }],
  sealType: [{ required: true, message: '请选择印章类型', trigger: 'change' }],
  electronicAccount: [{ required: true, message: '电子CA必须填写账号', trigger: 'blur' }],
  caPassword: form.caType === 'ENTITY_CA'
    ? [{ required: true, message: '实体CA必须填写密码', trigger: 'blur' }]
    : [],
  expiryDate: [{ required: true, message: '请选择有效期', trigger: 'change' }],
  custodianId: [{ required: true, message: '请选择保管员', trigger: 'change' }]
}))

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // Send platformIds as a JSON array (backend List<Long>)
  const submitData = {
    ...form,
    platformIds: form.platformIds.map(v => Number(v))
  }

  emit('submit', submitData)
}
</script>

<style scoped>
.form-help { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }
.password-input-wrapper { display: flex; align-items: center; gap: 8px; width: 100%; }
.password-toggle-btn { flex-shrink: 0; display: flex; align-items: center; }
</style>
