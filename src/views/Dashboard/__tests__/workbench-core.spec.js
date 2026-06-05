import { describe, expect, it } from 'vitest'
import {
  approvalStatusToProcessStatus,
  buildContractBorrowPayload,
  buildQualificationBorrowPayload,
  buildQuickExpensePayload,
  buildSupportRequestPayload,
  cleanDisplayText,
  createDefaultSupportRequestForm,
  createDefaultQuickExpenseForm,
  filterProjectsByRole,
  formatCount,
  formatCurrency,
  formatPercent,
  formatRelativeTime,
  getBannerActionConfig,
  getBannerSubtitle,
  getBannerTitle,
  getMetricRouteTarget,
  getPriorityLabel,
  getPriorityType,
  getProgressColor,
  getProjectStatusType,
  getRoleMetrics,
  hasQuickStartPermission,
  mergePriorityTodos,
  normalizeApiTodo,
  normalizePendingApproval,
  normalizeProcess,
  normalizeSupportProjects,
  validateBorrowRequest,
  validateQuickExpense,
  validateSupportRequest,
} from '@/views/Dashboard/workbench-core.js'

describe('workbench core formatters', () => {
  it('formats money, percent, count, and relative time without component state', () => {
    expect(formatCurrency(2340000)).toBe('¥234万')
    expect(formatCurrency(5000)).toBe('¥5000')
    expect(formatCurrency(undefined)).toBe('--')
    expect(formatPercent(66.666)).toBe('66.7%')
    expect(formatPercent('bad')).toBe('--')
    expect(formatCount(3, '项')).toBe('3项')
    expect(formatRelativeTime('2026-04-22T09:30:00', new Date('2026-04-22T11:30:00'))).toBe('2小时前')
  })
})

describe('workbench role model', () => {
  it('builds admin metrics from summary DTO values', () => {
    const metrics = getRoleMetrics('admin', {
      summaryStats: { totalBudget: 880000, successRate: 72.5, totalTenders: 18, activeProjects: 6 },
    })

    expect(metrics.map((item) => [item.key, item.value, item.icon])).toEqual([
      ['totalRevenue', '¥88万', 'TrendCharts'],
      ['winRate', '72.5%', 'Flag'],
      ['totalTenders', '18条', 'Document'],
      ['activeProjects', '6个', 'Briefcase'],
    ])
    expect(metrics[0]).toMatchObject({ variant: 'green', changeClass: 'neutral' })
  })

  it('builds manager and staff metrics with role-specific labels', () => {
    expect(getRoleMetrics('manager', { myProjectCount: 2, pendingCount: 5 })[0]).toMatchObject({
      key: 'myProjects',
      label: '负责项目',
      value: '2个',
    })
    expect(getRoleMetrics('staff', { completedTodoCount: 4 })[1]).toMatchObject({
      key: 'completedThisWeek',
      label: '已完成',
      value: '4项',
    })
  })

  it('builds banner copy and action route targets without router handlers', () => {
    expect(getBannerTitle('小王', 8)).toBe('上午好，小王')
    expect(getBannerSubtitle('admin', {
      currentDate: '2026年4月22日星期三',
      summaryStats: { activeProjects: 9 },
      pendingApprovalsTotalCount: 2,
    })).toBe('今天是2026年4月22日星期三，团队有9个进行中的项目，2个待审批事项')
    expect(getBannerActionConfig('manager')).toEqual([
      { key: 'projects', label: '我的项目', type: 'primary', icon: 'Briefcase', target: '/project' },
      { key: 'tenders', label: '查看标讯', type: 'default', icon: 'Document', target: '/bidding' },
    ])
  })
})

describe('workbench project and todo core', () => {
  const projects = [
    { id: 1, manager: '小王', priority: 'high', status: '编制中' },
    { id: 2, manager: '张经理', priority: 'medium', status: '评审中' },
    { id: 3, manager: '小王', priority: 'urgent', status: '已归档' },
    { id: 4, manager: '李工', priority: 'low', status: '投标中' },
  ]

  it('filters visible active projects by role priority and limit', () => {
    expect(filterProjectsByRole(projects, { role: 'admin' }).map((item) => item.id)).toEqual([1, 2, 4])
    expect(filterProjectsByRole(projects, { role: 'manager', userName: '小王', limit: 2 }).map((item) => item.id)).toEqual([1, 2])
    expect(filterProjectsByRole(null, { role: 'admin' })).toEqual([])
  })

  it('normalizes task DTOs and merges alert todos before API todos', () => {
    const todo = normalizeApiTodo({
      id: 7,
      title: '补齐资质文件',
      priority: 'HIGH',
      dueDate: '2026-04-22T09:30:00',
      status: 'PENDING',
    })

    expect(todo).toMatchObject({
      id: 7,
      title: '补齐资质文件',
      priority: 'high',
      done: false,
      type: 'task',
      sourceType: 'task',
      rawStatus: 'PENDING',
    })
    expect(todo.deadline).toContain('04')
    expect(mergePriorityTodos([{ id: 'alert-1' }], [{ id: 2 }, { id: 3 }], 2)).toEqual([{ id: 'alert-1' }, { id: 2 }])
  })
})

