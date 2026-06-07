// Input: bidding list records, forms, users, and browser storage payloads
// Output: pure helpers for permissions, safe URLs, payloads, and display values
// Pos: src/views/Bidding/list/ - Pure helper layer for the bidding list page
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { hasAnyPermission } from '@/utils/permission'
import { DEFAULT_SOURCE_CONFIG } from './constants.js'
import {
  formatBudgetWan as formatBudgetWanValue,
  safeTenderUrl as safeTenderUrlValue,
} from '../bidding-utils.js'
export {
  normalizeBudgetYuan,
  normalizeManualTenderParseResult,
} from './manualTenderParseHelpers.js'

export function normalizeRole(value) {
  return String(value || '').trim().toLowerCase().replace(/^role_/, '')
}

export function resolveUserRole(userStore) {
  return normalizeRole(
    userStore?.currentUser?.roleCode
      || userStore?.currentUser?.role
      || userStore?.userRole
      || 'staff',
  )
}


/**
 * 判断角色是否为管理员。与 normalizeRole 共享同一条规范化链，
 * 避免比较字符串与规范化结果漂移（如 ADMIN vs admin）。
 */
export function isAdminRole(role) {
  return normalizeRole(role) === 'admin'
}

export function buildPermissionFlags(menuPermissions) {
  const perms = Array.isArray(menuPermissions) ? menuPermissions : []
  return {
    canManageTenders: hasAnyPermission(perms, ['bidding.manage', 'settings', 'all']),
    canCreateTender: hasAnyPermission(perms, ['bidding.create', 'bidding', 'project.create', 'all']),
    canDeleteTenders: hasAnyPermission(perms, ['bidding.delete', 'all']),
    canSyncExternalSource: hasAnyPermission(perms, ['bidding.sync', 'all']),
  }
}

export function sanitizeSourceConfigForStorage(config = {}) {
  const merged = { ...DEFAULT_SOURCE_CONFIG, ...config }
  // Intentionally drop the API key before persisting — it must never land in storage.
  const { apiKey: _apiKey, ...safeConfig } = merged
  return {
    ...safeConfig,
    platforms: Array.isArray(safeConfig.platforms) ? safeConfig.platforms : [],
    keywords: Array.isArray(safeConfig.keywords) ? safeConfig.keywords : [],
    regions: Array.isArray(safeConfig.regions) ? safeConfig.regions : [],
  }
}

export function restoreSourceConfig(rawValue, storageWriter) {
  if (!rawValue) {
    return { ...DEFAULT_SOURCE_CONFIG }
  }

  try {
    const parsed = JSON.parse(rawValue)
    const safeConfig = sanitizeSourceConfigForStorage(parsed)
    if (Object.prototype.hasOwnProperty.call(parsed, 'apiKey') && storageWriter) {
      storageWriter(JSON.stringify(safeConfig))
    }
    return { ...DEFAULT_SOURCE_CONFIG, ...safeConfig, apiKey: '' }
  } catch {
    return { ...DEFAULT_SOURCE_CONFIG }
  }
}

export const safeTenderUrl = safeTenderUrlValue

export const formatBudgetWan = formatBudgetWanValue

export function formatLocalDateTime(value) {
  if (!value) return null
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return null
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function formatManualTenderDeadline(value) {
  if (!value) return null
  // Accept Date objects or strings ("yyyy-MM-dd HH:mm", "yyyy-MM-dd")
  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) return null
    const date = new Date(value.getTime())
    if (date.getHours() === 0 && date.getMinutes() === 0 && date.getSeconds() === 0) {
      date.setHours(23, 59, 59, 0)
    }
    return formatLocalDateTime(date)
  }
  let str = String(value).trim()
  if (!str.includes(' ')) str = str.slice(0, 10) + ' 00:00'
  const normalized = str.replace(' ', 'T') + ':00'
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return null
  if (date.getHours() === 0 && date.getMinutes() === 0 && date.getSeconds() === 0) {
    date.setHours(23, 59, 59, 0)
  }
  return formatLocalDateTime(date)
}

export function formatLocalDate(value = new Date()) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return null
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function buildManualTenderPayload(form = {}) {
  const formattedDeadline = formatManualTenderDeadline(form.deadline)
  return {
    title: form.title,
    region: form.region,
    purchaserName: form.purchaser || null,
    bidOpeningTime: formatManualTenderDeadline(form.bidOpeningTime),
    registrationDeadline: formattedDeadline,
    customerType: form.customerType,
    priority: form.priority,
    projectType: form.projectType || null,
    deadline: formattedDeadline,
    publishDate: formatLocalDate(),
    source: 'manual',
    // 联系人1
    contactName: form.contact || null,
    contactPhone: form.phone || null,
    contactLandline: form.landline || null,
    contactMail: form.mail || null,
    // 联系人2
    contactName2: form.contact2 || null,
    contactPhone2: form.phone2 || null,
    contactLandline2: form.landline2 || null,
    contactMail2: form.mail2 || null,
    description: form.description || null,
    tenderInfo: form.tenderInfo || null,
    sourceDocumentName: form.sourceDocumentName || '',
    sourceDocumentFileType: form.sourceDocumentFileType || '',
    sourceDocumentFileUrl: form.sourceDocumentFileUrl || '',
    status: 'PENDING_ASSIGNMENT',
  }
}

