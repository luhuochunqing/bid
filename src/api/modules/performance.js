// Input: httpClient and feature-availability helpers
// Output: performanceApi - performance record CRUD accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

export const CUSTOMER_TYPE_MAP = {
  GOVERNMENT_INSTITUTION: '政府机关/事业单位',
  CENTRAL_SOE: '央企',
  LOCAL_SOE: '地方国企',
  PRIVATE_ENTERPRISE: '民企',
  FOREIGN_HK_MACAO_TW: '港澳台/外企'
}

export const PROJECT_TYPE_MAP = {
  OFFICE: '办公',
  COMPREHENSIVE: '综合',
  CENTRALIZED: '集采',
  INDUSTRIAL: '工业品',
  OTHER: '其他'
}

export const DOCKING_METHOD_MAP = {
  EMALL: 'Emall',
  PUNCH_OUT: 'Punch-out',
  API: 'API'
}

export const CUSTOMER_LEVEL_MAP = {
  GROUP: '集团',
  SUBSIDIARY: '二级单位'
}

export const CONTRACT_STATUS_MAP = {
  IN_PERFORMANCE: '履约中',
  EXPIRING: '即将到期',
  EXPIRED: '已到期'
}

function formatDate(d) {
  return d ? String(d).slice(0, 10) : null
}

function normalizePerformance(p) {
  if (!p) return null
  return {
    id: p.id,
    contractName: p.contractName || '-',
    signingEntity: p.signingEntity || '-',
    groupCompany: p.groupCompany || '-',
    customerType: p.customerType || '',
    customerTypeLabel: CUSTOMER_TYPE_MAP[p.customerType] || p.customerType || '-',
    industry: p.industry || '-',
    projectType: p.projectType || '',
    projectTypeLabel: PROJECT_TYPE_MAP[p.projectType] || p.projectType || '-',
    dockingMethod: p.dockingMethod || '',
    dockingMethodLabel: DOCKING_METHOD_MAP[p.dockingMethod] || p.dockingMethod || '-',
    customerLevel: p.customerLevel || '',
    customerLevelLabel: CUSTOMER_LEVEL_MAP[p.customerLevel] || p.customerLevel || '-',
    signingDate: formatDate(p.signingDate),
    expiryDate: formatDate(p.expiryDate),
    totalExpiryDate: formatDate(p.totalExpiryDate),
    daysRemaining: p.daysRemaining,
    expiryReminder: p.expiryReminder || '',
    status: p.status || 'IN_PERFORMANCE',
    statusLabel: CONTRACT_STATUS_MAP[p.status] || p.status || '未知',
    contactPerson: p.contactPerson || '-',
    contactInfo: p.contactInfo || '-',
    territory: p.territory || '-',
    customerAddress: p.customerAddress || '-',
    xiyuProjectManager: p.xiyuProjectManager || '-',
    mallWebsiteUrl: p.mallWebsiteUrl || '',
    hasBidNotice: !!p.hasBidNotice,
    remarks: p.remarks || '',
    attachments: Array.isArray(p.attachments) ? p.attachments : [],
    createdAt: formatDate(p.createdAt),
    updatedAt: formatDate(p.updatedAt)
  }
}

function buildPayload(data) {
  return {
    contractName: data.contractName || '',
    signingEntity: data.signingEntity || '',
    groupCompany: data.groupCompany || '',
    customerType: data.customerType || null,
    industry: data.industry || '',
    projectType: data.projectType || null,
    dockingMethod: data.dockingMethod || null,
    customerLevel: data.customerLevel || null,
    signingDate: data.signingDate || null,
    expiryDate: data.expiryDate || null,
    totalExpiryDate: data.totalExpiryDate || null,
    contactPerson: data.contactPerson || '',
    contactInfo: data.contactInfo || '',
    territory: data.territory || '',
    customerAddress: data.customerAddress || '',
    xiyuProjectManager: data.xiyuProjectManager || '',
    mallWebsiteUrl: data.mallWebsiteUrl || '',
    hasBidNotice: !!data.hasBidNotice,
    remarks: data.remarks || '',
    attachments: Array.isArray(data.attachments) ? data.attachments.map(a => ({
      fileName: a.fileName,
      fileUrl: a.fileUrl,
      fileType: a.fileType
    })) : []
  }
}

