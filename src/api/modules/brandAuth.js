// Input: httpClient
// Output: brandAuthApi - manufacturer authorization CRUD, upload, revoke
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const PRODUCT_LINES = [
  '工具', '工具耗材', '刀具', '量具', '焊接', '机床', '磨具', '润滑', '胶粘', '车间化学品',
  '劳保', '安全', '消防', '搬运', '存储', '工位', '包材', '清洁', '办公', '制冷',
  '暖通', '工控', '低压', '电工', '照明', '轴承', '皮带', '机械', '气动', '液压',
  '管阀', '泵', '紧固', '密封', '工业检测', '实验室产品', '企业福礼', '紧急救护', '建工材料'
]

const STATUS_MAP = {
  DRAFT: '草稿', ACTIVE: '生效中', EXPIRING_SOON: '即将到期', EXPIRED: '已失效', REVOKED: '已作废'
}

function formatDate(d) { return d ? String(d).slice(0, 10) : null }

function normalizeAuth(item) {
  const endDate = item.authEndDate ? new Date(item.authEndDate) : null
  const remainingDays = endDate ? Math.ceil((endDate - Date.now()) / 86400000) : null
  const status = item.status || 'ACTIVE'
  return {
    id: item.id,
    authorizationType: item.authorizationType || 'MANUFACTURER',
    productLine: item.productLine || '-',
    brandId: item.brandId || '-',
    brandName: item.brandName || '-',
    importDomestic: item.importDomestic || '-',
    manufacturerName: item.manufacturerName || '-',
    agentName: item.agentName || '-',
    authStartDate: formatDate(item.authStartDate),
    authEndDate: formatDate(item.authEndDate),
    auth1StartDate: formatDate(item.auth1StartDate),
    auth1EndDate: formatDate(item.auth1EndDate),
    auth1Remarks: item.auth1Remarks || '',
    auth2StartDate: formatDate(item.auth2StartDate),
    auth2EndDate: formatDate(item.auth2EndDate),
    auth2Remarks: item.auth2Remarks || '',
    remarks: item.remarks || '',
    status,
    statusLabel: STATUS_MAP[status] || status,
    statusTagType: status === 'ACTIVE' ? 'success' : status === 'EXPIRING_SOON' ? 'warning' : status === 'REVOKED' ? 'info' : 'danger',
    revokeReason: item.revokeReason || '',
    attachments: (item.attachments || []).map(a => ({
      id: a.id, attachmentType: a.attachmentType, fileName: a.fileName,
      fileUrl: a.fileUrl, fileSize: a.fileSize, fileType: a.fileType
    })),
    remainingDays,
    createdBy: item.createdBy,
    createdAt: formatDate(item.createdAt),
    updatedAt: formatDate(item.updatedAt)
  }
}

export const PRODUCT_LINE_OPTIONS = PRODUCT_LINES.map(p => ({ label: p, value: p }))
export const IMPORT_DOMESTIC_OPTIONS = [{ label: '进口', value: '进口' }, { label: '国产', value: '国产' }]
export const STATUS_OPTIONS = Object.entries(STATUS_MAP).map(([v, l]) => ({ label: l, value: v }))

export const brandAuthApi = {
  PRODUCT_LINES, STATUS_MAP,

  async getList(params = {}) {
    const q = new URLSearchParams()
    if (params.productLines?.length) params.productLines.forEach(p => q.append('productLines', p))
    if (params.brandId) q.set('brandId', params.brandId)
    if (params.brandName) q.set('brandName', params.brandName)
    if (params.importDomestic) q.set('importDomestic', params.importDomestic)
    if (params.manufacturerName) q.set('manufacturerName', params.manufacturerName)
    if (params.authStartFrom) q.set('authStartFrom', params.authStartFrom)
    if (params.authStartTo) q.set('authStartTo', params.authStartTo)
    if (params.authEndFrom) q.set('authEndFrom', params.authEndFrom)
    if (params.authEndTo) q.set('authEndTo', params.authEndTo)
    if (params.statuses?.length) params.statuses.forEach(s => q.append('statuses', s))
    if (params.keyword) q.set('keyword', params.keyword)
    // Accept authorizationType to filter by OEM or Agent
    if (params.authorizationType) q.set('authorizationType', params.authorizationType)
    q.set('page', params.page || 0)
    q.set('size', params.size || 20)
    const res = await httpClient.get(`/api/knowledge/brand-auth?${q.toString()}`)
    const content = (res?.data?.content || []).map(normalizeAuth)
    return { ...res, data: { ...res?.data, content } }
  },

  async getDetail(id) {
    const res = await httpClient.get(`/api/knowledge/brand-auth/${id}`)
    return { ...res, data: normalizeAuth(res?.data) }
  },

  async create(data) {
    const payload = {
      authorizationType: data.authorizationType || 'MANUFACTURER',
      productLine: data.productLine || '工具',
      brandId: data.brandId || '',
      brandName: data.brandName || '',
      importDomestic: data.importDomestic || '国产',
      manufacturerName: data.manufacturerName || '',
      agentName: data.agentName || null,
      authStartDate: data.authStartDate || null,
      authEndDate: data.authEndDate || null,
      auth1StartDate: data.auth1StartDate || null,
      auth1EndDate: data.auth1EndDate || null,
      auth1Remarks: data.auth1Remarks || '',
      auth2StartDate: data.auth2StartDate || null,
      auth2EndDate: data.auth2EndDate || null,
      auth2Remarks: data.auth2Remarks || '',
      remarks: data.remarks || ''
    }
    const res = await httpClient.post('/api/knowledge/brand-auth', payload)
    return { ...res, data: res?.data?.data ? normalizeAuth(res.data.data) : null, warning: res?.data?.warning }
  },

  async update(id, data) {
    const payload = {
      productLine: data.productLine || undefined,
      brandId: data.brandId || undefined,
      brandName: data.brandName || undefined,
      importDomestic: data.importDomestic || undefined,
      manufacturerName: data.manufacturerName || undefined,
      agentName: data.agentName !== undefined ? data.agentName : undefined,
      authStartDate: data.authStartDate || undefined,
      authEndDate: data.authEndDate || undefined,
      auth1StartDate: data.auth1StartDate !== undefined ? data.auth1StartDate : undefined,
      auth1EndDate: data.auth1EndDate !== undefined ? data.auth1EndDate : undefined,
      auth1Remarks: data.auth1Remarks !== undefined ? data.auth1Remarks : undefined,
      auth2StartDate: data.auth2StartDate !== undefined ? data.auth2StartDate : undefined,
      auth2EndDate: data.auth2EndDate !== undefined ? data.auth2EndDate : undefined,
      auth2Remarks: data.auth2Remarks !== undefined ? data.auth2Remarks : undefined,
      remarks: data.remarks !== undefined ? data.remarks : undefined
    }
    const res = await httpClient.put(`/api/knowledge/brand-auth/${id}`, payload)
    return { ...res, data: normalizeAuth(res?.data) }
  },

  async uploadAttachments(authorizationId, attachmentType, files) {
    const fd = new FormData()
    fd.append('authorizationId', authorizationId)
    fd.append('attachmentType', attachmentType)
    files.forEach(f => fd.append('files', f))
    const res = await httpClient.post('/api/knowledge/brand-auth/attachments/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return res
  },

  async revoke(id, reason) {
    const res = await httpClient.post(`/api/knowledge/brand-auth/${id}/revoke`, { reason })
    return { ...res, data: normalizeAuth(res?.data) }
  },

  async getLogs(id) {
    return httpClient.get(`/api/knowledge/brand-auth/${id}/logs`)
  }
}

export default brandAuthApi
