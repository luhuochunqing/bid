// Input: backend FeeDTO, AuditLogItemDTO, project/task status strings
// Output: pure normalizer and display functions for project data transformations
// Pos: src/views/Project/ - Project module utilities
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const FEE_TYPE_MAP = {
  BID_BOND: '保证金',
  SERVICE_FEE: '服务费',
  DOCUMENT_FEE: '标书费',
  TRAVEL_FEE: '差旅费',
  NOTARY_FEE: '公证费',
  OTHER_FEE: '其他'
}

const FEE_STATUS_MAP = {
  PENDING: 'pending',
  PAID: 'paid',
  RETURNED: 'returned',
  CANCELLED: 'cancelled'
}

const TASK_STATUS_TO_API = {
  // legacy lowercase bridge (for any call site still sending lowercase)
  todo: 'TODO',
  doing: 'IN_PROGRESS',
  review: 'REVIEW',
  done: 'COMPLETED',
  cancelled: 'CANCELLED',
  // identity for uppercase codes (backend canonical form)
  TODO: 'TODO',
  IN_PROGRESS: 'IN_PROGRESS',
  REVIEW: 'REVIEW',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED'
}

const TASK_STATUS_FROM_API = {
  // identity for uppercase codes (canonical)
  TODO: 'TODO',
  IN_PROGRESS: 'IN_PROGRESS',
  REVIEW: 'REVIEW',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  // legacy lowercase compatibility — backend should no longer return these,
  // but keeps old data paths deterministic during migration.
  todo: 'TODO',
  doing: 'IN_PROGRESS',
  review: 'REVIEW',
  done: 'COMPLETED',
  cancelled: 'CANCELLED'
}

const TASK_PRIORITY_TO_API = {
  low: 'LOW',
  medium: 'MEDIUM',
  high: 'HIGH',
  urgent: 'URGENT',
  LOW: 'LOW',
  MEDIUM: 'MEDIUM',
  HIGH: 'HIGH',
  URGENT: 'URGENT'
}

const PROJECT_STATUS_TEXT = {
  PENDING_INITIATION: '待立项',
  INITIATED: '已立项',
  BIDDING: '投标中',
  EVALUATING: '评标中',
  WON: '已中标',
  LOST: '未中标',
  FAILED: '已流标',
  ABANDONED: '已放弃',
}

const PROJECT_STATUS_TYPE = {
  PENDING_INITIATION: 'info',
  INITIATED: 'info',
  BIDDING: 'primary',
  EVALUATING: 'warning',
  WON: 'success',
  LOST: 'danger',
  FAILED: 'danger',
  ABANDONED: 'info',
}

const ACTION_TYPE_LABEL = {
  create: '创建',
  update: '更新',
  delete: '删除',
  query: '查询',
  login: '登录',
  logout: '登出',
  approve: '审批通过',
  reject: '审批驳回',
  submit: '提交',
  export: '导出',
  import: '导入'
}

/**
 * Backend FeeDTO → frontend display shape
 */
export function normalizeFeeForDisplay(backendFee) {
  if (backendFee == null) {
    return { id: null, type: '其他', amount: 0, status: 'pending', date: '', remark: '' }
  }

  const rawAmount = backendFee.amount != null ? Number(backendFee.amount) : 0
  const amount = Number.isNaN(rawAmount) ? 0 : rawAmount

  const feeDate = backendFee.feeDate
  const date = typeof feeDate === 'string' && feeDate.length > 0
    ? feeDate.slice(0, 10)
    : ''

  return {
    id: backendFee.id !== undefined ? backendFee.id : null,
    type: FEE_TYPE_MAP[backendFee.feeType] || '其他',
    amount,
    status: FEE_STATUS_MAP[backendFee.status] || 'pending',
    date,
    remark: backendFee.remarks || ''
  }
}

/**
 * Backend AuditLogItemDTO → frontend activity timeline shape
 */
export function normalizeAuditLogForTimeline(auditLog) {
  if (auditLog == null) {
    return { id: null, user: '未知用户', action: '', time: '' }
  }

  const actionTypeLabel = auditLog.actionType
    ? (ACTION_TYPE_LABEL[String(auditLog.actionType).toLowerCase()] || auditLog.actionType)
    : ''

  return {
    id: auditLog.id !== undefined ? auditLog.id : null,
    user: auditLog.operator || '未知用户',
    action: auditLog.detail || actionTypeLabel || '',
    time: auditLog.time || ''
  }
}

