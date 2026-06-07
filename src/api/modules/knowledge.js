// Input: httpClient, API mode config, knowledge normalizers and case query helpers
// Output: knowledgeApi - qualification, case, and template accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 知识库模块 API (资质、案例、模板)
 * 真实 API 知识库访问层
 */
import httpClient from '../client.js'
import { qualificationsApi } from './qualification.js'

const caseIndustryMap = {
  government: 'INFRASTRUCTURE',
  finance: 'OTHER',
  energy: 'ENERGY',
  transport: 'TRANSPORTATION',
  healthcare: 'OTHER',
  education: 'OTHER',
  manufacturing: 'MANUFACTURING',
  internet: 'OTHER',
  政府: 'government',
  能源: 'energy',
  交通: 'transport',
  制造业: 'manufacturing',
  教育: 'education',
  医疗: 'healthcare',
  互联网: 'internet',
  园区: 'government',
  INFRASTRUCTURE: 'government',
  MANUFACTURING: 'manufacturing',
  ENERGY: 'energy',
  TRANSPORTATION: 'transport',
  ENVIRONMENTAL: 'government',
  REAL_ESTATE: 'government',
  OTHER: 'government' }

const templateCategoryMap = {
  technical: 'TECHNICAL',
  commercial: 'COMMERCIAL',
  implementation: 'OTHER',
  quotation: 'LEGAL',
  qualification: 'QUALIFICATION',
  contract: 'CONTRACT',
  技术方案: 'technical',
  商务文件: 'commercial',
  行业方案: 'implementation',
  实施方案: 'implementation',
  资质文件: 'qualification',
  合同范本: 'contract',
  TECHNICAL: 'technical',
  COMMERCIAL: 'commercial',
  LEGAL: 'quotation',
  QUALIFICATION: 'qualification',
  CONTRACT: 'contract',
  OTHER: 'implementation' }

function isNumericId(id) {
  return /^\d+$/.test(String(id))
}

function formatDate(date) {
  if (!date) return ''
  return String(date).slice(0, 10)
}

function formatCasePeriod(projectDate) {
  const date = formatDate(projectDate)
  return date ? `${date} - ${date}` : ''
}

function normalizeCase(item) {
  const projectDate = formatDate(item?.projectDate)
  const normalizedIndustry = caseIndustryMap[item?.industry] || ''
  const description = item?.description || item?.summary || ''
  const customer = item?.customer || item?.customerName || ''
  const location = item?.location || item?.locationName || ''
  const period = item?.period || item?.projectPeriod || formatCasePeriod(projectDate)
  const technologies = Array.isArray(item?.technologies) ? item.technologies : []
  const archivedInfo = item?.archivedInfo || {
    techHighlights: item?.techHighlights || '',
    priceStrategy: item?.priceStrategy || '',
    successFactors: item?.successFactors || [],
    lessons: item?.lessons || '',
    attachments: item?.attachments || [] }

  return {
    id: item?.id,
    title: item?.title || '未命名案例',
    customer,
    customerName: customer,
    industry: normalizedIndustry,
    outcome: item?.outcome || '',
    amount: Number(item?.amount || 0),
    year: item?.year || (projectDate ? new Date(projectDate).getFullYear() : ''),
    location,
    locationName: location,
    period,
    projectPeriod: period,
    productLine: item?.productLine || '',
    tags: Array.isArray(item?.tags) ? item.tags : [],
    highlights: Array.isArray(item?.highlights) ? item.highlights : [],
    description,
    summary: item?.summary || description,
    technologies,
    viewCount: Number(item?.viewCount || 0),
    useCount: Number(item?.useCount || 0),
    archivedInfo }
}

function buildCasePayload(data = {}) {
  const projectDate = Array.isArray(data.period) && data.period.length
    ? data.period[data.period.length - 1]
    : data.projectDate || new Date().toISOString().slice(0, 10)

  return {
    title: data.title,
    industry: caseIndustryMap[data.industry] || 'OTHER',
    outcome: data.outcome || 'WON',
    amount: data.amount ?? 0,
    projectDate,
    description: data.description || data.summary || '',
    customerName: data.customerName || data.customer || '',
    locationName: data.locationName || data.location || '',
    projectPeriod: data.projectPeriod || data.period || '',
    tags: Array.isArray(data.tags) ? data.tags : [],
    highlights: Array.isArray(data.highlights) ? data.highlights : [],
    technologies: Array.isArray(data.technologies) ? data.technologies : [],
    viewCount: Number(data.viewCount || 0),
    useCount: Number(data.useCount || 0) }
}

