// Input: active score items and the current project identity
// Output: server refresh counts/reasons until item reload, certificate-only freshness and isolated errors
// Pos: projectDetail presentation; does not parse, activate candidates or run actual scoring
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { computed, ref, watch } from 'vue'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { formatTime, normalizeScoreItem } from './scoreParseTask.js'

export const normalizeEstimatedItem = (item, index) => ({
  ...normalizeScoreItem(item, index), estimateStatus: item.estimateStatus, estimatedAt: item.estimatedAt,
})

function validateRefreshReceipt(data, readbackItems) {
  const refresh = data?.refresh, items = data?.items
  if (!refresh) throw new Error('未返回本次刷新统计，请重试核对')
  if (refresh.processedCount === 0) throw new Error('本次没有处理任何生效评分项，请核对评分项后重试')
  const { processedCount, scoredCount, pendingCount, pendingItems } = refresh
  if (![processedCount, scoredCount, pendingCount].every(count => Number.isSafeInteger(count) && count >= 0)
    || scoredCount + pendingCount !== processedCount || !Array.isArray(items) || items.length !== processedCount
    || !Array.isArray(pendingItems) || pendingItems.length !== pendingCount) {
    throw new Error('本次刷新统计不完整或不一致，请重试核对')
  }
  const receiptItems = new Map(items.map(item => [item.id, item]))
  if (receiptItems.size !== processedCount || items.some(item => item.id == null)
    || new Set(pendingItems.map(item => item.id)).size !== pendingCount
    || pendingItems.some(item => !receiptItems.has(item.id) || typeof item.reason !== 'string' || !item.reason.trim())) {
    throw new Error('本次刷新评分项或无分值原因不完整，请重试核对')
  }
  const ruleFields = ['code', 'dim', 'detail', 'weight', 'scoreType', 'parentScoreItemId', 'location', 'sourceCellIds', 'weightEvidence']
  if (readbackItems.length !== processedCount || new Set(readbackItems.map(item => item.id)).size !== processedCount
    || readbackItems.some(item => !receiptItems.has(item.id)
      || ruleFields.some(field => (item[field] ?? null) !== (receiptItems.get(item.id)[field] ?? null)))) {
    throw new Error('生效评分项在刷新后发生变化，本次统计不再适用，请重新更新知识库预判')
  }
  const resultFields = ['estScore', 'status', 'estBasis', 'kbHit', 'estimatedAt', 'estimateStatus']
  if (readbackItems.some(item => resultFields.some(field => item[field] !== receiptItems.get(item.id)[field]))) {
    throw new Error('预判结果在刷新后发生变化，本次统计不再适用，请重新更新知识库预判')
  }
  return refresh
}

export function useScoreEstimateRefresh(props, scoreItems, loading, scoringOverlayVisible) {
  let projectRevision = 0
  const estimatesRefreshing = ref(false), estimateRefreshError = ref('')
  const estimateRefreshResult = ref(null)
  watch(scoreItems, () => { estimateRefreshResult.value = null }, { flush: 'sync' })
  watch(() => props.projectId, () => {
    projectRevision++
    estimatesRefreshing.value = false
    estimateRefreshError.value = ''
    estimateRefreshResult.value = null
  }, { flush: 'sync' })
  const staleEstimateCount = computed(() => scoreItems.value.filter(item => item.estimateStatus === 'STALE').length)
  const estimateCheckedAt = computed(() => {
    const dates = scoreItems.value.filter(item => item.estimateStatus === 'FRESH' && item.estimatedAt)
      .map(item => item.estimatedAt).sort()
    return dates.length ? formatTime(dates[0]) : '尚未核验'
  })
  async function refreshEstimates() {
    if (estimatesRefreshing.value || loading.value || scoringOverlayVisible.value || !scoreItems.value.length) return
    const projectId = props.projectId, revision = projectRevision
    const isCurrent = () => props.projectId === projectId && projectRevision === revision
    estimatesRefreshing.value = true
    estimateRefreshError.value = ''
    estimateRefreshResult.value = null
    try {
      const refreshed = await scoreParseApi.refreshEstimates(projectId)
      if (!isCurrent()) return
      const response = await scoreParseApi.getItems(projectId)
      if (!isCurrent()) return
      if (!Array.isArray(response?.data?.items)) throw new Error('知识库预判回读失败，请重试')
      scoreItems.value = response.data.items.map(normalizeEstimatedItem)
      estimateRefreshResult.value = validateRefreshReceipt(refreshed?.data, response.data.items)
    } catch (error) {
      if (isCurrent()) estimateRefreshError.value = error?.response?.data?.msg || error?.message || '知识库预判更新失败'
    } finally { if (isCurrent()) estimatesRefreshing.value = false }
  }
  return { estimatesRefreshing, estimateRefreshError, estimateRefreshResult, staleEstimateCount, estimateCheckedAt, refreshEstimates }
}
