<template>
  <el-dialog 
    :model-value="visible" 
    @update:model-value="val => $emit('update:visible', val)"
    :title="isEdit ? '编辑业绩档案' : '新增业绩档案'" 
    width="780px"
    class="premium-dialog"
  >
    <el-tabs v-if="visible" v-model="activeFormTab" class="form-tabs">
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
        <el-form ref="formRefContact" :model="form" label-width="120px" :rules="rules">
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

import { rules, createDefaultForm } from './performanceRules.js'

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

const form = ref(createDefaultForm())

const formRefContact = ref(null)

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
    form.value = createDefaultForm()
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    activeFormTab.value = 'base'
    isEdit.value = !!props.data
    initForm()
  }
})

const validateForm = async () => {
  let baseValid
  let datesValid
  let contactValid

  try {
    await formRefBase.value?.validate()
    baseValid = true
  } catch {
    baseValid = false
  }
  if (!baseValid) {
    activeFormTab.value = 'base'
    return false
  }

  try {
    await formRefDates.value?.validate()
    datesValid = true
  } catch {
    datesValid = false
  }
  if (!datesValid) {
    activeFormTab.value = 'dates'
    return false
  }

  if (form.value.expiryDate && form.value.signingDate) {
    if (new Date(form.value.expiryDate) <= new Date(form.value.signingDate)) {
      ElMessage.warning('截止日期必须晚于签约日期')
      activeFormTab.value = 'dates'
      return false
    }
  }

  if (form.value.totalExpiryDate && form.value.expiryDate) {
    if (new Date(form.value.totalExpiryDate) < new Date(form.value.expiryDate)) {
      ElMessage.warning('总截止日期需晚于截止日期')
      activeFormTab.value = 'dates'
      return false
    }
  }

  try {
    await formRefContact.value?.validate()
    contactValid = true
  } catch {
    contactValid = false
  }
  if (!contactValid) {
    activeFormTab.value = 'contact'
    return false
  }

  if (!form.value.attachmentMap.CONTRACT_AGREEMENT.fileUrl) {
    ElMessage.warning('请上传合同协议')
    activeFormTab.value = 'attachments'
    return false
  }

  if (form.value.customerType === 'CENTRAL_SOE') {
    const hasSoeDir = !!form.value.attachmentMap.SOE_DIRECTORY.fileUrl
    const hasRelProof = !!form.value.attachmentMap.RELATIONSHIP_PROOF.fileUrl
    if (!hasSoeDir) {
      ElMessage.warning('央企客户必须上传央企名录截图')
      activeFormTab.value = 'attachments'
      return false
    }
    if (!hasRelProof) {
      ElMessage.warning('央企客户必须上传关系证明')
      activeFormTab.value = 'attachments'
      return false
    }
  }

  if (form.value.hasBidNotice) {
    if (!form.value.attachmentMap.BID_NOTICE.fileUrl) {
      ElMessage.warning('当中标通知书为是的时候，必传')
      activeFormTab.value = 'attachments'
      return false
    }
  }

  return true
}

import { ElMessageBox } from 'element-plus'

const handleSave = async () => {
  const isValid = await validateForm()
  if (!isValid) return

  if (isEdit.value && props.data) {
    const typeChanged = form.value.customerType !== props.data.customerType
    const dateChanged = form.value.signingDate !== props.data.signingDate

    if (typeChanged) {
      try {
        await ElMessageBox.confirm('修改客户类型将影响到期提醒规则，请确认必要性', '重要提示', {
          confirmButtonText: '确认修改',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch {
        return
      }
    }

    if (dateChanged) {
      try {
        await ElMessageBox.confirm('修改签约日期将影响业绩归档时间', '重要提示', {
          confirmButtonText: '确认修改',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch {
        return
      }
    }
  }

  emit('submit', form.value)
}
</script>
