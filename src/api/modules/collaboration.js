// Input: httpClient, API mode config, collaboration feature placeholders
// Output: collaborationApi - thread, comment, version, and document collaboration accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 协作与文档模块 API
 * 真实 API 协作与文档访问层
 */
import httpClient from '../client.js'

function isNumericId(id) {
  return /^\d+$/.test(String(id))
}

function invalidIdMessage(entityName) {
  return {
    success: false,
    message: `Current backend only supports numeric ${entityName} IDs in API mode` }
}

function failureForInvalidId(entityName) {
  return Promise.resolve(invalidIdMessage(entityName))
}

function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

function normalizeThread(item = {}) {
  if (item.title || item.createdAt || item.updatedAt) {
    return {
      id: item.id,
      projectId: item.projectId,
      title: item.title || '未命名讨论',
      type: 'thread',
      status: String(item.status || 'OPEN').toLowerCase(),
      author: item.createdBy ? `用户#${item.createdBy}` : '未知用户',
      avatar: item.createdBy ? String(item.createdBy).slice(-2) : '协',
      content: item.title || '',
      timestamp: formatDateTime(item.updatedAt || item.createdAt),
      resolved: String(item.status || '').toUpperCase() === 'RESOLVED',
      replies: [] }
  }

  return {
    id: item.id,
    projectId: item.projectId,
    title: item.content || '协作记录',
    type: item.type || 'comment',
    status: item.resolved ? 'resolved' : 'open',
    author: item.author || '未知用户',
    avatar: item.avatar || String(item.author || '协').slice(0, 1),
    content: item.content || '',
    timestamp: item.time || '',
    resolved: Boolean(item.resolved),
    replies: Array.isArray(item.replies) ? item.replies : [],
    position: item.position || null }
}

function normalizeVersion(item = {}) {
  if (item.versionNumber !== undefined || item.changeSummary !== undefined) {
    return {
      id: item.id,
      version: String(item.versionNumber || ''),
      isCurrent: Boolean(item.isCurrent),
      timestamp: formatDateTime(item.createdAt),
      author: item.createdBy ? `用户#${item.createdBy}` : '未知用户',
      avatar: item.createdBy ? String(item.createdBy).slice(-2) : '版',
      changes: item.changeSummary ? [item.changeSummary] : ['暂无变更摘要'],
      content: item.content || '' }
  }

  return {
    id: item.id,
    version: item.version,
    isCurrent: Boolean(item.isCurrent),
    timestamp: item.timestamp || '',
    author: item.author || '未知用户',
    avatar: item.avatar || '版',
    changes: Array.isArray(item.changes) ? item.changes : [],
    content: item.content || '' }
}

function normalizeSection(item = {}) {
  return {
    id: item.id,
    chapter: item.name || item.title || '未命名章节',
    owner: item.owner || '',
    assignedBy: item.assignedBy || null,
    status: item.status || (item.content ? 'editing' : 'pending'),
    dueDate: item.dueDate || '',
    locked: Boolean(item.locked),
    lockedBy: item.lockedBy || null,
    lockedAt: item.lockedAt || '',
    children: Array.isArray(item.children) ? item.children.map(normalizeSection) : [] }
}

function normalizeVersionCompare(data, versions) {
  if (data?.differences) {
    const versionMap = new Map(versions.map((item) => [String(item.id), item]))
    const oldVersion = versionMap.get(String(data.version1Id)) || null
    const newVersion = versionMap.get(String(data.version2Id)) || null

    return {
      version1: oldVersion,
      version2: newVersion,
      differences: Array.isArray(data.differences) ? data.differences : [],
      content1: data.content1 || '',
      content2: data.content2 || '' }
  }

  return data
}

function normalizeEditorSection(item = {}) {
  const children = Array.isArray(item.children) ? item.children.map(normalizeEditorSection) : []
  const normalizedType = item.type || (children.length > 0 ? 'folder' : 'section')

  return {
    id: item.id,
    apiId: item.apiId ?? item.id,
    structureId: item.structureId ?? null,
    parentId: item.parentId ?? null,
    sectionType: item.sectionType || null,
    name: item.name || item.title || '未命名章节',
    type: normalizedType,
    content: item.content || '',
    orderIndex: item.orderIndex ?? 0,
    metadata: item.metadata || '',
    owner: item.owner || '',
    dueDate: item.dueDate || '',
    locked: Boolean(item.locked),
    assignedBy: item.assignedBy || null,
    lockedBy: item.lockedBy || null,
    lockedAt: item.lockedAt || '',
    children }
}

