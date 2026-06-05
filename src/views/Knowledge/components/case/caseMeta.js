export const caseIndustryOptions = [
  { label: '政府机构', value: 'government' },
  { label: '金融银行', value: 'finance' },
  { label: '能源电力', value: 'energy' },
  { label: '交通运输', value: 'transport' },
  { label: '医疗卫生', value: 'healthcare' },
  { label: '教育科研', value: 'education' },
  { label: '制造业', value: 'manufacturing' },
  { label: '互联网', value: 'internet' }
]

export function getCaseYearOptions() {
  const currentYear = new Date().getFullYear()
  return Array.from({ length: 10 }, (_, index) => ({
    label: `${currentYear - index}年`,
    value: currentYear - index
  }))
}

export const caseAmountRanges = [
  { label: '100万以下', value: '0-100' },
  { label: '100-500万', value: '100-500' },
  { label: '500-1000万', value: '500-1000' },
  { label: '1000万以上', value: '1000+' }
]

export const caseCommonTags = [
  '智慧城市',
  '大数据',
  '云计算',
  '物联网',
  '人工智能',
  '信息安全',
  '数字化',
  '移动应用',
  '系统集成',
  '软件开发'
]

const caseTagTypes = {
  '智慧城市': 'success',
  '大数据': 'warning',
  '云计算': 'primary',
  '物联网': 'success',
  '人工智能': 'danger',
  '信息安全': 'warning',
  '数字化': 'primary',
  '移动应用': 'info',
  '系统集成': '',
  '软件开发': ''
}

const caseIndustryLabels = {
  government: '政府机构',
  finance: '金融银行',
  energy: '能源电力',
  transport: '交通运输',
  healthcare: '医疗卫生',
  education: '教育科研',
  manufacturing: '制造业',
  internet: '互联网'
}

export function createEmptyCaseForm() {
  return {
    sourceProjectId: null,
    title: '',
    customer: '',
    industry: '',
    amount: null,
    period: null,
    location: '',
    tags: [],
    description: '',
    techHighlights: '',
    priceStrategy: '',
    successFactors: [],
    lessons: '',
    attachments: []
  }
}

export function createCaseFormRules() {
  return {
    sourceProjectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
    title: [{ required: true, message: '请输入案例标题', trigger: 'blur' }],
    customer: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
    industry: [{ required: true, message: '请选择行业', trigger: 'change' }],
    amount: [{ required: true, message: '请输入项目金额', trigger: 'blur' }],
    location: [{ required: true, message: '请输入所在地区', trigger: 'blur' }],
    description: [{ required: true, message: '请输入项目概述', trigger: 'blur' }],
    techHighlights: [{ required: true, message: '请输入技术亮点', trigger: 'blur' }]
  }
}

export function createCaseEditRules() {
  return {
    title: [{ required: true, message: '请输入案例标题', trigger: 'blur' }],
    customer: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
    industry: [{ required: true, message: '请选择行业', trigger: 'change' }],
    amount: [{ required: true, message: '请输入项目金额', trigger: 'blur' }],
    location: [{ required: true, message: '请输入所在地区', trigger: 'blur' }],
    description: [{ required: true, message: '请输入项目概述', trigger: 'blur' }],
    highlights: [{ required: true, message: '请输入项目亮点', trigger: 'blur' }]
  }
}

export function getYearTagType(year) {
  const currentYear = new Date().getFullYear()
  if (year === currentYear) return 'success'
  if (year === currentYear - 1) return ''
  return 'info'
}

export function getTagType(tag) {
  return caseTagTypes[tag] || 'info'
}

export function formatAmount(amount) {
  const numericAmount = Number(amount || 0)
  if (numericAmount >= 100000000) {
    return `${(numericAmount / 100000000).toFixed(2)} 亿元`
  }
  if (numericAmount >= 10000) {
    return `${(numericAmount / 10000).toFixed(1)} 万元`
  }
  return `${numericAmount.toLocaleString('zh-CN')} 元`
}

export function getIndustryLabel(value) {
  return caseIndustryLabels[value] || value || '-'
}

export function buildCaseListQuery(searchForm = {}, selectedTags = [], pagination = {}) {
  const amount = String(searchForm.amount || '').trim()
  let amountMin
  let amountMax

  if (amount) {
    const [minPart, maxPart] = amount.split('-')
    amountMin = Number.parseFloat(String(minPart).replace('+', ''))
    amountMax = Number.parseFloat(String(maxPart || '').replace('+', ''))

    if (!Number.isFinite(amountMin)) {
      amountMin = undefined
    }
    if (!Number.isFinite(amountMax)) {
      amountMax = undefined
    }
  }

  return {
    keyword: searchForm.keyword?.trim() || undefined,
    industry: searchForm.industry || undefined,
    year: searchForm.year || undefined,
    amountMin,
    amountMax,
    tags: selectedTags.length > 0 ? selectedTags : undefined,
    page: pagination.page || 1,
    pageSize: pagination.pageSize || 12
  }
}