function normalizeTemplate(item) {
  const category = templateCategoryMap[item?.category] || 'implementation'
  const updateTime = formatDate(item?.updatedAt || item?.createdAt || item?.updateTime)

  return {
    id: item?.id,
    name: item?.name || '未命名模板',
    category,
    tags: Array.isArray(item?.tags) ? item.tags : [],
    description: item?.description || '暂无真实模板描述',
    downloads: Number(item?.downloads || 0),
    useCount: Number(item?.useCount || 0),
    updateTime: updateTime || '-',
    version: item?.currentVersion || item?.version || '1.0',
    fileSize: item?.fileSize || '未知',
    fileUrl: item?.fileUrl || '',
    productType: item?.productType || '',
    industry: item?.industry || '',
    documentType: item?.documentType || '',
    content: item?.content || '',
    structure: Array.isArray(item?.structure) ? item.structure : [],
    createdBy: item?.createdBy || null }
}

function buildTemplatePayload(data = {}) {
  return {
    name: data.name,
    category: templateCategoryMap[data.category] || 'OTHER',
    productType: data.productType || '',
    industry: data.industry || '',
    documentType: data.documentType || '',
    fileUrl: data.fileUrl || '',
    description: data.description || '',
    fileSize: data.fileSize || '',
    tags: Array.isArray(data.tags) ? data.tags : [],
    createdBy: data.createdBy ?? null }
}

function normalizeCaseNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : undefined
}

function normalizeCaseQuery(params = {}) {
  return {
    keyword: params.keyword ? String(params.keyword).trim() : undefined,
    industry: params.industry || undefined,
    productLine: params.productLine || undefined,
    outcome: params.outcome || undefined,
    year: params.year ? Number(params.year) : undefined,
    amountMin: normalizeCaseNumber(params.amountMin),
    amountMax: normalizeCaseNumber(params.amountMax),
    tags: Array.isArray(params.tags) && params.tags.length > 0 ? params.tags : undefined,
    scoringCategory: params.scoringCategory || undefined,
    customerType: params.customerType || undefined,
        status: params.status || undefined,
    projectType: params.projectType || undefined,
    page: params.page ? Number(params.page) : undefined,
    pageSize: params.pageSize ? Number(params.pageSize) : undefined,
    sort: params.sort || undefined
  }
}

function filterCaseByQuery(item, params = {}) {
  if (params.industry && item.industry !== params.industry) {
    return false
  }

  if (params.productLine && String(item.productLine || '') !== String(params.productLine)) {
    return false
  }

  if (params.outcome && String(item.outcome || '') !== String(params.outcome)) {
    return false
  }

  if (params.year && Number(item.year) !== Number(params.year)) {
    return false
  }

  if (params.amountMin != null && Number(item.amount) < Number(params.amountMin)) {
    return false
  }

  if (params.amountMax != null && Number(item.amount) >= Number(params.amountMax)) {
    return false
  }

  if (params.keyword) {
    const keyword = String(params.keyword).toLowerCase()
    const matchesKeyword =
      String(item.title || '').toLowerCase().includes(keyword) ||
      String(item.customer || '').toLowerCase().includes(keyword) ||
      String(item.location || '').toLowerCase().includes(keyword) ||
      String(item.summary || '').toLowerCase().includes(keyword) ||
      item.highlights.some((highlight) => String(highlight).toLowerCase().includes(keyword))

    if (!matchesKeyword) {
      return false
    }
  }

  if (Array.isArray(params.tags) && params.tags.length > 0) {
    const hasAnyTag = params.tags.some((tag) => item.tags.includes(tag))
    if (!hasAnyTag) {
      return false
    }
  }

  return true
}

function applyCasePagination(items, params = {}) {
  const pageSize = Number(params.pageSize || 0)
  const page = Number(params.page || 1)
  if (!pageSize || pageSize <= 0) {
    return items
  }

  const start = Math.max(page - 1, 0) * pageSize
  return items.slice(start, start + pageSize)
}

