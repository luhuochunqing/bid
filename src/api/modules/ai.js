// Input: httpClient, API mode config, feature availability helpers
// Output: aiApi - AI analysis accessors aligned with frontend contracts
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * AI 智能分析模块 API
 * 真实 API AI 分析访问层
 */
import httpClient from '../client.js'
import { buildFeatureUnavailableResponse } from '../featureAvailability.js'
import { projectQualityApi } from './ai/quality.js'

function isNumericId(id) {
  return /^\d+$/.test(String(id))
}

function invalidIdMessage(entityName) {
  return {
    success: false,
    message: `Current backend only supports numeric ${entityName} IDs in API mode` }
}

function normalizeBidAnalysis(data = {}) {
  return {
    tenderId: data?.tenderId,
    winScore: Number(data?.winScore || 0),
    suggestion: data?.suggestion || data?.summary || '暂无分析建议',
    dimensionScores: Array.isArray(data?.dimensionScores) ? data.dimensionScores : [],
    risks: Array.isArray(data?.risks) ? data.risks : [],
    autoTasks: Array.isArray(data?.autoTasks) ? data.autoTasks : [] }
}

function normalizeScoreAnalysis(data = {}) {
  return {
    id: data?.id,
    projectId: data?.projectId,
    overallScore: Number(data?.overallScore || 0),
    riskLevel: data?.riskLevel || 'UNKNOWN',
    summary: data?.summary || '',
    dimensions: Array.isArray(data?.dimensions)
      ? data.dimensions.map((dimension) => ({
          id: dimension?.id,
          name: dimension?.dimensionName || '未命名维度',
          score: Number(dimension?.score || 0),
          weight: Number(dimension?.weight || 0),
          comments: dimension?.comments || '' }))
      : [] }
}

function normalizeCompetitionAnalysis(data) {
  const list = Array.isArray(data) ? data : data ? [data] : []
  return list.map((item) => ({
    id: item?.id,
    projectId: item?.projectId,
    competitorId: item?.competitorId,
    winProbability: Number(item?.winProbability || 0),
    competitiveAdvantage: item?.competitiveAdvantage || '',
    recommendedStrategy: item?.recommendedStrategy || '',
    riskFactors: item?.riskFactors || '',
    analysisDate: item?.analysisDate || '' }))
}

function normalizeRoiAnalysis(data = {}) {
  const estimatedRevenue = Number(data?.estimatedRevenue || 0)
  const estimatedCost = Number(data?.estimatedCost || 0)
  const estimatedProfit = Number(data?.estimatedProfit || estimatedRevenue - estimatedCost)
  return {
    id: data?.id,
    projectId: data?.projectId,
    estimatedRevenue,
    estimatedCost,
    estimatedProfit,
    roiPercentage: Number(data?.roiPercentage || 0),
    paybackPeriodMonths: Number(data?.paybackPeriodMonths || 0),
    riskFactors: data?.riskFactors || '',
    assumptions: data?.assumptions || '' }
}

function normalizeComplianceResult(data = {}) {
  const parseIssues = () => {
    if (Array.isArray(data?.issues)) {
      return data.issues
    }

    if (typeof data?.checkDetails !== 'string' || data.checkDetails.trim() === '') {
      return []
    }

    try {
      const parsed = JSON.parse(data.checkDetails)
      const issueList = Array.isArray(parsed) ? parsed : parsed ? [parsed] : []

      return issueList.map((issue) => ({
        category: issue?.ruleType || issue?.severity || '合规',
        item: issue?.ruleName || issue?.description || '检查项',
        status: issue?.passed === false ? 'fail' : 'pass',
        suggestion: issue?.recommendation || issue?.description || '' }))
    } catch {
      return []
    }
  }

  return {
    id: data?.id,
    projectId: data?.projectId,
    tenderId: data?.tenderId,
    overallStatus: data?.overallStatus || 'UNKNOWN',
    overallScore: Number(data?.riskScore || data?.overallScore || 0),
    issues: parseIssues(),
    checkedAt: data?.checkedAt || '',
    checkedBy: data?.checkedBy || '' }
}

function normalizeProjectAiCards(data = {}) {
  return {
    score: data?.score
      ? {
          overallScore: Number(data.score.overallScore || 0),
          riskLevel: data.score.riskLevel || 'UNKNOWN',
          summary: data.score.summary || '',
          dimensions: Array.isArray(data.score.dimensions) ? data.score.dimensions : [] }
      : null,
    competition: Array.isArray(data?.competition) ? data.competition : [],
    compliance: Array.isArray(data?.compliance)
      ? data.compliance.map(normalizeComplianceResult)
      : [],
    roi: data?.roi ? normalizeRoiAnalysis(data.roi) : null }
}

export const bidAnalysisApi = {
  async getAnalysis(tenderId) {
    if (!isNumericId(tenderId)) return Promise.resolve(invalidIdMessage('tender'))
    try {
      const response = await httpClient.get(`/api/tenders/${tenderId}/ai-analysis`)
      return { ...response, data: normalizeBidAnalysis(response?.data) }
    } catch (error) {
      if (error?.response?.status !== 404) {
        throw error
      }
      const createResponse = await httpClient.post(`/api/tenders/${tenderId}/ai-analysis`)
      return { ...createResponse, data: normalizeBidAnalysis(createResponse?.data) }
    }
  } }

