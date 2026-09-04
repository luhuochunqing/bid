// Input: drawer silent/scope flags
// Output: parse/scoring request body and skip hint helpers (spec 044)

import { normalizeScoreResult } from './scoreParseTask.js'

export function parseTriggerSource(silent) {
  return silent ? 'AUTO' : 'MANUAL'
}

export function scoringBody({ source = 'MANUAL', scope = 'ALL', itemIds = [] } = {}) {
  const normalized = scope || 'ALL'
  return {
    source: source || 'MANUAL',
    scope: normalized,
    itemIds: normalized === 'ITEMS' ? (itemIds || []).filter((id) => id != null) : [],
  }
}

export const CIRCUIT_MESSAGE = '自动路径已停，请检查文件后手点重新解析或重新打分'

export function circuitHintFromMeta(meta) {
  return meta?.circuitOpen ? CIRCUIT_MESSAGE : ''
}

export function scoringSkipHint(data) {
  if (data?.outcome === 'SKIPPED') {
    return data.hint || '文件未变化'
  }
  return data?.hint || ''
}

export function mapScoreResults(results) {
  const resultMap = Object.create(null)
  for (const row of results || []) {
    const normalized = normalizeScoreResult(row)
    if (row.scoreItemId != null) {
      resultMap[`id:${row.scoreItemId}`] = normalized
    }
    if (row.code != null && resultMap[`code:${row.code}`] == null) {
      resultMap[`code:${row.code}`] = normalized
    }
  }
  return resultMap
}

export function lookupScoreResult(results, item) {
  if (!results || !item) return null
  if (item.id != null) {
    return results[`id:${item.id}`] ?? null
  }
  if (item.code != null) {
    return results[`code:${item.code}`] ?? results[item.code] ?? null
  }
  return null
}

export function hasMeaningfulResults(results) {
  return (results || []).some((row) =>
    row.actualScore != null ||
    row.score != null ||
    (row.status && row.status !== 'neutral' && row.status !== 'PENDING' && row.status !== 'PENDING_EXPERT') ||
    Boolean(row.evidence) ||
    Boolean(row.quote) ||
    Boolean(row.suggestion) ||
    Boolean(row.missedReason)
  )
}
