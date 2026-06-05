// Input: httpClient and expense-ledger backend endpoints
// Output: expensesApi - expense ledger, application, approval, and return accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

function isNumericId(id) {
  return /^\d+$/.test(String(id))
}

function invalidIdMessage(entityName) {
  return {
    success: false,
    message: `Current backend only supports numeric ${entityName} IDs in API mode`
  }
}

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toISOString().split('T')[0]
}

function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

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
  let statusLabel = backendStatus || '-'

  if (backendStatus === 'PENDING_APPROVAL') {
    status = 'pending'
    approvalStatus = 'pending'
    statusLabel = '待审批'
  } else if (backendStatus === 'APPROVED') {
    status = 'pending'
    approvalStatus = 'approved'
    statusLabel = '待支付'
  } else if (backendStatus === 'REJECTED') {
    status = 'pending'
    approvalStatus = 'rejected'
    statusLabel = '已驳回'
  } else if (backendStatus === 'PAID') {
    status = 'paid'
    approvalStatus = 'approved'
    statusLabel = '已支付'
  } else if (backendStatus === 'RETURN_REQUESTED') {
    status = 'paid'
    approvalStatus = 'approved'
    statusLabel = '退还中'
  } else if (backendStatus === 'RETURNED') {
    status = 'returned'
    approvalStatus = 'approved'
    statusLabel = '已退还'
  }

  const projectName = item.projectName || item.project || ''
  const departmentName = item.departmentName || item.department || item.departmentCode || ''

  return {
    id: item.id,
    project: projectName || (item.projectId ? `项目#${item.projectId}` : '未关联项目'),
    projectName,
    projectId: item.projectId,
    department: departmentName,
    departmentName,
    departmentCode: item.departmentCode || '',
    type: item.type || item.expenseType || normalizeExpenseCategory(item.category),
    amount: Number(item.amount || 0),
    status,
    statusLabel,
    approvalStatus,
    backendStatus,
    date: item.date || formatDate(item.createdAt),
    returnDate: item.returnDate || formatDate(item.returnConfirmedAt),
    returnRequestedAt: formatDateTime(item.returnRequestedAt),
    returnConfirmedAt: formatDateTime(item.returnConfirmedAt),
    description: item.description || '',
    createdBy: item.createdBy || '',
    approvedBy: item.approvedBy || '',
    approvedAt: formatDateTime(item.approvedAt),
    approvalComment: item.approvalComment || '',
    returnComment: item.returnComment || '',
    createdAt: formatDateTime(item.createdAt),
    updatedAt: formatDateTime(item.updatedAt),
    raw: item
  }
}

function normalizeApprovalRecord(item = {}) {
  return {
    id: item.id,
    expenseId: item.expenseId,
    projectId: item.projectId,
    project: item.project || (item.projectId ? `项目#${item.projectId}` : `费用#${item.expenseId || '-'}`),
    type: item.type || item.expenseType || '其他',
    amount: Number(item.amount || 0),
    applicant: item.applicant || item.createdBy || '',
    applyTime: formatDateTime(item.applyTime || item.createdAt),
    approver: item.approver || item.approvedBy || '',
    approvalTime: formatDateTime(item.approvalTime || item.approvedAt),
    approvalStatus: String(item.result || item.approvalStatus || '').toLowerCase() || 'pending',
    remark: item.remark || item.comment || item.description || '',
    raw: item
  }
}

function normalizeLedgerGroup(group = {}) {
  return {
    key: group.key || '',
    label: group.label || group.key || '未命名',
    count: Number(group.count || 0),
    totalAmount: Number(group.totalAmount || 0)
  }
}

function createEmptySummary() {
  return {
    recordCount: 0,
    totalAmount: 0,
    pendingApprovalAmount: 0,
    approvedAmount: 0,
    returnedAmount: 0,
    depositCount: 0,
    pendingReturnCount: 0,
    byDepartment: [],
    byProject: []
  }
}

