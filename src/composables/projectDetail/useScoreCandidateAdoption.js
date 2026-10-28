// Input: props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData
// Output: candidateFacts, candidateTotalWeight, isAdoptingCandidates, adoptCandidateFacts
// Pos: src/composables/projectDetail/ - Candidate adoption sub-composable (under 300 lines)

import { ref, computed, watch } from 'vue'
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
  const selectedSchemeId = ref(null)
  const schemes = computed(() => Array.isArray(reviewSnapshot.value?.schemes) ? reviewSnapshot.value.schemes : [])
  const schemeSelectionRequired = computed(() => reviewSnapshot.value?.schemeSelectionRequired === true
    || schemes.value.length > 1)

  watch(schemes, (current) => {
    const ids = current.map((scheme) => scheme.schemeId).filter(Boolean)
    if (!ids.includes(selectedSchemeId.value)) {
      selectedSchemeId.value = ids.length === 1 ? ids[0] : reviewSnapshot.value?.selectedSchemeId || null
    }
  }, { immediate: true, deep: true })

  const activeSchemeId = computed(() => selectedSchemeId.value
    || (!schemeSelectionRequired.value ? reviewSnapshot.value?.selectedSchemeId || schemes.value[0]?.schemeId || null : null))

  const candidateFacts = computed(() => reviewFacts.value)
  const eligibleFacts = computed(() => reviewFacts.value.filter((fact) => fact.candidateEligible === true
    && (activeSchemeId.value ? fact.schemeId === activeSchemeId.value : schemes.value.length === 0)))

  const candidateTotalWeight = computed(() => {
    const sum = effectiveCandidateWeight(eligibleFacts.value)
    return Math.round(sum * 100) / 100
  })

  async function adoptCandidateFacts(editedFacts = []) {
    if (isAdoptingCandidates.value) return
    if (schemeSelectionRequired.value && !activeSchemeId.value) {
      ElMessage.warning('存在多个评分方案，请先选择一个方案')
      return
    }
    const factsToAdopt = editedFacts.length > 0
      ? editedFacts.filter((fact) => fact.candidateEligible === true
        && (!activeSchemeId.value || fact.schemeId === activeSchemeId.value))
      : eligibleFacts.value
    if (factsToAdopt.length === 0) {
      ElMessage.warning('没有来源已验证且属于所选评分方案的候选项')
      return
    }
    isAdoptingCandidates.value = true
    try {
      const actions = factsToAdopt.map((fact) => ({
        factId: fact.factId,
        action: 'ADOPT_CANDIDATES',
        ...(editedFacts.length > 0 ? {
          adjustedWeight: Number(fact.weight),
          adjustedCode: fact.code,
          adjustedDimension: fact.dimension,
          adjustedSourceText: fact.sourceText,
          adjustedParentFactId: fact.parentFactId || '',
          adjustedScoreType: fact.scoreType,
        } : {}),
      }))

      const res = await scoreParseApi.submitReview(props.projectId, {
        parseTaskId: reviewSnapshot.value?.parseTaskId,
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
      try {
        await fetchAnalysisData({ stage: currentStage.value, autoScore: false, autoParse: false })
      } catch {
        reviewSnapshot.value = null
      }
    } finally {
      isAdoptingCandidates.value = false
    }
  }

  return {
    candidateFacts,
    selectedSchemeId,
    schemes,
    schemeSelectionRequired,
    candidateTotalWeight,
    declaredTotalWeight: computed(() => {
      const selected = schemes.value.find((scheme) => scheme.schemeId === activeSchemeId.value)
      const raw = selected ? selected.declaredTotal : reviewSnapshot.value?.declaredTotal
      if (raw === null || raw === undefined || raw === '') return null
      const parsed = Number(raw)
      return Number.isFinite(parsed) ? parsed : null
    }),
    adoptionStatus: computed(() => {
      if (reviewSnapshot.value?.adoptionStatus === 'SOURCE_ROWS_UNCOVERED') return 'SOURCE_ROWS_UNCOVERED'
      if (schemeSelectionRequired.value && !activeSchemeId.value) return 'SCHEME_SELECTION_REQUIRED'
      const selected = schemes.value.find((scheme) => scheme.schemeId === activeSchemeId.value)
      if (selected?.totalEvidenceStatus === 'CONFLICT') return 'CONFLICTING_TOTAL'
      if (selected?.totalEvidenceStatus === 'VERIFIED') return 'DECLARED_TOTAL'
      if (selected) return selected.tableIds?.length === 1 ? 'SINGLE_UNKNOWN_TOTAL' : 'UNKNOWN_TOTAL'
      return reviewSnapshot.value?.adoptionStatus || 'DECLARED_TOTAL'
    }),
    isAdoptingCandidates,
    adoptCandidateFacts,
  }
}

function effectiveCandidateWeight(facts) {
  const eligibleById = new Map(facts.map((fact) => [fact.factId, fact]))
  const childrenByParent = new Map()
  facts.forEach((fact) => {
    if (!eligibleById.has(fact.parentFactId)) return
    const children = childrenByParent.get(fact.parentFactId) || []
    children.push(fact)
    childrenByParent.set(fact.parentFactId, children)
  })

  const visited = new Set()
  const contribution = (fact) => {
    if (visited.has(fact.factId)) return 0
    visited.add(fact.factId)
    const children = childrenByParent.get(fact.factId) || []
    if (children.length === 0) return Number(fact.weight) || 0
    const relation = String(fact.childAggregation || 'SUM').toUpperCase()
    if (relation === 'UNKNOWN') return children.reduce((total, child) => total + contribution(child), 0)
    if (relation === 'SUM') return children.reduce((total, child) => total + contribution(child), 0)
    return Number(fact.weight) || 0
  }

  return facts.filter((fact) => !eligibleById.has(fact.parentFactId))
    .reduce((total, fact) => total + contribution(fact), 0)
}
