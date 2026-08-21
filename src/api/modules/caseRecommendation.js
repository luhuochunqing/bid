// Input: httpClient, projectId, scoreItemId, keyword, and caseId
// Output: caseRecommendationApi - recommendCases, getScoreItems, getCaseDetail, reuseCase
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

export const caseRecommendationApi = {
  /**
   * 基于评分项与关键词推荐案例
   * POST /api/projects/{projectId}/cases/recommend
   * 注：后端直接返回裸 {total, items}（不走统一信封），
   * client.js 拦截器已解包 response.data，此处不可再取 .data
   */
  async recommendCases(projectId, params = {}) {
    return httpClient.post(`/api/projects/${projectId}/cases/recommend`, {
      scoreItemId: params.scoreItemId || null,
      keyword: params.keyword || '',
      page: params.page || 0,
      size: params.size || 10
    })
  },

  /**
   * 获取当前项目的评分项列表 (PRD 4.2 契约)
   * GET /api/projects/{projectId}/score-items
   * 注：后端直接返回裸数组（不走统一信封），
   * client.js 拦截器已解包 response.data，此处不可再取 .data
   */
  async getScoreItems(projectId) {
    return httpClient.get(`/api/projects/${projectId}/score-items`)
  },

  /**
   * 获取案例详情
   * GET /api/cases/{caseId}
   */
  async getCaseDetail(caseId) {
    return httpClient.get(`/api/cases/${caseId}`).then(res => res.data)
  },

  /**
   * 复用案例并记录
   * POST /api/cases/{caseId}/reuse
   */
  async reuseCase(caseId, data = {}) {
    return httpClient.post(`/api/cases/${caseId}/reuse`, data).then(res => res.data)
  }
}
