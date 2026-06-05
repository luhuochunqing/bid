// Input: raw settings API payload
// Output: normalized organization rows and save payload builders
// Pos: frontend pure normalization helpers for Settings organization panels

export const normalizeDeptCode = (value) => String(value || '').trim()

export const normalizeDeptTree = (items = []) => (Array.isArray(items) ? items : [])
  .map((item, index) => ({
    deptCode: normalizeDeptCode(item.deptCode),
    deptName: String(item.deptName || '').trim(),
    parentDeptCode: normalizeDeptCode(item.parentDeptCode),
    sortOrder: Number.isFinite(Number(item.sortOrder)) ? Number(item.sortOrder) : index
  }))
  .filter((item) => item.deptCode || item.deptName)

export const normalizeRoles = (items = []) => (Array.isArray(items) ? items : [])
  .map((role) => ({
    id: role.id ?? null,
    code: String(role.code || role.roleCode || '').trim().toLowerCase(),
    name: role.name || role.code || '未命名角色',
    description: role.description || '',
    isSystem: Boolean(role.isSystem ?? role.system),
    enabled: Boolean(role.enabled ?? true),
    userCount: Number(role.userCount || 0),
    dataScope: role.dataScope || 'self',
    menuPermissions: Array.isArray(role.menuPermissions) ? [...new Set(role.menuPermissions)] : [],
    allowedProjects: Array.isArray(role.allowedProjects) ? [...role.allowedProjects] : [],
    allowedDepts: Array.isArray(role.allowedDepts) ? [...new Set(role.allowedDepts)] : []
  }))

export const normalizeUsers = (items = []) => (Array.isArray(items) ? items : [])
  .map((user) => ({
    id: user.id,
    username: user.username || '',
    fullName: user.fullName || user.name || '',
    email: user.email || '',
    departmentCode: normalizeDeptCode(user.departmentCode || user.deptCode),
    departmentName: user.departmentName || user.dept || '未配置部门',
    roleId: user.roleId ?? null,
    roleCode: String(user.roleCode || user.role || '').toLowerCase(),
    roleName: user.roleName || user.role || '未配置角色',
    enabled: Boolean(user.enabled)
  }))

export const toDeptTreeNodes = (items = []) => {
  const rows = normalizeDeptTree(items)
  return rows.map((row) => ({
    value: row.deptCode,
    label: row.deptName || row.deptCode,
    disabled: !row.deptCode
  }))
}

export const buildRolePayload = (role = {}) => ({
  code: String(role.code || '').trim().toLowerCase(),
  name: String(role.name || '').trim(),
  description: String(role.description || '').trim(),
  enabled: Boolean(role.enabled),
  dataScope: role.dataScope || 'self',
  menuPermissions: Array.isArray(role.menuPermissions) ? [...new Set(role.menuPermissions)] : [],
  allowedProjects: Array.isArray(role.allowedProjects) ? [...role.allowedProjects] : [],
  allowedDepts: Array.isArray(role.allowedDepts) ? [...new Set(role.allowedDepts)] : []
})

export const buildUserOrganizationPayload = (form = {}) => ({
  departmentCode: normalizeDeptCode(form.departmentCode),
  roleId: form.roleId ?? null,
  enabled: Boolean(form.enabled)
})
