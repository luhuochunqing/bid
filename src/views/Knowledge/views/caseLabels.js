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
  // 兼容旧数据
  STATE_OWNED: '央企',
  FOREIGN: '港澳台及外企'
}

export const SCORING_CATEGORIES = ['技术', '商务', '实施服务', '资质业绩']

export const STATUS_LABELS = {
  ACTIVE: '上架',
  OFF_SHELF: '已下架'
}

// 案例模块保留 '常规项目' 作为空值 fallback（语义：未归类项目视为常规项目）
export const getProjectTypeLabel = (val) => PROJECT_TYPE_LABELS[val] || val || '常规项目'
export const getCustomerTypeLabel = (val) => CUSTOMER_TYPE_LABELS[val] || val || '通用客户'
