// Input: bidding list records, forms, users, and browser storage payloads
// Output: pure helpers for permissions, safe URLs, payloads, and display values
// Pos: src/views/Bidding/list/ - Pure helper layer for the bidding list page
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { hasAnyPermission } from '@/utils/permission'
import { DEFAULT_SOURCE_CONFIG } from './constants.js'
import { formatDisplayName } from '@/utils/formatDisplayName.js'
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
    source: '人工录入',
    contactName: form.contact || null,
    contactPhone: form.phone || null,
    contactTel: form.landline || null,
    contactMail: form.mail || null,
    contactName2: form.contact2 || null,
    contactPhone2: form.phone2 || null,
    contactTel2: form.landline2 || null,
    contactMail2: form.mail2 || null,
    description: form.description || null,
    tenderInfo: form.tenderInfo || null,
    attachments: (form.attachments || []).map(f => ({
      fileName: f.name || f.fileName || '',
      fileType: f.type || f.fileType || '',
      fileUrl: f.url || f.fileUrl || ''
    })),
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
  // 兼容旧英文值与新的四个中文标准值；中文键必须显式列出（fallback 为 'info'）。
  const map = {
    external: 'success',
    manual: 'warning',
    crm: 'primary',
    bulk: 'warning',
    '第三方平台': 'success',
    'CRM 商机': 'primary',
    '人工录入': 'warning',
    '批量导入': 'warning',
  }
  return map[source] || 'info'
}

/**
 * 根据来源平台获取显示文本。
 * 兼容旧英文值；中文标准值由后端统一写入并透传。
 *
 * @param {string} source - 来源平台
 * @returns {string} 中文展示文本
 */
export function getSourceText(source) {
  const map = {
    external: '第三方平台',
    manual: '人工录入',
    crm: 'CRM 商机',
    bulk: '人工录入',
    // 向后兼容：数据库中 source 字段仍为旧标签"批量导入"的历史数据
    '批量导入': '人工录入',
  }
  return map[source] || source || '未知'
}

/**
 * 根据标讯来源类型获取标签类型。
 * 兼容英文枚举名与中文标签；中文键必须显式列出（fallback 为 'info'）。
 *
 * @param {string} sourceType - 英文枚举名或中文标签
 * @returns {string} Element Plus tag type
 */
export function getSourceTypeTagType(sourceType) {
  const map = {
    EXTERNAL_PLATFORM: 'success',
    CRM_OPPORTUNITY: 'primary',
    MANUAL_SINGLE: 'warning',
    BULK_IMPORT: 'warning',
    MANUAL: 'warning',
    EXTERNAL: 'success',
    '第三方平台': 'success',
    'CRM 商机': 'primary',
    '人工录入': 'warning',
    '批量导入': 'warning',
  }
  return map[sourceType] || 'info'
}
export { getSourceTypeText } from '../bidding-utils.js'

export function normalizeAssignmentCandidate(candidate = {}) {
  return {
    id: Number(candidate.id),
    name: candidate.name || candidate.fullName || `用户#${candidate.id}`,
    fullName: candidate.fullName || candidate.name || '',
    username: candidate.username || '',
    employeeNumber: candidate.employeeNumber || candidate.employeeId || candidate.username || '',
    departmentName: candidate.departmentName || '未分组',
    roleCode: candidate.roleCode || '',
  }
}

export function formatAssignmentCandidateLabel(candidate = {}) {
  return formatDisplayName(candidate.name || `用户#${candidate.id}`, candidate.employeeNumber || candidate.username)
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

export { normalizeTenderForExport } from './tenderExportFields.js'
