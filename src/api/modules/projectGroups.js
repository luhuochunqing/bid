// Input: httpClient, API mode config, project-group payload normalizers and fallback snapshots
// Output: projectGroupsApi - admin project-group configuration accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const fallbackPayload = {
  projectGroups: [],
  userOptions: []
}

const normalizeLongIds = (values) => {
  if (!Array.isArray(values)) {
    return []
  }

  return [...new Set(
    values
      .map((value) => Number(value))
      .filter((value) => Number.isFinite(value))
  )].sort((left, right) => left - right)
}

const normalizeRoles = (roles) => {
  if (!Array.isArray(roles)) {
    return []
  }

  return [...new Set(roles.map((role) => String(role || '').trim()).filter(Boolean))]
}

const normalizeProjectGroupRow = (row = {}) => ({
  id: row.id ?? null,
  groupCode: row.groupCode || '',
  groupName: row.groupName || '',
  managerUserId: row.managerUserId ?? null,
  manager: row.manager || '',
  memberCount: Number.isFinite(Number(row.memberCount)) ? Number(row.memberCount) : 0,
  visibility: row.visibility || 'members',
  memberUserIds: normalizeLongIds(row.memberUserIds),
  allowedRoles: normalizeRoles(row.allowedRoles),
  projectIds: normalizeLongIds(row.projectIds)
})

const normalizeUserOption = (option = {}) => ({
  id: option.id,
  name: option.name || '',
  role: option.role || '',
  deptCode: option.deptCode || '',
  dept: option.dept || ''
})

const normalizePayload = (payload = fallbackPayload) => ({
  projectGroups: Array.isArray(payload.projectGroups)
    ? payload.projectGroups.map(normalizeProjectGroupRow)
    : [],
  userOptions: Array.isArray(payload.userOptions)
    ? payload.userOptions.map(normalizeUserOption)
    : []
})

export const projectGroupsApi = {
  async getProjectGroups() {

    const response = await httpClient.get('/api/admin/project-groups')
    return {
      ...response,
      data: normalizePayload(response?.data)
    }
  },

  async saveProjectGroups(payload) {
    const normalizedPayload = normalizePayload(payload)

    const response = await httpClient.put('/api/admin/project-groups', normalizedPayload)
    return {
      ...response,
      data: normalizePayload(response?.data)
    }
  },

  async createProjectGroup(payload) {
    const normalizedItem = normalizeProjectGroupRow(payload)

    const response = await httpClient.post('/api/admin/project-groups', normalizedItem)
    return {
      ...response,
      data: normalizeProjectGroupRow(response?.data)
    }
  },

  async updateProjectGroup(id, payload) {
    const numericId = Number(id)
    const normalizedItem = normalizeProjectGroupRow(payload)
    if (!Number.isFinite(numericId)) {
      return {
        success: false,
        message: 'Invalid project group id'
      }
    }


    const response = await httpClient.patch(`/api/admin/project-groups/${numericId}`, normalizedItem)
    return {
      ...response,
      data: normalizeProjectGroupRow(response?.data)
    }
  },

  async deleteProjectGroup(id) {
    const numericId = Number(id)
    if (!Number.isFinite(numericId)) {
      return {
        success: false,
        message: 'Invalid project group id'
      }
    }


    return httpClient.delete(`/api/admin/project-groups/${numericId}`)
  }
}

export default projectGroupsApi
