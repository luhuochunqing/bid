// Input: httpClient, API mode config, project normalizers and demo adapters
// Output: projectsApi — project list, detail, task decomposition, and lifecycle accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 项目模块 API
 * 真实 API 项目访问层
 */
import httpClient from '../client.js'
import { apiModeFailure, demoReadonlyFailure, isDemoEntityId, isNumericId } from './projectApiGuards.js'
import * as tenderBreakdownApi from './projectTenderBreakdown.js'
import * as projectDocumentsApi from './projectDocuments.js'
function matchesProjectStatus(projectStatus, filterStatus) {
  return String(projectStatus || '').toLowerCase() === String(filterStatus || '').toLowerCase()
}

function applyProjectFilters(projects, params = {}) {
  return projects.filter((project) => {
    const statusArr = [].concat(params.bidStatus || params.status || []).filter(Boolean)
    if (statusArr.length && !statusArr.some(s => matchesProjectStatus(project.bidStatus || project.status, s))) return false
    const stageArr = [].concat(params.stage || []).filter(Boolean)
    if (stageArr.length && !stageArr.some(s => matchesProjectStatus(project.stage, s))) return false

    if (params.managerId && String(project.managerId) !== String(params.managerId)) {
      return false
    }

    if (params.tenderId && String(project.tenderId) !== String(params.tenderId)) {
      return false
    }

    if (params.name) {
      const keyword = String(params.name).trim().toLowerCase()
      if (!String(project.name || '').toLowerCase().includes(keyword)) {
        return false
      }
    }

    return true
  })
}
function normalizeScoreDraft(draft = {}) {
  const deliverables = Array.isArray(draft.suggestedDeliverables)
    ? draft.suggestedDeliverables
    : (() => {
        if (typeof draft.suggestedDeliverables !== 'string' || draft.suggestedDeliverables.trim() === '') {
          return []
        }
        try {
          const parsed = JSON.parse(draft.suggestedDeliverables)
          return Array.isArray(parsed) ? parsed : []
        } catch {
          return []
        }
      })()

  const dueDate = typeof draft.dueDate === 'string' && draft.dueDate.length >= 10
    ? draft.dueDate.slice(0, 10) + 'T00:00:00'
    : draft.dueDate || ''

  return {
    ...draft,
    category: draft.category || 'unknown',
    suggestedDeliverables: deliverables,
    dueDate,
    status: draft.status || 'DRAFT',
    sourceFileName: draft.sourceFileName || '',
    sourceTableIndex: Number.isFinite(Number(draft.sourceTableIndex)) ? Number(draft.sourceTableIndex) : null,
    sourceRowIndex: Number.isFinite(Number(draft.sourceRowIndex)) ? Number(draft.sourceRowIndex) : null }
}

function normalizeScoreDraftList(drafts = []) {
  return Array.isArray(drafts) ? drafts.map((draft) => normalizeScoreDraft(draft)) : []
}

