// Input: template library category and backend-owned three-dimensional vocabulary
// Output: shared template-library labels, options, and non-fallback normalization helpers
// Pos: src/config/ - shared frontend configuration
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export const TEMPLATE_CATEGORY_META = {
  all: { label: '全部', icon: 'Grid', color: '#409eff', tagType: '' },
  technical: { label: '技术方案', icon: 'Document', color: '#409eff', tagType: '' },
  commercial: { label: '商务文件', icon: 'DocumentCopy', color: '#67c23a', tagType: 'success' },
  implementation: { label: '实施方案', icon: 'Operation', color: '#e6a23c', tagType: 'warning' },
  quotation: { label: '报价清单', icon: 'Tickets', color: '#f56c6c', tagType: 'danger' },
  qualification: { label: '资质文件', icon: 'Medal', color: '#909399', tagType: 'info' },
  contract: { label: '合同范本', icon: 'Notebook', color: '#8e44ad', tagType: 'primary' }
}

export const TEMPLATE_CATEGORY_OPTIONS = Object.entries(TEMPLATE_CATEGORY_META)
  .filter(([value]) => value !== 'all')
  .map(([value, meta]) => ({ value, label: meta.label }))

export const PRODUCT_TYPE_OPTIONS = [
  '智慧城市',
  '智慧交通',
  '智慧园区',
  'MES',
  'ERP',
  '数据中台',
  '电商平台',
  '其他'
]

export const INDUSTRY_OPTIONS = [
  '政府',
  '能源',
  '交通',
  '医疗',
  '教育',
  '制造业',
  '互联网',
  '其他'
]

export const DOCUMENT_TYPE_OPTIONS = [
  '技术方案',
  '商务应答',
  '行业方案',
  '实施方案',
  '资格文件',
  '其他'
]

const templateCategoryUiMap = {
  all: 'all',
  technical: 'technical',
  commercial: 'commercial',
  implementation: 'implementation',
  quotation: 'quotation',
  qualification: 'qualification',
  contract: 'contract',
  技术方案: 'technical',
  商务文件: 'commercial',
  实施方案: 'implementation',
  报价清单: 'quotation',
  资质文件: 'qualification',
  合同范本: 'contract',
  TECHNICAL: 'technical',
  COMMERCIAL: 'commercial',
  OTHER: 'implementation',
  LEGAL: 'quotation',
  QUALIFICATION: 'qualification',
  CONTRACT: 'contract'
}

const templateCategoryApiMap = {
  technical: 'TECHNICAL',
  commercial: 'COMMERCIAL',
  implementation: 'OTHER',
  quotation: 'LEGAL',
  qualification: 'QUALIFICATION',
  contract: 'CONTRACT'
}

const productTypeAliases = {
  SMART_CITY: '智慧城市',
  SMART_TRANSPORTATION: '智慧交通',
  SMART_PARK: '智慧园区',
  DATA_PLATFORM: '数据中台',
  DATA_MIDDLE_PLATFORM: '数据中台',
  E_COMMERCE: '电商平台',
  E_COMMERCE_PLATFORM: '电商平台',
  OTHER: '其他'
}

const industryAliases = {
  GOVERNMENT: '政府',
  ENERGY: '能源',
  TRANSPORTATION: '交通',
  HEALTHCARE: '医疗',
  EDUCATION: '教育',
  MANUFACTURING: '制造业',
  INTERNET: '互联网',
  OTHER: '其他'
}

const documentTypeAliases = {
  TECHNICAL_PROPOSAL: '技术方案',
  COMMERCIAL_RESPONSE: '商务应答',
  INDUSTRY_SOLUTION: '行业方案',
  IMPLEMENTATION_PLAN: '实施方案',
  QUALIFICATION_FILE: '资格文件',
  OTHER: '其他'
}

const productTypeSet = new Set(PRODUCT_TYPE_OPTIONS)
const industrySet = new Set(INDUSTRY_OPTIONS)
const documentTypeSet = new Set(DOCUMENT_TYPE_OPTIONS)

export function normalizeTemplateCategory(value) {
  return templateCategoryUiMap[value] || value || 'technical'
}

export function toTemplateCategoryApiValue(value) {
  const normalized = normalizeTemplateCategory(value)
  return templateCategoryApiMap[normalized] || 'TECHNICAL'
}

function coerceControlledValue(value, aliases, allowedSet) {
  const rawValue = typeof value === 'string' ? value.trim() : value || ''
  if (!rawValue) return ''
  const normalized = aliases[rawValue] || rawValue
  return allowedSet.has(normalized) ? normalized : rawValue
}

function toControlledPayloadValue(value, aliases, allowedSet) {
  const rawValue = typeof value === 'string' ? value.trim() : value || ''
  if (!rawValue) return ''
  const normalized = aliases[rawValue] || rawValue
  return allowedSet.has(normalized) ? normalized : rawValue
}

export function normalizeProductType(value) {
  return coerceControlledValue(value, productTypeAliases, productTypeSet)
}

export function normalizeIndustry(value) {
  return coerceControlledValue(value, industryAliases, industrySet)
}

export function normalizeDocumentType(value) {
  return coerceControlledValue(value, documentTypeAliases, documentTypeSet)
}

export function toProductTypeValue(value) {
  return toControlledPayloadValue(value, productTypeAliases, productTypeSet)
}

export function toIndustryValue(value) {
  return toControlledPayloadValue(value, industryAliases, industrySet)
}

export function toDocumentTypeValue(value) {
  return toControlledPayloadValue(value, documentTypeAliases, documentTypeSet)
}

export function getCategoryLabel(category) {
  return TEMPLATE_CATEGORY_META[normalizeTemplateCategory(category)]?.label || String(category || '-')
}

export function getCategoryColor(category) {
  return TEMPLATE_CATEGORY_META[normalizeTemplateCategory(category)]?.color || '#409eff'
}

export function getCategoryTagType(category) {
  return TEMPLATE_CATEGORY_META[normalizeTemplateCategory(category)]?.tagType || ''
}

export function getDefaultDocumentTypeForCategory(category) {
  const normalizedCategory = normalizeTemplateCategory(category)
  if (normalizedCategory === 'technical') return '技术方案'
  if (normalizedCategory === 'commercial') return '商务应答'
  if (normalizedCategory === 'implementation') return '实施方案'
  if (normalizedCategory === 'qualification') return '资格文件'
  return '其他'
}
