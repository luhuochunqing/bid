// Input: backend response objects, frontend form data
// Output: pure normalizer and display formatter functions for bidding data transformations and detail display
// Pos: src/views/Bidding/ - Bidding module utilities
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import {
  getTenderStatusText,
  toBackendTenderStatus,
} from './bidding-utils-status.js'

/**
 * Backend status enum → Chinese display text
 */
export function normalizeTenderStatus(backendStatus) {
  return getTenderStatusText(backendStatus) || backendStatus || '未知'
}

/**
 * Frontend form data → TenderRequest for POST /api/tenders
 */
export function normalizeTenderForCreate(formData) {
  return {
    title: formData.title || '',
    source: formData.source || '人工录入',
    budget: formData.budget != null ? Number(formData.budget) : 0,
    deadline: formData.deadline || null,
    status: 'PENDING_ASSIGNMENT',
    aiScore: formData.aiScore != null ? Number(formData.aiScore) : 0,
    riskLevel: formData.riskLevel || null,
    originalUrl: formData.originalUrl || '',
    externalId: formData.externalId || ''
  }
}

/**
 * Build partial update payload for PUT /api/tenders/{id}
 * Only includes fields that are present in changes
 */
export function buildTenderUpdatePayload(changes) {
  const payload = {}
  if (changes.status !== undefined) payload.status = changes.status
  if (changes.title !== undefined) payload.title = changes.title
  if (changes.budget !== undefined) payload.budget = Number(changes.budget)
  if (changes.deadline !== undefined) payload.deadline = changes.deadline
  if (changes.aiScore !== undefined) payload.aiScore = Number(changes.aiScore)
  if (changes.riskLevel !== undefined) payload.riskLevel = changes.riskLevel
  if (changes.originalUrl !== undefined) payload.originalUrl = changes.originalUrl
  return payload
}

export function safeTenderUrl(value) {
  if (!value) return ''
  try {
    const url = new URL(String(value))
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : ''
  } catch {
    return ''
  }
}

export function formatBudgetWan(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '0'
  const wan = number / 10000
  const maximumFractionDigits = Math.abs(wan) >= 100 ? 0 : 2
  return wan.toLocaleString('zh-CN', { maximumFractionDigits })
}

function isValidDateParts(year, month, day) {
  const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)))
  return date.getUTCFullYear() === Number(year)
    && date.getUTCMonth() === Number(month) - 1
    && date.getUTCDate() === Number(day)
}

function parseTenderDateParts(value) {
  if (value == null || value === '') return null
  const rawValue = String(value).trim()
  const match = rawValue.match(/^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?)?$/)
  if (!match) return null

  const [, year, month, day, hour, minute] = match
  if (!isValidDateParts(year, month, day)) return null
  if (hour != null && (Number(hour) > 23 || Number(minute) > 59)) return null

  return { date: `${year}-${month}-${day}`, time: hour == null ? '' : `${hour}:${minute}` }
}

export function formatTenderDate(value) {
  const parts = parseTenderDateParts(value)
  return parts?.date || '--'
}

export function formatTenderDateTime(value) {
  const parts = parseTenderDateParts(value)
  if (!parts) return '--'
  return parts.time ? `${parts.date} ${parts.time}` : parts.date
}

export function getTenderDateTimeParts(value) {
  const parts = parseTenderDateParts(value)
  if (!parts) {
    return { date: '--', time: '', text: '--', hasTime: false }
  }
  return {
    date: parts.date,
    time: parts.time,
    text: parts.time ? `${parts.date} ${parts.time}` : parts.date,
    hasTime: Boolean(parts.time)
  }
}

export function formatTenderDisplayField(value, missingText = '未提取') {
  const text = value == null ? '' : String(value).trim()
  if (text) {
    return { text, isMissing: false, tooltip: '' }
  }
  return {
    text: missingText,
    isMissing: true,
    tooltip: '真实 API 暂无该字段，未做推断填充'
  }
}

const TENDER_INDUSTRY_LABELS = {
  ENERGY: '能源',
  TRANSPORTATION: '交通',
  MANUFACTURING: '制造业',
  INFRASTRUCTURE: '基础设施',
  REAL_ESTATE: '房地产',
  ENVIRONMENTAL: '环保',
  GOVERNMENT: '政府',
  EDUCATION: '教育',
  MEDICAL: '医疗',
  INTERNET: '互联网',
  FINANCE: '金融',
  OTHER: '其他',
}

export function formatTenderIndustry(value, missingText = '未提取') {
  const raw = value == null ? '' : String(value).trim()
  if (!raw) {
    return {
      text: missingText,
      isMissing: true,
      tooltip: '真实 API 暂无该字段，未做推断填充'
    }
  }
  const label = TENDER_INDUSTRY_LABELS[raw.toUpperCase()] || raw
  return { text: label, isMissing: false, tooltip: '' }
}

