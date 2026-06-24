// Input: httpClient, API mode config, settings payload normalizers and runtime permission snapshots
// Output: settingsApi - admin settings accessors for data-scope, role-menu and AI model configuration
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

// 保留 roleCode 原始大小写：OSS 角色码大小写敏感（如 bidAdmin、bid-TeamLeader）
const normalizeRole = (role) => String(role || '').trim()
const normalizePermissionList = (permissions) => (
  Array.isArray(permissions)
    ? [...new Set(permissions.map((item) => String(item || '').trim()).filter(Boolean))]
    : []
)

let runtimeSettings = null

const fallbackConfig = {
  userDataScope: [],
  deptDataScope: [],
  deptOptions: [],
  deptTree: [],
  userOptions: [],
  users: [],
  roles: []
}

const normalizeAllowedProjects = (allowedProjects) => {
  if (!Array.isArray(allowedProjects)) {
    return []
  }

  return [...new Set(
    allowedProjects
      .map((projectId) => Number(projectId))
      .filter((projectId) => Number.isFinite(projectId))
  )].sort((left, right) => left - right)
}

const normalizeAllowedDepts = (allowedDepts) => {
  if (!Array.isArray(allowedDepts)) {
    return []
  }

  return [...new Set(
    allowedDepts
      .map((deptCode) => String(deptCode || '').trim())
      .filter(Boolean)
  )]
}

const normalizeUserRow = (row = {}) => ({
  userId: row.userId,
  userName: row.userName || '',
  deptCode: row.deptCode || '',
  dept: row.dept || '',
  role: row.role || '',
  dataScope: row.dataScope || 'self',
  allowedProjects: normalizeAllowedProjects(row.allowedProjects),
  allowedDepts: normalizeAllowedDepts(row.allowedDepts)
})

const normalizeDeptRow = (row = {}) => ({
  deptCode: row.deptCode || '',
  deptName: row.deptName || '',
  dataScope: row.dataScope || 'self',
  canViewOtherDepts: Boolean(row.canViewOtherDepts),
  allowedDepts: normalizeAllowedDepts(row.allowedDepts)
})

const normalizeDeptOption = (option = {}) => ({
  code: option.code || '',
  name: option.name || ''
})

const normalizeDeptTreeItem = (item = {}) => ({
  deptCode: item.deptCode || '',
  deptName: item.deptName || '',
  parentDeptCode: item.parentDeptCode || '',
  sortOrder: Number.isFinite(Number(item.sortOrder)) ? Number(item.sortOrder) : 0
})

const normalizeConfig = (payload = fallbackConfig) => ({
  userDataScope: Array.isArray(payload.userDataScope) ? payload.userDataScope.map(normalizeUserRow) : [],
  deptDataScope: Array.isArray(payload.deptDataScope) ? payload.deptDataScope.map(normalizeDeptRow) : [],
  deptOptions: Array.isArray(payload.deptOptions) ? payload.deptOptions.map(normalizeDeptOption) : [],
  deptTree: Array.isArray(payload.deptTree) ? payload.deptTree.map(normalizeDeptTreeItem) : [],
  userOptions: Array.isArray(payload.userOptions) ? payload.userOptions.map((user) => ({
    id: user?.id,
    name: user?.name || '',
    roleId: user?.roleId ?? null,
    role: normalizeRole(user?.roleCode || user?.role),
    roleName: user?.roleName || '',
    deptCode: user?.deptCode || '',
    dept: user?.dept || ''
  })) : [],
  users: Array.isArray(payload.users) ? payload.users.map((user) => ({
    id: user?.id,
    username: user?.username || '',
    fullName: user?.fullName || '',
    email: user?.email || '',
    phone: user?.phone || '',
    departmentCode: user?.departmentCode || '',
    departmentName: user?.departmentName || '',
    roleId: user?.roleId ?? null,
    role: normalizeRole(user?.roleCode || user?.role),
    roleCode: normalizeRole(user?.roleCode || user?.role),
    roleName: user?.roleName || '',
    enabled: Boolean(user?.enabled)
  })) : [],
  roles: Array.isArray(payload.roles)
    ? payload.roles.map((role) => ({
      id: role?.id ?? null,
      code: normalizeRole(role?.code),
      name: role?.name || '',
      description: role?.description || '',
      isSystem: Boolean(role?.system),
      enabled: Boolean(role?.enabled ?? true),
      userCount: Number.isFinite(Number(role?.userCount)) ? Number(role.userCount) : 0,
      dataScope: role?.dataScope || 'self',
      menuPermissions: normalizePermissionList(role?.menuPermissions),
      allowedProjects: normalizeAllowedProjects(role?.allowedProjects),
      allowedDepts: normalizeAllowedDepts(role?.allowedDepts)
    }))
    : []
})

const buildRuntimeRoleMap = (payload) => {
  const roles = Array.isArray(payload?.roles) ? payload.roles : []
  return roles.reduce((acc, role) => {
    const code = normalizeRole(role?.code)
    if (!code) return acc

    acc[code] = {
      code,
      menuPermissions: normalizePermissionList(role?.menuPermissions),
      dataScope: role?.dataScope || 'self',
      allowedProjects: Array.isArray(role?.allowedProjects) ? [...role.allowedProjects] : [],
      allowedDepts: Array.isArray(role?.allowedDepts) ? [...role.allowedDepts] : []
    }
    return acc
  }, {})
}

