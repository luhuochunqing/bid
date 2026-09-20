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

  const candidateFacts = computed(() => reviewFacts.value)

  const candidateTotalWeight = computed(() => {
    const parentIds = new Set(reviewFacts.value.map((f) => f.parentFactId).filter(Boolean))
    const sum = reviewFacts.value
      .filter((f) => !parentIds.has(f.factId))
      .reduce((acc, f) => acc + (Number(f.weight) || 0), 0)
    return Math.round(sum * 100) / 100
  })

  async function adoptCandidateFacts(editedFacts = []) {
    if (isAdoptingCandidates.value) return
    isAdoptingCandidates.value = true
    try {
      const actions = editedFacts.length > 0
        ? editedFacts.map((fact) => ({
            factId: fact.factId,
            action: 'ADOPT_CANDIDATES',
            adjustedWeight: Number(fact.weight),
            adjustedCode: fact.code,
            adjustedDimension: fact.dimension,
            adjustedSourceText: fact.sourceText,
            adjustedParentFactId: fact.parentFactId || '',
            adjustedScoreType: fact.scoreType,
          }))
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
    declaredTotalWeight: computed(() => {
      const raw = reviewSnapshot.value?.declaredTotal
      if (raw === null || raw === undefined || raw === '') return null
      const parsed = Number(raw)
      return Number.isFinite(parsed) ? parsed : null
    }),
    isAdoptingCandidates,
    adoptCandidateFacts,
  }
}
