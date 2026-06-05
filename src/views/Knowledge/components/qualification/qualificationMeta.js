// Input: qualification and borrow record fields
// Output: UI labels, colors, icons, and display helpers for qualification views
// Pos: src/views/Knowledge/components/qualification/ - View helper module

import {
  Box,
  CircleCheck,
  CircleClose,
  Medal,
  OfficeBuilding,
  User,
  Warning
} from '@element-plus/icons-vue'

export const qualificationTypeLabels = {
  enterprise: '企业资质',
  personnel: '人员资质',
  product: '产品资质',
  industry: '行业认证'
}

export const qualificationTypeTagTypes = {
  enterprise: 'primary',
  personnel: 'success',
  product: 'warning',
  industry: 'info'
}

export const qualificationTypeIcons = {
  enterprise: OfficeBuilding,
  personnel: User,
  product: Box,
  industry: Medal
}

export const qualificationTypeColors = {
  enterprise: '#409eff',
  personnel: '#67c23a',
  product: '#e6a23c',
  industry: '#909399'
}

export const qualificationStatusLabels = {
  valid: '有效',
  expiring: '即将到期',
  expired: '已过期'
}

export const qualificationStatusTagTypes = {
  valid: 'success',
  expiring: 'warning',
  expired: 'danger'
}

export const qualificationStatusIcons = {
  valid: CircleCheck,
  expiring: Warning,
  expired: CircleClose
}

export const borrowPurposeLabels = {
  bidding: '投标使用',
  audit: '资质审核',
  presentation: '客户展示',
  other: '其他'
}

export const borrowStatusLabels = {
  borrowed: '借阅中',
  returned: '已归还',
  overdue: '逾期'
}

export const borrowStatusTagTypes = {
  borrowed: 'warning',
  returned: 'success',
  overdue: 'danger'
}

export function formatDate(date) {
  return date || '-'
}

export function getDateClass(status) {
  if (status === 'expiring') return 'date-warning'
  if (status === 'expired') return 'date-expired'
  return ''
}

export function getDaysClass(days) {
  if (days < 0) return 'days-expired'
  if (days <= 30) return 'days-warning'
  if (days <= 90) return 'days-notice'
  return 'days-normal'
}

export function getTypeLabel(type) {
  return qualificationTypeLabels[type] || type
}

export function getPurposeLabel(purpose) {
  return borrowPurposeLabels[purpose] || purpose
}

export function getBorrowStatusLabel(status) {
  return borrowStatusLabels[status] || status
}
