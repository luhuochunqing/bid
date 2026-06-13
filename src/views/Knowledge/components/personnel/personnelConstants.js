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
