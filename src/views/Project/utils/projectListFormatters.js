/**
 * Formatters for project list display.
 */

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
    CRM: 'CRM系统',
    CRМ_OPPORTUNITY: 'CRM商机',
    THIRD_PARTY: '三方平台推送',
    EXTERNAL_PLATFORM: '外部平台',
    MANUAL: '人工录入',
    MANUAL_SINGLE: '人工录入',
    BULK_IMPORT: '批量导入',
  }
  return map[source] || source || '-'
}

