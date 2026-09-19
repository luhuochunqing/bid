// Input: props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData
// Output: candidateFacts, candidateTotalWeight, isAdoptingCandidates, adoptCandidateFacts
// Pos: src/composables/projectDetail/ - Candidate adoption sub-composable (under 300 lines)

import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'

export function useScoreCandidateAdoption({
  props,
  reviewFacts,
  reviewSnapshot,
  reviewSubmitted,
  currentStage,
  fetchAnalysisData,
}) {
  const isAdoptingCandidates = ref(false)

  const parentIds = computed(() => {
    return new Set(reviewFacts.value.map((f) => f.parentFactId).filter(Boolean))
  })

  const candidateFacts = computed(() => {
    return reviewFacts.value.filter(
      (f) => Number(f.weight) > 0 && !parentIds.value.has(f.factId)
    )
  })

  const candidateTotalWeight = computed(() => {
    const sum = candidateFacts.value.reduce((acc, f) => acc + (Number(f.weight) || 0), 0)
    return Math.round(sum * 100) / 100
  })

  async function adoptCandidateFacts(selectedFactIds = []) {
    if (isAdoptingCandidates.value) return
    isAdoptingCandidates.value = true
    try {
      const actions = selectedFactIds.length > 0
        ? selectedFactIds.map((id) => ({ factId: id, action: 'ADOPT_CANDIDATES' }))
        : [{ action: 'ADOPT_CANDIDATES', reason: '一键采纳所有候选有效评分项' }]

      const res = await scoreParseApi.submitReview(props.projectId, {
        reason: '人工确认采纳候选评分标准',
        actions,
      })
      if (res?.data) {
        reviewSnapshot.value = res.data
      }
      reviewSubmitted.value = true
      ElMessage.success('已成功采纳候选评分标准！')
      await fetchAnalysisData({ stage: currentStage.value, autoScore: false, autoParse: false })
      reviewSubmitted.value = true
    } catch (e) {
      notifyErrorUnlessRateLimit(e, '采纳候选评分标准失败')
    } finally {
      isAdoptingCandidates.value = false
    }
  }

  return {
    candidateFacts,
    candidateTotalWeight,
    isAdoptingCandidates,
    adoptCandidateFacts,
  }
}
