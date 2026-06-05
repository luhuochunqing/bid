// Input: httpClient and feature-availability helpers
// Output: qualificationsApi - qualification CRUD and borrow accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'
import { buildFeatureUnavailableResponse } from '../featureAvailability.js'

const DAY_IN_MS = 24 * 60 * 60 * 1000

const qualificationTypeMap = {
  enterprise: 'CONSTRUCTION',
  personnel: 'DESIGN',
  product: 'SERVICE',
  industry: 'OTHER',
  '企业资质': 'enterprise',
  '软件能力': 'product',
  '安全资质': 'industry',
  CONSTRUCTION: 'enterprise',
  DESIGN: 'personnel',
  SERVICE: 'product',
  OTHER: 'industry'
}

const qualificationLevelMap = {
  enterprise: 'FIRST',
  personnel: 'SECOND',
  product: 'THIRD',
  industry: 'OTHER'
}

function isNumericId(id) {
  return /^\d+$/.test(String(id))
}

function getDateValue(date) {
  return date ? new Date(date) : null
}

function calculateRemainingDays(expiryDate) {
  const expiry = getDateValue(expiryDate)
  if (!expiry) return null
  return Math.ceil((expiry.getTime() - Date.now()) / DAY_IN_MS)
}

function mapQualificationStatus(expiryDate, explicitStatus) {
  if (explicitStatus) {
    return explicitStatus
  }

  const remainingDays = calculateRemainingDays(expiryDate)
  if (remainingDays == null) return 'valid'
  if (remainingDays < 0) return 'expired'
  if (remainingDays <= 30) return 'expiring'
  return 'valid'
}

function formatDate(date) {
  if (!date) return ''
  return String(date).slice(0, 10)
}

function normalizeQualification(item) {
  const expiryDate = item?.expiryDate || item?.expiry
  const mappedType = qualificationTypeMap[item?.type] || item?.type
  const remainingDays = item?.remainingDays ?? calculateRemainingDays(expiryDate)

  return {
    id: item?.id,
    name: item?.name || '未命名资质',
    type: mappedType || 'industry',
    subjectType: (item?.subjectType || 'COMPANY').toLowerCase(),
    subjectName: item?.subjectName || '',
    certificateNo: item?.certificateNo || '-',
    issueDate: formatDate(item?.issueDate),
    expiryDate: formatDate(expiryDate),
    issuer: item?.issuer || '-',
    holderName: item?.holderName || item?.holder || '-',
    status: mapQualificationStatus(expiryDate, item?.status),
    remainingDays: remainingDays ?? 0,
    currentBorrowStatus: String(item?.currentBorrowStatus || (item?.borrowed ? 'borrowed' : 'available')).toLowerCase(),
    currentBorrower: item?.currentBorrower || '',
    expectedReturnDate: formatDate(item?.expectedReturnDate || item?.currentExpectedReturnDate),
    fileUrl: item?.fileUrl || '',
    level: item?.level || qualificationLevelMap[mappedType] || 'OTHER'
  }
}

function normalizeQualificationBorrowRecord(item) {
  return {
    id: item?.id,
    qualificationId: item?.qualificationId ?? item?.certificateId ?? null,
    qualificationName: item?.qualificationName || item?.qualification?.name || '资质文件',
    borrower: item?.borrower || item?.borrowedByName || '-',
    department: item?.department || '-',
    purpose: item?.purpose || 'other',
    borrowDate: formatDate(item?.borrowDate || item?.borrowedAt || item?.createdAt),
    returnDate: formatDate(item?.returnDate || item?.expectedReturnDate),
    status: normalizeBorrowStatus(item?.status)
  }
}

function normalizeBorrowStatus(status) {
  if (!status) return 'borrowed'
  const normalized = String(status).toUpperCase()
  if (normalized === 'RETURNED') return 'returned'
  if (normalized === 'OVERDUE') return 'overdue'
  return 'borrowed'
}

