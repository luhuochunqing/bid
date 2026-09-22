// Input: bid result form state and raw display values
// Output: reusable form factories, assignment helpers, and competitor persistence helpers
// Pos: src/views/Resource/bid-result/ - Bid result page helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { bidResultsApi } from '@/api'

const createCompetitor = () => ({
  company: '',
  competitorId: null,
  skuCount: null,
  category: '',
  discount: '',
  paymentTerms: '',
  notes: ''
})

export const createResultForm = (projectId = null) => ({
  id: null,
  projectId,
  result: 'won',
  amount: null,
  contractStartDate: '',
  contractEndDate: '',
  contractDurationMonths: null,
  remark: '',
  skuCount: null,
  attachmentType: 'WIN_NOTICE',
  attachmentFile: null,
  attachmentFiles: [],
  competitors: []
})

export const createUploadForm = () => ({
  attachmentType: 'WIN_NOTICE',
  file: null,
  fileList: [],
  comment: ''
})

export const formatDateTime = (value) => {
  if (!value) return ''
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

export const formatAmount = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const amount = Number(value)
  if (Number.isNaN(amount)) return String(value)
  return `${amount.toFixed(2)} 元`
}

export const buildResultPayload = (form) => ({
  projectId: form.projectId,
  result: form.result,
  amount: form.result === 'won' ? form.amount : null,
  contractStartDate: form.contractStartDate || null,
  contractEndDate: form.contractEndDate || null,
  contractDurationMonths: form.contractDurationMonths ?? null,
  remark: form.remark || '',
  skuCount: form.skuCount ?? null,
  attachmentType: form.attachmentType
})

export const assignFormValues = (target, source = {}, initialProjectId = null) => {
  Object.assign(target, createResultForm(source.projectId ?? initialProjectId), {
    id: source.id ?? null,
    projectId: source.projectId ?? initialProjectId,
    result: source.result || 'won',
    amount: source.amount ?? null,
    contractStartDate: source.contractStartDate || '',
    contractEndDate: source.contractEndDate || '',
    contractDurationMonths: source.contractDurationMonths ?? null,
    remark: source.remark || '',
    skuCount: source.skuCount ?? null,
    attachmentType: source.result === 'lost' ? 'LOSS_REPORT' : 'WIN_NOTICE',
    competitors: Array.isArray(source.competitors)
      ? source.competitors.map((item) => ({ ...createCompetitor(), ...item }))
      : []
  })
}

export const addCompetitorRow = (form) => {
  form.competitors.push(createCompetitor())
}

export const removeCompetitorRow = (form, index) => {
  form.competitors.splice(index, 1)
}

export const persistCompetitors = async (projectId, competitors) => {
  for (const item of competitors) {
    if (!item.company && !item.competitorId) continue
    await bidResultsApi.createCompetitorWin({
      ...item,
      projectId
    })
  }
}
