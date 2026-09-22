// Input: httpClient and score analysis endpoints
// Output: scoreAnalysisApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 评分分析模块 API
 * 真实 API 为唯一数据源
 */
import httpClient from '../client.js'

function normalizeDimension(item) {
  return {
    dimension: item?.dimension || item?.name || '',
    score: item?.score || 0,
    weight: item?.weight || 1.0,
    maxScore: item?.maxScore || 100,
    comment: item?.comment || ''
  }
}

function normalizeScoreAnalysis(item) {
  return {
    id: item?.id,
    projectId: item?.projectId || null,
    projectName: item?.projectName || '',
    analysisDate: item?.analysisDate || '',
    totalScore: item?.totalScore || 0,
    riskLevel: item?.riskLevel || 'MEDIUM',
    dimensions: (item?.dimensions || []).map(normalizeDimension),
    recommendation: item?.recommendation || 'HOLD',
    strengths: item?.strengths || [],
    weaknesses: item?.weaknesses || [],
    comparisonNote: item?.comparisonNote || '',
    createdBy: item?.createdBy || '',
    createdAt: item?.createdAt || ''
  }
}

export const scoreAnalysisApi = {
  async getAnalyses(projectId) {
    const url = projectId ? `/api/ai/score-analysis/project/${projectId}/history` : '/api/ai/score-analysis'
    const response = await httpClient.get(url)
    return {
      ...response,
      data: (response?.data || []).map(normalizeScoreAnalysis)
    }
  },

  async getAnalysis(id) {
    const response = await httpClient.get(`/api/ai/score-analysis/${id}`)
    return { ...response, data: normalizeScoreAnalysis(response?.data) }
  },

  async getAnalysisByProject(projectId) {
    const response = await httpClient.get(`/api/ai/score-analysis/project/${projectId}`)
    return { ...response, data: normalizeScoreAnalysis(response?.data) }
  },

  async createAnalysis(data) {
    const response = await httpClient.post('/api/ai/score-analysis', data)
    return { ...response, data: normalizeScoreAnalysis(response?.data) }
  },

  async updateAnalysis(id, data) {
    const response = await httpClient.put(`/api/ai/score-analysis/${id}`, data)
    return { ...response, data: normalizeScoreAnalysis({ ...response?.data, ...data }) }
  },

  async deleteAnalysis(id) {
    return httpClient.delete(`/api/ai/score-analysis/${id}`)
  },

  async compareProjects(projectIds) {
    const response = await httpClient.post('/api/ai/score-analysis/compare', { projectIds })
    return {
      ...response,
      data: (response?.data || []).map(normalizeScoreAnalysis)
    }
  }
}

export default {
  scoreAnalysis: scoreAnalysisApi,
}
