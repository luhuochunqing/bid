// Input: tender detail payload returned by the real API
// Output: project-create prefill values and small form helpers
// Pos: src/views/Project/ - View helper for tender-to-project form prefill

function firstText(...values) {
  const found = values.find(value => typeof value === 'string' && value.trim())
  return found ? found.trim() : ''
}

function isBlankFormValue(value) {
  return value === null || value === undefined || value === '' || (Array.isArray(value) && value.length === 0)
}

function normalizeTags(tags) {
  if (Array.isArray(tags)) {
    return tags.map(tag => String(tag).trim()).filter(Boolean)
  }
  if (typeof tags === 'string' && tags.trim()) {
    return tags.split(',').map(tag => tag.trim()).filter(Boolean)
  }
  return []
}

export function normalizeTenderDeadlineDate(value) {
  if (!value) return ''
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (/^\d{4}-\d{2}-\d{2}/.test(trimmed)) return trimmed.slice(0, 10)
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toISOString().slice(0, 10)
}

export function normalizeTenderBudgetWan(value) {
  if (value === null || value === undefined || value === '') return null
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return null
  return Math.round((numeric / 10000) * 100) / 100
}

export function buildProjectPrefillFromTender(tender = {}) {
  return {
    name: firstText(tender.projectName, tender.title, tender.name),
    customer: firstText(tender.purchaserName, tender.purchaser, tender.customerName, tender.customer),
    budget: normalizeTenderBudgetWan(tender.budget),
    industry: firstText(tender.industry, tender.industryCategory, tender.category),
    region: firstText(tender.region, tender.area, tender.province),
    platform: firstText(tender.platform, tender.sourceName, tender.source),
    deadline: normalizeTenderDeadlineDate(tender.deadline),
    description: firstText(tender.description, tender.projectDescription, tender.summary),
    tags: normalizeTags(tender.tags),
    projectLeaderName: firstText(tender.projectManagerName, tender.biddingPersonName),
    leaderDepartment: firstText(tender.department, tender.deptName),
  }
}

export function hasGlobalHttpErrorMessage(error) {
  return Boolean(error?.isAxiosError || error?.response || error?.code === 'ECONNABORTED')
}

export function applyTenderDetailPrefill(forms, prefill) {
  const { basicForm, detailForm } = forms
  if (!prefill) return
  if (isBlankFormValue(basicForm.name) && prefill.name) basicForm.name = prefill.name
  if (isBlankFormValue(basicForm.customer) && prefill.customer) basicForm.customer = prefill.customer
  if (isBlankFormValue(basicForm.budget) && prefill.budget !== null) basicForm.budget = prefill.budget
  if (isBlankFormValue(basicForm.industry) && prefill.industry) basicForm.industry = prefill.industry
  if (isBlankFormValue(basicForm.region) && prefill.region) basicForm.region = prefill.region
  if (isBlankFormValue(basicForm.platform) && prefill.platform) basicForm.platform = prefill.platform
  if (isBlankFormValue(basicForm.deadline) && prefill.deadline) basicForm.deadline = prefill.deadline
  if (isBlankFormValue(detailForm.description) && prefill.description) detailForm.description = prefill.description
  if (isBlankFormValue(detailForm.tags) && prefill.tags.length > 0) detailForm.tags = prefill.tags
  if (isBlankFormValue(detailForm.projectLeaderName) && prefill.projectLeaderName) detailForm.projectLeaderName = prefill.projectLeaderName
  if (isBlankFormValue(detailForm.leaderDepartment) && prefill.leaderDepartment) detailForm.leaderDepartment = prefill.leaderDepartment
}

export const tenderSchema = {
  groups: [
    {
      id: 'basic',
      title: '基本信息',
      fields: [
        { key: 'projectName', label: '项目名称', type: 'string' },
        { key: 'purchaserName', label: '采购人', type: 'string' },
        { key: 'budget', label: '项目预算', type: 'number' }
      ]
    },
    {
      id: 'timeline',
      title: '关键节点',
      fields: [
        { key: 'publishDate', label: '发布日期', type: 'date' },
        { key: 'deadline', label: '投标截止', type: 'datetime' }
      ]
    }
  ]
}
