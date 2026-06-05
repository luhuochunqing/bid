// Input: template-library page state and normalized template rows
// Output: reusable helpers for local tags/sort behavior, workspace summaries, and dialog form initialization
// Pos: src/views/Knowledge/components/template/ - template page helper layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import {
  getCategoryLabel,
  getDefaultDocumentTypeForCategory
} from '@/config/templateLibrary.js'

export const PROJECT_STATUS_META = {
  pending: { label: '待启动', type: 'info' },
  reviewing: { label: '评审中', type: 'warning' },
  bidding: { label: '投标中', type: 'primary' },
  won: { label: '已中标', type: 'success' },
  lost: { label: '未中标', type: 'danger' }
}

export const USE_TEMPLATE_DOC_TYPE_OPTIONS = [
  { value: 'tech', label: '技术方案', icon: 'Document' },
  { value: 'business', label: '商务应答', icon: 'DocumentCopy' },
  { value: 'contract', label: '合同文档', icon: 'Notebook' },
  { value: 'standalone', label: '独立文档', icon: 'Folder' }
]

export function createTemplateFilters() {
  return {
    name: '',
    productType: '',
    industry: '',
    documentType: '',
    tags: [],
    sort: 'default'
  }
}

export function createUseTemplateForm() {
  return {
    docType: 'standalone',
    projectId: '',
    docName: '',
    applyOptions: ['content', 'format', 'styles']
  }
}

export function createTemplateForm(category = 'technical') {
  return {
    id: null,
    name: '',
    category,
    productType: '',
    industry: '',
    documentType: getDefaultDocumentTypeForCategory(category),
    description: '',
    tagsText: '',
    fileUrl: '',
    fileSize: ''
  }
}

export function patchTemplateForm(target, data = {}) {
  const hasExplicitDocumentType = Object.prototype.hasOwnProperty.call(data, 'documentType')
  Object.assign(target, createTemplateForm(data.category || 'technical'), {
    id: data.id ?? null,
    name: data.name || '',
    category: data.category || 'technical',
    productType: data.productType || '',
    industry: data.industry || '',
    documentType: hasExplicitDocumentType
      ? data.documentType
      : (data.documentType || getDefaultDocumentTypeForCategory(data.category || 'technical')),
    description: data.description || '',
    tagsText: Array.isArray(data.tags) ? data.tags.join('，') : '',
    fileUrl: data.fileUrl || '',
    fileSize: data.fileSize || ''
  })
}

export function extractTags(tagsText = '') {
  return Array.from(new Set(
    String(tagsText)
      .split(/[，,]/)
      .map((item) => item.trim())
      .filter(Boolean)
  ))
}

export function formatDate(date) {
  if (!date) return '-'
  return String(date).slice(0, 10)
}

export function formatNumber(num) {
  const value = Number(num || 0)
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`
  return String(value)
}

export function getProjectStatusLabel(status) {
  return PROJECT_STATUS_META[status]?.label || status || '-'
}

export function getProjectStatusType(status) {
  return PROJECT_STATUS_META[status]?.type || 'info'
}

export function sortTemplateCollection(items, sort = 'default') {
  const nextItems = [...items]
  if (sort === 'downloads') {
    return nextItems.sort((left, right) => right.downloads - left.downloads)
  }
  if (sort === 'updateTime') {
    return nextItems.sort((left, right) => new Date(right.updateTime) - new Date(left.updateTime))
  }
  if (sort === 'name') {
    return nextItems.sort((left, right) => String(left.name || '').localeCompare(String(right.name || ''), 'zh'))
  }
  return nextItems
}

export function filterTemplateCollection(items, query = {}) {
  const filtered = items.filter((item) => {
    if (Array.isArray(query.tags) && query.tags.length > 0) {
      const matchesTags = query.tags.some((tag) => item.tags.includes(tag))
      if (!matchesTags) return false
    }
    return true
  })

  return sortTemplateCollection(filtered, query.sort)
}

export function paginateTemplates(items, page, pageSize) {
  const start = (page - 1) * pageSize
  return items.slice(start, start + pageSize)
}

export function buildRemoteTemplateFilters(activeCategory, filters) {
  return {
    category: activeCategory,
    name: filters.name,
    productType: filters.productType,
    industry: filters.industry,
    documentType: filters.documentType
  }
}

export function hasActiveOfficialFilters(activeCategory, filters) {
  return activeCategory !== 'all' ||
    Boolean(filters.name) ||
    Boolean(filters.productType) ||
    Boolean(filters.industry) ||
    Boolean(filters.documentType)
}

export function hasActiveLocalFilters(filters) {
  return (Array.isArray(filters.tags) && filters.tags.length > 0) || filters.sort !== 'default'
}

export function buildFilterSummaryItems(activeCategory, filters) {
  const items = []
  if (activeCategory && activeCategory !== 'all') {
    items.push({
      key: 'category',
      label: '历史视图',
      value: getCategoryLabel(activeCategory),
      emphasis: 'secondary'
    })
  }
  if (filters.name) {
    items.push({ key: 'name', label: '名称', value: filters.name, emphasis: 'primary' })
  }
  if (filters.productType) {
    items.push({ key: 'productType', label: '产品类型', value: filters.productType, emphasis: 'primary' })
  }
  if (filters.industry) {
    items.push({ key: 'industry', label: '行业', value: filters.industry, emphasis: 'primary' })
  }
  if (filters.documentType) {
    items.push({ key: 'documentType', label: '文档类型', value: filters.documentType, emphasis: 'primary' })
  }
  if (Array.isArray(filters.tags) && filters.tags.length > 0) {
    items.push({ key: 'tags', label: '标签', value: filters.tags.join(' / '), emphasis: 'local' })
  }
  if (filters.sort && filters.sort !== 'default') {
    const sortLabelMap = {
      downloads: '下载量',
      updateTime: '更新时间',
      name: '名称'
    }
    items.push({ key: 'sort', label: '排序', value: sortLabelMap[filters.sort] || filters.sort, emphasis: 'local' })
  }
  return items
}

export function createTemplateFormErrors() {
  return {
    name: '',
    productType: '',
    industry: '',
    documentType: ''
  }
}

export function validateTemplateForm(form) {
  const errors = createTemplateFormErrors()
  if (!String(form.name || '').trim()) errors.name = '请输入模板名称'
  if (!form.productType) errors.productType = '请选择产品类型'
  if (!form.industry) errors.industry = '请选择行业'
  if (!form.documentType) errors.documentType = '请选择文档类型'
  return errors
}

export function hasTemplateFormErrors(errors) {
  return Object.values(errors).some(Boolean)
}

export function buildDocumentDraft(template, useTemplateForm, currentUserName) {
  return {
    id: `doc_${Date.now()}`,
    name: useTemplateForm.docName,
    templateId: template.id,
    templateName: template.name,
    docType: useTemplateForm.docType,
    projectId: useTemplateForm.projectId || null,
    content: template.content || '',
    createdAt: new Date().toISOString(),
    createdBy: currentUserName || '当前用户',
    status: 'draft'
  }
}
