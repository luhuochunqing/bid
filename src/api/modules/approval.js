// Input: httpClient for approval-related requests
// Output: approvalApi - approval workflow accessors for frontend consumers
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

function formatRelativeTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)

  const diffMs = Date.now() - date.getTime()
  const minutes = Math.floor(diffMs / (1000 * 60))
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`

  const days = Math.floor(hours / 24)
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`

  return date.toLocaleDateString('zh-CN')
}

function statusLabel(status) {
  const map = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消' }
  return map[String(status || '').toUpperCase()] || (status || '未知状态')
}

function normalizeApproval(item = {}) {
  return {
    id: item.id,
    projectId: item.projectId,
    projectName: item.projectName || `项目#${item.projectId ?? '--'}`,
    title: item.title || `${item.projectName || '项目'} - ${item.approvalType || '审批'}`,
    approvalType: item.approvalType || 'project_review',
    typeName: item.approvalType || '项目审批',
    status: String(item.status || 'PENDING').toUpperCase(),
    statusDescription: item.statusDescription || statusLabel(item.status),
    requesterName: item.requesterName || item.applicantName || '未知申请人',
    applicantName: item.requesterName || item.applicantName || '未知申请人',
    applicantDept: item.applicantDept || item.departmentName || '未配置部门',
    currentApproverName: item.currentApproverName || '待分配',
    priority: Number(item.priority || 0),
    description: item.description || '暂无说明',
    submitTime: formatDateTime(item.submittedAt || item.submitTime || item.createdAt),
    submittedAt: item.submittedAt || item.submitTime || item.createdAt,
    dueDate: item.dueDate || '',
    isOverdue: Boolean(item.isOverdue),
    isNearDueDate: Boolean(item.isNearDueDate),
    processingHours: item.processingHours ?? null,
    approvalNodes: Array.isArray(item.actions)
      ? item.actions.map((action, index) => ({
          nodeName: action.actionType || `审批节点${index + 1}`,
          approverName: action.operatorName || action.operator || '系统',
          status: String(action.actionType || '').toLowerCase().includes('reject') ? 'rejected' : 'approved',
          comment: action.comment || '',
          actionTime: formatDateTime(action.createdAt || action.actionTime) }))
      : [],
    time: formatRelativeTime(item.submittedAt || item.submitTime || item.createdAt),
    raw: item }
}

async function getPendingApprovals(params = {}) {

  const response = await httpClient.get('/api/approvals/pending', { params })
  const rows = Array.isArray(response?.data?.content)
    ? response.data.content
    : Array.isArray(response?.data)
      ? response.data
      : []

  return {
    ...response,
    data: rows.map(normalizeApproval),
    totalCount: response?.data?.totalElements ?? rows.length,
    page: response?.data?.pageable ?? null }
}

async function getProjectApprovals(projectId, params = {}) {

  const response = await httpClient.get('/api/approvals/my', {
    params: { page: 0, size: 100, ...params } })

  const rows = Array.isArray(response?.data?.content)
    ? response.data.content
    : Array.isArray(response?.data)
      ? response.data
      : []

  return {
    ...response,
    data: rows
      .filter((item) => String(item.projectId) === String(projectId))
      .map(normalizeApproval) }
}

async function getMyApprovals(params = {}) {

  const response = await httpClient.get('/api/approvals/my', {
    params: { page: 0, size: 20, ...params } })
  const rows = Array.isArray(response?.data?.content)
    ? response.data.content
    : Array.isArray(response?.data)
      ? response.data
      : []

  return {
    ...response,
    data: rows.map(normalizeApproval),
    page: response?.data?.pageable ?? null }
}

async function submitApproval(payload) {

  const response = await httpClient.post('/api/approvals/submit', payload)
  return response
}

async function approve(id, payload) {
  return httpClient.post(`/api/approvals/${id}/approve`, payload)
}

async function reject(id, payload) {
  return httpClient.post(`/api/approvals/${id}/reject`, payload)
}

export const approvalApi = {
  getPendingApprovals,
  getProjectApprovals,
  getMyApprovals,
  submitApproval,
  approve,
  reject }

export default approvalApi