function normalizeLedgerResponse(data = {}) {
  const summary = data.summary || createEmptySummary()
  return {
    items: Array.isArray(data.items) ? data.items.map(normalizeExpense) : [],
    summary: {
      recordCount: Number(summary.recordCount || 0),
      totalAmount: Number(summary.totalAmount || 0),
      pendingApprovalAmount: Number(summary.pendingApprovalAmount || 0),
      approvedAmount: Number(summary.approvedAmount || 0),
      returnedAmount: Number(summary.returnedAmount || 0),
      depositCount: Number(summary.depositCount || 0),
      pendingReturnCount: Number(summary.pendingReturnCount || 0),
      byDepartment: Array.isArray(summary.byDepartment) ? summary.byDepartment.map(normalizeLedgerGroup) : [],
      byProject: Array.isArray(summary.byProject) ? summary.byProject.map(normalizeLedgerGroup) : []
    }
  }
}

function normalizeExpenseMutationResponse(response) {
  return {
    ...response,
    data: response?.data ? normalizeExpense(response.data) : response?.data
  }
}

function normalizeExpenseStatusFilter(status) {
  const value = String(status || '').trim().toUpperCase()
  const map = {
    PENDING_APPROVAL: 'PENDING_APPROVAL',
    APPROVED: 'APPROVED',
    REJECTED: 'REJECTED',
    PAID: 'PAID',
    RETURN_REQUESTED: 'RETURN_REQUESTED',
    RETURNED: 'RETURNED',
    PENDING: 'PENDING_APPROVAL',
    PENDING_APPROVE: 'PENDING_APPROVAL',
    RETURNREQUESTED: 'RETURN_REQUESTED'
  }
  return map[value] || ''
}

function buildLedgerParams(filters = {}) {
  const params = {}

  if (filters.projectId) {
    params.projectId = Number(filters.projectId)
  }

  if (filters.projectKeyword) {
    params.projectKeyword = filters.projectKeyword
  }

  const dateRange = Array.isArray(filters.dateRange) ? filters.dateRange : []
  if (dateRange[0]) params.startDate = dateRange[0]
  if (dateRange[1]) params.endDate = dateRange[1]

  if (filters.department) {
    params.department = filters.department
  }

  if (filters.type) {
    params.expenseType = filters.type
  }

  const normalizedStatus = normalizeExpenseStatusFilter(filters.status)
  if (normalizedStatus) {
    params.status = normalizedStatus
  }

  return params
}

export const expensesApi = {
  async getList(params = {}) {
    const response = await httpClient.get('/api/resources/expenses')
    const page = response?.data
    const content = Array.isArray(page?.content) ? page.content : Array.isArray(response?.data) ? response.data : []
    const data = content.map(normalizeExpense)
    return { ...response, data, total: page?.totalElements ?? data.length, query: params }
  },

  async getLedger(filters = {}) {
    const response = await httpClient.get('/api/resources/expenses/ledger', {
      params: buildLedgerParams(filters)
    })
    return {
      ...response,
      data: normalizeLedgerResponse(response?.data)
    }
  },

  async create(data) {
    const response = await httpClient.post('/api/resources/expenses', data)
    return normalizeExpenseMutationResponse(response)
  },

  async getDetail(id) {
    if (!isNumericId(id)) {
      return Promise.resolve(invalidIdMessage('expense'))
    }

    const response = await httpClient.get(`/api/resources/expenses/${id}`)
    return response?.data ? { ...response, data: normalizeExpense(response.data) } : { ...response, data: null }
  },

  async getApprovalRecords(projectId) {
    const response = await httpClient.get('/api/resources/expenses/approval-records', {
      params: projectId ? { projectId } : undefined
    })
    const records = Array.isArray(response?.data) ? response.data.map(normalizeApprovalRecord) : []
    return { ...response, data: records }
  },

  async approve(id, data) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('expense'))

    const response = await httpClient.post(`/api/resources/expenses/${id}/approve`, data)
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
  }
}

export default expensesApi
