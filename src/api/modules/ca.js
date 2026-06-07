// Input: httpClient
// Output: caApi - CA certificate CRUD, borrow, and approval accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const BASE = '/api/ca-certificates'

const CA_TYPE_MAP = {
  ENTITY_CA: '实体CA',
  ELECTRONIC_CA: '电子CA'
}

const SEAL_TYPE_MAP = {
  OFFICIAL_SEAL: '公章',
  LEGAL_PERSON_SEAL: '法人章',
  LEGAL_SIGN: '法人签字',
  CONTACT_SIGN: '联系人签字'
}

const BORROW_STATUS_MAP = {
  IN_STOCK: '在库',
  BORROWED: '已借出',
  OVERDUE: '已逾期'
}

const STATUS_MAP = {
  ACTIVE: '有效',
  EXPIRING: '即将到期',
  EXPIRED: '已过期',
  INACTIVE: '已下架'
}

export const caLabels = {
  caType: CA_TYPE_MAP,
  sealType: SEAL_TYPE_MAP,
  borrowStatus: BORROW_STATUS_MAP,
  status: STATUS_MAP
}

export const caTagTypes = {
  borrowStatus: { IN_STOCK: 'info', BORROWED: 'primary', OVERDUE: 'danger' },
  status: { ACTIVE: 'success', EXPIRING: 'warning', EXPIRED: 'danger', INACTIVE: 'info' }
}

function formatDate(value) {
  if (!value) return ''
  const d = value instanceof Date ? value : new Date(value)
  if (isNaN(d.getTime())) return String(value).slice(0, 10)
  return d.toISOString().slice(0, 10)
}

function calcRemainingDays(expiryDate) {
  if (!expiryDate) return null
  const d = new Date(expiryDate)
  if (isNaN(d.getTime())) return null
  return Math.ceil((d.getTime() - Date.now()) / (24 * 60 * 60 * 1000))
}

function parsePlatformIds(platformIds) {
  if (!platformIds) return []
  if (Array.isArray(platformIds)) return platformIds
  try { return JSON.parse(platformIds) } catch { return [platformIds] }
}

export function normalizeCaCertificate(item) {
  if (!item) return null
  const expiryDate = item.expiryDate ? formatDate(item.expiryDate) : ''
  const remainingDays = item.remainingDays ?? calcRemainingDays(expiryDate)

  return {
    id: item.id,
    // 关联平台
    platformIds: parsePlatformIds(item.platformIds),
    platformIdsRaw: item.platformIds,
    // CA类型
    caType: item.caType || 'ENTITY_CA',
    caTypeLabel: CA_TYPE_MAP[item.caType] || item.caType || '实体CA',
    // 印章类型
    sealType: item.sealType || 'OFFICIAL_SEAL',
    sealTypeLabel: SEAL_TYPE_MAP[item.sealType] || item.sealType || '公章',
    // 电子CA账号
    electronicAccount: item.electronicAccount || '',
    // CA密码（加密存储，脱敏展示）
    caPassword: item.caPassword || '',
    caPasswordMasked: item.caPassword ? '******' : '',
    // 有效期
    expiryDate,
    remainingDays: remainingDays ?? 0,
    // 平台URL/APP名
    caPlatformUrl: item.caPlatformUrl || '',
    // 保管人
    custodianId: item.custodianId || '',
    custodianName: item.custodianName || '-',
    // 借用状态
    borrowStatus: item.borrowStatus || 'IN_STOCK',
    borrowStatusLabel: BORROW_STATUS_MAP[item.borrowStatus] || item.borrowStatus || '在库',
    // 当前借用人
    currentBorrowerId: item.currentBorrowerId || '',
    currentBorrowerName: item.currentBorrowerName || '',
    // 证书状态
    status: item.status || 'ACTIVE',
    statusLabel: STATUS_MAP[item.status] || item.status || '有效',
    // 备注
    remark: item.remark || '',
    // 时间戳
    createdAt: item.createdAt || '',
    updatedAt: item.updatedAt || '',
    // 原始数据保留
    _raw: item
  }
}

