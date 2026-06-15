// 纯核心：标讯详情页的操作按钮矩阵
// 根据标讯状态和用户角色返回可见的操作按钮列表
// 无 I/O、无框架依赖、无副作用

import { getSourceTypeText } from '../bidding-utils.js'

// ---------------------------------------------------------------------------
// Action definitions (canonical shapes)
// ---------------------------------------------------------------------------
const ACTION_DEFS = {
  assign: { key: 'assign', label: '分配', type: 'primary', icon: null },
  transfer: { key: 'transfer', label: '转派', type: 'warning', icon: null },
  delete: { key: 'delete', label: '删除', type: 'danger', icon: null },
  viewProject: {
    key: 'viewProject',
    label: '查看项目',
    type: 'primary',
    icon: null,
  },
  edit: { key: 'edit', label: '编辑', type: 'primary', icon: 'edit' },
  editBasic: { key: 'editBasic', label: '编辑', type: 'primary', icon: 'edit' },
  editEvaluation: {
    key: 'editEvaluation',
    label: '编辑评估表',
    type: 'primary',
    icon: 'edit',
  },
  save: { key: 'save', label: '保存', type: 'primary', icon: null },
  cancel: { key: 'cancel', label: '取消', type: 'default', icon: null },
  bid: { key: 'bid', label: '立即投标', type: 'success', icon: null },
  abandon: { key: 'abandon', label: '放弃投标', type: 'danger', icon: null },
  reviewConfirm: { key: 'reviewConfirm', label: '确认审核', type: 'primary', icon: null },
  viewAnnouncement: {
    key: 'viewAnnouncement',
    label: '查看官网公告',
    type: 'default',
    icon: null,
  },
  nextStep: { key: 'nextStep', label: '下一步', type: 'primary', icon: null },
  prevStep: { key: 'prevStep', label: '上一步', type: 'default', icon: null },
  submit: { key: 'submit', label: '提交', type: 'primary', icon: null },
}

// ---------------------------------------------------------------------------
// Role grouping
// 'admin' (super admin), bid_admin and bid_lead always share the same column in the matrix.
// ---------------------------------------------------------------------------
function resolveRoleGroup(role) {
  if (role === 'admin' || role === 'bid_admin' || role === 'bid_lead' || role === 'bid_senior') return 'admin_lead'
  if (role === 'sales' || role === 'staff' || role === 'admin_staff') return 'sales'
  if (role === 'manager') return 'admin_lead'
  if (role === 'bid_specialist') return 'bid_specialist'
  return null
}

// ---------------------------------------------------------------------------
// Header actions matrix (status x role_group -> action keys)
// ---------------------------------------------------------------------------
const HEADER_MATRIX = {
  PENDING_ASSIGNMENT: {
    admin_lead: ['assign', 'delete'],
    sales: ({ currentUserId, creatorId }) =>
      currentUserId != null && currentUserId === creatorId
        ? ['edit', 'delete']
        : [],
    bid_specialist: ({ currentUserId, creatorId }) =>
      currentUserId != null && currentUserId === creatorId
        ? ['edit', 'delete']
        : [],
  },
  TRACKING: {
    admin_lead: ['transfer', 'delete'],
    sales: [],
    bid_specialist: [],
  },
  EVALUATED: {
    admin_lead: ['transfer'],
    sales: [],
    bid_specialist: [],
  },
  BIDDING: {
    admin_lead: ['viewProject'],
    sales: ['viewProject'],
    bid_specialist: ['viewProject'],
  },
  WON: {
    admin_lead: ['viewProject'],
    sales: ['viewProject'],
    bid_specialist: ['viewProject'],
  },
  LOST: {
    admin_lead: ['viewProject'],
    sales: ['viewProject'],
    bid_specialist: ['viewProject'],
  },
  ABANDONED: {
    admin_lead: [],
    sales: [],
    bid_specialist: [],
  },
}

