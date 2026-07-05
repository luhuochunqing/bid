// 项目类型统一常量（对齐后端 InitiationFieldPolicy.ProjectType 枚举）
// 蓝图 §3.1.1：办公/综合/集采/工业品/其他
// 注意：集采的标准枚举名为 COLLECTIVE（与后端 InitiationFieldPolicy.PROJECT_TYPE_MAPPING 一致），
// 不要使用 CENTRALIZED（历史遗留前端别名，会导致筛选不生效）。

export const PROJECT_TYPE_OPTIONS = [
  { value: 'OFFICE', label: '办公' },
  { value: 'COMPREHENSIVE', label: '综合' },
  { value: 'COLLECTIVE', label: '集采' },
  { value: 'INDUSTRIAL', label: '工业品' },
  { value: 'OTHER', label: '其他' }
]

export const PROJECT_TYPE_LABELS = PROJECT_TYPE_OPTIONS.reduce((acc, item) => {
  acc[item.value] = item.label
  return acc
}, {})

export const getProjectTypeLabel = (val) => PROJECT_TYPE_LABELS[val] || val || '-'