export function getScoreClass(score) {
  if (Number(score) >= 90) return 'score-excellent'
  if (Number(score) >= 80) return 'score-good'
  return 'score-normal'
}

export function getScoreTagType(score) {
  if (Number(score) >= 90) return 'success'
  if (Number(score) >= 80) return 'warning'
  return 'info'
}

export function getSourceTagType(source) {
  const map = {
    external: 'success',
    manual: 'warning',
    中国招标投标公共服务平台: 'success',
  }
  return map[source] || 'info'
}

export function getSourceText(source) {
  const map = {
    external: '外部获取',
    manual: '人工录入',
    中国招标投标公共服务平台: '外部获取',
  }
  return map[source] || source || '未知'
}

/**
 * 根据标讯来源类型获取标签类型
 * @param {string} sourceType - EXTERNAL_PLATFORM / CRM_OPPORTUNITY / MANUAL_SINGLE / BULK_IMPORT
 * @returns {string} Element Plus tag type
 */
export function getSourceTypeTagType(sourceType) {
  const map = {
    EXTERNAL_PLATFORM: 'success',
    CRM_OPPORTUNITY: 'primary',
    MANUAL_SINGLE: 'warning',
    BULK_IMPORT: 'info',
    MANUAL: 'warning',
    EXTERNAL: 'success',
  }
  return map[sourceType] || 'info'
}
export { getSourceTypeText } from '../bidding-utils.js'
export function normalizeAssignmentCandidate(candidate = {}) {
  return {
    id: Number(candidate.id),
    name: candidate.name || `用户#${candidate.id}`,
    departmentName: candidate.departmentName || '未分组',
    roleCode: candidate.roleCode || '',
  }
}

export function buildDistributionPreview({ tenders = [], candidates = [], form = {} } = {}) {
  const usableCandidates = candidates.filter((item) => Number.isFinite(Number(item.id)))
  if (tenders.length === 0 || usableCandidates.length === 0) return []
  const targets = form.type === 'manual' && form.assignees?.length
    ? usableCandidates.filter((item) => form.assignees.includes(item.id))
    : usableCandidates
  if (targets.length === 0) return []

  const groups = new Map(targets.map((candidate) => [candidate.id, { ...candidate, tenders: [] }]))
  tenders.forEach((tender, index) => {
    const target = targets[index % targets.length]
    groups.get(target.id)?.tenders.push(tender)
  })

  return [...groups.values()]
    .filter((item) => item.tenders.length > 0)
    .map((item) => ({ ...item, count: item.tenders.length }))
}

function firstFiniteNumber(...values) {
  for (const value of values) {
    const numericValue = Number(value)
    if (Number.isFinite(numericValue)) {
      return numericValue
    }
  }
  return 0
}

export function summarizeExternalSyncResult(response = {}) {
  const data = response?.data || {}
  return {
    visible: true,
    saved: firstFiniteNumber(data.saved, data.savedCount, response.saved, response.savedCount),
    skipped: firstFiniteNumber(data.skipped, data.skippedCount, response.skipped, response.skippedCount),
    message: data.msg || response?.msg || '标讯同步完成',
  }
}

/**
 * 标讯导出字段映射函数
 * 将标讯数据对象转换为导出的CSV列
 * @param {Object} tender - 标讯数据对象
 * @param {number} index - 序号（从1开始）
 * @returns {Object} 导出行对象，键为中文列名
 */
export function normalizeTenderForExport(tender, index) {
  const formatDate = (val) => {
    if (!val) return '-'
    const d = new Date(val)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }

  return {
    '序号': index,
    '项目名称': tender.title || '-',
    '来源平台': tender.source || '-',
    '总部所在地': tender.region || '-',
    '招标主体': tender.purchaserName || '-',
    '项目类型': tender.projectType || '-',
    '客户类型': tender.customerType || '-',
    '报名截止日期': formatDate(tender.registrationDeadline),
    '开标时间': formatDate(tender.bidOpeningTime),
    '标讯状态': tender.status || '-',
    '项目负责人': tender.projectManagerName || '-',
    '项目部门': tender.department || '-',
    '优先级': tender.priority ? `${tender.priority}级` : '-',
    '创建人': tender.creatorName || '-',
  }
}
