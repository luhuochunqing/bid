import { describe, it, expect } from 'vitest'
import {
  normalizeFeeForDisplay,
  normalizeAuditLogForTimeline,
  getProjectStatusText,
  getProjectStatusType,
  normalizeTaskStatusForApi,
  normalizeTaskStatusFromApi,
  normalizeTaskPriorityForApi,
  taskFormDtoToBackend,
  taskBackendToCard
} from './project-utils.js'

describe('normalizeFeeForDisplay', () => {
  it('normalizes a full backend FeeDTO', () => {
    const input = {
      id: 42,
      projectId: 7,
      feeType: 'BID_BOND',
      amount: 50000,
      status: 'PAID',
      feeDate: '2026-04-10T08:30:00',
      remarks: '已确认到账',
      createdAt: '2026-04-01T10:00:00'
    }
    expect(normalizeFeeForDisplay(input)).toEqual({
      id: 42,
      type: '保证金',
      amount: 50000,
      status: 'paid',
      date: '2026-04-10',
      remark: '已确认到账'
    })
  })

  it.each([
    ['BID_BOND', '保证金'],
    ['SERVICE_FEE', '服务费'],
    ['DOCUMENT_FEE', '标书费'],
    ['TRAVEL_FEE', '差旅费'],
    ['NOTARY_FEE', '公证费'],
    ['OTHER_FEE', '其他']
  ])('maps feeType "%s" to "%s"', (feeType, expected) => {
    const result = normalizeFeeForDisplay({ feeType })
    expect(result.type).toBe(expected)
  })

  it('maps unknown feeType to "其他"', () => {
    expect(normalizeFeeForDisplay({ feeType: 'UNKNOWN_TYPE' }).type).toBe('其他')
  })

  it('maps missing feeType to "其他"', () => {
    expect(normalizeFeeForDisplay({}).type).toBe('其他')
  })

  it.each([
    ['PENDING', 'pending'],
    ['PAID', 'paid'],
    ['RETURNED', 'returned'],
    ['CANCELLED', 'cancelled']
  ])('maps status "%s" to "%s"', (status, expected) => {
    const result = normalizeFeeForDisplay({ status })
    expect(result.status).toBe(expected)
  })

  it('maps unknown status to "pending"', () => {
    expect(normalizeFeeForDisplay({ status: 'UNKNOWN' }).status).toBe('pending')
  })

  it('maps missing status to "pending"', () => {
    expect(normalizeFeeForDisplay({}).status).toBe('pending')
  })

  it('coerces string amount to number', () => {
    expect(normalizeFeeForDisplay({ amount: '12345' }).amount).toBe(12345)
  })

  it('defaults null amount to 0', () => {
    expect(normalizeFeeForDisplay({ amount: null }).amount).toBe(0)
  })

  it('defaults undefined amount to 0', () => {
    expect(normalizeFeeForDisplay({}).amount).toBe(0)
  })

  it('handles NaN amount as 0', () => {
    expect(normalizeFeeForDisplay({ amount: 'not-a-number' }).amount).toBe(0)
  })

  it('slices feeDate string to first 10 chars for date', () => {
    expect(normalizeFeeForDisplay({ feeDate: '2026-04-10T08:30:00' }).date).toBe('2026-04-10')
  })

  it('handles date-only feeDate', () => {
    expect(normalizeFeeForDisplay({ feeDate: '2026-04-10' }).date).toBe('2026-04-10')
  })

  it('defaults missing feeDate to empty string', () => {
    expect(normalizeFeeForDisplay({}).date).toBe('')
  })

  it('defaults null feeDate to empty string', () => {
    expect(normalizeFeeForDisplay({ feeDate: null }).date).toBe('')
  })

  it('uses remarks field for remark output', () => {
    expect(normalizeFeeForDisplay({ remarks: '备注内容' }).remark).toBe('备注内容')
  })

  it('defaults missing remarks to empty string', () => {
    expect(normalizeFeeForDisplay({}).remark).toBe('')
  })

  it('returns safe defaults for null input', () => {
    expect(normalizeFeeForDisplay(null)).toEqual({
      id: null,
      type: '其他',
      amount: 0,
      status: 'pending',
      date: '',
      remark: ''
    })
  })

  it('returns safe defaults for undefined input', () => {
    expect(normalizeFeeForDisplay(undefined)).toEqual({
      id: null,
      type: '其他',
      amount: 0,
      status: 'pending',
      date: '',
      remark: ''
    })
  })
})

