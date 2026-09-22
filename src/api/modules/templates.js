// Input: httpClient and template-library shared classification config
// Output: templates API module with backend-owned official filtering and strict classification pass-through
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'
import {
  normalizeDocumentType,
  normalizeIndustry,
  normalizeProductType,
  normalizeTemplateCategory,
  toDocumentTypeValue,
  toIndustryValue,
  toProductTypeValue,
  toTemplateCategoryApiValue
} from '@/config/templateLibrary.js'

function isNumericId(id) {
  return /^\d+$/.test(String(id))
}

function formatDate(date) {
  if (!date) return ''
  return String(date).slice(0, 10)
}

function invalidIdMessage(entityName) {
  return {
    success: false,
    message: `Current backend only supports numeric ${entityName} IDs in API mode`
  }
}

export function normalizeTemplate(item) {
  return {
    id: item?.id,
    name: item?.name || '未命名模板',
    category: normalizeTemplateCategory(item?.category),
    productType: normalizeProductType(item?.productType),
    industry: normalizeIndustry(item?.industry),
    documentType: normalizeDocumentType(item?.documentType),
    tags: Array.isArray(item?.tags) ? item.tags : [],
    description: item?.description || '暂无真实模板描述',
    downloads: Number(item?.downloads || 0),
    useCount: Number(item?.useCount || 0),
    updateTime: formatDate(item?.updatedAt || item?.createdAt || item?.updateTime) || '-',
    version: item?.currentVersion || item?.version || '1.0',
    fileSize: item?.fileSize || '未知',
    fileUrl: item?.fileUrl || '',
    content: item?.content || '',
    structure: Array.isArray(item?.structure) ? item.structure : [],
    createdBy: item?.createdBy ?? null
  }
}

export function buildTemplatePayload(data = {}) {
  return {
    name: data.name,
    category: toTemplateCategoryApiValue(data.category || 'technical'),
    productType: toProductTypeValue(data.productType),
    industry: toIndustryValue(data.industry),
    documentType: toDocumentTypeValue(data.documentType),
    fileUrl: data.fileUrl || '',
    description: data.description || '',
    fileSize: data.fileSize || '',
    tags: Array.isArray(data.tags) ? data.tags : [],
    createdBy: data.createdBy ?? null
  }
}

function buildTemplateQuery(params = {}) {
  const query = {}
  if (params.category && params.category !== 'all') {
    query.category = toTemplateCategoryApiValue(params.category)
  }
  if (params.productType) {
    query.productType = toProductTypeValue(params.productType)
  }
  if (params.industry) {
    query.industry = toIndustryValue(params.industry)
  }
  if (params.documentType) {
    query.documentType = toDocumentTypeValue(params.documentType)
  }
  if (params.name) {
    query.name = params.name
  }
  return query
}

export const templatesApi = {
  async getList(params = {}) {
    const response = await httpClient.get('/api/knowledge/templates', {
      params: buildTemplateQuery(params)
    })
    const data = Array.isArray(response?.data) ? response.data.map(normalizeTemplate) : []
    return {
      ...response,
      data,
      total: data.length
    }
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
      createdBy: data.createdBy ?? null
    })
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
      usedBy: data.usedBy ?? null
    })
  },

  async recordDownload(id, data = {}) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('template'))
    const response = await httpClient.post(`/api/knowledge/templates/${id}/downloads`, {
      downloadedBy: data.downloadedBy ?? null
    })
    return { ...response, data: normalizeTemplate(response?.data) }
  }
}

export default templatesApi
