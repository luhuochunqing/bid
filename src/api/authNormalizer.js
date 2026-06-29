// Input: raw authentication payloads from the backend
// Output: normalized frontend user session values
// Pos: src/api/ - Pure auth DTO normalization helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const normalizeAllowedProjectIds = (allowedProjectIds) => {
  if (!Array.isArray(allowedProjectIds)) {
    return []
  }

  return [...new Set(allowedProjectIds.filter((projectId) => projectId !== null && projectId !== undefined))]
}

const normalizeAllowedDepts = (allowedDepts) => {
  if (!Array.isArray(allowedDepts)) {
    return []
  }

  return [...new Set(allowedDepts.filter((deptCode) => deptCode !== null && deptCode !== undefined && deptCode !== ''))]
}

const normalizeMenuPermissions = (menuPermissions) => {
  if (!Array.isArray(menuPermissions)) {
    return []
  }

  const normalized = new Set(menuPermissions.filter((permission) => permission))

  // 父权限自动补齐：OSS 同步用户只返回子权限码，路由守卫要求父子权限同时存在（AND 逻辑）
  // 与 knowledge-* 补齐 knowledge 父权限的逻辑对称
  const hasKnowledgeChild = [...normalized].some((permission) => String(permission).startsWith('knowledge-'))
  if (hasKnowledgeChild) {
    normalized.add('knowledge')
  }

  const hasResourceChild = [...normalized].some((permission) => String(permission).startsWith('resource-'))
  if (hasResourceChild) {
    normalized.add('resource')
  }

  return [...normalized]
}

export const normalizeUser = (authPayload) => ({
  id: authPayload?.id,
  name: authPayload?.fullName || authPayload?.name || authPayload?.username,
  employeeNumber: authPayload?.employeeNumber || '',
  username: authPayload?.username,
  email: authPayload?.email,
  phone: authPayload?.phone || authPayload?.mobile,
  // 保留 roleCode 原始大小写：OSS 角色码大小写敏感（如 bidAdmin、bid-TeamLeader）
  // 下游 stores/user.js 的权限判断用 r === '/bidAdmin' 等精确匹配
  role: String(authPayload?.roleCode || authPayload?.role || ''),
  roleCode: String(authPayload?.roleCode || authPayload?.role || ''),
  roleName: authPayload?.roleName || '',
  dept: authPayload?.dept || authPayload?.departmentName || '',
  deptCode: authPayload?.deptCode || authPayload?.departmentCode || '',
  allowedProjectIds: normalizeAllowedProjectIds(authPayload?.allowedProjectIds),
  allowedDepts: normalizeAllowedDepts(authPayload?.allowedDepts),
  menuPermissions: normalizeMenuPermissions(authPayload?.menuPermissions)
})

export const normalizeAuthSessionResponse = (response) => {
  const authPayload = response?.data
  const normalizedUser = normalizeUser(authPayload)

  return {
    ...response,
    data: {
      user: normalizedUser,
      token: authPayload?.token,
      type: authPayload?.type || 'Bearer'
    }
  }
}
