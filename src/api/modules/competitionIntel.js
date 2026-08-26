// Input: httpClient, competition intel service
// Output: competitionIntelApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 竞争情报模块 API
 * 真实 API 竞争情报访问层
 */
import httpClient from '../client.js'

// 对齐后端 CompetitorDTO（specs/055）：竞品字典仅含 name/industry/strengths/weaknesses/
// marketShare/typicalBidRange/status，不再伪造 region/website/winRate 等后端不返回的字段。
function normalizeCompetitor(item) {
  return {
    id: item?.id,
    name: item?.name || '',
    industry: item?.industry || '',
    strengths: item?.strengths || '',
    weaknesses: item?.weaknesses || '',
    marketShare: item?.marketShare ?? null,
    typicalBidRangeMin: item?.typicalBidRangeMin ?? null,
    typicalBidRangeMax: item?.typicalBidRangeMax ?? null,
    status: item?.status || 'ACTIVE',
    createdAt: item?.createdAt || '' }
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

  // 兼容旧调用（传对象则保留 name/industry/strengths/weaknesses 字段，传字符串只用 name），语义现为幂等 upsert（specs/055）
  async createCompetitor(data) {
    return this.upsertCompetitor(data)
  },

  async upsertCompetitor(payload) {
    const body = typeof payload === 'string' ? { name: payload } : payload
    const response = await httpClient.post('/api/ai/competition/competitors', body)
    return { ...response, data: normalizeCompetitor(response?.data) }
  },

  // 兼容旧调用（传对象取 .name），语义现为改名
  async updateCompetitor(id, data) {
    const name = typeof data === 'string' ? data : data?.name
    return this.renameCompetitor(id, name)
  },

  async renameCompetitor(id, name) {
    const response = await httpClient.put(`/api/ai/competition/competitors/${id}`, { name })
    return { ...response, data: normalizeCompetitor(response?.data) }
  },

  async mergeCompetitor(id, targetId) {
    const response = await httpClient.post(`/api/ai/competition/competitors/${id}/merge`, { targetId })
    return { ...response, data: normalizeCompetitor(response?.data) }
  },

  async disableCompetitor(id) {
    const response = await httpClient.post(`/api/ai/competition/competitors/${id}/disable`)
    return { ...response, data: normalizeCompetitor(response?.data) }
  },

  // 兼容旧调用：后端无物理删除端点，delete 语义转软停用（specs/055）
  async deleteCompetitor(id) {
    return this.disableCompetitor(id)
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
