// Input: project id, bid-agent API, error reporter, and existing panel refs
// Output: full analysis state and fetch actions for knowledge base matching
// Pos: src/composables/projectDetail/ - Project Detail feature composables

import { ref } from 'vue'

function getResponseData(response) {
  return response?.data ?? null
}

function isFailedResponse(response) {
  return response?.success === false
}

function computeTotal(summary) {
  if (!summary) return 0
  return (summary.redLineCount || 0) + (summary.unsatisfiedCount || 0) + (summary.attentionCount || 0)
}

function distributeToPanelRefs(panelRefs, data) {
  if (!panelRefs || !data) return
  const map = {
    qualificationMatch: data.knowledgeBaseMatch?.qualificationMatch,
    technicalRequirements: data.technicalRequirements,
    commercialRequirements: data.commercialRequirements,
    riskClassification: data.riskClassification,
    scoringCriteria: data.scoringCriteria,
  }
  for (const [key, value] of Object.entries(map)) {
    if (panelRefs[key] && value) panelRefs[key].value = value
  }
}

export function useFullAnalysis({ projectId, bidAgentApi, reportError, panelRefs }) {
  const knowledgeBaseMatch = ref(null)
  const knowledgeBaseMatchLoading = ref(false)
  const fullAnalysisLoading = ref(false)
  const riskSummary = ref(null)

  const fetchFullAnalysis = async () => {
    fullAnalysisLoading.value = true
    try {
      const response = await bidAgentApi.getFullAnalysis(projectId.value)
      if (isFailedResponse(response)) throw new Error(response.msg || '获取全维度分析失败')
      const data = getResponseData(response)
      if (!data) return
      knowledgeBaseMatch.value = data.knowledgeBaseMatch
      riskSummary.value = data.riskSummary
        ? { ...data.riskSummary, total: computeTotal(data.riskSummary) }
        : null
      distributeToPanelRefs(panelRefs, data)
    } catch (err) {
      reportError(err, '获取全维度分析失败')
    } finally {
      fullAnalysisLoading.value = false
    }
  }

  const fetchKnowledgeBaseMatch = async () => {
    knowledgeBaseMatchLoading.value = true
    try {
      const response = await bidAgentApi.getKnowledgeBaseMatch(projectId.value)
      if (isFailedResponse(response)) throw new Error(response.msg || '获取知识库匹配失败')
      knowledgeBaseMatch.value = getResponseData(response)
    } catch (err) {
      reportError(err, '获取知识库匹配失败')
    } finally {
      knowledgeBaseMatchLoading.value = false
    }
  }

  return {
    knowledgeBaseMatch, knowledgeBaseMatchLoading,
    fullAnalysisLoading, riskSummary,
    fetchFullAnalysis, fetchKnowledgeBaseMatch,
  }
}