function normalizeApiEditorSection(item = {}) {
  const children = Array.isArray(item.children) ? item.children.map(normalizeApiEditorSection) : []
  return normalizeEditorSection({
    id: String(item.id),
    apiId: item.id,
    structureId: item.structureId ?? null,
    parentId: item.parentId ?? null,
    sectionType: item.sectionType || 'SECTION',
    name: item.title || '未命名章节',
    type: children.length > 0 ? 'folder' : 'section',
    content: item.content || '',
    orderIndex: item.orderIndex ?? 0,
    metadata: item.metadata || '',
    owner: item.owner || '',
    dueDate: item.dueDate || '',
    locked: Boolean(item.locked),
    assignedBy: item.assignedBy || null,
    lockedBy: item.lockedBy || null,
    lockedAt: item.lockedAt || '',
    children })
}

export const collaborationApi = {
  async getThreads(params = {}) {

    if (!isNumericId(params.projectId)) {
      return failureForInvalidId('project')
    }

    const response = await httpClient.get('/api/collaboration/threads', {
      params: { projectId: params.projectId } })

    const apiData = Array.isArray(response?.data) ? response.data.map(normalizeThread) : []

    return {
      ...response,
      data: apiData }
  },

  async getThread(id) {
    if (!isNumericId(id)) return Promise.resolve(invalidIdMessage('thread'))

    const response = await httpClient.get(`/api/collaboration/threads/${id}`)
    return { ...response, data: normalizeThread(response?.data) }
  },

  async createThread(data) {
    if (!isNumericId(data?.projectId)) return Promise.resolve(invalidIdMessage('project'))

    return httpClient.post('/api/collaboration/threads', data)
  },

  async addComment(threadId, payload) {
    if (!isNumericId(threadId)) return Promise.resolve(invalidIdMessage('thread'))

    return httpClient.post(`/api/collaboration/threads/${threadId}/comments`, payload)
  },

  async getMentions(userId) {
    if (!isNumericId(userId)) return Promise.resolve(invalidIdMessage('user'))

    return httpClient.get('/api/collaboration/mentions', { params: { userId } })
  } }

export const calendarApi = {
  async getEvents(params) {
    return httpClient.get('/api/calendar', { params })
  },

  async getMonthEvents(year, month) {
    return httpClient.get(`/api/calendar/month/${year}/${month}`)
  },

  async getUrgentEvents() {
    return httpClient.get('/api/calendar/urgent')
  },

  async createEvent(data) {
    return httpClient.post('/api/calendar', data)
  } }

export const documentVersionsApi = {
  async getVersions(projectId) {
    if (!isNumericId(projectId)) {
      return failureForInvalidId('project')
    }

    const response = await httpClient.get(`/api/documents/${projectId}/versions`)
    const apiData = Array.isArray(response?.data) ? response.data.map(normalizeVersion) : []
    return {
      ...response,
      data: apiData }
  },

  async compare(projectId, version1Id, version2Id) {
    if (!isNumericId(projectId) || !isNumericId(version1Id) || !isNumericId(version2Id)) {
      return failureForInvalidId('project')
    }

    const versionsResponse = await documentVersionsApi.getVersions(projectId)
    const versionList = Array.isArray(versionsResponse?.data) ? versionsResponse.data : []
    const response = await httpClient.get(`/api/documents/${projectId}/versions/${version1Id}/compare/${version2Id}`)
    return {
      ...response,
      data: normalizeVersionCompare(response?.data, versionList) }
  },

  async rollback(projectId, versionId, userId) {
    if (!isNumericId(projectId) || !isNumericId(versionId) || !isNumericId(userId)) {
      return failureForInvalidId('project')
    }

    return httpClient.post(`/api/documents/${projectId}/versions/${versionId}/rollback`, null, {
      params: { userId } })
  },

  async createVersion(projectId, data) {
    if (!isNumericId(projectId)) {
      return failureForInvalidId('project')
    }

    return httpClient.post(`/api/documents/${projectId}/versions`, {
      ...data,
      projectId: Number(projectId) })
  } }