// ---------------------------------------------------------------------------
// Bottom actions matrix (status x role -> action keys)
// 支持 role 级别和 role_group 级别的键，role 级别的优先级更高
// ---------------------------------------------------------------------------
const BOTTOM_MATRIX = {
  PENDING_ASSIGNMENT: {
    admin_lead: ['edit'],
    sales: [],
    bid_specialist: [],
  },
  TRACKING: {
    admin_lead: [],
    bid_lead: ['editBasic', 'editEvaluation', 'save', 'cancel'],
    bid_senior: ['editBasic', 'editEvaluation', 'save', 'cancel'],
    sales: ['nextStep', 'prevStep', 'submit'],
    bid_specialist: [],
  },
  EVALUATED: {
    admin_lead: ['bid', 'abandon'],
    sales: [],
    bid_specialist: [],
  },
  BIDDING: {
    admin_lead: [],
    sales: [],
    bid_specialist: [],
  },
  WON: {
    admin_lead: [],
    sales: [],
    bid_specialist: [],
  },
  LOST: {
    admin_lead: [],
    sales: [],
    bid_specialist: [],
  },
  ABANDONED: {
    admin_lead: [],
    sales: [],
    bid_specialist: [],
  },
}

// ---------------------------------------------------------------------------
// getHeaderActions - 获取顶部操作按钮列表
// ---------------------------------------------------------------------------
export function getHeaderActions(status, role, hasOriginalUrl, currentUserId, creatorId) {
  const group = resolveRoleGroup(role)
  if (!group) return []

  const statusActions = HEADER_MATRIX[status]
  if (!statusActions) return []

  let keys = statusActions[group]
  if (!keys) return []

  // 动态权限判定：函数类型的 keys 根据当前用户身份计算
  if (typeof keys === 'function') {
    keys = keys({ currentUserId, creatorId })
  }

  let result = keys.map((k) => ({ ...ACTION_DEFS[k] }))

  // bid_lead 不能有 delete 操作
  if (role === 'bid_lead') {
    result = result.filter((a) => a.key !== 'delete')
  }

  // viewAnnouncement 需要 originalUrl 才追加
  if (hasOriginalUrl && ACTION_DEFS.viewAnnouncement) {
    result.push({ ...ACTION_DEFS.viewAnnouncement })
  }

  return result
}

// ---------------------------------------------------------------------------
// getBottomActions - 获取底部操作按钮列表
// evaluationTabActive: true 表示当前在评估表 tab，false 表示在基本信息 tab
// ---------------------------------------------------------------------------
export function getBottomActions(status, role, _requiresReview, evaluationTabActive = false, evaluationSubmitted = false, currentUserId, creatorId) {
  const group = resolveRoleGroup(role)
  if (!group) return []

  const statusActions = BOTTOM_MATRIX[status]
  if (!statusActions) return []

  // 优先查 role 级别（如 bid_lead），再回退到 group 级别（如 admin_lead）
  let keys = statusActions[role]
  if (keys === undefined) {
    keys = statusActions[group]
  }
  if (!keys) return []

  // 动态权限判定
  if (typeof keys === 'function') {
    keys = keys({ currentUserId, creatorId })
  }

  let result = keys.map((k) => ({ ...ACTION_DEFS[k] }))

  // sales 角色的 TRACKING 状态：按钮受 tab 状态影响
  if (role === 'sales' && status === 'TRACKING') {
    if (evaluationTabActive) {
      // 评估表 tab: 显示「上一步」「提交」（提交成功后隐藏提交按钮）
      result = result.filter(a => a.key === 'prevStep' || (a.key === 'submit' && !evaluationSubmitted))
    } else {
      // 基本信息 tab: 只显示「下一步」
      result = result.filter(a => a.key === 'nextStep')
    }
  }

  return result
}

// ---------------------------------------------------------------------------
// shouldShowLogsTab - 判断是否显示日志 Tab（保留函数签名兼容性）
// ---------------------------------------------------------------------------
export function shouldShowLogsTab(_sourceType) {
  // 所有来源类型（第三方平台、CRM 商机、批量导入、人工录入）均显示操作日志 Tab
  return true
}