export const projectsApi = {
  /**
   * 获取项目列表
   */
  async getList(params) {
    const response = await httpClient.get('/api/projects')
    const projects = Array.isArray(response?.data) ? response.data : []
    const data = applyProjectFilters(projects, params)

    return {
      ...response,
      data,
      total: data.length
    }
  },

  /**
   * 获取项目详情
   */
  async getDetail(id) {
    if (!isNumericId(id)) {
      return apiModeFailure('project')
    }

    const response = await httpClient.get(`/api/projects/${id}`)
    return {
      ...response,
      data: response?.data ?? null
    }
  },

  /**
   * 创建项目
   */
  async create(data) {
    return httpClient.post('/api/projects', data)
  },

  /**
   * 更新项目
   */
  async update(id, data) {
    if (!isNumericId(id)) {
      return apiModeFailure('project')
    }

    if (isDemoEntityId(id)) {
      return demoReadonlyFailure()
    }

    return httpClient.put(`/api/projects/${id}`, data)
  },

  /**
   * 删除项目
   */
  async delete(id) {
    if (!isNumericId(id)) {
      return apiModeFailure('project')
    }

    if (isDemoEntityId(id)) {
      return demoReadonlyFailure()
    }

    return httpClient.delete(`/api/projects/${id}`)
  },

  /**
   * 获取项目任务
   */
  async getTasks(projectId) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    if (isDemoEntityId(projectId)) {
      return { success: true, data: [] }
    }

    return httpClient.get(`/api/tasks/project/${projectId}`)
  },

  async createTask(projectId, data) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    if (isDemoEntityId(projectId)) {
      return demoReadonlyFailure()
    }

    return httpClient.post('/api/tasks', { ...data, projectId })
  },

  async decomposeTasks(projectId, payload) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    if (isDemoEntityId(projectId)) {
      return demoReadonlyFailure()
    }

    return httpClient.post(`/api/projects/${projectId}/tasks/decompose`, payload, { silentError: true })
  },

  ...tenderBreakdownApi,

  async updateTask(taskId, dto) {
    return httpClient.put(`/api/tasks/${taskId}`, dto)
  },

  async updateTaskStatus(projectId, taskId, status, reviewComment, completionNotes) {
    if (!isNumericId(projectId) || !isNumericId(taskId)) {
      return apiModeFailure('task')
    }

    if (isDemoEntityId(projectId) || isDemoEntityId(taskId)) {
      return demoReadonlyFailure()
    }

    const payload = { status }
    if (reviewComment) payload.reviewComment = reviewComment
    if (completionNotes) payload.completionNotes = completionNotes
    return httpClient.patch(`/api/projects/${projectId}/tasks/${taskId}/status`, payload)
  },

  /**
   * 获取项目文档
   * @deprecated 请使用 projectDocumentsApi.getDocuments() 替代
   */
  async getDocuments(projectId, params = {}) {
    return projectDocumentsApi.getDocuments(projectId, params)
  },

  /**
   * 上传文档
   * @deprecated 请使用 projectDocumentsApi.uploadDocument() 替代
   */
  async uploadDocument(projectId, formData) {
    return projectDocumentsApi.uploadDocument(projectId, formData)
  },

  /**
   * @deprecated 请使用 projectDocumentsApi.deleteDocument() 替代
   */
  async deleteDocument(projectId, documentId) {
    return projectDocumentsApi.deleteDocument(projectId, documentId)
  },

  /**
   * @deprecated 请使用 projectDocumentsApi.getDocumentDownloadUrl() 替代
   */
  getDocumentDownloadUrl(projectId, documentId) {
    return projectDocumentsApi.getDocumentDownloadUrl(projectId, documentId)
  },

  async createReminder(projectId, data) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    if (isDemoEntityId(projectId)) {
      return demoReadonlyFailure()
    }

    return httpClient.post(`/api/projects/${projectId}/reminders`, data)
  },

  async createShareLink(projectId, data) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    if (isDemoEntityId(projectId)) {
      return demoReadonlyFailure()
    }

    return httpClient.post(`/api/projects/${projectId}/share-links`, data)
  },

  async parseScoreDrafts(projectId, formData) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    return httpClient.post(`/api/projects/${projectId}/score-drafts/parse`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    }).then((response) => ({
      ...response,
      data: response?.data
        ? {
            ...response.data,
            drafts: normalizeScoreDraftList(response.data.drafts)
          }
        : response?.data
    }))
  },

  async getScoreDrafts(projectId) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    return httpClient.get(`/api/projects/${projectId}/score-drafts`).then((response) => ({
      ...response,
      data: normalizeScoreDraftList(Array.isArray(response?.data) ? response.data : [])
    }))
  },

  async updateScoreDraft(projectId, draftId, payload) {
    if (!isNumericId(projectId) || !isNumericId(draftId)) {
      return apiModeFailure('project score draft')
    }

    return httpClient.patch(`/api/projects/${projectId}/score-drafts/${draftId}`, payload).then((response) => ({
      ...response,
      data: response?.data ? normalizeScoreDraft(response.data) : response?.data
    }))
  },

  async generateScoreDraftTasks(projectId, draftIds) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    return httpClient.post(`/api/projects/${projectId}/score-drafts/generate-tasks`, { draftIds })
  },

  async clearScoreDrafts(projectId) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    return httpClient.delete(`/api/projects/${projectId}/score-drafts`)
  },

  // --- Bid submission endpoints ---

  async submitToBidDocument(projectId) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    return httpClient.post(`/api/projects/${projectId}/submit-to-bid-document`)
  },

  async getBidProcessStatus(projectId) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    return httpClient.get(`/api/projects/${projectId}/bid-process-status`)
  },

  // --- Collaboration / Member management ---

  async getMembers(projectId) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    return httpClient.get(`/api/projects/${projectId}/members`)
  },

  async addMember(projectId, data) {
    if (!isNumericId(projectId)) {
      return apiModeFailure('project')
    }

    return httpClient.post(`/api/projects/${projectId}/members`, data)
  },

  async removeMember(projectId, userId) {
    if (!isNumericId(projectId) || !isNumericId(userId)) {
      return apiModeFailure('project member')
    }

    return httpClient.delete(`/api/projects/${projectId}/members/${userId}`)
  }
}

export default projectsApi
