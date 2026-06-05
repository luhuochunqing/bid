// Input: expense API responses and expense/payment action payloads
// Output: expensesApi - normalized expense and payment accessors
// Pos: src/api/modules/resources/ - Expense API submodule
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { httpClient, formatDate, formatDateTime, invalidIdMessage, isNumericId, pageContent } from '@/api/modules/resources/shared'

function normalizeExpenseCategory(category) {
  const value = String(category || '').toUpperCase()
  const map = {
    MATERIAL: '其他',
    LABOR: '其他',
    EQUIPMENT: '其他',
    TRANSPORTATION: '差旅费',
    SUBCONTRACTING: '其他',
    OVERHEAD: '其他',
    OTHER: '其他'
  }
  return map[value] || category || '其他'
}

function normalizeExpense(item = {}) {
  const backendStatus = String(item.status || '').toUpperCase()
  let status = item.status || 'paid'
  let approvalStatus = item.approvalStatus || 'approved'

  if (backendStatus === 'PENDING_APPROVAL') {
    status = 'pending'
    approvalStatus = 'pending'
  } else if (backendStatus === 'APPROVED') {
    status = 'pending'
    approvalStatus = 'approved'
  } else if (backendStatus === 'REJECTED') {
    status = 'pending'
    approvalStatus = 'rejected'
  } else if (backendStatus === 'PAID') {
    status = 'paid'
    approvalStatus = 'approved'
  } else if (backendStatus === 'RETURN_REQUESTED') {
    status = 'paid'
    approvalStatus = 'approved'
  } else if (backendStatus === 'RETURNED') {
    status = 'returned'
    approvalStatus = 'approved'
  }

  return {
    id: item.id,
    project: item.project || item.projectName || (item.projectId ? `项目#${item.projectId}` : '未关联项目'),
    projectId: item.projectId,
    type: item.type || item.expenseType || normalizeExpenseCategory(item.category),
    amount: Number(item.amount || 0),
    status,
    approvalStatus,
    backendStatus,
    date: item.date || formatDate(item.createdAt),
    returnDate: formatDate(item.returnDate || item.returnConfirmedAt),
    expectedReturnDate: formatDate(item.expectedReturnDate || item.returnDate),
    lastRemindedAt: formatDateTime(item.lastReturnReminderAt || item.lastRemindedAt),
    overdue: typeof item.overdue === 'boolean'
      ? item.overdue
      : Boolean(item.expectedReturnDate) && backendStatus !== 'RETURNED' && new Date(item.expectedReturnDate) < new Date(),
    returnRequestedAt: formatDateTime(item.returnRequestedAt),
    returnConfirmedAt: formatDateTime(item.returnConfirmedAt),
    paidAt: formatDateTime(item.paidAt),
    paidBy: item.paidBy || '',
    paymentReference: item.paymentReference || '',
    paymentMethod: item.paymentMethod || '',
    description: item.description || '',
    createdBy: item.createdBy || '',
    approvedBy: item.approvedBy || '',
    approvedAt: item.approvedAt || '',
    approvalComment: item.approvalComment || '',
    returnComment: item.returnComment || '',
    raw: item
  }
}

function normalizeExpenseMutationResponse(response) {
  return { ...response, data: response?.data ? normalizeExpense(response.data) : response?.data }
}

function normalizeApprovalRecord(item = {}) {
  return {
    id: item.id,
    expenseId: item.expenseId,
    projectId: item.projectId || item.raw?.projectId || null,
    project: item.project || (item.projectId ? `项目#${item.projectId}` : `费用#${item.expenseId || '-'}`),
    type: item.type || '',
    amount: Number(item.amount || 0),
    applicant: item.applicant || '',
    applyTime: formatDateTime(item.applyTime || item.createdAt),
    approver: item.approver || '',
    approvalStatus: String(item.result || item.approvalStatus || '').toLowerCase() || 'pending',
    remark: item.comment || item.remark || '',
    raw: item
  }
}

function normalizePaymentRecord(item = {}) {
  return {
    id: item.id,
    expenseId: item.expenseId,
    amount: Number(item.amount || 0),
    paidAt: formatDateTime(item.paidAt || item.paymentDate || item.createdAt),
    paidBy: item.paidBy || '',
    paymentReference: item.paymentReference || item.reference || '',
    paymentMethod: item.paymentMethod || item.method || '',
    remark: item.remark || item.comment || '',
    createdAt: formatDateTime(item.createdAt),
    raw: item
  }
}

function filterExpenses(expenses, params = {}) {
  return expenses.filter((item) => {
    if (params.project) {
      const keyword = String(params.project).trim().toLowerCase()
      if (!String(item.project || '').toLowerCase().includes(keyword)) return false
    }
    if (params.type && item.type !== params.type) return false
    if (params.status && item.status !== params.status) return false
    return true
  })
}

export const expensesApi = {
  async getList(params = {}) {
    const response = await httpClient.get('/api/resources/expenses')
    const { page, content } = pageContent(response)
    const expenses = content.map(normalizeExpense)
    const data = filterExpenses(expenses, params)
    return { ...response, data, total: page?.totalElements ?? data.length }
  },

  async getByProject(projectId, params = {}) {
    if (!isNumericId(projectId)) return Promise.resolve(invalidIdMessage('project'))
    const response = await httpClient.get(`/api/resources/expenses/project/${projectId}`, { params })
    const { page, content } = pageContent(response)
    const expenses = content.map(normalizeExpense)
    return { ...response, data: expenses, total: page?.totalElements ?? expenses.length }
  },

  async create(data) {
    const response = await httpClient.post('/api/resources/expenses', data)
    return normalizeExpenseMutationResponse(response)
  },

  async getDetail(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('expense'))
    const response = await httpClient.get(`/api/resources/expenses/${id}`)
    return response?.data ? { ...response, data: normalizeExpense(response?.data) } : { ...response, data: null }
  },

  async getApprovalRecords(projectId) {
    const response = await httpClient.get('/api/resources/expenses/approval-records', {
      params: projectId ? { projectId } : undefined
    })
    return { ...response, data: Array.isArray(response?.data) ? response.data.map(normalizeApprovalRecord) : [] }
  },

  async approve(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('expense'))
    const response = await httpClient.post(`/api/resources/expenses/${id}/approve`, data)
    return normalizeExpenseMutationResponse(response)
  },

  async getPayments(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('expense'))
    const response = await httpClient.get(`/api/resources/expenses/${id}/payments`)
    const { page, content } = pageContent(response)
    return { ...response, data: content.map(normalizePaymentRecord), total: page?.totalElements ?? content.length }
  },

  async createPayment(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('expense'))
    const response = await httpClient.post(`/api/resources/expenses/${id}/payments`, data)
    return normalizeExpenseMutationResponse(response)
  },

  async requestReturn(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('expense'))
    const response = await httpClient.post(`/api/resources/expenses/${id}/return-request`, data)
    return normalizeExpenseMutationResponse(response)
  },

  async confirmReturn(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('expense'))
    const response = await httpClient.post(`/api/resources/expenses/${id}/confirm-return`, data)
    return normalizeExpenseMutationResponse(response)
  },

  async remindReturn(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('expense'))
    const response = await httpClient.post(`/api/resources/expenses/${id}/return-reminder`, data)
    return normalizeExpenseMutationResponse(response)
  },

  async sendReturnReminder(id, data) {
    return this.remindReturn(id, data)
  }
}