export const documentEditorApi = {
  async getEditorDocument(projectId) {
    if (!isNumericId(projectId)) {
      return failureForInvalidId('project')
    }

    const [structureResponse, treeResponse] = await Promise.all([
      documentEditorApi.getStructure(projectId),
      documentEditorApi.getTree(projectId),
    ])

    const sections = Array.isArray(treeResponse?.data)
      ? treeResponse.data.map(normalizeApiEditorSection)
      : []

    return {
      success: true,
      data: {
        structureId: structureResponse?.data?.id ?? null,
        projectId: Number(projectId),
        projectName: '',
        templateId: structureResponse?.data?.name || 'API_DOCUMENT',
        templateName: structureResponse?.data?.name || '文档结构',
        sections } }
  },

  async getStructure(projectId) {
    if (!isNumericId(projectId)) {
      return failureForInvalidId('project')
    }

    return httpClient.get(`/api/documents/${projectId}/editor/structure`)
  },

  async createStructure(projectId, data) {
    if (!isNumericId(projectId)) return Promise.resolve(invalidIdMessage('project'))

    return httpClient.post(`/api/documents/${projectId}/editor/structure`, {
      projectId: Number(projectId),
      name: data?.name || '文档结构' })
  },

  async createSection(projectId, data) {
    if (!isNumericId(projectId) || !isNumericId(data?.structureId)) return Promise.resolve(invalidIdMessage('project/structure'))

    const response = await httpClient.post(`/api/documents/${projectId}/editor/sections`, {
      ...data,
      structureId: Number(data.structureId),
      parentId: data?.parentId ? Number(data.parentId) : null })

    return {
      ...response,
      data: normalizeApiEditorSection(response?.data) }
  },

  async updateSection(projectId, sectionId, data) {
    if (!isNumericId(projectId) || !isNumericId(sectionId)) return Promise.resolve(invalidIdMessage('project/section'))

    const response = await httpClient.put(`/api/documents/${projectId}/editor/sections/${sectionId}`, data)
    return {
      ...response,
      data: normalizeApiEditorSection(response?.data) }
  },

  async deleteSection(projectId, sectionId) {
    if (!isNumericId(projectId) || !isNumericId(sectionId)) return Promise.resolve(invalidIdMessage('project/section'))

    return httpClient.delete(`/api/documents/${projectId}/editor/sections/${sectionId}`)
  },

  async reorderSections(projectId, data) {
    if (!isNumericId(projectId) || !isNumericId(data?.structureId)) return Promise.resolve(invalidIdMessage('project/structure'))

    const normalizedOrders = Object.fromEntries(
      Object.entries(data?.sectionOrders || {}).map(([sectionId, orderIndex]) => [Number(sectionId), orderIndex]),
    )

    return httpClient.put(`/api/documents/${projectId}/editor/sections/reorder`, {
      structureId: Number(data.structureId),
      sectionOrders: normalizedOrders })
  },

  async assignSection(projectId, data) {
    if (!isNumericId(projectId) || !isNumericId(data?.sectionId) || !isNumericId(data?.assignedBy)) {
      return Promise.resolve(invalidIdMessage('project/section/user'))
    }

    return httpClient.post(`/api/documents/${projectId}/editor/assignments`, {
      ...data,
      sectionId: Number(data.sectionId),
      assignedBy: Number(data.assignedBy) })
  },

  async updateLock(projectId, data) {
    if (!isNumericId(projectId) || !isNumericId(data?.sectionId) || !isNumericId(data?.userId)) {
      return Promise.resolve(invalidIdMessage('project/section/user'))
    }

    return httpClient.post(`/api/documents/${projectId}/editor/locks`, {
      ...data,
      sectionId: Number(data.sectionId),
      userId: Number(data.userId) })
  },

  async createReminder(projectId, data) {
    if (!isNumericId(projectId) || !isNumericId(data?.sectionId) || !isNumericId(data?.remindedBy)) {
      return Promise.resolve(invalidIdMessage('project/section/user'))
    }

    return httpClient.post(`/api/documents/${projectId}/editor/reminders`, {
      ...data,
      sectionId: Number(data.sectionId),
      remindedBy: Number(data.remindedBy) })
  },

  async getTree(projectId) {
    if (!isNumericId(projectId)) {
      return failureForInvalidId('project')
    }

    const response = await httpClient.get(`/api/documents/${projectId}/editor/sections/tree`)
    const apiData = Array.isArray(response?.data) ? response.data.map(normalizeSection) : []
    return {
      ...response,
      data: apiData }
  },

  async getEditorTree(projectId) {
    if (!isNumericId(projectId)) {
      return failureForInvalidId('project')
    }

    const response = await httpClient.get(`/api/documents/${projectId}/editor/sections/tree`)
    const apiData = Array.isArray(response?.data) ? response.data.map(normalizeApiEditorSection) : []
    return {
      ...response,
      data: apiData }
  } }

