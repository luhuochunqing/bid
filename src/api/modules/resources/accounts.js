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
    BIDDING_PLATFORM: '投标平台',
    CONSTRUCTION_PLATFORM: '采购平台',
    GOV_PROCUREMENT: '政府平台',
    OTHER: '其他平台'
  }
  return fallback || map[type] || type || '未知平台'
}

function normalizeAccount(item = {}) {
  return {
    id: item.id,
    platform: item.platform || normalizePlatformLabel(item.platformType, item.accountName),
    accountName: item.accountName || '',
    username: item.username || '',
    password: item.password || '',
    url: item.url || '',
    contactPerson: item.contactPerson || '',
    contactPhone: item.contactPhone || '',
    contactEmail: item.contactEmail || '',
    hasCa: item.hasCa || false,
    custodian: item.custodian || '',
    caCustodian: item.caCustodian || null,
    caCustodianName: item.caCustodianName || '',
    platformType: item.platformType || '',
    remarks: item.remarks || '',
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

  async returnWithPassword(id, payload) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('account'))
    return httpClient.post(`/api/platform/accounts/${id}/return-with-password`, payload)
  },

  // ── 借用申请审批流程 ──────────────────────────────────────────────────────────

  async submitBorrowApplication(accountId, payload) {
    if (!isNumericId(accountId)) return Promise.resolve(invalidIdMessage('account'))
    return httpClient.post(`/api/platform/accounts/${accountId}/borrow-applications`, payload)
  },

  async getMyBorrowApplications() {
    return httpClient.get('/api/borrow-applications/my-applications')
  },

  async getMyBorrowApprovals() {
    return httpClient.get('/api/borrow-applications/my-approvals')
  },

  async approveBorrowApplication(id, payload = {}) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('application'))
    return httpClient.post(`/api/borrow-applications/${id}/approve`, payload)
  },

  async rejectBorrowApplication(id, payload) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('application'))
    return httpClient.post(`/api/borrow-applications/${id}/reject`, payload)
  },

  async cancelBorrowApplication(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('application'))
    return httpClient.post(`/api/borrow-applications/${id}/cancel`)
  },

  async returnBorrowApplication(id, payload) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('application'))
    return httpClient.post(`/api/borrow-applications/${id}/return`, payload)
  },

  async getPassword(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('account'))
    return httpClient.get(`/api/platform/accounts/${id}/password`)
  },

  // ── 批量导入 ────────────────────────────────────────────────────────────────

  async importFile(file) {
    const fd = new FormData()
    fd.append('file', file)
    return httpClient.post('/api/platform/accounts/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  async getImportTask(taskId) {
    return httpClient.get(`/api/platform/accounts/import/tasks/${taskId}`)
  },

  async listImportTasks() {
    return httpClient.get('/api/platform/accounts/import/tasks')
  }
}