export const scoreAnalysisApi = {
  async getAnalysis(projectId) {
    if (!isNumericId(projectId)) return Promise.resolve(invalidIdMessage('project'))

    const response = await httpClient.get(`/api/ai/score-analysis/project/${projectId}`)
    return { ...response, data: normalizeScoreAnalysis(response?.data) }
  },

  async create(data) {
    return httpClient.post('/api/ai/score-analysis', data)
  },

  async compare(id1, id2) {
    if (!isNumericId(id1) || !isNumericId(id2)) return Promise.resolve(invalidIdMessage('project'))

    const response = await httpClient.get(`/api/ai/score-analysis/compare/${id1}/${id2}`)
    const list = Array.isArray(response?.data) ? response.data.map(normalizeScoreAnalysis) : []
    return {
      ...response,
      data: {
        project1: list[0] || null,
        project2: list[1] || null } }
  },

  async generatePreview(context) {

    const response = await httpClient.post('/api/projects/score-preview', {
      projectId: context?.projectId || null,
      tenderId: context?.tenderId || null,
      projectName: context?.projectName || context?.name || '',
      industry: context?.industry || '',
      budget: Number(context?.budget || 0),
      tags: Array.isArray(context?.tags) ? context.tags : [] })
    return { ...response, data: response?.data ?? null }
  } }

export const projectAiApi = {
  async getCards(projectId) {
    if (!isNumericId(projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('project-ai-cards', {
        message: 'Project ID must be numeric in API mode' }))
    }

    const response = await httpClient.get(`/api/projects/${projectId}/ai-cards`)
    return { ...response, data: response?.data ? normalizeProjectAiCards(response.data) : null }
  } }

export const competitionApi = {
  async getProjectAnalysis(projectId) {
    if (!isNumericId(projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('competition-analysis', {
        message: 'Project ID must be numeric in API mode' }))
    }

    const response = await httpClient.get(`/api/ai/competition/project/${projectId}`)
    return {
      ...response,
      data: response?.data ? normalizeCompetitionAnalysis(response.data) : [] }
  },

  async getCompetitors() {
    return httpClient.get('/api/ai/competition/competitors')
  },

  async addCompetitor(data) {
    return httpClient.post('/api/ai/competition/competitors', data)
  },

  async analyze(projectId) {
    if (!isNumericId(projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('competition-analysis', {
        message: 'Project ID must be numeric in API mode' }))
    }

    const response = await httpClient.post(`/api/ai/competition/project/${projectId}/analyze`)
    return { ...response, data: response?.data ? normalizeCompetitionAnalysis(response.data)[0] || null : null }
  } }

export const roiApi = {
  async getAnalysis(projectId) {
    if (!isNumericId(projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('roi-analysis', {
        message: 'Project ID must be numeric in API mode' }))
    }

    const response = await httpClient.get(`/api/ai/roi/project/${projectId}`)
    return { ...response, data: response?.data ? normalizeRoiAnalysis(response.data) : null }
  },

  async calculate(data) {
    if (!isNumericId(data?.projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('roi-analysis', {
        message: 'Project ID must be numeric in API mode' }))
    }

    return httpClient.post(`/api/ai/roi/project/${data.projectId}/calculate`, data)
  },

  async sensitivity(data) {
    return httpClient.post('/api/ai/roi/sensitivity', data)
  } }

export const complianceApi = {
  async getCheckResult(projectId) {
    if (!isNumericId(projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('compliance-check', {
        message: 'Project ID must be numeric in API mode' }))
    }

    const response = await httpClient.get(`/api/compliance/project/${projectId}/results`)
    const results = Array.isArray(response?.data) ? response.data.map(normalizeComplianceResult) : []
    return { ...response, data: results }
  },

  async performCheck(projectId) {
    if (!isNumericId(projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('compliance-check', {
        message: 'Project ID must be numeric in API mode' }))
    }

    const response = await httpClient.post(`/api/compliance/check/project/${projectId}`)
    return { ...response, data: response?.data ? normalizeComplianceResult(response.data) : null }
  },

  async performTenderCheck(tenderId) {
    if (!isNumericId(tenderId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('compliance-check', {
        message: 'Tender ID must be numeric in API mode' }))
    }

    const response = await httpClient.post(`/api/compliance/check/tender/${tenderId}`)
    return { ...response, data: response?.data ? normalizeComplianceResult(response.data) : null }
  },

  async checkBidDocumentQuality(projectId) {
    if (!isNumericId(projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('bid-document-quality-check', {
        message: 'Project ID must be numeric in API mode' }))
    }

    const response = await httpClient.post(`/api/compliance/bid-document/check/${projectId}`)
    return { ...response, data: response?.data ? normalizeComplianceResult(response.data) : null }
  },

  async getBidDocumentQualityResult(projectId) {
    if (!isNumericId(projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('bid-document-quality-check', {
        message: 'Project ID must be numeric in API mode' }))
    }

    const response = await httpClient.get(`/api/compliance/bid-document/results/${projectId}`)
    return { ...response, data: response?.data ? normalizeComplianceResult(response.data) : null }
  } }

export default {
  bid: bidAnalysisApi,
  score: scoreAnalysisApi,
  project: projectAiApi,
  competition: competitionApi,
  roi: roiApi,
  compliance: complianceApi,
  quality: projectQualityApi }

export { projectQualityApi }
