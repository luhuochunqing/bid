// Input: httpClient and tender batch endpoints
// Output: batchTendersApi - batch claim, assign, status, and assignment accessors
// Pos: src/api/modules/tenders/ - Frontend tender batch API layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../../client.js'

function generateIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function withIdempotencyKey(config = {}) {
  return {
    ...config,
    headers: {
      ...(config.headers || {}),
      'Idempotency-Key': generateIdempotencyKey()
    }
  }
}

function normalizeBatchResponse(response = {}) {
  const payload = response?.data || {}
  const successCount = Number(payload?.successCount || 0)
  const failureCount = Number(payload?.failureCount || 0)
  const partialSuccess = successCount > 0 && failureCount > 0

  return {
    ...response,
    success: payload?.success !== false,
    partialSuccess,
    data: {
      ...payload,
      successIds: Array.isArray(payload?.successIds) ? payload.successIds : [],
      errors: Array.isArray(payload?.errors) ? payload.errors : [],
    },
  }
}

export const batchTendersApi = {
  async batchClaim(tenderIds) {
    const response = await httpClient.post('/api/batch/tenders/claim', {
      itemIds: tenderIds,
      itemType: 'tender',
    }, withIdempotencyKey())
    return normalizeBatchResponse(response)
  },

  async batchAssign(tenderIds, assigneeId, remark = '') {
    const response = await httpClient.post('/api/batch/tenders/assign', {
      tenderIds,
      assigneeId: Number(assigneeId),
      remark,
    }, withIdempotencyKey())
    return normalizeBatchResponse(response)
  },

  async batchUpdateStatus(tenderIds, status) {
    const response = await httpClient.patch('/api/batch/tenders/status', {
      tenderIds,
      status,
    }, withIdempotencyKey())
    return normalizeBatchResponse(response)
  },

  async getAssignmentRecords(tenderId) {
    const response = await httpClient.get(`/api/tenders/${tenderId}/assignment`)
    return {
      ...response,
      data: response?.data || { latest: null, history: [] },
    }
  },

  async getAssignmentCandidates() {
    const response = await httpClient.get('/api/tenders/assignment-candidates')
    return {
      ...response,
      data: Array.isArray(response?.data) ? response.data : [],
    }
  },
}

export default batchTendersApi
