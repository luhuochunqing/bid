// Input: httpClient, isNumericId, buildFeatureUnavailableResponse
// Output: complianceApi - Compliance & Bid Quality Check APIs
// Pos: src/api/modules/ai/ - AI compliance API layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../../client.js'
import { buildFeatureUnavailableResponse } from '../../featureAvailability.js'

function isNumericId(id) {
  return /^\d+$/.test(String(id))
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
    checkType: data?.checkType || 'GENERAL',
    overallStatus: data?.overallStatus || 'UNKNOWN',
    riskScore: Number(data?.riskScore || 0),
    checkedAt: data?.checkedAt || '',
    issues: parseIssues(),
    aiOverallAssessment: data?.aiOverallAssessment || '',
    aiKeyRisks: Array.isArray(data?.aiKeyRisks) ? data.aiKeyRisks : [] }
}

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

  async triggerBidQualityCheck(projectId, force = false) {
    if (!isNumericId(projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('bid-document-quality-check', {
        message: 'Project ID must be numeric in API mode' }))
    }
    return httpClient.post(`/api/compliance/bid-quality/trigger/${projectId}?force=${Boolean(force)}`)
  },

  async getBidQualityProgress(taskId) {
    if (!taskId) {
      return Promise.resolve({ success: false, message: 'Task ID is required' })
    }
    return httpClient.get(`/api/compliance/bid-quality/progress/${taskId}`)
  },

  async getBidQualityReport(projectId) {
    if (!isNumericId(projectId)) {
      return Promise.resolve(buildFeatureUnavailableResponse('bid-document-quality-check', {
        message: 'Project ID must be numeric in API mode' }))
    }
    return httpClient.get(`/api/compliance/bid-quality/report/${projectId}`)
  }
}
