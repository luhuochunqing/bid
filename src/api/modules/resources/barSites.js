// Input: BAR asset/site API responses and BAR site payloads
// Output: barSitesApi and related BAR site sub-accessors
// Pos: src/api/modules/resources/ - BAR site API submodule
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { httpClient, formatDate, formatDateTime, invalidIdMessage, isNumericId, pageContent } from '@/api/modules/resources/shared'

function parseBarMeta(remark) {
  if (!remark || typeof remark !== 'string' || !remark.startsWith('BAR_SITE_META:')) return {}

  try {
    const meta = JSON.parse(remark.slice('BAR_SITE_META:'.length))
    return {
      url: meta.u || '',
      region: meta.r || '',
      industry: meta.i || '',
      siteType: meta.s || '',
      loginType: meta.l || '',
      remark: meta.m || '',
      lastVerifyTime: meta.v || ''
    }
  } catch {
    return {}
  }
}

function createBarRemark(site = {}) {
  const meta = {
    u: site.url || '',
    r: site.region || '',
    i: site.industry || '',
    s: site.siteType || '',
    l: site.loginType || '',
    m: site.remark || '',
    v: site.lastVerifyTime || ''
  }

  return `BAR_SITE_META:${JSON.stringify(meta)}`
}

function normalizeBarStatus(status) {
  const value = String(status || '').toUpperCase()
  return value === 'AVAILABLE' || value === 'IN_USE' ? 'active' : 'inactive'
}

function normalizeRiskLevel(asset = {}) {
  const value = String(asset.status || '').toUpperCase()
  if (value === 'MAINTENANCE' || value === 'RETIRED' || value === 'DISPOSED') return 'high'
  if (value === 'IN_USE') return 'medium'
  return 'low'
}

function normalizeBarAssetType(type) {
  const value = String(type || '').toUpperCase()
  const map = {
    EQUIPMENT: '设备资产',
    FACILITY: '站点设施',
    VEHICLE: '车辆资产',
    INVENTORY: '库存资产',
    LICENSE: '数字证书/许可',
    OTHER: '其他资产'
  }
  return map[value] || '其他资产'
}

function mapSiteStatusToAssetStatus(status) {
  return String(status || '').toLowerCase() === 'inactive' ? 'MAINTENANCE' : 'AVAILABLE'
}

function normalizeBarSite(item = {}) {
  const meta = parseBarMeta(item.remark)
  const riskLevel = normalizeRiskLevel(item)
  return {
    id: item.id,
    name: item.name || '',
    url: meta.url || '',
    region: meta.region || '',
    industry: meta.industry || '',
    siteType: meta.siteType || normalizeBarAssetType(item.type),
    loginType: meta.loginType || '',
    remark: meta.remark || '',
    status: normalizeBarStatus(item.status),
    riskLevel,
    hasRisk: riskLevel !== 'low',
    lastVerifyTime: meta.lastVerifyTime || formatDate(item.updatedAt || item.acquireDate),
    accounts: [],
    uks: [],
    attachments: [],
    auditLog: [],
    sop: null,
    assetType: item.type || 'OTHER',
    assetValue: Number(item.value || 0),
    acquireDate: formatDate(item.acquireDate),
    raw: item
  }
}

function normalizeBarSiteAccount(item = {}) {
  return {
    id: item.id,
    username: item.username || '',
    role: item.role || 'viewer',
    owner: item.owner || '',
    phone: item.phone || '',
    email: item.email || '',
    status: item.status || 'active',
    raw: item
  }
}

function normalizeBarSiteAttachment(item = {}) {
  return {
    id: item.id,
    name: item.name || '',
    size: item.size || '',
    contentType: item.contentType || '',
    url: item.url || '',
    uploadedBy: item.uploadedBy || '',
    uploadedAt: formatDateTime(item.uploadedAt),
    raw: item
  }
}

function normalizeBarVerification(item = {}) {
  return {
    id: item.id,
    verifiedBy: item.verifiedBy || '',
    verifiedAt: formatDateTime(item.verifiedAt),
    status: String(item.status || '').toUpperCase() || 'SUCCESS',
    message: item.message || '',
    raw: item
  }
}

function normalizeBarSop(item = {}) {
  return {
    resetUrl: item.resetUrl || '',
    unlockUrl: item.unlockUrl || '',
    contacts: Array.isArray(item.contacts) ? item.contacts : [],
    requiredDocs: Array.isArray(item.requiredDocs) ? item.requiredDocs : [],
    faqs: Array.isArray(item.faqs) ? item.faqs : [],
    history: Array.isArray(item.history) ? item.history : [],
    estimatedTime: item.estimatedTime || ''
  }
}

function createBarAssetPayload(site = {}) {
  const parsedValue = Number(site.assetValue || 1)
  return {
    name: site.name || '',
    type: site.assetType || 'OTHER',
    value: Number.isFinite(parsedValue) && parsedValue > 0 ? parsedValue : 1,
    status: mapSiteStatusToAssetStatus(site.status),
    acquireDate: site.acquireDate || new Date().toISOString().split('T')[0],
    remark: createBarRemark(site)
  }
}

