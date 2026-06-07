// Case labels: project type and customer type i18n mappings
// 蓝图 4.1.1.2: 项目类型=办公/综合/集采/工业品/其他
// 蓝图 4.1.1.2: 客户类型=政府机关/事业单位/高校、央企、地方国企、民企、港澳台及外企
export const PROJECT_TYPE_LABELS = {
  OFFICE: '办公',
  COMPREHENSIVE: '综合',
  PROCUREMENT: '集采',
  INDUSTRIAL: '工业品',
  OTHER: '其他'
}

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

export const getProjectTypeLabel = (val) => PROJECT_TYPE_LABELS[val] || val || '常规项目'
export const getCustomerTypeLabel = (val) => CUSTOMER_TYPE_LABELS[val] || val || '通用客户'
