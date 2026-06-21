/**
 * Formatters for project list display.
 */
import {
  CRM_SOURCE_LABEL,
  EXTERNAL_PLATFORM_SOURCE_LABEL,
  LEGACY_CRM_SOURCE_LABEL,
  MANUAL_SOURCE_LABEL,
} from '@/utils/sourceLabels.js'

export function formatDate(d) {
  if (!d) return '-'
  const date = new Date(d)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}


export function bidResultTag(result) {
  if (result === 'WON') return 'success'
  if (['LOST', 'FAILED', 'ABANDONED'].includes(result)) return 'danger'
  return 'info'
}

export function priorityTag(priority) {
  if (priority === 'S') return 'danger'
  if (priority === 'A') return 'warning'
  return 'info'
}

export function priorityLabel(priority) {
  const p = String(priority || '').toUpperCase()
  if (p === 'S') return 'S级'
  if (p === 'A') return 'A级'
  if (p === 'B') return 'B级'
  if (p === 'C') return 'C级'
  return priority || '-'
}

export function stageText(stage) {
  const map = {
    INITIATED: '项目立项',
    DRAFTING: '标书制作',
    EVALUATING: '评标中',
    RESULT_PENDING: '结果确认',
    RETROSPECTIVE: '项目复盘',
    CLOSED: '项目结项',
  }
  return map[stage] || stage || '-'
}

export function sourceText(source) {
  const map = {
    // 历史数据兼容：旧版写入的英文枚举名仍按 Tender.SourceType 中文 label 显示
    CRM_OPPORTUNITY: CRM_SOURCE_LABEL,
    EXTERNAL_PLATFORM: EXTERNAL_PLATFORM_SOURCE_LABEL,
    MANUAL_SINGLE: MANUAL_SOURCE_LABEL,
    BULK_IMPORT: MANUAL_SOURCE_LABEL,
    // 历史中文标签兼容：旧 CRM 空格标签展示为当前标准标签
    [LEGACY_CRM_SOURCE_LABEL]: CRM_SOURCE_LABEL,
    [CRM_SOURCE_LABEL]: CRM_SOURCE_LABEL,
    [EXTERNAL_PLATFORM_SOURCE_LABEL]: EXTERNAL_PLATFORM_SOURCE_LABEL,
    [MANUAL_SOURCE_LABEL]: MANUAL_SOURCE_LABEL,
  }
  return map[source] || source || '-'
}