// 构造导出接口的 query string：支持 ids（勾选）+ 筛选条件（与 /list 一致）
function buildExportQuery(params = {}) {
  const qs = new URLSearchParams()
  if (Array.isArray(params.ids) && params.ids.length > 0) {
    params.ids.forEach(id => qs.append('ids', id))
  }
  if (params.keyword) qs.set('keyword', params.keyword)
  if (Array.isArray(params.customerTypes) && params.customerTypes.length > 0) {
    params.customerTypes.forEach(v => qs.append('customerTypes', v))
  }
  if (Array.isArray(params.projectTypes) && params.projectTypes.length > 0) {
    params.projectTypes.forEach(v => qs.append('projectTypes', v))
  }
  if (Array.isArray(params.statuses) && params.statuses.length > 0) {
    params.statuses.forEach(v => qs.append('statuses', v))
  }
  if (Array.isArray(params.customerLevels) && params.customerLevels.length > 0) {
    params.customerLevels.forEach(v => qs.append('customerLevels', v))
  }
  if (params.territory) qs.set('territory', params.territory)
  if (Array.isArray(params.signingDateRange) && params.signingDateRange[0])
    qs.set('signingDateStart', params.signingDateRange[0])
  if (Array.isArray(params.signingDateRange) && params.signingDateRange[1])
    qs.set('signingDateEnd', params.signingDateRange[1])
  if (Array.isArray(params.expiryDateRange) && params.expiryDateRange[0])
    qs.set('expiryDateStart', params.expiryDateRange[0])
  if (Array.isArray(params.expiryDateRange) && params.expiryDateRange[1])
    qs.set('expiryDateEnd', params.expiryDateRange[1])
  if (params.hasBidNotice !== null && params.hasBidNotice !== undefined && params.hasBidNotice !== '')
    qs.set('hasBidNotice', String(params.hasBidNotice))
  if (params.projectManagerKeyword) qs.set('projectManagerKeyword', params.projectManagerKeyword)
  const s = qs.toString()
  return s ? `?${s}` : ''
}

export const performanceApi = {
  async getList(params = {}) {
    const qs = new URLSearchParams()
    if (params.keyword) qs.set('keyword', params.keyword)
    if (Array.isArray(params.customerTypes) && params.customerTypes.length > 0) {
      params.customerTypes.forEach(v => qs.append('customerTypes', v))
    }
    if (Array.isArray(params.projectTypes) && params.projectTypes.length > 0) {
      params.projectTypes.forEach(v => qs.append('projectTypes', v))
    }
    if (Array.isArray(params.statuses) && params.statuses.length > 0) {
      params.statuses.forEach(v => qs.append('statuses', v))
    }
    if (Array.isArray(params.customerLevels) && params.customerLevels.length > 0) {
      params.customerLevels.forEach(v => qs.append('customerLevels', v))
    }
    if (params.territory) qs.set('territory', params.territory)
    // 签约日期范围 [start, end]
    if (Array.isArray(params.signingDateRange) && params.signingDateRange[0])
      qs.set('signingDateStart', params.signingDateRange[0])
    if (Array.isArray(params.signingDateRange) && params.signingDateRange[1])
      qs.set('signingDateEnd', params.signingDateRange[1])
    // 截止日期范围
    if (Array.isArray(params.expiryDateRange) && params.expiryDateRange[0])
      qs.set('expiryDateStart', params.expiryDateRange[0])
    if (Array.isArray(params.expiryDateRange) && params.expiryDateRange[1])
      qs.set('expiryDateEnd', params.expiryDateRange[1])
    // 是否有中标通知书（boolean，注意 false 也需要传）
    if (params.hasBidNotice !== null && params.hasBidNotice !== undefined && params.hasBidNotice !== '')
      qs.set('hasBidNotice', String(params.hasBidNotice))
    if (params.projectManagerKeyword) qs.set('projectManagerKeyword', params.projectManagerKeyword)
    const query = qs.toString() ? `?${qs.toString()}` : ''
    const res = await httpClient.get(`/api/knowledge/performance${query}`)
    return { ...res, data: Array.isArray(res?.data) ? res.data.map(normalizePerformance) : [] }
  },


  async getDetail(id) {
    const res = await httpClient.get(`/api/knowledge/performance/${id}`)
    return { ...res, data: normalizePerformance(res?.data) }
  },

  async create(data) {
    const res = await httpClient.post('/api/knowledge/performance', buildPayload(data))
    return { ...res, data: normalizePerformance(res?.data) }
  },

  async update(id, data) {
    const res = await httpClient.put(`/api/knowledge/performance/${id}`, buildPayload(data))
    return { ...res, data: normalizePerformance(res?.data) }
  },

  async delete(id) {
    return httpClient.delete(`/api/knowledge/performance/${id}`)
  },

  async downloadTemplate() {
    const res = await httpClient.get('/api/knowledge/performance/template', { responseType: 'blob' })
    const blob = res.data
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', 'performance_template.xlsx')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    return res
  },

  async batchImport(file, attachments = []) {
    const formData = new FormData()
    formData.append('file', file)
    attachments.forEach(a => formData.append('attachments', a))
    return httpClient.post('/api/knowledge/performance/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  async batchExport(params = {}) {
    const query = buildExportQuery(params)
    const res = await httpClient.get(`/api/knowledge/performance/export${query}`, { responseType: 'blob' })
    const blob = res.data
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `performance_export_${new Date().toISOString().slice(0,10)}.xlsx`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    return res
  },

  async batchExportZip(params = {}) {
    const query = buildExportQuery(params)
    const res = await httpClient.get(`/api/knowledge/performance/export-zip${query}`, { responseType: 'blob' })
    const blob = res.data
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const timestamp = new Date().toISOString().slice(0,10).replace(/-/g, '')
    link.setAttribute('download', `performance_export_${timestamp}.zip`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    return res
  },

  // CO-442: 上传业绩附件（不依赖 performanceId，新增业绩时先用此接口拿 fileUrl）
  async uploadAttachment(file, fileType) {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('fileType', fileType)
    return httpClient.post('/api/knowledge/performance/attachments/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

export default performanceApi
