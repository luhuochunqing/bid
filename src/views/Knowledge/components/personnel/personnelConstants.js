export const GENDER_OPTIONS = [
  { label: '男', value: '男' },
  { label: '女', value: '女' }
]

export const EDUCATION_OPTIONS = ['初中', '高中', '中专', '大专', '本科', '硕士', '博士']

export const STUDY_FORM_OPTIONS = ['全日制', '非全日制', '网络教育', '自学考试']

export const CERT_STATUS_OPTIONS = [
  { label: '有效', value: 'VALID' },
  { label: '即将到期（60天内）', value: 'EXPIRING' },
  { label: '已过期', value: 'EXPIRED' }
]

export const CERT_STATUS_LABELS = {
  VALID: '有效',
  EXPIRING: '即将到期',
  EXPIRED: '已过期',
  PERMANENT: '永久有效'
}

export const CERT_STATUS_TAG_TYPES = {
  VALID: 'success',
  EXPIRING: 'warning',
  EXPIRED: 'danger',
  PERMANENT: 'primary'
}

export const certStatusLabel = (status) => CERT_STATUS_LABELS[status] || status || '—'
export const certStatusTagType = (status) => CERT_STATUS_TAG_TYPES[status] || 'info'

export const EDUCATION_FORM_OPTIONS = [
  { label: '初中', value: '初中' },
  { label: '高中', value: '高中' },
  { label: '中专', value: '中专' },
  { label: '大专', value: '大专' },
  { label: '本科', value: '本科' },
  { label: '硕士', value: '硕士' },
  { label: '博士', value: '博士' }
]

export const STUDY_FORM_SELECT_OPTIONS = [
  { label: '全日制', value: '全日制' },
  { label: '非全日制', value: '非全日制' },
  { label: '网络教育', value: '网络教育' },
  { label: '自学考试', value: '自学考试' },
  { label: '其他', value: '其他' }
]

export const CERT_TYPE_OPTIONS = [
  { label: '建造师', value: 'CONSTRUCTOR' },
  { label: 'PMP', value: 'PMP' },
  { label: '工程师', value: 'ENGINEER' },
  { label: '会计师', value: 'ACCOUNTANT' },
  { label: '律师', value: 'LAWYER' },
  { label: '安全工程师', value: 'SECURITY' },
  { label: 'IT类证书', value: 'IT' },
  { label: '其他', value: 'OTHER' }
]

export const CERT_TITLE_OPTIONS = [
  { label: '初级', value: '初级' },
  { label: '中级', value: '中级' },
  { label: '高级', value: '高级' }
]

export const STATUS_OPTIONS = [
  { label: '在职', value: 'ACTIVE' },
  { label: '停用', value: 'INACTIVE' },
  { label: '离职', value: 'TERMINATED' }
]

// ============ 操作日志格式化（CO-417） ============

export const CHANGE_FIELD_LABELS = {
  employeeNumber: '工号',
  name: '姓名',
  departmentName: '部门',
  gender: '性别',
  entryDate: '入职日期',
  birthDate: '出生日期',
  phone: '手机号',
  education: '教育经历',
  technicalTitle: '职称',
  remark: '备注',
  status: '状态',
  certificate: '证书',
  certificateNumber: '证书编号',
  type: '证书类型',
  issueDate: '发证日期',
  expiryDate: '到期日期',
  attachmentUrl: '附件',
  title: '职称',
  isPermanent: '永久有效',
  educationEntry: '教育经历',
  schoolName: '学校',
  startDate: '开始日期',
  endDate: '结束日期',
  highestEducation: '最高学历',
  studyForm: '学习形式',
  major: '专业',
  isHighestEducationSchool: '最高学历学校',
  attachment: '附件',
  count: '数量'
}

export const OPERATION_TYPE_LABELS = {
  CREATE: '新建',
  UPDATE: '编辑',
  DELETE: '删除',
  RESTORE: '恢复',
  CERTIFICATE_ADD: '新增证书',
  CERTIFICATE_REMOVE: '删除证书',
  CERTIFICATE_UPDATE: '修改证书',
  ATTACHMENT_REPLACE: '替换附件',
  EDUCATION_ADD: '新增教育经历',
  EDUCATION_REMOVE: '删除教育经历',
  EDUCATION_UPDATE: '修改教育经历',
  BATCH_IMPORT_PERSONNEL: '批量导入人员',
  BATCH_IMPORT_CERTIFICATE: '批量导入证书',
  BATCH_EXPORT_PERSONNEL: '批量导出人员',
  BATCH_EXPORT_CERTIFICATE: '批量导出证书'
}

// 新增类操作：只展示 newValue（如 CERTIFICATE_ADD / EDUCATION_ADD / BATCH_IMPORT_*）
const ADD_TYPE_OPERATIONS = new Set([
  'CERTIFICATE_ADD',
  'EDUCATION_ADD',
  'BATCH_IMPORT_PERSONNEL',
  'BATCH_IMPORT_CERTIFICATE'
])

// 删除类操作：只展示 oldValue（如 CERTIFICATE_REMOVE / EDUCATION_REMOVE）
const REMOVE_TYPE_OPERATIONS = new Set([
  'CERTIFICATE_REMOVE',
  'EDUCATION_REMOVE'
])

/** 格式化操作类型为中文标签 */
export const formatOperationType = (type) => {
  if (!type) return '-'
  return OPERATION_TYPE_LABELS[type] || type
}

/** 格式化字段名为中文 */
const formatFieldName = (field) => {
  if (!field) return ''
  return CHANGE_FIELD_LABELS[field] || field
}

/** 格式化变更摘要：按操作类型差异化渲染，字段名展示为中文 */
export const formatChangeSummary = (operationType, changeDetails) => {
  if (!changeDetails || !changeDetails.length) return ''
  const isAddOnly = ADD_TYPE_OPERATIONS.has(operationType)
  const isRemoveOnly = REMOVE_TYPE_OPERATIONS.has(operationType)
  return changeDetails
    .map(d => {
      const field = formatFieldName(d.field)
      const oldVal = d.oldValue || '-'
      const newVal = d.newValue || '-'
      if (isAddOnly) return `${field}: ${newVal}`
      if (isRemoveOnly) return `${field}: ${oldVal}`
      return `${field}: ${oldVal} → ${newVal}`
    })
    .join('; ')
}