export function getProjectStatusText(status) {
  if (status == null || status === '') return ''
  return PROJECT_STATUS_TEXT[status] || status
}

export function getProjectStatusType(status) {
  return PROJECT_STATUS_TYPE[status] || 'info'
}

/**
 * Frontend task status → backend enum
 */
export function normalizeTaskStatusForApi(frontendStatus) {
  if (frontendStatus == null) return undefined
  return TASK_STATUS_TO_API[frontendStatus] || frontendStatus
}

export function normalizeTaskPriorityForApi(frontendPriority) {
  if (frontendPriority == null) return undefined
  return TASK_PRIORITY_TO_API[frontendPriority] || frontendPriority
}

/**
 * Backend task status enum → frontend status
 */
export function normalizeTaskStatusFromApi(backendStatus) {
  if (backendStatus == null) return undefined
  return TASK_STATUS_FROM_API[backendStatus] || backendStatus
}

/**
 * 把 TaskForm.vue 表单字段（前端命名）转成后端 TaskDTO 字段。
 *  - name → title
 *  - deadline → dueDate
 *  - owner / assigneeId → assigneeName / assigneeId
 *  - extendedFields → extendedFields
 *  - undefined 字段不写入，保留 PATCH 语义
 */
export function taskFormDtoToBackend(form = {}) {
  const dto = {}
  if (form.name !== undefined) dto.title = form.name
  if (form.content !== undefined) dto.content = form.content
  if (form.status !== undefined) dto.status = normalizeTaskStatusForApi(form.status)
  if (form.priority !== undefined) dto.priority = normalizeTaskPriorityForApi(form.priority)
  if (form.deadline !== undefined) dto.dueDate = form.deadline
  if (form.assigneeId !== undefined) dto.assigneeId = form.assigneeId
  if (form.owner !== undefined) dto.assigneeName = form.owner
  if (form.assigneeDeptCode !== undefined) dto.assigneeDeptCode = form.assigneeDeptCode
  if (form.assigneeDeptName !== undefined) dto.assigneeDeptName = form.assigneeDeptName
  if (form.assigneeRoleCode !== undefined) dto.assigneeRoleCode = form.assigneeRoleCode
  if (form.assigneeRoleName !== undefined) dto.assigneeRoleName = form.assigneeRoleName
  if (form.completionNote !== undefined) dto.completionNote = form.completionNote
  if (form.createdById !== undefined) dto.createdById = form.createdById
  if (form.deliverableFiles !== undefined) dto.deliverableFiles = form.deliverableFiles
  if (form.extendedFields !== undefined) dto.extendedFields = form.extendedFields
  return dto
}

/**
 * 把后端 TaskDTO 转回看板卡片字段（前端命名 + 计算字段）。
 */
export function taskBackendToCard(dto = {}) {
  const deliverables = Array.isArray(dto.deliverables) ? dto.deliverables : []
  const assigneeName = dto.assigneeName ?? dto.owner ?? dto.assignee ?? ''
  return {
    id: dto.id,
    name: dto.title ?? dto.name ?? '',
    content: dto.content ?? '',
    status: normalizeTaskStatusFromApi(dto.status),
    priority: dto.priority,
    deadline: dto.dueDate ?? '',
    owner: assigneeName,
    assignee: assigneeName,
    assigneeId: dto.assigneeId ?? null,
    department: dto.assigneeDeptName ?? dto.department ?? '',
    roleName: dto.assigneeRoleName ?? dto.roleName ?? '',
    assigneeDeptCode: dto.assigneeDeptCode ?? '',
    assigneeDeptName: dto.assigneeDeptName ?? dto.department ?? '',
    assigneeRoleCode: dto.assigneeRoleCode ?? '',
    assigneeRoleName: dto.assigneeRoleName ?? dto.roleName ?? '',
    createdById: dto.createdById ?? null,
    createdByName: dto.createdByName ?? dto.createdBy ?? '',
    completionNote: dto.completionNote ?? '',
    deliverableFiles: dto.deliverableFiles ?? [],
    extendedFields: dto.extendedFields || {},
    deliverables,
    hasDeliverable: deliverables.length > 0,
  }
}
