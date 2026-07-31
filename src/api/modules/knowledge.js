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
import { isNumericId, invalidIdMessage } from './resources/shared.js'

// 后端 CaseDTO.Industry enum → 前端展示名（单向，不可逆）
// 后端 enum 定义见 backend/src/main/java/com/xiyu/bid/casework/dto/CaseDTO.java
const caseIndustryDisplayMap = {
  INFRASTRUCTURE: '基础设施',
  MANUFACTURING: '制造业',
  ENERGY: '能源',
  TRANSPORTATION: '交通',
  ENVIRONMENTAL: '环保',
  REAL_ESTATE: '房地产',
  OTHER: '其他'
}

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
  const normalizedIndustry = caseIndustryDisplayMap[item?.industry] || item?.industry || ''
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

export const casesApi = {
  async getList(params) {
    const query = normalizeCaseQuery(params)
    const response = await httpClient.get('/api/knowledge/cases', {
      params: query
    })
    // 后端已支持服务端分页和过滤，前端不再做二次本地过滤/分页
    if (response?.data && !Array.isArray(response.data) && Array.isArray(response.data.items)) {
      return {
        ...response,
        data: response.data.items.map(normalizeCase),
        total: Number(response.data.total ?? response.data.items.length ?? 0) }
    }
    const rawItems = Array.isArray(response?.data) ? response.data : []
    return { ...response, data: rawItems.map(normalizeCase), total: response?.total ?? rawItems.length }
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

  async exportZip(params = {}) {
    const queryParams = {
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
      sortBy: params.sortBy || 'created'
    }
    const response = await httpClient.post('/api/cases/export-zip', null, {
      params: queryParams,
      responseType: 'blob'
    })
    return response?.data || response
  },

  async exportExcel(params = {}) {
    const query = {
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
        ? params.statuses.join(',') : undefined
    }
    const response = await httpClient.post('/api/cases/export-excel', null, {
      params: query,
      responseType: 'blob'
    })
    const blob = response?.data
    if (blob && blob instanceof Blob) {
      const contentDisposition = response?.headers?.['content-disposition']
      let filename = '案例库台账.xlsx'
      if (contentDisposition) {
        const match = contentDisposition.match(/filename[^;=\n]*=(?:(\\?['"])(.*?)\1|[^;\n]*)/)
        if (match && match[2]) {
          filename = decodeURIComponent(match[2])
        }
      }
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
      return { success: true, filename }
    }
    return { success: false }
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
