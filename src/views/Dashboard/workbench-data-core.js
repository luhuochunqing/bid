// Input: Workbench API DTOs and support request forms
// Output: pure Workbench normalizers, mergers, validators, and payload builders
// Pos: src/views/Dashboard/ - Dashboard pure core helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { formatTodoDeadline } from '@/views/Dashboard/workbench-formatters.js'

const WINDOWS_1252_BYTES = new Map([
  ['€', 0x80], ['‚', 0x82], ['ƒ', 0x83], ['„', 0x84], ['…', 0x85],
  ['†', 0x86], ['‡', 0x87], ['ˆ', 0x88], ['‰', 0x89], ['Š', 0x8A],
  ['‹', 0x8B], ['Œ', 0x8C], ['Ž', 0x8E], ['‘', 0x91], ['’', 0x92],
  ['“', 0x93], ['”', 0x94], ['•', 0x95], ['–', 0x96], ['—', 0x97],
  ['˜', 0x98], ['™', 0x99], ['š', 0x9A], ['›', 0x9B], ['œ', 0x9C],
  ['ž', 0x9E], ['Ÿ', 0x9F],
])

const MOJIBAKE_SIGNAL = /[ÃÂÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ€œŒŠšŸ¿¡¢£¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾]/
const CJK_TEXT = /[\u3400-\u9fff]/
const APPROVAL_TYPE_LABELS = {
  bid_support: '标书支持申请',
  technical_support: '技术支持申请',
  project_review: '立项审批',
  expense: '费用审批',
  budget: '预算审批',
  bid_review: '标书评审',
}

function windows1252Bytes(value) {
  const bytes = []
  for (const char of value) {
    const byte = WINDOWS_1252_BYTES.get(char) ?? char.charCodeAt(0)
    if (byte > 0xFF) return null
    bytes.push(byte)
  }
  return bytes
}

export function cleanDisplayText(value, fallback = '') {
  if (value == null) return fallback
  const text = String(value)
  if (!MOJIBAKE_SIGNAL.test(text)) return text

  const bytes = windows1252Bytes(text)
  if (!bytes) return fallback || text.replace(MOJIBAKE_SIGNAL, '').trim()

  try {
    const decoded = new TextDecoder('utf-8', { fatal: true }).decode(Uint8Array.from(bytes))
    return CJK_TEXT.test(decoded) ? decoded : text
  } catch {
    return fallback || text.replace(MOJIBAKE_SIGNAL, '').trim()
  }
}

function normalizeApprovalTypeName(value, approvalType) {
  const normalized = String(value || approvalType || '').trim()
  return APPROVAL_TYPE_LABELS[normalized] || cleanDisplayText(normalized)
}

export function normalizeApiTodo(task) {
  const priority = String(task?.priority || 'MEDIUM').toLowerCase()
  const taskType = task?.type || 'task'
  const isBidReview = taskType === 'bid_review'
  return {
    id: task?.id,
    title: cleanDisplayText(task?.title || ''),
    priority,
    deadline: formatTodoDeadline(task?.dueDate),
    done: task?.status === 'COMPLETED',
    type: isBidReview ? 'bid_review' : 'task',
    sourceType: isBidReview ? 'bid_review' : 'task',
    ...(isBidReview ? { badge: '标书评审' } : {}),
    rawStatus: task?.status,
  }
}

export function mergePriorityTodos(alertTodos = [], apiTodos = [], limit = 8) {
  return [...(alertTodos || []), ...(apiTodos || [])].slice(0, limit)
}

export function normalizePendingApproval(item) {
  const projectName = cleanDisplayText(item?.projectName || '')
  const typeName = normalizeApprovalTypeName(item?.typeName, item?.approvalType)
  const fallbackTitle = `${projectName} - ${typeName}`.trim()
  return {
    ...item,
    projectName,
    typeName,
    title: cleanDisplayText(item?.title || fallbackTitle, fallbackTitle),
    type: item?.approvalType || 'project_review',
    department: item?.applicantDept || '投标管理部',
    time: item?.time || item?.submitTime || '',
  }
}

export function approvalStatusToProcessStatus(status) {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'APPROVED') return 'in-progress'
  if (normalized === 'REJECTED' || normalized === 'CANCELLED') return 'urgent'
  return 'pending'
}

export function normalizeProcess(item) {
  const normalizedStatus = String(item?.status || '').toUpperCase()
  const projectName = cleanDisplayText(item?.projectName || '')
  const typeName = normalizeApprovalTypeName(item?.typeName, item?.approvalType)
  const fallbackTitle = `${projectName} - ${typeName}`.trim()
  return {
    id: item?.id,
    title: cleanDisplayText(item?.title || fallbackTitle, fallbackTitle || '当前流程'),
    status: approvalStatusToProcessStatus(item?.status),
    description: cleanDisplayText(item?.description || '暂无说明', '暂无说明'),
    progress: normalizedStatus === 'APPROVED' ? 100 : normalizedStatus === 'PENDING' ? 55 : 0,
    time: item?.submittedAt || item?.submitTime || item?.time || '',
  }
}

export function normalizeSupportProject(item) {
  return { id: Number(item?.id), name: cleanDisplayText(item?.name || item?.projectName || `项目#${item?.id}`) }
}

export function normalizeSupportProjects(items) {
  return (Array.isArray(items) ? items : [])
    .map(normalizeSupportProject)
    .filter((item) => Number.isFinite(item.id))
}

export function createDefaultSupportRequestForm(projects = []) {
  return { projectId: projects[0]?.id || null, type: 'bid_support', dueDate: '', description: '' }
}

export function validateSupportRequest(form) {
  if (!form?.projectId) return { valid: false, message: '请选择关联项目' }
  if (!String(form?.description || '').trim()) return { valid: false, message: '请填写需求说明' }
  return { valid: true, message: '' }
}

export function buildSupportRequestPayload(form, projects = []) {
  const projectId = Number(form?.projectId)
  const selectedProject = projects.find((item) => item.id === projectId)
  const projectName = selectedProject?.name || `项目#${form?.projectId}`
  return {
    projectId,
    projectName,
    approvalType: form?.type || 'bid_support',
    title: `${selectedProject?.name || '当前项目'} - 标书支持申请`,
    description: String(form?.description || '').trim(),
    dueDate: form?.dueDate || null,
    priority: 1,
  }
}
