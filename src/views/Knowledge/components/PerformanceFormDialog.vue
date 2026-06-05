<template>
  <el-dialog 
    :model-value="visible" 
    @update:model-value="val => $emit('update:visible', val)"
    :title="isEdit ? '编辑业绩档案' : '新增业绩档案'" 
    width="780px"
    class="premium-dialog"
  >
    <el-tabs v-model="activeFormTab" class="form-tabs">
      <!-- Tab 1: 合同基础信息 -->
      <el-tab-pane label="合同基础" name="base">
        <el-form ref="formRefBase" :model="form" label-width="120px" :rules="rules">
          <PerformanceFormBase :form="form" />
        </el-form>
      </el-tab-pane>

      <!-- Tab 2: 关键日期 -->
      <el-tab-pane label="关键日期" name="dates">
        <el-form ref="formRefDates" :model="form" label-width="120px" :rules="rules">
          <PerformanceFormDates :form="form" />
        </el-form>
      </el-tab-pane>

      <!-- Tab 3: 客户与联系人 -->
      <el-tab-pane label="客户信息" name="contact">
        <el-form ref="formRefContact" :model="form" label-width="120px">
          <PerformanceFormContact :form="form" />
        </el-form>
      </el-tab-pane>

      <!-- Tab 4: 附件资料 -->
      <el-tab-pane label="附件资料" name="attachments">
        <el-form ref="formRefAttachments" :model="form" label-width="120px">
          <PerformanceFormAttachments :form="form" />
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="$emit('update:visible', false)">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">保存档案</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PerformanceFormBase from './PerformanceFormBase.vue'
import PerformanceFormDates from './PerformanceFormDates.vue'
import PerformanceFormContact from './PerformanceFormContact.vue'
import PerformanceFormAttachments from './PerformanceFormAttachments.vue'

const props = defineProps({
  visible: Boolean,
  data: Object,
  submitting: Boolean
})

const emit = defineEmits(['update:visible', 'submit'])

const isEdit = ref(false)
const activeFormTab = ref('base')
const formRefBase = ref(null)
const formRefDates = ref(null)

const form = ref({
  id: null,
  contractName: '',
  signingEntity: '',
  groupCompany: '',
  customerType: '',
  industry: '',
  projectType: '',
  dockingMethod: '',
  customerLevel: 'GROUP',
  signingDate: '',
  expiryDate: '',
  totalExpiryDate: '',
  contactPerson: '',
  contactInfo: '',
  territory: '',
  customerAddress: '',
  xiyuProjectManager: '',
  mallWebsiteUrl: '',
  hasBidNotice: false,
  remarks: '',
  attachmentMap: {
    CONTRACT_AGREEMENT: { fileName: '', fileUrl: '' },
    MALL_SCREENSHOT: { fileName: '', fileUrl: '' },
    SOE_DIRECTORY: { fileName: '', fileUrl: '' },
    CATEGORY_PAGE: { fileName: '', fileUrl: '' },
    RELATIONSHIP_PROOF: { fileName: '', fileUrl: '' },
    BID_NOTICE: { fileName: '', fileUrl: '' },
    OTHER: { fileName: '', fileUrl: '' }
  }
})

const rules = {
  contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }],
  signingEntity: [{ required: true, message: '请输入签约单位', trigger: 'blur' }],
  groupCompany: [{ required: true, message: '请输入集团公司名称', trigger: 'blur' }],
  signingDate: [{ required: true, message: '请选择签约日期', trigger: 'change' }],
  expiryDate: [{ required: true, message: '请选择截止日期', trigger: 'change' }]
}

watch(() => props.visible, (val) => {
  if (val) {
    activeFormTab.value = 'base'
    isEdit.value = !!props.data
    initForm()
  }
})

const initForm = () => {
  if (props.data) {
    const map = {
      CONTRACT_AGREEMENT: { fileName: '', fileUrl: '' },
      MALL_SCREENSHOT: { fileName: '', fileUrl: '' },
      SOE_DIRECTORY: { fileName: '', fileUrl: '' },
      CATEGORY_PAGE: { fileName: '', fileUrl: '' },
      RELATIONSHIP_PROOF: { fileName: '', fileUrl: '' },
      BID_NOTICE: { fileName: '', fileUrl: '' },
      OTHER: { fileName: '', fileUrl: '' }
    }
    if (props.data.attachments) {
      props.data.attachments.forEach(a => {
        if (map[a.fileType]) {
          map[a.fileType] = { fileName: a.fileName, fileUrl: a.fileUrl, id: a.id }
        }
      })
    }
    form.value = {
      ...props.data,
      attachmentMap: map
    }
  } else {
    form.value = {
      id: null,
      contractName: '',
      signingEntity: '',
      groupCompany: '',
      customerType: '',
      industry: '',
      projectType: '',
      dockingMethod: '',
      customerLevel: 'GROUP',
      signingDate: '',
      expiryDate: '',
      totalExpiryDate: '',
      contactPerson: '',
      contactInfo: '',
      territory: '',
      customerAddress: '',
      xiyuProjectManager: '',
      mallWebsiteUrl: '',
      hasBidNotice: false,
      remarks: '',
      attachmentMap: {
        CONTRACT_AGREEMENT: { fileName: '', fileUrl: '' },
        MALL_SCREENSHOT: { fileName: '', fileUrl: '' },
        SOE_DIRECTORY: { fileName: '', fileUrl: '' },
        CATEGORY_PAGE: { fileName: '', fileUrl: '' },
        RELATIONSHIP_PROOF: { fileName: '', fileUrl: '' },
        BID_NOTICE: { fileName: '', fileUrl: '' },
        OTHER: { fileName: '', fileUrl: '' }
      }
    }
  }
}

const validateForm = async () => {
  let baseValid = false
  let datesValid = false

  await formRefBase.value?.validate(valid => { baseValid = valid })
  if (!baseValid) {
    activeFormTab.value = 'base'
    return false
  }

  await formRefDates.value?.validate(valid => { datesValid = valid })
  if (!datesValid) {
    activeFormTab.value = 'dates'
    return false
  }

  if (!form.value.attachmentMap.CONTRACT_AGREEMENT.fileUrl) {
    ElMessage.warning('【合同协议】下载链接为必填项')
    activeFormTab.value = 'attachments'
    return false
  }

  if (form.value.customerType === 'CENTRAL_SOE') {
    const hasSoeDir = !!form.value.attachmentMap.SOE_DIRECTORY.fileUrl
    const hasRelProof = !!form.value.attachmentMap.RELATIONSHIP_PROOF.fileUrl
    if (!hasSoeDir && !hasRelProof) {
      ElMessage.warning('客户类型为央企时，【央企名录】与【关系证明】链接至少需要录入一个')
      activeFormTab.value = 'attachments'
      return false
    }
  }

  if (form.value.hasBidNotice) {
    if (!form.value.attachmentMap.BID_NOTICE.fileUrl) {
      ElMessage.warning('启用了包含中标通知书，【中标通知书】链接为必填项')
      activeFormTab.value = 'attachments'
      return false
    }
  }

  return true
}

const handleSave = async () => {
  const isValid = await validateForm()
  if (!isValid) return

  emit('submit', form.value)
}
</script>
