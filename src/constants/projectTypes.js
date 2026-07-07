// 项目类型统一常量（对齐后端 ProjectType 枚举：立项模块 InitiationFieldPolicy.ProjectType
// 与业绩模块 performance.domain.valueobject.ProjectType 均使用 COLLECTIVE 表示"集采"）
// 蓝图 §3.1.1：办公/综合/集采/工业品/其他

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
