// Input: endpoint permission matrix rows and user filters
// Output: pure filtering and display helpers for the settings panel
// Pos: src/views/System/settings/ - pure core helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const ROLE_LABELS = {
  admin: '管理员',
  manager: '经理',
  staff: '项目负责人',
}

const SOURCE_LABELS = {
  METHOD_PRE_AUTHORIZE: '后端注解',
  SECURITY_ROUTE_RULE_MIRROR: '安全路由规则镜像',
  SECURITY_WHITELIST_MIRROR: '公开白名单镜像',
  DEFAULT_AUTHENTICATED_MIRROR: '默认登录态镜像',
  UNKNOWN: '未知来源',
}

export const hasEndpointRole = (row, role) => {
  const roles = Array.isArray(row?.roles) ? row.roles : []
  return roles.includes(String(role || '').toLowerCase())
}

export const permissionRoleTags = (row) => (
  Array.isArray(row?.roles)
    ? row.roles.map((role) => ROLE_LABELS[role] || role)
    : []
)

export const sourceLabel = (source) => SOURCE_LABELS[source] || source || '未知来源'

export const riskTagType = (riskLevel) => ({
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'success',
}[riskLevel] || 'info')

export function filterEndpointPermissions(rows = [], filters = {}) {
  const keyword = String(filters.keyword || '').trim().toLowerCase()
  const method = String(filters.method || '').trim().toUpperCase()
  const moduleName = String(filters.module || '').trim()
  const role = String(filters.role || '').trim().toLowerCase()
  const riskLevel = String(filters.riskLevel || '').trim()
  const source = String(filters.source || '').trim()

  return rows.filter((row) => (
    matchesKeyword(row, keyword)
    && (!method || row.method === method)
    && (!moduleName || row.module === moduleName)
    && (!role || hasEndpointRole(row, role))
    && (!riskLevel || row.riskLevel === riskLevel)
    && (!source || row.source === source)
  ))
}

function matchesKeyword(row, keyword) {
  if (!keyword) return true
  return [row.path, row.controller, row.handler, row.expression]
    .some((value) => String(value || '').toLowerCase().includes(keyword))
}
