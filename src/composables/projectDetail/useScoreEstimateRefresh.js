// Input: active score items and the current project identity
// Output: independent knowledge estimate refresh, freshness counts and readback errors
// Pos: projectDetail presentation; does not parse, activate candidates or run actual scoring
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { computed, ref, watch } from 'vue'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { formatTime, normalizeScoreItem } from './scoreParseTask.js'

export const normalizeEstimatedItem = (item, index) => ({
  ...normalizeScoreItem(item, index), estimateStatus: item.estimateStatus, estimatedAt: item.estimatedAt,
})

export function useScoreEstimateRefresh(props, scoreItems, loading, scoringOverlayVisible) {
  let projectRevision = 0
  const estimatesRefreshing = ref(false), estimateRefreshError = ref('')
  watch(() => props.projectId, () => { projectRevision++; estimateRefreshError.value = '' }, { flush: 'sync' })
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
    try {
      await scoreParseApi.refreshEstimates(projectId)
      if (!isCurrent()) return
      const response = await scoreParseApi.getItems(projectId)
      if (!isCurrent()) return
      if (!Array.isArray(response?.data?.items)) throw new Error('知识库预判回读失败，请重试')
      scoreItems.value = response.data.items.map(normalizeEstimatedItem)
    } catch (error) {
      if (isCurrent()) estimateRefreshError.value = error?.response?.data?.msg || error?.message || '知识库预判更新失败'
    } finally { estimatesRefreshing.value = false }
  }
  return { estimatesRefreshing, estimateRefreshError, staleEstimateCount, estimateCheckedAt, refreshEstimates }
}
