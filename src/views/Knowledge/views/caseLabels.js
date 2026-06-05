// Case labels: project type and customer type i18n mappings
// 4.1.1.2.2: 办公/综合/集采/工业品/其他
export const PROJECT_TYPE_LABELS = {
  OFFICE: '办公',
  COMPREHENSIVE: '综合',
  PROCUREMENT: '集采',
  INDUSTRIAL: '工业品',
  OTHER: '其他'
}

export const CUSTOMER_TYPE_LABELS = {
  STATE_OWNED: '国央企',
  PRIVATE: '民营企业',
  FOREIGN: '外资企业',
  GOVERNMENT: '政府机构'
}

export const SCORING_CATEGORIES = ['技术', '商务', '实施服务', '资质业绩']

export const STATUS_LABELS = {
  ACTIVE: '上架',
  OFF_SHELF: '已下架'
}

export const getProjectTypeLabel = (val) => PROJECT_TYPE_LABELS[val] || val || '常规项目'
export const getCustomerTypeLabel = (val) => CUSTOMER_TYPE_LABELS[val] || val || '通用客户'