function filterBarSites(sites, params = {}) {
  return sites.filter((site) => {
    if (params.region && site.region !== params.region) return false
    if (params.industry && site.industry !== params.industry) return false
    if (params.loginType && site.loginType !== params.loginType) return false
    if (params.status && site.status !== params.status) return false
    if (params.riskLevel && site.riskLevel !== params.riskLevel) return false
    return true
  })
}

export const barSitesApi = {
  async getList(params = {}) {
    const response = await httpClient.get('/api/resources/bar-assets')
    const { page, content } = pageContent(response)
    const sites = filterBarSites(content.map(normalizeBarSite), params)
    return { ...response, data: sites, total: page?.totalElements ?? sites.length }
  },

  async getDetail(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('bar asset'))
    const response = await httpClient.get(`/api/resources/bar-assets/${id}`)
    return response?.data ? { ...response, data: normalizeBarSite(response?.data) } : { ...response, data: null }
  },

  async create(data) {
    const response = await httpClient.post('/api/resources/bar-assets', createBarAssetPayload(data))
    return { ...response, data: normalizeBarSite(response?.data) }
  },

  async update(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('bar asset'))
    const response = await httpClient.put(`/api/resources/bar-assets/${id}`, createBarAssetPayload(data))
    return { ...response, data: normalizeBarSite(response?.data) }
  },

  async updateStatus(id, status) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('bar asset'))
    const response = await httpClient.patch(`/api/resources/bar-assets/${id}/status`, { status })
    return { ...response, data: normalizeBarSite(response?.data) }
  },

  async verify(id, payload = {}) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('bar asset'))
    const response = await httpClient.post(`/api/resources/bar-assets/${id}/verify`, payload)
    return { ...response, data: normalizeBarVerification(response?.data) }
  },

  async getVerificationRecords(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('bar asset'))
    const response = await httpClient.get(`/api/resources/bar-assets/${id}/verification-records`)
    return { ...response, data: Array.isArray(response?.data) ? response.data.map(normalizeBarVerification) : [] }
  },

  async delete(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('bar asset'))
    return httpClient.delete(`/api/resources/bar-assets/${id}`)
  }
}

export const barSiteAccountsApi = {
  async getList(siteId) {
    if (!isNumericId(siteId)) return Promise.resolve(invalidIdMessage('bar site account'))
    const response = await httpClient.get(`/api/resources/bar-assets/${siteId}/accounts`)
    const { content } = pageContent(response)
    return { ...response, data: content.map(normalizeBarSiteAccount) }
  },

  async create(siteId, data) {
    if (!isNumericId(siteId)) return Promise.resolve(invalidIdMessage('bar asset'))
    const response = await httpClient.post(`/api/resources/bar-assets/${siteId}/accounts`, data)
    return { ...response, data: normalizeBarSiteAccount(response?.data) }
  },

  async update(siteId, accountId, data) {
    if (!isNumericId(siteId) || !isNumericId(accountId)) return Promise.resolve(invalidIdMessage('bar site account'))
    const response = await httpClient.put(`/api/resources/bar-assets/${siteId}/accounts/${accountId}`, data)
    return { ...response, data: normalizeBarSiteAccount(response?.data) }
  },

  async delete(siteId, accountId) {
    if (!isNumericId(siteId) || !isNumericId(accountId)) return Promise.resolve(invalidIdMessage('bar site account'))
    return httpClient.delete(`/api/resources/bar-assets/${siteId}/accounts/${accountId}`)
  }
}

export const barSiteSopApi = {
  async get(siteId) {
    if (!isNumericId(siteId)) return Promise.resolve(invalidIdMessage('bar asset'))
    const response = await httpClient.get(`/api/resources/bar-assets/${siteId}/sop`)
    return { ...response, data: normalizeBarSop(response?.data || {}) }
  },

  async update(siteId, data) {
    if (!isNumericId(siteId)) return Promise.resolve(invalidIdMessage('bar asset'))
    const response = await httpClient.put(`/api/resources/bar-assets/${siteId}/sop`, data)
    return { ...response, data: normalizeBarSop(response?.data || {}) }
  }
}

export const barSiteAttachmentsApi = {
  async getList(siteId) {
    if (!isNumericId(siteId)) return Promise.resolve(invalidIdMessage('bar site attachment'))
    const response = await httpClient.get(`/api/resources/bar-assets/${siteId}/attachments`)
    const { content } = pageContent(response)
    return { ...response, data: content.map(normalizeBarSiteAttachment) }
  },

  async create(siteId, data) {
    if (!isNumericId(siteId)) return Promise.resolve(invalidIdMessage('bar asset'))
    const response = await httpClient.post(`/api/resources/bar-assets/${siteId}/attachments`, data)
    return { ...response, data: normalizeBarSiteAttachment(response?.data) }
  },

  async delete(siteId, attachmentId) {
    if (!isNumericId(siteId) || !isNumericId(attachmentId)) return Promise.resolve(invalidIdMessage('bar site attachment'))
    return httpClient.delete(`/api/resources/bar-assets/${siteId}/attachments/${attachmentId}`)
  }
}
