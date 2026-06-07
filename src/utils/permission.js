/**
 * Permission utilities for frontend role/permission checks.
 * Replaces hardcoded role comparisons with menuPermission-driven logic.
 */

/**
 * Check if a user has any of the required permissions.
 * Authorization model: "deny by default, allow only when explicitly authorized."
 * @param {string[]|null|undefined} userPermissions - user's menuPermissions
 * @param {string[]|null|undefined} requiredPermissions - required permission keys
 * @returns {boolean}
 */
export function hasAnyPermission(userPermissions, requiredPermissions) {
  if (!requiredPermissions || requiredPermissions.length === 0) return true
  const perms = Array.isArray(userPermissions) ? userPermissions : []
  if (perms.length === 0) return false
  if (perms.includes('all')) return true
  return requiredPermissions.some((key) => perms.includes(key))
}

/**
 * Check if the given role code represents an admin.
 * @param {string} roleCode
 * @returns {boolean}
 */
export function isAdminRole(roleCode) {
  return roleCode === 'admin'
}
