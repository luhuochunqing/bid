/**
 * 角色码常量（前端单一真相源）
 *
 * 与后端 {@code RoleProfileCatalog.java} 保持对齐。
 * 修改角色码时只需更新此文件，所有引用处自动同步。
 *
 * 角色码风格：
 * - camelCase 或 hyphen 风格（与 OSS 文档对齐）
 * - authority 形式：连字符转下划线再大写（如 bid-TeamLeader → BID_TEAMLEADER）
 *
 * 注意（PR !2020+）：
 * - `bid-SystemAdmin` 是 OSS 端「投标系统管理员」独立角色码，**不再映射为 admin**。
 * - 本地 `admin` 仅本地超级管理员；OSS 用户不得持有 `admin` / `"all"`。
 */

// 角色码（与后端 RoleProfileCatalog 常量一致）
export const ROLE_CODES = {
  ADMIN: 'admin',
  BID_ADMIN: '/bidAdmin',
  /** OSS 投标系统管理员（第 8 角色，权限基线等同 /bidAdmin，不含 "all"） */
  BID_SYSTEM_ADMIN: 'bid-SystemAdmin',
  BID_LEAD: 'bid-TeamLeader',
  SALES: 'bid-projectLeader',
  BID_SPECIALIST: 'bid-Team',
  ADMIN_STAFF: 'bid-administration',
  BID_OTHER_DEPT: 'bid-otherDept',
}

/**
 * 投标管理级角色（可执行转移等严格管理操作）。
 * 对齐后端 isBidAdmin 语义：admin + /bidAdmin + bid-SystemAdmin（不含组长）。
 */
export const BID_ADMIN_LEVEL_ROLES = [
  ROLE_CODES.ADMIN,
  ROLE_CODES.BID_ADMIN,
  ROLE_CODES.BID_SYSTEM_ADMIN,
]

// 全局管理/审核角色（与后端 RoleProfileCatalog.GLOBAL_ACCESS_ROLES 对齐）
export const GLOBAL_MANAGE_ROLES = [
  ...BID_ADMIN_LEVEL_ROLES,
  ROLE_CODES.BID_LEAD,
]

// 可作为项目转移目标负责人的角色（管理角色 + 投标项目负责人）
export const PROJECT_TRANSFER_TARGET_ROLES = [
  ...GLOBAL_MANAGE_ROLES,
  ROLE_CODES.SALES,
]

/**
 * 工作台角色化改造分组（与 spec.md §2 对齐）。
 * - admin_lead 分组直接复用 GLOBAL_MANAGE_ROLES / isGlobalManageRole（投标管理员/系统管理员/组长）
 * - SALES_ROLES 仅投标项目负责人（跨部门协同人员单独分组，不显示标讯待办）
 * - BID_TEAM_ROLES 投标专员
 * - CROSS_DEPT_ROLES 跨部门协同人员（任务待办跳独立看板）
 */
export const SALES_ROLES = [ROLE_CODES.SALES]
export const BID_TEAM_ROLES = [ROLE_CODES.BID_SPECIALIST]
export const CROSS_DEPT_ROLES = [ROLE_CODES.BID_OTHER_DEPT]

// authority 形式（大写，连字符转下划线，用于 @PreAuthorize 和前端权限判断）
// 规则对齐 RoleProfileCatalog.toAuthorityName
export const ROLE_AUTHORITIES = {
  ADMIN: 'ADMIN',
  BID_ADMIN: 'BIDADMIN',
  BID_SYSTEM_ADMIN: 'BID_SYSTEMADMIN',
  BID_LEAD: 'BID_TEAMLEADER',
  SALES: 'BID_PROJECTLEADER',
  BID_SPECIALIST: 'BID_TEAM',
  ADMIN_STAFF: 'BID_ADMINISTRATION',
  BID_OTHER_DEPT: 'BID_OTHERDEPT',
}

// 角色显示名（用于 UI 展示）
export const ROLE_DISPLAY_NAMES = {
  [ROLE_CODES.ADMIN]: '管理员',
  [ROLE_CODES.BID_ADMIN]: '投标管理员',
  [ROLE_CODES.BID_SYSTEM_ADMIN]: '投标系统管理员',
  [ROLE_CODES.BID_LEAD]: '投标组长',
  [ROLE_CODES.SALES]: '投标项目负责人',
  [ROLE_CODES.BID_SPECIALIST]: '投标专员',
  [ROLE_CODES.ADMIN_STAFF]: '行政人员',
  [ROLE_CODES.BID_OTHER_DEPT]: '跨部门协同人员',
  [ROLE_AUTHORITIES.ADMIN]: '管理员',
  [ROLE_AUTHORITIES.BID_ADMIN]: '投标管理员',
  [ROLE_AUTHORITIES.BID_SYSTEM_ADMIN]: '投标系统管理员',
  [ROLE_AUTHORITIES.BID_LEAD]: '投标组长',
  [ROLE_AUTHORITIES.SALES]: '投标项目负责人',
  [ROLE_AUTHORITIES.BID_SPECIALIST]: '投标专员',
  [ROLE_AUTHORITIES.ADMIN_STAFF]: '行政人员',
  [ROLE_AUTHORITIES.BID_OTHER_DEPT]: '跨部门协同人员',
}

/**
 * 是否为投标管理级角色（admin / /bidAdmin / bid-SystemAdmin）。
 * @param {string|null|undefined} roleCode
 * @returns {boolean}
 */
export function isBidAdminLevelRole(roleCode) {
  if (roleCode == null || roleCode === '') return false
  return BID_ADMIN_LEVEL_ROLES.includes(roleCode)
}

/**
 * 是否为全局管理角色（管理级 + 投标组长）。
 * @param {string|null|undefined} roleCode
 * @returns {boolean}
 */
export function isGlobalManageRole(roleCode) {
  if (roleCode == null || roleCode === '') return false
  return GLOBAL_MANAGE_ROLES.includes(roleCode)
}

/**
 * 是否为投标项目负责人（sales 分组）。
 * @param {string|null|undefined} roleCode
 * @returns {boolean}
 */
export function isSalesRole(roleCode) {
  if (roleCode == null || roleCode === '') return false
  return SALES_ROLES.includes(roleCode)
}

/**
 * 是否为投标专员（bid_team 分组）。
 * @param {string|null|undefined} roleCode
 * @returns {boolean}
 */
export function isBidTeamRole(roleCode) {
  if (roleCode == null || roleCode === '') return false
  return BID_TEAM_ROLES.includes(roleCode)
}

/**
 * 是否为跨部门协同人员（任务待办跳独立看板）。
 * @param {string|null|undefined} roleCode
 * @returns {boolean}
 */
export function isCrossDeptRole(roleCode) {
  if (roleCode == null || roleCode === '') return false
  return CROSS_DEPT_ROLES.includes(roleCode)
}

/**
 * 根据角色码或 authority 获取显示名
 * @param {string} role 角色码或 authority
 * @returns {string} 显示名，未匹配返回原值
 */
export function getRoleDisplayName(role) {
  return ROLE_DISPLAY_NAMES[role] || role
}