export function buildWinProbabilityView(scoreValue) {
  const score = Number(scoreValue)
  const sourceScore = Number.isFinite(score) ? Math.max(0, Math.min(100, score)) : 0
  if (sourceScore <= 0) {
    return {
      rate: 0,
      percent: 0,
      label: '暂无',
      sourceScore,
      hasScore: false,
      tooltip: '暂无真实匹配评分，无法换算赢面参考'
    }
  }

  let rate = 1
  if (sourceScore >= 90) rate = 5
  else if (sourceScore >= 80) rate = 4
  else if (sourceScore >= 70) rate = 3
  else if (sourceScore >= 60) rate = 2

  const percent = rate * 20
  return {
    rate,
    percent,
    label: `${percent}%`,
    sourceScore,
    hasScore: true,
    tooltip: '由真实投标匹配评分按星级分档换算，仅作赢面参考，不是后端直接返回的中标概率'
  }
}

/**
 * BatchOperationResponse → user-facing summary message
 */
export function normalizeBatchResult(response) {
  if (!response) return { ok: false, message: '操作失败：无响应' }

  const data = response.data || response
  const success = data.success !== false
  const succeeded = data.successCount || 0
  const failed = data.failureCount || 0

  if (success && failed === 0) {
    return { ok: true, message: `操作成功，共处理 ${succeeded} 条` }
  }
  if (succeeded > 0 && failed > 0) {
    const errors = (data.errors || []).map(e => e.errorMessage).join('; ')
    return { ok: false, message: `部分成功：${succeeded} 条成功，${failed} 条失败。${errors}` }
  }
  const errors = (data.errors || []).map(e => e.errorMessage).join('; ')
  return { ok: false, message: `操作失败：${errors || '未知错误'}` }
}

/**
 * AI analysis dimensionScores from API → chart-friendly format
 * Input: [{name: "技术匹配", score: 85}, ...]
 * Output: [{name: "技术匹配", score: 85, percentage: "85%", level: "high"}, ...]
 */
export function normalizeAiDimensions(dimensionScores) {
  if (!Array.isArray(dimensionScores)) return []
  return dimensionScores.map(d => ({
    name: d.name || '未知维度',
    score: d.score != null ? Number(d.score) : 0,
    percentage: `${d.score != null ? d.score : 0}%`,
    level: d.score >= 80 ? 'high' : d.score >= 60 ? 'medium' : 'low',
    description: d.description || '',
    suggestion: d.suggestion || ''
  }))
}

/**
 * AI analysis risks from API → frontend display format
 * Input: [{level: "HIGH", desc: "...", action: "..."}, ...]
 */
export function normalizeAiRisks(risks) {
  if (!Array.isArray(risks)) return []
  const levelMap = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
  return risks.map(r => ({
    level: r.level || 'LOW',
    type: levelMap[r.level] || 'info',
    description: r.desc || '',
    action: r.action || ''
  }))
}

/**
 * Frontend status (lowercase) → Backend enum (uppercase)
 * Unknown values (not in status constants or aliases) pass through unchanged.
 */
export function toBackendStatus(frontendStatus) {
  if (frontendStatus == null) {
    return frontendStatus
  }

  const rawValue = String(frontendStatus).trim()
  const upperValue = rawValue.toUpperCase()

  // If already a valid backend enum, return as-is
  if (['PENDING_ASSIGNMENT', 'TRACKING', 'EVALUATED', 'BIDDING', 'WON', 'LOST', 'ABANDONED'].includes(upperValue)) {
    return upperValue
  }

  // Try to normalize via aliases (pending, tracking, 已投标, etc.)
  const normalized = toBackendTenderStatus(frontendStatus)
  if (normalized !== 'PENDING_ASSIGNMENT') {
    return normalized
  }

  // If normalized to PENDING_ASSIGNMENT, check if input was a known pending alias
  const lowerValue = rawValue.toLowerCase()
  const knownPendingAliases = ['new', 'pending', 'pending_assignment', '待处理', '待分配']
  if (knownPendingAliases.includes(lowerValue)) {
    return 'PENDING_ASSIGNMENT'
  }

  // Unknown value - pass through unchanged
  return rawValue
}

/**
 * 根据标讯来源类型获取显示文本
 * @param {string} sourceType - EXTERNAL_PLATFORM / CRM_OPPORTUNITY / MANUAL_SINGLE / BULK_IMPORT
 * @returns {string} 显示文本
 */
export function getSourceTypeText(sourceType) {
  const map = {
    EXTERNAL_PLATFORM: '第三方平台',
    CRM_OPPORTUNITY: 'CRM 商机',
    MANUAL_SINGLE: '人工录入',
    BULK_IMPORT: '批量导入',
    MANUAL: '人工录入',
    EXTERNAL: '外部获取',
  }
  return map[sourceType] || sourceType || '未知'
}
