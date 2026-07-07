/**
 * 仓库附件类型筛选纯函数。
 *
 * 从 WarehouseDrawer.vue 抽出，便于单测。符合「纯核心可单测」原则。
 *
 * @param {Array<{type?: string}>} attachments 附件列表
 * @param {string} typeFilter 筛选值；'ALL' 或空值表示全部，否则按附件 type 字段精确匹配
 * @returns {Array} 过滤后的附件列表（不修改入参）
 */
export function filterAttachmentsByType(attachments, typeFilter) {
  if (!Array.isArray(attachments)) return []
  if (!typeFilter || typeFilter === 'ALL') return attachments
  return attachments.filter((a) => a?.type === typeFilter)
}