export const documentExportApi = {
  async getExports(projectId) {
    if (!isNumericId(projectId)) {
      return failureForInvalidId('project')
    }

    return httpClient.get(`/api/documents/${projectId}/exports`)
  },

  async createExport(projectId, data = {}) {
    if (!isNumericId(projectId)) return Promise.resolve(invalidIdMessage('project'))

    return httpClient.post(`/api/documents/${projectId}/exports`, {
      format: data.format || 'json',
      exportedBy: data.exportedBy ?? null,
      exportedByName: data.exportedByName || '当前用户' })
  },

  async getArchiveRecords(projectId) {
    if (!isNumericId(projectId)) {
      return failureForInvalidId('project')
    }

    return httpClient.get(`/api/documents/${projectId}/archive-records`)
  },

  async archive(projectId, data = {}) {
    if (!isNumericId(projectId)) return Promise.resolve(invalidIdMessage('project'))

    return httpClient.post(`/api/documents/${projectId}/archive`, {
      archivedBy: data.archivedBy ?? null,
      archivedByName: data.archivedByName || '当前用户',
      archiveReason: data.archiveReason || '项目资料归档' })
  } }

export const documentAssemblyApi = {
  async getTemplates(category) {
    if (category) {
      return httpClient.get('/api/documents/assembly/templates', {
        params: { category } })
    }

    const categories = ['TECHNICAL', 'COMMERCIAL', 'QUALIFICATION', 'CONTRACT', 'OTHER']
    const responses = await Promise.all(
      categories.map((item) => httpClient.get('/api/documents/assembly/templates', {
        params: { category: item } })),
    )

    return {
      ...responses[0],
      data: responses.flatMap((response) => Array.isArray(response?.data) ? response.data : []) }
  },

  async getConfig(projectId) {
    if (!isNumericId(projectId)) return Promise.resolve(invalidIdMessage('project'))

    return httpClient.get(`/api/documents/assembly/${projectId}`)
  },

  async getAssemblies(projectId) {
    return documentAssemblyApi.getConfig(projectId)
  },

  async assemble(data) {
    if (!isNumericId(data?.projectId)) return Promise.resolve(invalidIdMessage('project'))

    return httpClient.post(`/api/documents/assembly/${data.projectId}/assemble`, data)
  },

  async assembleDocument(projectId, data = {}) {
    if (!isNumericId(projectId)) return Promise.resolve(invalidIdMessage('project'))

    return httpClient.post(`/api/documents/assembly/${projectId}/assemble`, {
      templateId: data.templateId,
      variables: data.variables || '',
      assembledBy: data.assembledBy ?? null })
  },

  async createTemplate(data = {}) {
    return httpClient.post('/api/documents/assembly/templates', data)
  },

  async updateTemplate() {
    return Promise.resolve({
      success: false,
      message: 'Template update is not available in the current API'
    })
  },

  async deleteTemplate() {
    return Promise.resolve({
      success: false,
      message: 'Template delete is not available in the current API'
    })
  } }

export default {
  collaboration: collaborationApi,
  calendar: calendarApi,
  versions: documentVersionsApi,
  editor: documentEditorApi,
  exports: documentExportApi,
  assembly: documentAssemblyApi }
