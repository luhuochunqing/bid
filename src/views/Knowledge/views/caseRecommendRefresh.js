// Input: 推荐接口响应、推荐异常、项目与旧评分项
// Output: 推荐案例与详情、评分项列表、404 判定、错误文案与重匹配后的评分项 ID
// Pos: src/views/Knowledge/views/ - 案例推荐抽屉共用工具

import { caseRecommendationApi } from '@/api/modules/caseRecommendation'
import { getMatchLabel } from './caseLabels.js'

export const SCORE_ITEM_RESELECT_MESSAGE = '评分项已更新，请重新选择评分项。'

export async function fetchRecommendScoreItems(projectId, silentError) {
  const res = await caseRecommendationApi.getScoreItems(projectId, { silentError })
  if (Array.isArray(res?.data)) return res.data
  return Array.isArray(res) ? res : []
}

export function isScoreItemNotFoundError(err) {
  return err?.response?.status === 404 || err?.status === 404
}

export function extractRecommendErrorMessage(err, fallback = '获取推荐案例失败，请稍后重试') {
  const data = err?.response?.data
  return data?.msg || data?.message || err?.message || fallback
}

// undefined 表示刷新失败，null 表示刷新成功但没有可安全重选的条款。
export function matchRefreshedScoreItemId(items, previousItem) {
  if (!items) return undefined

  const previousDim = previousItem?.dim?.trim()
  const previousDetail = previousItem?.detail?.trim()
  if (!previousDim || !previousDetail || previousDetail === '—') return null

  // 编号可随换版重新生成，因此只按名称和完整细则确认唯一条款。
  const matches = items.filter(item =>
    item.dim?.trim() === previousDim && item.detail?.trim() === previousDetail)
  return matches.length === 1 ? matches[0].id ?? null : null
}

export function normalizeRecommendCase(item) {
  const matchScore = item.matchScore ?? item.score ?? 0
  return {
    caseId: item.id ?? item.caseId,
    scoringTitle: item.scoringPointTitle || item.scoringTitle,
    responseTextSummary: item.responseTextSummary || item.responseText || '',
    projectType: item.projectType,
    customerType: item.customerType,
    createdAt: item.createdAt || '',
    score: matchScore,
    matchScore,
    matchLabel: getMatchLabel(matchScore, item.matchLabel),
    matchReasons: item.matchReasons || [],
    matchedKeywords: item.matchedKeywords || [],
    responseText: item.responseText || '',
    sourceProjectName: item.sourceProjectName,
    subTopic: item.subTopic,
    qualityScore: item.qualityScore,
  }
}

export function mergeRecommendCaseDetail(res, current) {
  const full = res?.data || res
  return {
    ...full,
    matchScore: current?.matchScore ?? current?.score ?? full?.matchScore,
    matchLabel: current?.matchLabel ?? full?.matchLabel,
    matchReasons: current?.matchReasons ?? full?.matchReasons,
    matchedKeywords: current?.matchedKeywords ?? full?.matchedKeywords,
  }
}

export function filterRecommendCases(cases, keyword) {
  const kw = keyword.trim().toLowerCase()
  if (!kw) return cases
  return cases.filter(c =>
    (c.scoringTitle || '').toLowerCase().includes(kw) ||
    (c.responseTextSummary || '').toLowerCase().includes(kw) ||
    (c.sourceProjectName || '').toLowerCase().includes(kw)
  )
}
