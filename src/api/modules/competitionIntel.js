// Input: httpClient, competition intel service
// Output: competitionIntelApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 竞争情报模块 API
 * 真实 API 竞争情报访问层
 */
import httpClient from '../client.js'

function normalizeCompetitor(item) {
  return {
    id: item?.id,
    name: item?.name || '未知竞争对手',
    industry: item?.industry || 'OTHER',
    region: item?.region || '',
    website: item?.website || '',
    contactEmail: item?.contactEmail || '',
    contactPhone: item?.contactPhone || '',
    annualRevenue: item?.annualRevenue || 0,
    employeeCount: item?.employeeCount || 0,
    description: item?.description || '',
    strengths: item?.strengths || [],
    weaknesses: item?.weaknesses || [],
    winCount: item?.winCount || 0,
    bidCount: item?.bidCount || 0,
    winRate: item?.winRate || 0,
    avgBidAmount: item?.avgBidAmount || 0,
    createdAt: item?.createdAt || '',
    updatedAt: item?.updatedAt || '' }
}

function normalizeAnalysis(item) {
  return {
    id: item?.id,
    competitorId: item?.competitorId || null,
    competitorName: item?.competitorName || '',
    projectId: item?.projectId || null,
    projectName: item?.projectName || '',
    analysisDate: item?.analysisDate || '',
    overallScore: item?.overallScore || 0,
    technicalScore: item?.technicalScore || 0,
    priceScore: item?.priceScore || 0,
    serviceScore: item?.serviceScore || 0,
    recommendation: item?.recommendation || 'HOLD',
    keyFindings: item?.keyFindings || [],
    riskLevel: item?.riskLevel || 'MEDIUM',
    createdBy: item?.createdBy || '',
    createdAt: item?.createdAt || '' }
}

export const competitionIntelApi = {
  async getCompetitors(params = {}) {
    const response = await httpClient.get('/api/ai/competition/competitors', { params })
    return {
      ...response,
      data: (response?.data || []).map(normalizeCompetitor) }
  },

  async getCompetitor(id) {
    const response = await httpClient.get(`/api/ai/competition/competitors/${id}`)
    return { ...response, data: normalizeCompetitor(response?.data) }
  },

  async createCompetitor(data) {
    const response = await httpClient.post('/api/ai/competition/competitors', data)
    return { ...response, data: normalizeCompetitor(response?.data) }
  },

  async updateCompetitor(id, data) {
    const response = await httpClient.put(`/api/ai/competition/competitors/${id}`, data)
    return { ...response, data: normalizeCompetitor({ ...response?.data, ...data }) }
  },

  async deleteCompetitor(id) {
    return httpClient.delete(`/api/ai/competition/competitors/${id}`)
  },

  async getAnalysis(projectId) {
    const response = await httpClient.get(`/api/ai/competition/analysis/${projectId}`)
    return {
      ...response,
      data: response?.data ? normalizeAnalysis(response.data) : null }
  },

  async createAnalysis(data) {
    const response = await httpClient.post('/api/ai/competition/analysis', data)
    return { ...response, data: normalizeAnalysis(response?.data) }
  } }

export default {
  competitionIntel: competitionIntelApi }
