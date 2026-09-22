// Input: httpClient and contract borrow payloads
// Output: contractBorrowApi - contract borrow list, lifecycle, and event accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '@/api/client'

const STATUS_LABELS = {
  PENDING_APPROVAL: '待审批',
  APPROVED: '已审批',
  BORROWED: '借阅中',
  RETURNED: '已归还',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  OVERDUE: '已逾期'
}

function normalizeStatus(status) {
  return String(status || '').trim().toUpperCase()
}

function normalizeFilters(filters = {}) {
  const params = { ...filters }
  if (params.status) {
    params.status = normalizeStatus(params.status)
  }
  return params
}

function normalizeItem(item = {}) {
  const displayStatus = normalizeStatus(item.displayStatus || item.status)
  return {
    ...item,
    displayStatus,
    statusLabel: STATUS_LABELS[displayStatus] || STATUS_LABELS[item.status] || item.status || '-'
  }
}

function normalizePageResponse(response) {
  if (Array.isArray(response?.data)) {
    const data = response.data.map(normalizeItem)
    return {
      ...response,
      data: {
        items: data,
        total: data.length,
        page: 1,
        size: data.length,
        totalPages: data.length > 0 ? 1 : 0
      }
    }
  }

  const payload = response?.data || {}
  const items = Array.isArray(payload.items) ? payload.items.map(normalizeItem) : []
  return {
    ...response,
    data: {
      ...payload,
      items,
      total: Number(payload.total || items.length),
      page: Number(payload.page || 1),
      size: Number(payload.size || items.length),
      totalPages: Number(payload.totalPages || 0)
    }
  }
}

function normalizeResponse(response) {
  if (!response?.data || Array.isArray(response.data)) {
    return response
  }
  return { ...response, data: normalizeItem(response.data) }
}

export const contractBorrowApi = {
  async getOverview() {
    return httpClient.get('/api/contract-borrows/overview')
  },

  async getList(filters = {}) {
    const response = await httpClient.get('/api/contract-borrows', {
      params: normalizeFilters(filters)
    })
    return normalizePageResponse(response)
  },

  async getDetail(id) {
    const response = await httpClient.get(`/api/contract-borrows/${id}`)
    return normalizeResponse(response)
  },

  async create(payload) {
    const response = await httpClient.post('/api/contract-borrows', payload)
    return normalizeResponse(response)
  },

  async approve(id, payload) {
    const response = await httpClient.post(`/api/contract-borrows/${id}/approve`, payload)
    return normalizeResponse(response)
  },

  async reject(id, payload) {
    const response = await httpClient.post(`/api/contract-borrows/${id}/reject`, payload)
    return normalizeResponse(response)
  },

  async returnBack(id, payload) {
    const response = await httpClient.post(`/api/contract-borrows/${id}/return`, payload)
    return normalizeResponse(response)
  },

  async cancel(id, payload) {
    const response = await httpClient.post(`/api/contract-borrows/${id}/cancel`, payload)
    return normalizeResponse(response)
  },

  async getEvents(id) {
    return httpClient.get(`/api/contract-borrows/${id}/events`)
  }
}

export default contractBorrowApi
