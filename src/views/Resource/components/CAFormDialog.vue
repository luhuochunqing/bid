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
        <el-input
          v-model="form.caPassword"
          placeholder="请输入 CA 密码（将加密存储）"
          maxlength="100"
          show-password
          type="password"
        />
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
        <el-select
          v-model="form.custodianId"
          filterable
          remote
          reserve-keyword
          placeholder="搜索选择保管员"
          :remote-method="searchCustodian"
          :loading="custodianSearching"
          style="width: 100%"
          @change="onCustodianSelect"
        >
          <el-option v-for="u in custodianOptions" :key="u.id" :label="u.name" :value="u.id" />
        </el-select>
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
import { usersApi } from '@/api/modules/users.js'
import { usePlatformAccountSearch } from '@/composables/usePlatformAccountSearch.js'

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
const custodianSearching = ref(false)
const custodianOptions = ref([])

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
    status: 'ACTIVE',
    remarks: ''
  }
}

const form = reactive(createDefaultForm())

async function searchCustodian(query) {
  if (!query) return
  custodianSearching.value = true
  try {
    const list = await usersApi.search(query)
    custodianOptions.value = Array.isArray(list) ? list.map(u => ({ id: Number(u.id), name: u.name || u.fullName || u.username })) : []
  } catch { custodianOptions.value = [] }
  finally { custodianSearching.value = false }
}

function onCustodianSelect(id) {
  const selected = custodianOptions.value.find(u => u.id === Number(id))
  if (selected) {
    form.custodianName = selected.name
  }
}

// Watch external data changes
watch(() => props.ca, (ca) => {
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
    form.caPassword = ca.caPassword || ''
    form.expiryDate = ca.expiryDate || ''
    form.issuer = ca.issuer || ''
    form.holderName = ca.holderName || ''
    form.caPlatformUrl = ca.caPlatformUrl || ''
    form.custodianId = ca.custodianId || ''
    if (ca.custodianId && ca.custodianName) {
      custodianOptions.value = [{ id: Number(ca.custodianId), name: ca.custodianName }]
    }
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

// When CA type changes to ENTITY_CA, clear electronic account
watch(() => form.caType, (val) => {
  if (val !== 'ELECTRONIC_CA') {
    form.electronicAccount = ''
  }
})

const validatePlatformIds = (_rule, _value, callback) => {
  if (!Array.isArray(form.platformIds) || form.platformIds.length === 0) {
    callback(new Error('请至少选择一个关联平台'))
  } else {
    callback()
  }
}

const rules = {
  platformIds: [
    { required: true, validator: validatePlatformIds, trigger: 'change' }
  ],
  caType: [
    { required: true, message: '请选择 CA 类型', trigger: 'change' }
  ],
  sealType: [
    { required: true, message: '请选择印章类型', trigger: 'change' }
  ],
  electronicAccount: [
    { required: true, message: '电子CA必须填写账号', trigger: 'blur' }
  ],
  expiryDate: [
    { required: true, message: '请选择有效期', trigger: 'change' }
  ],
  custodianId: [
    { required: true, message: '请选择保管员', trigger: 'change' }
  ]
}

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
.form-help {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
