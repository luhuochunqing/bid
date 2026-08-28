// Case labels: project type and customer type i18n mappings
// 蓝图 4.1.1.2: 项目类型=办公/综合/集采/工业品/其他
// 蓝图 4.1.1.2: 客户类型=政府机关/事业单位/高校、央企、地方国企、民企、港澳台及外企
// 项目类型统一常量抽取到 src/constants/projectTypes.js，此处 re-export 保持向后兼容
import { PROJECT_TYPE_LABELS } from '@/constants/projectTypes.js'

export { PROJECT_TYPE_LABELS }
export const CUSTOMER_TYPE_LABELS = {
  GOVERNMENT: '政府机关/事业单位/高校',
  CENTRAL_SOE: '央企',
  LOCAL_SOE: '地方国企',
  PRIVATE: '民企',
  FOREIGN_ENTERPRISE: '港澳台及外企',
  // 兼容旧数据（仅用于展示映射，不进筛选项）
  STATE_OWNED: '央企',
  FOREIGN: '港澳台及外企'
}

// 筛选用客户类型选项（不含旧数据兼容键，避免下拉出现重复文案）
export const CUSTOMER_TYPE_OPTIONS = Object.entries(CUSTOMER_TYPE_LABELS)
  .filter(([val]) => val !== 'STATE_OWNED' && val !== 'FOREIGN')
  .map(([value, label]) => ({ value, label }))

// 案例模块保留 '常规项目' 作为空值 fallback（语义：未归类项目视为常规项目）
export const getProjectTypeLabel = (val) => PROJECT_TYPE_LABELS[val] || val || '常规项目'
export const getCustomerTypeLabel = (val) => CUSTOMER_TYPE_LABELS[val] || val || '通用客户'

/**
 * 计算匹配度等级标签（优质/良好/一般）
 */
export function getMatchLabel(score, explicitLabel) {
  if (explicitLabel) return explicitLabel
  if (score === null || score === undefined || score === '') return ''
  const num = Number(score) || 0
  return num >= 80 ? '优质' : num >= 60 ? '良好' : '一般'
}

/**
 * 获取匹配度对应的 Element Plus 标签类型（danger/warning/info）
 */
export function getMatchTagType(score, explicitLabel) {
  const lbl = getMatchLabel(score, explicitLabel)
  const num = Number(score) || 0
  if (lbl === '优质' || num >= 80) return 'danger'
  if (lbl === '良好' || num >= 60) return 'warning'
  return 'info'
}

/**
 * 解析数组或分隔符字符串为精简字符串数组
 */
export function parseStringOrArray(val, delimiter = /[,，、\s]+/) {
  if (Array.isArray(val)) return val.map(s => String(s).trim()).filter(Boolean)
  if (typeof val === 'string' && val.trim()) return val.split(delimiter).map(s => s.trim()).filter(Boolean)
  return []
}

