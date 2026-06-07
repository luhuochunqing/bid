// Input: httpClient and project text quality endpoints
// Output: projectQualityApi - run/latest/adopt/ignore accessors for quality checks
// Pos: src/api/modules/ai/ - Frontend quality API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../../client.js'

function normalizeIssue(issue = {}) {
  return {
    id: issue?.id,
    type: String(issue?.type || 'format').toLowerCase(),
    original: issue?.originalText || issue?.original || '',
    suggestion: issue?.suggestionText || issue?.suggestion || '',
    location: issue?.locationLabel || issue?.location || '',
    ignored: Boolean(issue?.ignored),
    adopted: Boolean(issue?.adopted),
  }
}

function normalizeQualityResult(data = {}) {
  const issues = Array.isArray(data?.issues) ? data.issues.map(normalizeIssue) : []
  return {
    id: data?.id,
    projectId: data?.projectId,
    documentId: data?.documentId || null,
    documentName: data?.documentName || '',
    status: data?.status || (data?.empty ? 'EMPTY' : (issues.length > 0 ? 'COMPLETED' : 'EMPTY')),
    checkedAt: data?.checkedAt || '',
    summary: data?.summary || '',
    issues,
    errors: issues.filter((item) => !item.ignored && !item.adopted),
    suggestions: issues.filter((item) => !item.ignored),
    empty: Boolean(data?.empty),
  }
}

export const projectQualityApi = {
  async runProjectQualityCheck(projectId) {
    const response = await httpClient.post(`/api/projects/${projectId}/quality-checks`)
    return { ...response, data: normalizeQualityResult(response?.data) }
  },

  async getProjectQualityResult(projectId) {
    const response = await httpClient.get(`/api/projects/${projectId}/quality-checks/latest`)
    return { ...response, data: response?.data ? normalizeQualityResult(response.data) : null }
  },

  async adoptQualitySuggestion(projectId, checkId, issueId) {
    const response = await httpClient.post(`/api/projects/${projectId}/quality-checks/${checkId}/issues/${issueId}/adopt`)
    return { ...response, data: normalizeQualityResult(response?.data) }
  },

  async ignoreQualitySuggestion(projectId, checkId, issueId) {
    const response = await httpClient.post(`/api/projects/${projectId}/quality-checks/${checkId}/issues/${issueId}/ignore`)
    return { ...response, data: normalizeQualityResult(response?.data) }
  },
}

export { normalizeQualityResult }

export default projectQualityApi