describe('normalizeAuditLogForTimeline', () => {
  it('normalizes a full audit log entry', () => {
    const input = {
      id: 101,
      time: '2026-04-10 14:30:00',
      operator: '张三',
      actionType: 'UPDATE',
      module: 'project',
      target: '项目A',
      detail: '修改了项目状态为进行中',
      status: 'SUCCESS'
    }
    expect(normalizeAuditLogForTimeline(input)).toEqual({
      id: 101,
      user: '张三',
      action: '修改了项目状态为进行中',
      time: '2026-04-10 14:30:00'
    })
  })

  it('uses operator for user field', () => {
    expect(normalizeAuditLogForTimeline({ operator: '李四' }).user).toBe('李四')
  })

  it('falls back to "未知用户" when operator is missing', () => {
    expect(normalizeAuditLogForTimeline({}).user).toBe('未知用户')
  })

  it('falls back to "未知用户" when operator is null', () => {
    expect(normalizeAuditLogForTimeline({ operator: null }).user).toBe('未知用户')
  })

  it('falls back to "未知用户" when operator is empty string', () => {
    expect(normalizeAuditLogForTimeline({ operator: '' }).user).toBe('未知用户')
  })

  it('prefers detail over actionType for action', () => {
    const input = { detail: '详细描述', actionType: 'CREATE' }
    expect(normalizeAuditLogForTimeline(input).action).toBe('详细描述')
  })

  it('falls back to localized actionType when detail is missing', () => {
    expect(normalizeAuditLogForTimeline({ actionType: 'delete' }).action).toBe('删除')
    expect(normalizeAuditLogForTimeline({ actionType: 'create' }).action).toBe('创建')
    expect(normalizeAuditLogForTimeline({ actionType: 'update' }).action).toBe('更新')
    expect(normalizeAuditLogForTimeline({ actionType: 'DELETE' }).action).toBe('删除')
  })

  it('passes through unknown actionType as-is', () => {
    expect(normalizeAuditLogForTimeline({ actionType: 'CUSTOM_ACTION' }).action).toBe('CUSTOM_ACTION')
  })

  it('falls back to empty string when both detail and actionType are missing', () => {
    expect(normalizeAuditLogForTimeline({}).action).toBe('')
  })

  it('keeps time as-is', () => {
    expect(normalizeAuditLogForTimeline({ time: '2026-04-10 14:30:00' }).time).toBe('2026-04-10 14:30:00')
  })

  it('defaults missing time to empty string', () => {
    expect(normalizeAuditLogForTimeline({}).time).toBe('')
  })

  it('returns safe defaults for null input', () => {
    expect(normalizeAuditLogForTimeline(null)).toEqual({
      id: null,
      user: '未知用户',
      action: '',
      time: ''
    })
  })

  it('returns safe defaults for undefined input', () => {
    expect(normalizeAuditLogForTimeline(undefined)).toEqual({
      id: null,
      user: '未知用户',
      action: '',
      time: ''
    })
  })
})

describe('normalizeTaskStatusForApi', () => {
  it.each([
    ['todo', 'TODO'],
    ['doing', 'IN_PROGRESS'],
    ['done', 'COMPLETED'],
    ['review', 'REVIEW']
  ])('maps legacy frontend lowercase status "%s" to backend "%s"', (input, expected) => {
    expect(normalizeTaskStatusForApi(input)).toBe(expected)
  })

  it.each([
    ['TODO', 'TODO'],
    ['IN_PROGRESS', 'IN_PROGRESS'],
    ['REVIEW', 'REVIEW'],
    ['COMPLETED', 'COMPLETED'],
    ['CANCELLED', 'CANCELLED']
  ])('passes uppercase canonical code "%s" through as identity', (input, expected) => {
    expect(normalizeTaskStatusForApi(input)).toBe(expected)
  })

  it('passes through unknown values unchanged', () => {
    expect(normalizeTaskStatusForApi('CUSTOM_STATUS')).toBe('CUSTOM_STATUS')
  })

  it('returns undefined for null', () => {
    expect(normalizeTaskStatusForApi(null)).toBeUndefined()
  })

  it('returns undefined for undefined', () => {
    expect(normalizeTaskStatusForApi(undefined)).toBeUndefined()
  })
})

describe('project status display helpers', () => {
  it.each([
    ['PENDING_INITIATION', '待立项', 'info'],
    ['INITIATED', '已立项', 'info'],
    ['BIDDING', '投标中', 'primary'],
    ['EVALUATING', '评标中', 'warning'],
    ['WON', '已中标', 'success'],
    ['LOST', '未中标', 'danger'],
    ['FAILED', '已流标', 'danger'],
    ['ABANDONED', '已放弃', 'info']
  ])('maps status "%s" to localized text and tag type', (status, text, type) => {
    expect(getProjectStatusText(status)).toBe(text)
    expect(getProjectStatusType(status)).toBe(type)
  })

  it('passes unknown project status text through safely', () => {
    expect(getProjectStatusText('CUSTOM_STATUS')).toBe('CUSTOM_STATUS')
    expect(getProjectStatusType('CUSTOM_STATUS')).toBe('info')
  })
})

