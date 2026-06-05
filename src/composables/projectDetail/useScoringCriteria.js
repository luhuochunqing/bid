// Input: project id and bid-agent API module
// Output: scoring criteria state and fetch action
// Pos: src/composables/projectDetail/ - Project Detail feature composables

import { ref } from 'vue'

function getResponseData(response) {
  return response?.data ?? null
}

function isFailedResponse(response) {
  return response?.success === false
}

export function useScoringCriteria({ projectId, bidAgentApi, reportError }) {
  const scoringCriteria = ref(null)
  const scoringCriteriaLoading = ref(false)

  const fetchScoringCriteria = async () => {
    scoringCriteriaLoading.value = true
    try {
      const response = await bidAgentApi.getScoringCriteria(projectId.value)
      if (isFailedResponse(response)) throw new Error(response.msg || '获取分类失败')
      scoringCriteria.value = getResponseData(response)
    } catch (err) {
      reportError(err, '获取评分标准解析失败')
    } finally {
      scoringCriteriaLoading.value = false
    }
  }

  return {
    scoringCriteria,
    scoringCriteriaLoading,
    fetchScoringCriteria,
  }
}
