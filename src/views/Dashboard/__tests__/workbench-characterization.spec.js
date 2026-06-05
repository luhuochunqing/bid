import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  findByText,
  mocks,
  mountWorkbench,
  refreshWorkbench,
  resetApiMocks,
  users,
} from './workbench-characterization.fixture.js'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-04-22T09:00:00'))
  vi.clearAllMocks()
  resetApiMocks()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('Dashboard Workbench characterization', () => {
  it('renders the role-specific first screen sections for the known demo personas', async () => {
    const sales = await mountWorkbench(users.sales)
    expect(sales.text()).toContain('上午好，小王')
    expect(sales.text()).toContain('快速发起')
    expect(sales.text()).toContain('标书支持申请')
    // TenderList 不渲染：sales 用户无 bidding 权限，canViewTenderList=false
    sales.unmount()

    const manager = await mountWorkbench(users.manager)
    expect(manager.text()).toContain('上午好，张经理')
    expect(manager.text()).toContain('我的项目')
    expect(manager.text()).toContain('团队任务分配')
    manager.unmount()

    const staff = await mountWorkbench(users.staff)
    expect(staff.text()).toContain('上午好，李工')
    expect(staff.text()).toContain('我的任务')
    expect(staff.text()).toContain('待我评审')
    staff.unmount()

    const admin = await mountWorkbench(users.admin)
    expect(admin.text()).toContain('上午好，管理员')
    expect(admin.text()).toContain('团队绩效')
    expect(admin.text()).toContain('待审批事项')
    admin.unmount()
  })

  it('shows quick start only when the role permission snapshot grants it', async () => {
    const allowed = await mountWorkbench({ ...users.sales, menuPermissions: ['dashboard.quickStart'] })
    expect(allowed.text()).toContain('快速发起')
    allowed.unmount()

    const all = await mountWorkbench({ ...users.manager, menuPermissions: ['all'] })
    expect(all.text()).toContain('快速发起')
    all.unmount()

    const denied = await mountWorkbench({ ...users.staff, menuPermissions: ['dashboard'] })
    expect(denied.text()).not.toContain('快速发起')
    denied.unmount()
  })

  it('renders API-loaded summary, todos, approvals, process and calendar data on the page', async () => {
    const wrapper = await mountWorkbench(users.admin)

    expect(wrapper.text()).toContain('年度中标金额')
    expect(wrapper.text()).toContain('¥200万')
    expect(wrapper.text()).toContain('整体中标率')
    expect(wrapper.text()).toContain('48.6%')
    expect(wrapper.text()).toContain('总标讯数')
    expect(wrapper.text()).toContain('12条')
    expect(wrapper.text()).toContain('进行中项目')
    expect(wrapper.text()).toContain('4个')
    expect(wrapper.text()).toContain('API任务：完善技术方案')
    // AlertCard 组件未实现，alertGetUnresolved mock 仅作为数据契约存在，暂不断言渲染
    expect(wrapper.text()).toContain('数字政府项目 - 预算审批')
    expect(wrapper.text()).toContain('数字政府项目 - 标书支持申请')
    expect(wrapper.text()).toContain('数字政府项目截标')
    expect(wrapper.text()).toContain('本月节点1')
  })

  it('routes metric cards through the dashboard drilldown contract', async () => {
    const wrapper = await mountWorkbench(users.admin)
    const revenueCard = findByText(wrapper, '.metric-card', '年度中标金额')

    await revenueCard.trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: '/analytics/dashboard',
      query: { drilldown: 'revenue' },
    })
  })

  it('keeps quick actions wired to in-page real API request dialogs', async () => {
    const wrapper = await mountWorkbench(users.sales)
    const quickStart = wrapper.findComponent({ name: 'WorkbenchQuickStart' }).vm.quickStart

    await findByText(wrapper, '.quick-action-item', '标书支持申请').trigger('click')
    await refreshWorkbench(wrapper)
    expect(quickStart.supportRequestDialogVisible.value).toBe(true)
    expect(mocks.projectsGetList).toHaveBeenCalled()

    await quickStart.openBorrowDialog()
    expect(quickStart.borrowDialogVisible.value).toBe(true)
    expect(mocks.qualificationsGetList).toHaveBeenCalled()

    await quickStart.openExpenseDialog()
    expect(quickStart.expenseDialogVisible.value).toBe(true)
  })

  it('validates support requests and submits the current payload shape', async () => {
    const wrapper = await mountWorkbench(users.sales)
    const quickStart = wrapper.findComponent({ name: 'WorkbenchQuickStart' }).vm.quickStart

    await quickStart.openSupportDialog()
    await quickStart.submitSupportRequest()

    expect(mocks.messageWarning).toHaveBeenCalledWith('请填写需求说明')
    expect(mocks.approvalSubmitApproval).not.toHaveBeenCalled()

    quickStart.supportRequestForm.value.description = '  需要技术标评审和商务响应支持  '
    await quickStart.submitSupportRequest()

    expect(mocks.approvalSubmitApproval).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 101,
      projectName: '数字政府项目',
      approvalType: 'bid_support',
      title: '数字政府项目 - 标书支持申请',
      description: '需要技术标评审和商务响应支持',
      priority: 1,
    }))
    expect(mocks.messageSuccess).toHaveBeenCalledWith('标书支持申请已提交')
  })

  it('completes API-backed todo items without re-completing finished rows', async () => {
    const wrapper = await mountWorkbench(users.staff)
    const todo = findByText(wrapper, '.todo-item', 'API任务：完善技术方案')

    await todo.find('.todo-checkbox').trigger('click')

    expect(mocks.tasksComplete).toHaveBeenCalledWith(501)
    expect(mocks.messageSuccess).toHaveBeenCalledWith('完成任务: API任务：完善技术方案')
    expect(todo.text()).toContain('API任务：完善技术方案')

    await todo.find('.todo-checkbox').trigger('click')
    expect(mocks.tasksComplete).toHaveBeenCalledTimes(1)
  })

  it('pushes normalized calendar events into the bidding store after loading schedule overview', async () => {
    await mountWorkbench(users.manager)

    expect(mocks.scheduleGetOverview).toHaveBeenCalledWith({
      start: expect.any(Date),
      end: expect.any(Date),
      assigneeId: 8,
    })
    expect(mocks.setCalendar).toHaveBeenCalledWith([
      expect.objectContaining({
        id: 301,
        date: '2026-04-23',
        eventType: 'DEADLINE',
        type: 'deadline',
        title: '数字政府项目截标',
        projectId: 101,
        urgent: true,
      }),
    ])
  })
})
