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

export const normalizeUser = (authPayload) => ({
  id: authPayload?.id,
  name: authPayload?.fullName || authPayload?.name || authPayload?.username,
  username: authPayload?.username,
  email: authPayload?.email,
  role: String(authPayload?.roleCode || authPayload?.role || '').toLowerCase(),
  roleCode: String(authPayload?.roleCode || authPayload?.role || '').toLowerCase(),
  roleName: authPayload?.roleName || '',
  dept: authPayload?.dept || authPayload?.departmentName || '',
  deptCode: authPayload?.deptCode || authPayload?.departmentCode || '',
  allowedProjectIds: normalizeAllowedProjectIds(authPayload?.allowedProjectIds),
  allowedDepts: normalizeAllowedDepts(authPayload?.allowedDepts),
  menuPermissions: Array.isArray(authPayload?.menuPermissions) ? authPayload.menuPermissions : []
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
