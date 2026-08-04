import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  mocks,
  mountWorkbench,
  resetApiMocks,
  users,
} from './workbench-characterization.fixture.js'

// 背景：Workbench UI 改造（193c96325 / 8b2943fb8）移除了 QuickStart、Metrics、
// TenderList 等模块，改为 TodoCategoryCards（4 分类）、DeadlinePanels（3 tab）、
// WorkbenchCalendarRebuild、WorkbenchNotifications。原 case 2-7 依赖已移除的模块，
// 已删除。本测试现锁定当前渲染结构与日历事件推送契约。

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
  it('renders the welcome banner with role-specific greeting for known demo personas', async () => {
    const sales = await mountWorkbench(users.sales)
    expect(sales.text()).toContain('上午好，小王')
    sales.unmount()

    const manager = await mountWorkbench(users.manager)
    expect(manager.text()).toContain('上午好，张经理')
    manager.unmount()

    const bid_specialist = await mountWorkbench(users.bid_specialist)
    expect(bid_specialist.text()).toContain('上午好，李工')
    bid_specialist.unmount()

    const admin = await mountWorkbench(users.admin)
    expect(admin.text()).toContain('上午好，管理员')
    admin.unmount()
  })

  it('renders todo category cards (admin sees all four categories)', async () => {
    const wrapper = await mountWorkbench(users.admin)
    expect(wrapper.text()).toContain('任务·待办')
    expect(wrapper.text()).toContain('标讯·待办')
    expect(wrapper.text()).toContain('项目·待办')
    expect(wrapper.text()).toContain('资源·待办')
  })

  it('renders deadline panels with three deadline type tabs', async () => {
    const wrapper = await mountWorkbench(users.admin)
    expect(wrapper.text()).toContain('截止时间')
    expect(wrapper.text()).toContain('报名截止')
    expect(wrapper.text()).toContain('开标时间')
    expect(wrapper.text()).toContain('保证金截止')
  })

  it('renders the workbench calendar, AI prediction placeholder and notifications area', async () => {
    const wrapper = await mountWorkbench(users.admin)
    expect(wrapper.text()).toContain('投标日历')
    expect(wrapper.text()).toContain('AI 商机预测')
    expect(wrapper.text()).toContain('消息通知')
  })

  it('renders calendar events from API-loaded schedule overview', async () => {
    const wrapper = await mountWorkbench(users.manager)
    expect(wrapper.text()).toContain('数字政府项目截标')
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
