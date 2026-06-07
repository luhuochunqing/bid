// Input: httpClient and backend endpoint permission matrix payloads
// Output: permissionMatrixApi with normalized endpoint permission rows
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const normalizeRoles = (roles) => (
  Array.isArray(roles)
    ? [...new Set(roles.map((role) => String(role || '').trim().toLowerCase()).filter(Boolean))]
    : []
)

export const normalizeEndpointPermission = (item = {}) => ({
  method: String(item.method || 'ANY').toUpperCase(),
  path: item.path || '',
  module: item.module || 'system',
  controller: item.controller || '',
  handler: item.handler || '',
  expression: item.expression || '',
  roles: normalizeRoles(item.allowedRoles || item.roles),
  accessLevel: item.accessLevel || 'UNKNOWN',
  riskLevel: item.riskLevel || 'LOW',
  configurable: Boolean(item.configurable),
  source: item.source || 'UNKNOWN',
})

export const permissionMatrixApi = {
  async getEndpointPermissions() {
    const response = await httpClient.get('/api/admin/permissions/endpoints')
    return {
      ...response,
      data: Array.isArray(response?.data)
        ? response.data.map(normalizeEndpointPermission)
        : [],
    }
  },
}

export default permissionMatrixApi