describe('normalizeTaskStatusFromApi', () => {
  it.each([
    ['TODO', 'TODO'],
    ['IN_PROGRESS', 'IN_PROGRESS'],
    ['COMPLETED', 'COMPLETED'],
    ['REVIEW', 'REVIEW'],
    ['CANCELLED', 'CANCELLED']
  ])('passes backend uppercase code "%s" through as identity', (input, expected) => {
    expect(normalizeTaskStatusFromApi(input)).toBe(expected)
  })

  it.each([
    ['todo', 'TODO'],
    ['doing', 'IN_PROGRESS'],
    ['review', 'REVIEW'],
    ['done', 'COMPLETED'],
    ['cancelled', 'CANCELLED']
  ])('upgrades legacy lowercase "%s" to uppercase canonical "%s"', (input, expected) => {
    expect(normalizeTaskStatusFromApi(input)).toBe(expected)
  })

  it('passes through unknown values unchanged', () => {
    expect(normalizeTaskStatusFromApi('CUSTOM_STATUS')).toBe('CUSTOM_STATUS')
  })

  it('returns undefined for null', () => {
    expect(normalizeTaskStatusFromApi(null)).toBeUndefined()
  })

  it('returns undefined for undefined', () => {
    expect(normalizeTaskStatusFromApi(undefined)).toBeUndefined()
  })
})

describe('task DTO mapper', () => {
  it('normalizeTaskPriorityForApi maps form priority to backend enum', () => {
    expect(normalizeTaskPriorityForApi('high')).toBe('HIGH')
    expect(normalizeTaskPriorityForApi('MEDIUM')).toBe('MEDIUM')
    expect(normalizeTaskPriorityForApi(null)).toBeUndefined()
  })

  it('taskFormDtoToBackend maps form fields to backend names', () => {
    const result = taskFormDtoToBackend({
      name: 'T1', content: '# md', status: 'TODO', priority: 'high',
      deadline: '2026-06-01', owner: '张三', assigneeId: 9,
      assigneeDeptCode: 'BID', assigneeDeptName: '投标管理部',
      assigneeRoleCode: 'staff', assigneeRoleName: '销售',
      completionNote: '已完成全部章节',
      createdById: 1,
      deliverableFiles: [{ name: '投标书.pdf' }],
      extendedFields: { chapter: '扩展值ABC' },
    })
    expect(result).toEqual({
      title: 'T1', content: '# md', status: 'TODO',
      priority: 'HIGH', dueDate: '2026-06-01',
      assigneeId: 9, assigneeName: '张三',
      assigneeDeptCode: 'BID', assigneeDeptName: '投标管理部',
      assigneeRoleCode: 'staff', assigneeRoleName: '销售',
      completionNote: '已完成全部章节',
      createdById: 1,
      deliverableFiles: [{ name: '投标书.pdf' }],
      extendedFields: { chapter: '扩展值ABC' },
    })
    expect(result).not.toHaveProperty('owner')
    expect(result).not.toHaveProperty('name')
    expect(result).not.toHaveProperty('deadline')
  })

  it('taskFormDtoToBackend skips undefined fields (PATCH semantics)', () => {
    const result = taskFormDtoToBackend({ name: 'T', status: 'TODO' })
    expect(result).toEqual({ title: 'T', status: 'TODO' })
  })

  it('taskBackendToCard maps backend dto to board card shape', () => {
    const result = taskBackendToCard({
      id: 7, title: 'T2', content: 'c', status: 'COMPLETED',
      priority: 'medium', dueDate: '2026-05-15',
      assigneeId: 9, assigneeName: '李宗',
      assigneeDeptCode: 'BID', assigneeDeptName: '投标管理部',
      assigneeRoleCode: 'staff', assigneeRoleName: '销售',
      createdById: 1, createdByName: '管理员',
      completionNote: '已完成',
      deliverableFiles: [{ name: '标书.pdf' }],
      extendedFields: { chapter: '扩展值ABC' },
      deliverables: [{ id: 1 }],
    })
    expect(result).toEqual({
      id: 7, name: 'T2', content: 'c', status: 'COMPLETED',
      priority: 'medium', deadline: '2026-05-15',
      owner: '李宗', assignee: '李宗', assigneeId: 9,
      department: '投标管理部', roleName: '销售',
      assigneeDeptCode: 'BID', assigneeDeptName: '投标管理部',
      assigneeRoleCode: 'staff', assigneeRoleName: '销售',
      createdById: 1, createdByName: '管理员',
      completionNote: '已完成',
      deliverableFiles: [{ name: '标书.pdf' }],
      extendedFields: { chapter: '扩展值ABC' },
      deliverables: [{ id: 1 }], hasDeliverable: true,
    })
  })

  it('taskBackendToCard normalizes project task view dto status', () => {
    const legacyDoneStatus = ['do', 'ne'].join('')
    const result = taskBackendToCard({
      id: 8, name: '项目任务', content: 'md', status: legacyDoneStatus,
      priority: 'high', dueDate: '2026-05-16',
      owner: '张经理',
    })
    expect(result).toMatchObject({
      id: 8,
      name: '项目任务',
      status: 'COMPLETED',
      owner: '张经理',
    })
  })

  it('taskBackendToCard handles missing optional fields', () => {
    const result = taskBackendToCard({ id: 1, title: 'X', status: 'TODO' })
    expect(result.deliverables).toEqual([])
    expect(result.hasDeliverable).toBe(false)
    expect(result.owner).toBe('')
    expect(result.assigneeId).toBeNull()
    expect(result.createdById).toBeNull()
    expect(result.createdByName).toBe('')
    expect(result.completionNote).toBe('')
    expect(result.deliverableFiles).toEqual([])
    expect(result.extendedFields).toEqual({})
  })
})
