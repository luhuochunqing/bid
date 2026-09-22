// Input: httpClient and ROI analysis endpoints
// Output: roiApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * ROI分析模块 API
 * 真实 API 为唯一数据源
 */
import httpClient from '../client.js'

function normalizeROI(item) {
  return {
    id: item?.id,
    projectId: item?.projectId || null,
    projectName: item?.projectName || '',
    analysisDate: item?.analysisDate || '',
    investment: item?.investment || item?.cost || 0,
    revenue: item?.revenue || 0,
    profit: item?.profit || 0,
    roiPercentage: item?.roiPercentage || item?.roi || 0,
    paybackMonths: item?.paybackMonths || 0,
    npv: item?.npv || 0,
    irr: item?.irr || 0,
    riskLevel: item?.riskLevel || 'MEDIUM',
    sensitivityData: item?.sensitivityData || null,
    assumptions: item?.assumptions || '',
    createdBy: item?.createdBy || '',
    createdAt: item?.createdAt || ''
  }
}

export const roiApi = {
  async getAnalyses(projectId) {
    const url = projectId ? `/api/ai/roi/project/${projectId}` : '/api/ai/roi'
    const response = await httpClient.get(url)
    return {
      ...response,
      data: response?.data ? normalizeROI(response.data) : null,
    }
  },

  async getAnalysis(id) {
    const response = await httpClient.get(`/api/ai/roi/${id}`)
    return { ...response, data: normalizeROI(response?.data) }
  },

  async createAnalysis(data) {
    const response = await httpClient.post('/api/ai/roi', data)
    return { ...response, data: normalizeROI(response?.data) }
  },

  async updateAnalysis(id, data) {
    const response = await httpClient.put(`/api/ai/roi/${id}`, data)
    return { ...response, data: normalizeROI({ ...response?.data, ...data }) }
  },

  async deleteAnalysis(id) {
    return httpClient.delete(`/api/ai/roi/${id}`)
  },

  async getSensitivity(projectId) {
    const response = await httpClient.get(`/api/ai/roi/${projectId}/sensitivity`)
    return { ...response, data: response?.data }
  }
}

export default {
  roi: roiApi,
}