function buildCaseListResponse(response, params = {}) {
  if (response?.data && !Array.isArray(response.data) && Array.isArray(response.data.items)) {
    return {
      ...response,
      data: response.data.items.map(normalizeCase),
      total: Number(response.data.total ?? response.data.items.length ?? 0),
      page: Number(response.data.page ?? params.page ?? 1),
      pageSize: Number(response.data.pageSize ?? params.pageSize ?? response.data.items.length ?? 0),
      totalPages: Number(response.data.totalPages ?? 1),
      sort: response.data.sort || params.sort
    }
  }

  const rawItems = Array.isArray(response?.data)
    ? response.data
    : Array.isArray(response?.data?.records)
      ? response.data.records
      : Array.isArray(response?.data?.items)
        ? response.data.items
        : []

  const normalized = rawItems.map(normalizeCase)
  const filtered = normalized.filter((item) => filterCaseByQuery(item, params))
  const paged = applyCasePagination(filtered, params)
  const total = Number.isFinite(Number(response?.total))
    ? Number(response.total)
    : Number.isFinite(Number(response?.data?.total))
      ? Number(response.data.total)
      : filtered.length

  return {
    ...response,
    data: paged,
    total
  }
}
function filterTemplates(items, params = {}) {
  return items.filter((item) => {
    if (params.category && params.category !== 'all' && item.category !== params.category) {
      return false
    }
    if (params.name) {
      const keyword = String(params.name).toLowerCase()
      const matchesKeyword =
        String(item.name || '').toLowerCase().includes(keyword) ||
        String(item.description || '').toLowerCase().includes(keyword)
      if (!matchesKeyword) {
        return false
      }
    }
    if (params.productType && String(item.productType || '') !== String(params.productType)) {
      return false
    }
    if (params.industry && String(item.industry || '') !== String(params.industry)) {
      return false
    }
    if (params.documentType && String(item.documentType || '') !== String(params.documentType)) {
      return false
    }
    if (Array.isArray(params.tags) && params.tags.length > 0) {
      const matchesTags = params.tags.some((tag) => item.tags.includes(tag))
      if (!matchesTags) {
        return false
      }
    }
    return true
  })
}

function invalidIdMessage(entityName) {
  return {
    success: false,
    message: `Current backend only supports numeric ${entityName} IDs in API mode` }
}

export const casesApi = {
  async getList(params) {
    const query = normalizeCaseQuery(params)
    const response = await httpClient.get('/api/knowledge/cases', {
      params: query
    })
    return buildCaseListResponse(response, query)
  },

    async getGridList(params) {
    const response = await httpClient.get('/api/cases', {
      params: {
        keyword: params.keyword || undefined,
        scoringCategory: params.scoringCategory || undefined,
        customerType: params.customerType || undefined,
        projectTypes: Array.isArray(params.projectTypes) && params.projectTypes.length > 0
          ? params.projectTypes.join(',') : undefined,
        uploadDateFrom: params.uploadDateFrom || undefined,
        uploadDateTo: params.uploadDateTo || undefined,
        closeDateFrom: params.closeDateFrom || undefined,
        closeDateTo: params.closeDateTo || undefined,
        statuses: Array.isArray(params.statuses) && params.statuses.length > 0
          ? params.statuses.join(',') : undefined,
        sortBy: params.sort || 'created',
        page: typeof params.page === 'number' ? params.page - 1 : 0,
        size: params.pageSize || 16
      }
    })
    const content = response.content || response.data?.content || []
    const total = response.totalElements || response.data?.totalElements || content.length
    return { data: content, total }
  },

  async getDetail(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('case'))

    const response = await httpClient.get(`/api/cases/${id}`)
    return response?.data || response
  },

  async create(data) {

    const response = await httpClient.post('/api/knowledge/cases', buildCasePayload(data))
    return { ...response, data: normalizeCase({ ...response?.data, ...data, viewCount: 0, useCount: 0 }) }
  },

  async update(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('case'))

    const response = await httpClient.put(`/api/knowledge/cases/${id}`, buildCasePayload(data))
    return { ...response, data: normalizeCase({ ...response?.data, ...data, id }) }
  },

  async delete(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('case'))
    return httpClient.delete(`/api/knowledge/cases/${id}`)
  },

  async getShareRecords(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('case'))
    return httpClient.get(`/api/knowledge/cases/${id}/share-records`)
  },

  async createShareRecord(id, data = {}) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('case'))
    return httpClient.post(`/api/knowledge/cases/${id}/share-records`, {
      createdBy: data.createdBy ?? null,
      createdByName: data.createdByName || '',
      baseUrl: data.baseUrl || window.location.origin,
      expiresAt: data.expiresAt ?? null })
  },

  async getReferenceRecords(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('case'))
    const response = await httpClient.get(`/api/cases/${id}/references`)
    return response?.data || response
  },

  async createReferenceRecord(id, data = {}) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('case'))
    return httpClient.post(`/api/knowledge/cases/${id}/references`, {
      referencedBy: data.referencedBy ?? null,
      referencedByName: data.referencedByName || '',
      referenceTarget: data.referenceTarget || '',
      referenceContext: data.referenceContext || '' })
  },

  async recommendCases(projectId, scoringItem, keyword) {
    const response = await httpClient.get('/api/cases/recommend', {
      params: {
        projectId,
        scoringItem: scoringItem || undefined,
        keyword: keyword || undefined
      }
    })
    return response?.data || []
  },

  async recommendForProject(projectId, keyword) {
    const response = await httpClient.get('/api/cases/recommend/project', {
      params: {
        projectId,
        keyword: keyword || undefined
      }
    })
    return response?.data || []
  },

  async reuseCase(id) {
    const response = await httpClient.post(`/api/cases/${id}/reuse`)
    return response?.data || response
  },

  async offShelfCase(id) {
    const response = await httpClient.post(`/api/cases/${id}/off-shelf`)
    return response?.data || response
  },
  async checkPrecipitationReadiness(projectId) {
    const response = await httpClient.get('/api/cases/precipitation-readiness', {
      params: { projectId }
    })
    return response
  },

  async precipitateCases(projectId) {
    const response = await httpClient.post('/api/cases/precipitate', null, {
      params: { projectId }
    })
    return response
  },

}

