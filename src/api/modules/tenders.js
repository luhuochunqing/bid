// Input: httpClient, tender endpoints, and doc-insight parse endpoint
// Output: tendersApi - tender list, detail, upload, and manual intake parse accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 标讯模块 API
 * 真实 API 为唯一数据源
 */
import httpClient from '../client.js'

function normalizeTags(tags) {
  if (Array.isArray(tags)) {
    return tags
  }
  if (typeof tags === 'string' && tags.trim()) {
    return tags.split(',').map(tag => tag.trim()).filter(Boolean)
  }
  return []
}

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

function normalizeTenderRecord(tender = {}) {
  return {
    ...tender,
    tags: normalizeTags(tender.tags)
  }
}

function isNumericId(id) {
  return /^\d+$/.test(String(id))
}

export const tendersApi = {
  async getList(params = {}) {
    const response = await httpClient.get('/api/tenders', { params: { ...params, size: params.size || 200 } })
    // 兼容两种格式：旧 flat array / 新 PagedResult { content, totalElements }
    const rawData = response?.data
    const list = Array.isArray(rawData) ? rawData
               : rawData?.content ? rawData.content
               : []
    const data = list.map(normalizeTenderRecord)

    return {
      ...response,
      data,
      total: rawData?.totalElements ?? response?.total ?? data.length
    }
  },

  async getDetail(id) {
    if (!isNumericId(id)) {
      return {
        success: false,
        data: null,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }

    return httpClient.get(`/api/tenders/${id}`)
  },

  async create(data) {
    return httpClient.post('/api/tenders', data, withIdempotencyKey())
  },

  async downloadImportTemplate() {
    return httpClient.get('/api/tenders/import-template', {
      responseType: 'blob',
      timeout: 60000
    })
  },

  async bulkImport(file) {
    const formData = new FormData()
    formData.set('file', file, file?.name || 'tender-import.xlsx')
    return httpClient.post('/api/tenders/import', formData, withIdempotencyKey({
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    }))
  },

  async parseTenderIntakeDocument(file, { entityId = 'manual-tender' } = {}) {
    const formData = new FormData()
    formData.set('profile', 'TENDER_INTAKE')
    formData.set('entityId', entityId)
    formData.set('file', file, file?.name || 'manual-tender-document')

    return httpClient.post('/api/doc-insight/parse', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 45000
    })
  },

  async parseTenderIntakeText(text, { entityId = 'manual-tender' } = {}) {
    const file = new File([String(text || '')], '粘贴标讯文本.txt', { type: 'text/plain' })
    return tendersApi.parseTenderIntakeDocument(file, { entityId })
  },

  async update(id, data) {
    if (!isNumericId(id)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }

    return httpClient.put(`/api/tenders/${id}`, data)
  },

  async delete(id) {
    if (!isNumericId(id)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }

    return httpClient.delete(`/api/tenders/${id}`)
  },

  async getAIAnalysis(id) {
    if (!isNumericId(id)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }

    try {
      return await httpClient.get(`/api/tenders/${id}/ai-analysis`)
    } catch (error) {
      if (error?.response?.status !== 404) {
        throw error
      }
      return httpClient.post(`/api/tenders/${id}/ai-analysis`)
    }
  },

  async initUploadSession(data) {
    return httpClient.post('/api/tenders/upload-init', data)
  },

  async completeUpload(data) {
    return httpClient.post('/api/tenders/upload-complete', data)
  },

  /**
   * 上传评估表附件（如项目计划 GAP 相关文件）。
   * @param {number} tenderId - 标讯 ID
   * @param {File} file - 文件对象
   * @returns {Promise}
   */
  async uploadEvaluationDocument(tenderId, file) {
    if (!isNumericId(tenderId)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }
    const formData = new FormData()
    formData.set('file', file, file?.name || 'evaluation-document')
    return httpClient.post(`/api/tenders/${tenderId}/evaluation/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    })
  },

  /**
   * 获取评估表附件列表。
   */
  async getEvaluationDocuments(tenderId) {
    if (!isNumericId(tenderId)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }
    return httpClient.get(`/api/tenders/${tenderId}/evaluation/documents`)
  },

  /**
   * 删除评估表附件。
   */
  async deleteEvaluationDocument(tenderId, documentId) {
    if (!isNumericId(tenderId) || !isNumericId(documentId)) {
      return {
        success: false,
        message: '参数不合法'
      }
    }
    return httpClient.delete(`/api/tenders/${tenderId}/evaluation/documents/${documentId}`)
  },

  async getUploadTaskStatus(taskId) {
    if (!isNumericId(taskId)) {
      return {
        success: false,
        message: '当前后端仅支持数字型任务 ID'
      }
    }
    return httpClient.get(`/api/tenders/tasks/${taskId}`)
  },

  async getEvaluation(tenderId) {
    if (!isNumericId(tenderId)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }
    return httpClient.get(`/api/tenders/${tenderId}/evaluation`)
  },

  /**
   * V119: Load existing evaluation or get a blank DRAFT view from the server.
   * Alias for getEvaluation() that uses an explicit name expected by the
   * detail page.
   */
  async loadEvaluation(tenderId) {
    return tendersApi.getEvaluation(tenderId)
  },

  /**
   * V119: Save evaluation as draft (PUT /api/tenders/{id}/evaluation).
   * Performs no business-required validation server-side.
   */
  async saveEvaluationDraft(tenderId, data) {
    if (!isNumericId(tenderId)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }
    return httpClient.put(`/api/tenders/${tenderId}/evaluation`, data)
  },

  /**
   * V119: Submit evaluation form (POST /api/tenders/{id}/evaluation/submit).
   * Backend runs TenderEvaluationFormPolicy and transitions DRAFT → SUBMITTED.
   */
  async submitEvaluationFinal(tenderId, data) {
    if (!isNumericId(tenderId)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }
    return httpClient.post(`/api/tenders/${tenderId}/evaluation/submit`, data)
  },

  /**
   * @deprecated V118 entry point removed by V119 cleanup. Use
   * {@link saveEvaluationDraft} + {@link submitEvaluationFinal} on the detail
   * page instead. Retained only as a soft alias to keep transient callers
   * from blowing up; it will be removed in a follow-up.
   */
  async submitEvaluation(tenderId, data) {
    return tendersApi.submitEvaluationFinal(tenderId, data)
  },

  async reviewTender(tenderId, data) {
    if (!isNumericId(tenderId)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }
    return httpClient.post(`/api/tenders/${tenderId}/review`, data)
  },

  async proceedToBid(tenderId) {
    if (!isNumericId(tenderId)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }
    return httpClient.post(`/api/tenders/${tenderId}/bid`)
  },

  async participate(id) {
    return httpClient.post(`/api/tenders/${id}/participate`)
  },

  async abandon(id, data) {
    return httpClient.post(`/api/tenders/${id}/abandon`, data)
  },

  /**
   * 转派标讯给新项目负责人。
   * @param {number} id 标讯 ID
   * @param {Object} payload - { newOwnerId }
   * @returns {Promise}
   */
  async transferTender(id, payload) {
    if (!isNumericId(id)) {
      return {
        success: false,
        message: '当前后端仅支持数字型标讯 ID'
      }
    }
    return httpClient.post(`/api/tenders/${id}/transfer`, payload)
  }
}

export default tendersApi
