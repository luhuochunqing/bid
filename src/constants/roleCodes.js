/**
 * 角色码常量（前端单一真相源）
 *
 * 与后端 {@code RoleProfileCatalog.java} 保持对齐。
 * 修改角色码时只需更新此文件，所有引用处自动同步。
 *
 * 角色码风格：
 * - camelCase 或 hyphen 风格（与 OSS 文档对齐）
 * - authority 形式：连字符转下划线再大写（如 bid-TeamLeader → BID_TEAMLEADER）
 */

// 角色码（与后端 RoleProfileCatalog 常量一致）
export const ROLE_CODES = {
  ADMIN: 'admin',
  BID_ADMIN: '/bidAdmin',
  BID_LEAD: 'bid-TeamLeader',
  SALES: 'bid-projectLeader',
  BID_SPECIALIST: 'bid-Team',
  ADMIN_STAFF: 'bid-administration',
  BID_OTHER_DEPT: 'bid-otherDept',
}

// authority 形式（大写，连字符转下划线，用于 @PreAuthorize 和前端权限判断）
export const ROLE_AUTHORITIES = {
  ADMIN: 'ADMIN',
  BID_ADMIN: 'BIDADMIN',
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
  [ROLE_CODES.BID_LEAD]: '投标组长',
  [ROLE_CODES.SALES]: '投标项目负责人',
  [ROLE_CODES.BID_SPECIALIST]: '投标专员',
  [ROLE_CODES.ADMIN_STAFF]: '行政人员',
  [ROLE_CODES.BID_OTHER_DEPT]: '跨部门协同人员',
  [ROLE_AUTHORITIES.ADMIN]: '管理员',
  [ROLE_AUTHORITIES.BID_ADMIN]: '投标管理员',
  [ROLE_AUTHORITIES.BID_LEAD]: '投标组长',
  [ROLE_AUTHORITIES.SALES]: '投标项目负责人',
  [ROLE_AUTHORITIES.BID_SPECIALIST]: '投标专员',
  [ROLE_AUTHORITIES.ADMIN_STAFF]: '行政人员',
  [ROLE_AUTHORITIES.BID_OTHER_DEPT]: '跨部门协同人员',
}

/**
 * 根据角色码或 authority 获取显示名
 * @param {string} role 角色码或 authority
 * @returns {string} 显示名，未匹配返回原值
 */
export function getRoleDisplayName(role) {
  return ROLE_DISPLAY_NAMES[role] || role
}
