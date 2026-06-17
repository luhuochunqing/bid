import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { chinaRegionOptions } from '@/components/common/chinaRegionData.js'
import {
  CUSTOMER_TYPE_OPTIONS,
  PROJECT_TYPE_OPTIONS,
  PRIORITY_OPTIONS,
  MANUAL_FORM_RULES,
} from '../constants.js'

export function useTenderCreateForm() {
  const form = ref(createForm())

  const regions = chinaRegionOptions
  const customerTypes = CUSTOMER_TYPE_OPTIONS
  const projectTypes = PROJECT_TYPE_OPTIONS
  const priorities = PRIORITY_OPTIONS
  const rules = MANUAL_FORM_RULES

  const canSave = computed(() =>
    form.value.title?.trim() &&
    form.value.purchaser?.trim() &&
    form.value.region?.trim() &&
    form.value.deadline &&
    form.value.bidOpeningTime &&
    form.value.customerType?.trim() &&
    form.value.priority?.trim()
  )

  function resetForm() {
    form.value = createForm()
  }

  function populateForm(data) {
    form.value = mapTenderToForm(data)
  }

  async function validateBeforeSave(formElRef) {
    try {
      await formElRef.value?.validate()
    } catch {
      ElMessage.warning('请填写必填项后再保存')
      return false
    }
    if (!validateContacts()) return false
    if (form.value.deadline) {
      if (new Date(form.value.deadline) <= new Date()) {
        ElMessage.warning('报名截止时间必须晚于当前时间')
        return false
      }
    }
    if (form.value.deadline && form.value.bidOpeningTime) {
      if (new Date(form.value.bidOpeningTime) < new Date(form.value.deadline)) {
        ElMessage.warning('开标时间不得早于报名截止时间')
        return false
      }
    }
    return true
  }

  function validateContacts() {
    const f = form.value
    if (f.contact?.trim()) {
      if (!f.phone?.trim() && !f.landline?.trim() && !f.mail?.trim()) {
        ElMessage.warning('请填写联系人1的至少一种联系方式（手机/座机/邮箱）')
        return false
      }
    }
    if (f.contact2?.trim()) {
      if (!f.phone2?.trim() && !f.landline2?.trim() && !f.mail2?.trim()) {
        ElMessage.warning('请填写联系人2的至少一种联系方式（手机/座机/邮箱）')
        return false
      }
    }
    if (!f.contact?.trim() && !f.contact2?.trim()) {
      ElMessage.warning('请至少填写一个联系人的联系方式')
      return false
    }
    return true
  }

  return {
    form, rules, regions, customerTypes, projectTypes, priorities,
    canSave, resetForm, populateForm, validateBeforeSave,
  }
}

function createForm() {
  return {
    title: '', purchaser: '', region: '', deadline: null, bidOpeningTime: null,
    customerType: '', priority: '', projectType: '',
    sourcePlatform: '人工录入',
    contact: '', phone: '', landline: '', mail: '',
    contact2: '', phone2: '', landline2: '', mail2: '',
    description: '', tenderInfo: '', attachments: [],
    sourceDocumentName: '', sourceDocumentFileType: '', sourceDocumentFileUrl: '',
    pastedText: '',
  }
}

function mapTenderToForm(tender) {
  return {
    title: tender.title || '',
    purchaser: tender.purchaserName || '',
    region: tender.region || '',
    deadline: tender.registrationDeadline || tender.deadline || null,
    bidOpeningTime: tender.bidOpeningTime || null,
    customerType: tender.customerType || '',
    priority: tender.priority || '',
    projectType: tender.projectType || '',
    sourcePlatform: tender.source || '人工录入',
    contact: tender.contactName || '', phone: tender.contactPhone || '',
    landline: tender.contactTel || '', mail: tender.contactMail || '',
    contact2: tender.contactName2 || '', phone2: tender.contactPhone2 || '',
    landline2: tender.contactTel2 || '', mail2: tender.contactMail2 || '',
    description: tender.description || '', tenderInfo: tender.tenderInfo || '',
    attachments: [],
    sourceDocumentName: tender.sourceDocumentName || '',
    sourceDocumentFileType: tender.sourceDocumentFileType || '',
    sourceDocumentFileUrl: tender.sourceDocumentFileUrl || '',
    pastedText: '',
  }
}
