// Input: httpClient and case slice request parameters (projectId, scoringItemId, query, topK)
// Output: caseSlicesApi - thin wrappers for /api/case-slices/recommend, /api/case-slices/recommend/by-query, /api/case-slices/{id}, /api/case-slices/admin/stats endpoints
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

function normalizeRecommendation(item) {
  return {
    id: item.id,
    projectDir: item.projectDir || '',
    projectIdx: item.projectIdx || 0,
    docxFile: item.docxFile || '',
    docxLabel: item.docxLabel || '',
    sectionIdx: item.sectionIdx || 0,
    level: item.level || 1,
    title: item.title || '',
    textLength: item.textLength || 0,
    textPreview: item.textPreview || '',
    paraCount: item.paraCount || 0,
    similarity: item.similarity || 0,
    embedding: item.embedding || null,
    createdAt: item.createdAt || '',
    updatedAt: item.updatedAt || ''
  }
}

export const caseSlicesApi = {
  recommendByScoringItem(projectId, scoringItemId, topK = 10) {
    return httpClient.get('/api/case-slices/recommend', {
      params: { projectId, scoringItemId, topK }
    }).then(res => res.data)
  },

  recommendByQuery(query, topK = 10) {
    return httpClient.get('/api/case-slices/recommend/by-query', {
      params: { query, topK }
    }).then(res => res.data)
  },

  getSliceDetail(id, projectId) {
    return httpClient.get(`/api/case-slices/${id}`, {
      params: { projectId }
    }).then(res => res.data)
  },

  getStats() {
    return httpClient.get('/api/case-slices/admin/stats').then(res => res.data)
  }
}