describe('workbench approval and support core', () => {
  it('cleans mojibake project names before showing process/activity text', () => {
    const mojibakeProject = 'QAå¿«é€Ÿå‘èµ·æµ‹è¯•é¡¹ç›®'

    expect(cleanDisplayText(mojibakeProject)).toBe('QA快速发起测试项目')
    expect(normalizeProcess({
      id: 8,
      projectName: mojibakeProject,
      typeName: 'bid_support',
      title: `${mojibakeProject} - 标书支持申请`,
      approvalType: 'bid_support',
      status: 'PENDING',
      description: 'QA验证：请协助准备技术标和商务响应材料',
      submitTime: '2026-04-25T11:53:07.59642',
    })).toMatchObject({
      title: 'QA快速发起测试项目 - 标书支持申请',
      description: 'QA验证：请协助准备技术标和商务响应材料',
    })
  })

  it('normalizes pending approval and my process DTOs', () => {
    expect(normalizePendingApproval({
      id: 1,
      projectName: '智慧办公',
      typeName: '标书支持',
      approvalType: 'bid_support',
      applicantDept: '销售部',
      submitTime: '10:00',
    })).toMatchObject({
      title: '智慧办公 - 标书支持',
      type: 'bid_support',
      department: '销售部',
      time: '10:00',
    })

    expect(approvalStatusToProcessStatus('REJECTED')).toBe('urgent')
    expect(normalizeProcess({
      id: 2,
      projectName: '智慧办公',
      typeName: '资质借阅',
      status: 'PENDING',
      submitTime: '11:00',
    })).toEqual({
      id: 2,
      title: '智慧办公 - 资质借阅',
      status: 'pending',
      description: '暂无说明',
      progress: 55,
      time: '11:00',
    })
  })

  it('normalizes support project options, validates form, and builds submit payload', () => {
    const projects = normalizeSupportProjects([
      { id: '12', projectName: '智慧办公' },
      { id: 'bad', name: '无效项目' },
    ])
    const form = createDefaultSupportRequestForm(projects)

    expect(projects).toEqual([{ id: 12, name: '智慧办公' }])
    expect(form).toMatchObject({ projectId: 12, type: 'bid_support' })
    expect(validateSupportRequest({ ...form, description: '' })).toEqual({ valid: false, message: '请填写需求说明' })
    expect(validateSupportRequest({ ...form, description: '  需要技术方案支持  ' })).toEqual({ valid: true, message: '' })
    expect(buildSupportRequestPayload({
      ...form,
      type: 'technical_support',
      dueDate: '2026-04-24T18:00:00',
      description: '  需要技术方案支持  ',
    }, projects)).toEqual({
      projectId: 12,
      projectName: '智慧办公',
      approvalType: 'technical_support',
      title: '智慧办公 - 标书支持申请',
      description: '需要技术方案支持',
      dueDate: '2026-04-24T18:00:00',
      priority: 1,
    })
  })

  it('checks quick-start permission from configured role menu permissions', () => {
    expect(hasQuickStartPermission({ menuPermissions: ['dashboard.quickStart'] })).toBe(true)
    expect(hasQuickStartPermission({ menuPermissions: ['all'] })).toBe(true)
    expect(hasQuickStartPermission({ menuPermissions: ['dashboard'] })).toBe(false)
  })

  it('builds borrow and quick expense payloads for real API submission', () => {
    const qualificationForm = {
      mode: 'qualification',
      projectId: 9,
      qualificationId: 12,
      borrowerName: '小王',
      borrowerDept: '销售部',
      purpose: '投标使用',
      expectedReturnDate: '2026-05-01',
      remark: '原件',
    }
    expect(validateBorrowRequest(qualificationForm)).toEqual({ valid: true, message: '' })
    expect(buildQualificationBorrowPayload(qualificationForm)).toEqual({
      borrower: '小王',
      department: '销售部',
      projectId: '9',
      purpose: '投标使用',
      returnDate: '2026-05-01',
      remark: '原件',
    })

    const contractForm = {
      mode: 'contract',
      contractNo: 'HT-01',
      contractName: '框架合同',
      sourceName: '合同库',
      borrowerName: '小王',
      borrowerDept: '销售部',
      customerName: '客户A',
      purpose: '投标证明',
      borrowType: '复印件',
      expectedReturnDate: '2026-05-02',
    }
    expect(buildContractBorrowPayload(contractForm)).toMatchObject({
      contractNo: 'HT-01',
      contractName: '框架合同',
      expectedReturnDate: '2026-05-02',
    })

    const expenseForm = { ...createDefaultQuickExpenseForm([{ id: 9 }]), amount: 1000, type: '标书购买费' }
    expect(validateQuickExpense(expenseForm)).toEqual({ valid: true, message: '' })
    expect(buildQuickExpensePayload(expenseForm, { today: '2026-04-25', createdBy: '小王' })).toEqual({
      projectId: 9,
      category: 'OTHER',
      amount: 1000,
      date: '2026-04-25',
      expenseType: '标书购买费',
      expectedReturnDate: null,
      description: '',
      createdBy: '小王',
    })
  })
})

describe('workbench route targets and UI mappings', () => {
  it('maps metric keys to route targets', () => {
    expect(getMetricRouteTarget('totalRevenue')).toEqual({
      path: '/analytics/dashboard',
      query: { drilldown: 'revenue' },
    })
    expect(getMetricRouteTarget({ key: 'myProjects' })).toBe('/project')
    expect(getMetricRouteTarget('unknown')).toBeUndefined()
  })

  it('maps project progress, statuses, and priorities to UI tokens', () => {
    expect(getProgressColor(85)).toBe('#059669')
    expect(getProgressColor(10)).toBe('#EF4444')
    expect(getProjectStatusType('评审中')).toBe('primary')
    expect(getPriorityType('medium')).toBe('warning')
    expect(getPriorityLabel('high')).toBe('高')
    expect(getPriorityLabel('urgent')).toBe('urgent')
  })
})
