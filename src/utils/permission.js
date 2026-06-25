/**
 * Permission utilities for frontend role/permission checks.
 * Replaces hardcoded role comparisons with menuPermission-driven logic.
 */

import { useUserStore } from '@/stores/user.js'

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
 * Check if a user has ALL of the required permissions.
 * Used for hierarchical permission checks where permissionKeys like
 * ['knowledge', 'knowledge-archive'] require BOTH parent and child permissions.
 * @param {string[]|null|undefined} userPermissions - user's menuPermissions
 * @param {string[]|null|undefined} requiredPermissions - required permission keys (ALL must be present)
 * @returns {boolean}
 */
export function hasAllPermissions(userPermissions, requiredPermissions) {
  if (!requiredPermissions || requiredPermissions.length === 0) return true
  const perms = Array.isArray(userPermissions) ? userPermissions : []
  if (perms.length === 0) return false
  if (perms.includes('all')) return true
  return requiredPermissions.every((key) => perms.includes(key))
}

/**
 * Check if the given role code represents an admin.
 * @param {string} roleCode
 * @returns {boolean}
 */
export function isAdminRole(roleCode) {
  return roleCode === 'admin'
}

/**
 * Check if the role code has bid manager authorities (admin, bidAdmin, bid-TeamLeader).
 * Note: bid_senior 已删除，映射到 bid-TeamLeader。
 * @param {string} roleCode
 * @returns {boolean}
 */
export function isBidManager(roleCode) {
  const r = (roleCode || '').replace(/^role_/, '').toLowerCase()
  return ['admin', '/bidadmin', 'bid-teamleader'].includes(r)
}

/**
 * Check if the role code represents a bid admin or senior bid role (bidAdmin, bid-TeamLeader).
 * Note: bid_senior 已删除，映射到 bid-TeamLeader。
 * @param {string} roleCode
 * @returns {boolean}
 */
export function isBidAdminOrSenior(roleCode) {
  const r = (roleCode || '').replace(/^role_/, '').toLowerCase()
  return ['/bidadmin', 'bid-teamleader'].includes(r)
}

/**
 * Check if the role code is a bid manager but not a super admin.
 * Note: bid_senior 已删除，映射到 bid-TeamLeader。
 * @param {string} roleCode
 * @returns {boolean}
 */
export function isBidManagerExcludeAdmin(roleCode) {
  const r = (roleCode || '').replace(/^role_/, '').toLowerCase()
  return ['/bidadmin', 'bid-teamleader'].includes(r)
}

/**
 * Check if the given id matches the currently logged-in user.
 * Safely handles null/undefined values for both sides.
 * @param {number|string|null|undefined} id
 * @returns {boolean}
 */
export function matchesCurrentUser(id) {
  if (id == null || id === '') return false
  const store = useUserStore()
  const uid = store.currentUser?.id
  return uid != null && String(uid) === String(id)
}

/**
 * Check if the given task is assigned to the currently logged-in user.
 * @param {Object|null|undefined} task - task object with assigneeId
 * @returns {boolean}
 */
export function isTaskAssignee(task) {
  return matchesCurrentUser(task?.assigneeId)
}

/**
 * Check if the current user is the reviewer for the given item.
 * @param {Object|null|undefined} item - item object with reviewerId
 * @returns {boolean}
 */
export function isBidReviewer(item) {
  return matchesCurrentUser(item?.reviewerId)
}