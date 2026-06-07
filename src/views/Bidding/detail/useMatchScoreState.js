import { computed } from 'vue'
import { normalizeMatchScoreForView, summarizeScoreState } from '../match-scoring/normalizers.js'

export function useMatchScoreState(matchScoreRef, scoreLoadingRef, scoreGeneratingRef, scoreErrorRef) {
  const scoreForView = computed(() => normalizeMatchScoreForView(matchScoreRef.value))
  const scoreSummary = computed(() => summarizeScoreState({
    loading: scoreLoadingRef.value,
    generating: scoreGeneratingRef.value,
    error: scoreErrorRef.value,
    score: scoreForView.value,
  }))

  return {
    scoreForView,
    scoreSummary,
    matchScoreState: computed(() => scoreSummary.value.state),
    scoreEmptyText: computed(() => scoreSummary.value.actionText || scoreSummary.value.text),
    scoreEmptyDescription: computed(() => scoreSummary.value.description),
  }
}