export const persistRuntimeSettings = (payload) => {
  if (!payload) return null

  runtimeSettings = {
    updatedAt: Date.now(),
    roleMap: buildRuntimeRoleMap(payload)
  }
  return runtimeSettings
}

export const clearRuntimeSettings = () => {
  runtimeSettings = null
}

export const getRuntimeSettings = () => runtimeSettings

export const getRolePermissionProfile = (role) => {
  const currentRuntimeSettings = getRuntimeSettings()
  if (!currentRuntimeSettings) return null

  return currentRuntimeSettings.roleMap?.[normalizeRole(role)] || null
}

export const hasMenuAccessForRole = (role, permissionKeys = []) => {
  const profile = getRolePermissionProfile(role)
  if (!profile) return null

  const normalizedKeys = normalizePermissionList(permissionKeys)
  if (normalizedKeys.length === 0) return true

  if (profile.menuPermissions.includes('all')) return true
  return normalizedKeys.every((key) => profile.menuPermissions.includes(key))
}

export const settingsApi = {
  async getDataScopeConfig() {

    const response = await httpClient.get('/api/admin/settings/data-scope')
    return {
      ...response,
      data: normalizeConfig(response?.data)
    }
  },

  async saveDataScopeConfig(payload) {
    const normalizedPayload = normalizeConfig(payload)

    const response = await httpClient.put('/api/admin/settings/data-scope', normalizedPayload)
    return {
      ...response,
      data: normalizeConfig(response?.data)
    }
  },

  async getUsers() {

    const response = await httpClient.get('/api/admin/users')
    return {
      ...response,
      data: Array.isArray(response?.data) ? response.data.map((user) => ({
        id: user?.id,
        username: user?.username || '',
        fullName: user?.fullName || '',
        email: user?.email || '',
        phone: user?.phone || '',
        departmentCode: user?.departmentCode || '',
        departmentName: user?.departmentName || '',
        roleId: user?.roleId ?? null,
        role: normalizeRole(user?.roleCode || user?.role),
        roleCode: normalizeRole(user?.roleCode || user?.role),
        roleName: user?.roleName || '',
        enabled: Boolean(user?.enabled)
      })) : []
    }
  },

  async createUser(payload) {
    const response = await httpClient.post('/api/admin/users', payload)
    return {
      ...response,
      data: response?.data
    }
  },

  async updateUser(userId, payload) {
    const response = await httpClient.put(`/api/admin/users/${userId}`, payload)
    return {
      ...response,
      data: response?.data
    }
  },

  async updateUserStatus(userId, enabled) {
    const response = await httpClient.patch(`/api/admin/users/${userId}/status`, { enabled })
    return {
      ...response,
      data: response?.data
    }
  },

  async getRoles() {

    const response = await httpClient.get('/api/admin/roles')
    return {
      ...response,
      data: Array.isArray(response?.data)
        ? response.data.map((role) => ({
          id: role?.id ?? null,
          code: normalizeRole(role?.code),
          name: role?.name || '',
          description: role?.description || '',
          isSystem: Boolean(role?.system),
          enabled: Boolean(role?.enabled ?? true),
          userCount: Number.isFinite(Number(role?.userCount)) ? Number(role.userCount) : 0,
          dataScope: role?.dataScope || 'self',
          menuPermissions: normalizePermissionList(role?.menuPermissions),
          allowedProjects: normalizeAllowedProjects(role?.allowedProjects),
          allowedDepts: normalizeAllowedDepts(role?.allowedDepts)
        }))
        : []
    }
  },

  async createRole(payload) {
    const response = await httpClient.post('/api/admin/roles', payload)
    return {
      ...response,
      data: response?.data
    }
  },

  async updateRole(roleId, payload) {
    const response = await httpClient.put(`/api/admin/roles/${roleId}`, payload)
    return {
      ...response,
      data: response?.data
    }
  },

  async updateRoleStatus(roleId, enabled) {
    const response = await httpClient.patch(`/api/admin/roles/${roleId}/status`, { enabled })
    return {
      ...response,
      data: response?.data
    }
  },

  async resetRole(roleId) {
    const response = await httpClient.post(`/api/admin/roles/${roleId}/reset-default`)
    return {
      ...response,
      data: response?.data
    }
  },

  async syncRoleMenuPermissionsFromOss(roleId, jobNumber) {
    const response = await httpClient.post(
      `/api/admin/roles/${roleId}/sync-oss-menu-permissions`,
      { jobNumber }
    )
    return {
      ...response,
      data: response?.data
    }
  },

  async getSystemSettings() {

    return httpClient.get('/api/settings')
  },

  async updateSystemSettings(payload) {

    return httpClient.put('/api/settings', payload)
  },

  async testAiModelConnection(payload) {

    return httpClient.post('/api/settings/ai-models/test', payload)
  },

  async getSystemInfo() {

    return httpClient.get('/api/settings/system-info')
  }
}

export default settingsApi