export const templatesApi = {
  async getList(params) {
    const query = {
      name: params?.name ? String(params.name).trim() : undefined,
      category: params?.category && params.category !== 'all'
        ? templateCategoryMap[params.category] || params.category
        : undefined,
      productType: params?.productType || undefined,
      industry: params?.industry || undefined,
      documentType: params?.documentType || undefined
    }
    const response = await httpClient.get('/api/knowledge/templates', {
      params: query
    })
    const normalized = Array.isArray(response?.data) ? response.data.map(normalizeTemplate) : []
    const filtered = filterTemplates(normalized, query)
    return {
      ...response,
      data: filtered,
      total: filtered.length }
  },

  async getDetail(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('template'))

    const response = await httpClient.get(`/api/knowledge/templates/${id}`)
    return { ...response, data: normalizeTemplate(response?.data) }
  },

  async create(data) {

    const response = await httpClient.post('/api/knowledge/templates', buildTemplatePayload(data))
    return { ...response, data: normalizeTemplate({ ...response?.data, ...data }) }
  },

  async update(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('template'))

    const response = await httpClient.put(`/api/knowledge/templates/${id}`, buildTemplatePayload(data))
    return { ...response, data: normalizeTemplate({ ...response?.data, ...data, id }) }
  },

  async delete(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('template'))
    return httpClient.delete(`/api/knowledge/templates/${id}`)
  },

  async copy(id, data = {}) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('template'))

    const response = await httpClient.post(`/api/knowledge/templates/${id}/copy`, {
      name: data.name,
      createdBy: data.createdBy ?? null })
    return { ...response, data: normalizeTemplate(response?.data) }
  },

  async getVersions(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('template'))

    return httpClient.get(`/api/knowledge/templates/${id}/versions`)
  },

  async recordUse(id, data = {}) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('template'))

    return httpClient.post(`/api/knowledge/templates/${id}/use-records`, {
      documentName: data.documentName,
      docType: data.docType,
      projectId: data.projectId ?? null,
      applyOptions: Array.isArray(data.applyOptions) ? data.applyOptions : [],
      usedBy: data.usedBy ?? null })
  },

  async recordDownload(id, data = {}) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('template'))

    const response = await httpClient.post(`/api/knowledge/templates/${id}/downloads`, {
      downloadedBy: data.downloadedBy ?? null })
    return { ...response, data: normalizeTemplate(response?.data) }
  } }

export default {
  qualifications: qualificationsApi,
  cases: casesApi,
  templates: templatesApi }