function buildQualificationPayload(data = {}) {
  return {
    name: data.name,
    type: qualificationTypeMap[data.type] || 'OTHER',
    level: qualificationLevelMap[data.type] || data.level || 'OTHER',
    subjectType: String(data.subjectType || 'company').toUpperCase(),
    subjectName: data.subjectName || '',
    issueDate: data.issueDate || null,
    expiryDate: data.expiryDate || null,
    certificateNo: data.certificateNo || '',
    issuer: data.issuer || '',
    holderName: data.holderName || '',
    fileUrl: data.fileUrl || ''
  }
}

function buildBorrowPayload(data = {}) {
  return {
    borrower: data.borrower || '',
    department: data.department || '',
    projectId: data.projectId || '',
    purpose: data.purpose || '',
    expectedReturnDate: data.returnDate || '',
    remark: data.remark || ''
  }
}

function filterQualifications(items, params = {}) {
  return items.filter((item) => {
    if (params.name && !String(item.name || '').toLowerCase().includes(String(params.name).toLowerCase())) {
      return false
    }
    if (params.type && item.type !== params.type) {
      return false
    }
    if (params.status && item.status !== params.status) {
      return false
    }
    return true
  })
}

function invalidIdMessage(entityName) {
  return {
    success: false,
    message: `Current backend only supports numeric ${entityName} IDs in API mode`
  }
}

function buildBorrowUnavailableResponse() {
  return buildFeatureUnavailableResponse('qualificationBorrow', {
    title: '资质借阅暂未接入',
    message: '真实资质借阅接口尚未提供，当前仅保留真实资质列表能力。',
    hint: '后端补齐借阅记录、借阅申请和归还接口后即可恢复完整流程。'
  })
}

async function fetchQualificationList() {
  const response = await httpClient.get('/api/knowledge/qualifications')
  const data = Array.isArray(response?.data) ? response.data.map(normalizeQualification) : []
  return {
    ...response,
    data
  }
}

export const qualificationsApi = {
  async getList(params = {}) {
    const response = await fetchQualificationList()
    return {
      ...response,
      data: filterQualifications(response.data || [], params)
    }
  },

  async getDetail(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('qualification'))

    const response = await httpClient.get(`/api/knowledge/qualifications/${id}`)
    return { ...response, data: normalizeQualification(response?.data) }
  },

  async create(data) {
    const response = await httpClient.post('/api/knowledge/qualifications', buildQualificationPayload(data))
    return { ...response, data: normalizeQualification({ ...response?.data, ...data }) }
  },

  async update(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('qualification'))

    const response = await httpClient.put(`/api/knowledge/qualifications/${id}`, buildQualificationPayload(data))
    return { ...response, data: normalizeQualification({ ...response?.data, ...data, id }) }
  },

  async delete(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('qualification'))
    return httpClient.delete(`/api/knowledge/qualifications/${id}`)
  },

  async getBorrowRecords(qualificationId) {
    if (qualificationId != null && !isNumericId(qualificationId)) {
      return Promise.resolve(invalidIdMessage('qualification'))
    }

    const query = qualificationId != null ? `?qualificationId=${qualificationId}` : ''

    try {
      const response = await httpClient.get(`/api/knowledge/qualifications/borrow-records${query}`)
      return {
        ...response,
        data: Array.isArray(response?.data) ? response.data.map(normalizeQualificationBorrowRecord) : []
      }
    } catch (error) {
      if (error?.response?.status === 400 || error?.response?.status === 404) {
        return buildBorrowUnavailableResponse()
      }
      throw error
    }
  },

  async createBorrow(id, data = {}) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('qualification'))

    try {
      return await httpClient.post(`/api/knowledge/qualifications/${id}/borrow`, buildBorrowPayload(data))
    } catch (error) {
      if (error?.response?.status === 404) {
        return buildBorrowUnavailableResponse()
      }
      throw error
    }
  },

  async returnBorrow(recordId) {
    if (!isNumericId(recordId)) return Promise.resolve(invalidIdMessage('qualification borrow record'))

    try {
      return await httpClient.post(`/api/knowledge/qualifications/borrow-records/${recordId}/return`)
    } catch (error) {
      if (error?.response?.status === 404) {
        return buildBorrowUnavailableResponse()
      }
      throw error
    }
  }
}

export default qualificationsApi
