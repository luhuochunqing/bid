// Input: platform account API responses and account payloads
// Output: accountsApi - normalized platform account accessors
// Pos: src/api/modules/resources/ - Account API submodule
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { httpClient, invalidIdMessage, isNumericId, formatDateTime, pageContent } from '@/api/modules/resources/shared'

function normalizeAccountStatus(status) {
  const value = String(status || '').toUpperCase()
  if (value === 'AVAILABLE') return 'available'
  if (value === 'BORROWED' || value === 'IN_USE') return 'in_use'
  if (value === 'DISABLED') return 'disabled'
  return 'available'
}

function normalizePlatformLabel(platformType, fallback) {
  const type = String(platformType || '').toUpperCase()
  const map = {
    BID_PLATFORM: '投标平台',
    PROCUREMENT_PLATFORM: '采购平台',
    GOVERNMENT_PLATFORM: '政府平台',
    OTHER: '其他平台'
  }
  return fallback || map[type] || type || '未知平台'
}

function normalizeAccount(item = {}) {
  return {
    id: item.id,
    platform: item.platform || normalizePlatformLabel(item.platformType, item.accountName),
    username: item.username || '',
    password: item.password || '',
    status: item.status ? normalizeAccountStatus(item.status) : 'available',
    lastUsed: formatDateTime(item.updatedAt || item.borrowedAt || item.lastUsed),
    borrower: item.borrower || (item.borrowedBy ? `用户#${item.borrowedBy}` : ''),
    dueAt: formatDateTime(item.dueAt),
    raw: item
  }
}

function filterAccounts(accounts, params = {}) {
  return accounts.filter((account) => {
    if (params.status && account.status !== params.status) return false
    if (params.platform) {
      const keyword = String(params.platform).trim().toLowerCase()
      if (!String(account.platform || '').toLowerCase().includes(keyword)) return false
    }
    return true
  })
}

function withSuccess(response, patch = {}) {
  const base = response && !Array.isArray(response) && typeof response === 'object' ? response : {}
  const success = typeof base.success === 'boolean' ? base.success : true
  return {
    ...base,
    ...patch,
    success
  }
}

export const accountsApi = {
  async getList(params = {}) {
    const response = await httpClient.get('/api/platform/accounts')
    const { page, content } = pageContent(response)
    const accounts = content.map(normalizeAccount)
    const data = filterAccounts(accounts, params)
    return withSuccess(response, { data, total: page?.totalElements ?? data.length })
  },

  async getDetail(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('account'))

    const response = await httpClient.get(`/api/platform/accounts/${id}`)
    return response?.data ? { ...response, data: normalizeAccount(response?.data) } : { ...response, data: null }
  },

  async create(data) {
    return httpClient.post('/api/platform/accounts', data)
  },

  async update(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('account'))
    return httpClient.put(`/api/platform/accounts/${id}`, data)
  },

  async delete(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('account'))
    return httpClient.delete(`/api/platform/accounts/${id}`)
  },

  async borrow(id, payload) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('account'))
    return httpClient.post(`/api/platform/accounts/${id}/borrow`, payload)
  },

  async return(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('account'))
    return httpClient.post(`/api/platform/accounts/${id}/return`)
  },

  async getPassword(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('account'))
    return httpClient.get(`/api/platform/accounts/${id}/password`)
  }
}