export function normalizeBorrowApplication(item) {
  if (!item) return null
  return {
    id: item.id,
    caCertificateId: item.caCertificateId,
    caName: item.caName || item.caCertificateName || '',
    applicantId: item.applicantId || '',
    applicantName: item.applicantName || '',
    purpose: item.purpose || '',
    projectId: item.projectId || '',
    projectName: item.projectName || '',
    borrowDate: formatDate(item.borrowDate || item.createdAt),
    expectedReturnDate: formatDate(item.expectedReturnDate),
    actualReturnDate: formatDate(item.actualReturnDate || item.returnedAt),
    status: item.status || 'PENDING',
    statusLabel: { PENDING: '待审批', APPROVED: '已批准', REJECTED: '已拒绝', RETURNED: '已归还', CANCELLED: '已取消' }[item.status] || item.status,
    remark: item.remark || '',
    approvedById: item.approvedById || '',
    approvedByName: item.approvedByName || '',
    approvedAt: item.approvedAt || '',
    createdAt: item.createdAt || ''
  }
}

export function normalizeOperationEvent(item) {
  if (!item) return null
  return {
    id: item.id,
    caCertificateId: item.caCertificateId,
    eventType: item.eventType || '',
    eventTypeLabel: {
      CREATED: '创建', UPDATED: '更新', BORROWED: '借用',
      RETURNED: '归还', APPROVED: '批准', REJECTED: '拒绝',
      CANCELLED: '取消', DEACTIVATED: '下架', ACTIVATED: '上架'
    }[item.eventType] || item.eventType,
    operatorId: item.operatorId || '',
    operatorName: item.operatorName || '-',
    detail: item.detail || item.description || '',
    createdAt: item.createdAt || ''
  }
}

export const caApi = {
  // 列表（带筛选）
  async getList(params = {}) {
    const response = await httpClient.get(BASE, { params })
    const data = Array.isArray(response?.data?.content)
      ? response.data.content.map(normalizeCaCertificate)
      : Array.isArray(response?.data)
        ? response.data.map(normalizeCaCertificate)
        : []
    return { ...response, data }
  },

  // 统计概览
  async getOverview() {
    const response = await httpClient.get(`${BASE}/overview`)
    return {
      ...response,
      data: {
        total: response?.data?.total ?? 0,
        expiring: response?.data?.expiring ?? 0,
        expired: response?.data?.expired ?? 0,
        borrowed: response?.data?.borrowed ?? 0
      }
    }
  },

  // 详情
  async getDetail(id) {
    const response = await httpClient.get(`${BASE}/${id}`)
    return { ...response, data: normalizeCaCertificate(response?.data) }
  },

  // 新增
  async create(data) {
    const response = await httpClient.post(BASE, data)
    return { ...response, data: normalizeCaCertificate(response?.data) }
  },

  // 更新
  async update(id, data) {
    const response = await httpClient.put(`${BASE}/${id}`, data)
    return { ...response, data: normalizeCaCertificate(response?.data) }
  },

  // 下架（deactivate）
  async deactivate(id) {
    return httpClient.delete(`${BASE}/${id}`)
  },

  // CA 借用申请
  async borrow(id, data = {}) {
    return httpClient.post(`${BASE}/${id}/borrow`, data)
  },

  // 审批：批准
  async approve(applicationId, data = {}) {
    return httpClient.post(`${BASE}/borrow-applications/${applicationId}/approve`, data)
  },

  // 审批：拒绝
  async reject(applicationId, data = {}) {
    return httpClient.post(`${BASE}/borrow-applications/${applicationId}/reject`, data)
  },

  // 归还
  async returnCa(applicationId, data = {}) {
    return httpClient.post(`${BASE}/borrow-applications/${applicationId}/return`, data)
  },

  // 取消借用
  async cancelBorrow(applicationId) {
    return httpClient.post(`${BASE}/borrow-applications/${applicationId}/cancel`)
  },

  // 借用记录
  async getBorrowApplications(caId) {
    const response = await httpClient.get(`${BASE}/${caId}/borrow-applications`)
    const data = Array.isArray(response?.data)
      ? response.data.map(normalizeBorrowApplication)
      : []
    return { ...response, data }
  },

  // 操作日志
  async getOperationEvents(applicationId) {
    const response = await httpClient.get(`${BASE}/borrow-applications/${applicationId}/events`)
    const data = Array.isArray(response?.data)
      ? response.data.map(normalizeOperationEvent)
      : []
    return { ...response, data }
  },

  // 待审批列表
  async getPendingApprovals() {
    const response = await httpClient.get(`${BASE}/pending-approvals`)
    const data = Array.isArray(response?.data)
      ? response.data.map(normalizeBorrowApplication)
      : []
    return { ...response, data }
  }
}

export default caApi
