// Input: 纯核心函数入参（events / todos / projects / approvals / deadlineStats）
// Output: workbench-rebuild-core 纯核心函数单元测试
// Pos: src/views/Dashboard/__tests__ - Dashboard 纯核心测试
import { describe, it, expect } from 'vitest'
import {
  classifyDeadlineEvent,
  buildDeadlinePanels,
  buildTodoCategoryCards,
  buildWelcomeStats,
  formatCountdown,
} from '@/views/Dashboard/workbench-rebuild-core.js'

describe('classifyDeadlineEvent', () => {
  it('标题含"报名"归为 signup', () => {
    expect(classifyDeadlineEvent({ title: '智慧城市项目 - 报名截止' })).toBe('signup')
  })

  it('标题含"开标"归为 opening', () => {
    expect(classifyDeadlineEvent({ title: '市政桥梁 - 开标时间' })).toBe('opening')
  })

  it('标题含"保证金"归为 deposit', () => {
    expect(classifyDeadlineEvent({ title: '保证金截止提醒' })).toBe('deposit')
  })

  it('无法识别返回 null', () => {
    expect(classifyDeadlineEvent({ title: '普通提醒' })).toBeNull()
  })

  it('空值返回 null', () => {
    expect(classifyDeadlineEvent(null)).toBeNull()
    expect(classifyDeadlineEvent({})).toBeNull()
  })
})

describe('formatCountdown', () => {
  const today = new Date('2026-07-15')

  it('0 天显示"今天"', () => {
    expect(formatCountdown('2026-07-15', today)).toEqual({ text: '今天', cls: 'urgent' })
  })

  it('1 天显示"明天"', () => {
    expect(formatCountdown('2026-07-16', today)).toEqual({ text: '明天', cls: 'urgent' })
  })

  it('2 天显示"后天"', () => {
    expect(formatCountdown('2026-07-17', today)).toEqual({ text: '后天', cls: 'urgent' })
  })

  it('3-7 天显示"N 天后"且 cls=warn', () => {
    expect(formatCountdown('2026-07-18', today)).toEqual({ text: '3 天后', cls: 'warn' })
    expect(formatCountdown('2026-07-22', today)).toEqual({ text: '7 天后', cls: 'warn' })
  })

  it('超过 7 天显示"N 天后"且 cls=ok', () => {
    expect(formatCountdown('2026-07-23', today)).toEqual({ text: '8 天后', cls: 'ok' })
  })

  it('过期显示"已过期"', () => {
    expect(formatCountdown('2026-07-14', today)).toEqual({ text: '已过期', cls: 'ok' })
  })

  it('无效日期返回安全默认', () => {
    expect(formatCountdown('', today)).toEqual({ text: '--', cls: 'ok' })
    expect(formatCountdown('invalid', today)).toEqual({ text: '--', cls: 'ok' })
  })
})

describe('buildDeadlinePanels', () => {
  const today = new Date('2026-07-15')
  const events = [
    { projectId: 1, title: '智慧城市项目 - 报名截止', date: '2026-07-15' },
    { projectId: 2, title: '市政桥梁 - 开标', date: '2026-07-16' },
    { projectId: 3, title: '保证金截止', date: '2026-07-18' },
    { projectId: 4, title: '普通提醒', date: '2026-07-20' },
    { projectId: 5, title: '医院项目 - 报名截止', date: '2026-07-20' },
  ]

  it('按 period=today 过滤今天的事件', () => {
    const panels = buildDeadlinePanels(events, 'today', today)
    expect(panels.signup).toHaveLength(1)
    expect(panels.signup[0].name).toBe('智慧城市项目 - 报名截止')
    expect(panels.signup[0].projectId).toBe(1)
    expect(panels.opening).toHaveLength(0)
  })

  it('按 period=week 过滤本周（今天起 7 天内）', () => {
    const panels = buildDeadlinePanels(events, 'week', today)
    expect(panels.signup.length).toBeGreaterThanOrEqual(1)
    expect(panels.opening).toHaveLength(1)
    expect(panels.deposit).toHaveLength(1)
  })

  it('按 period=month 过滤本月（今天起 30 天内）', () => {
    const panels = buildDeadlinePanels(events, 'month', today)
    expect(panels.signup).toHaveLength(2)
    expect(panels.opening).toHaveLength(1)
  })

  it('每项包含 name/date/countdown/countdownCls/projectId', () => {
    const panels = buildDeadlinePanels(events, 'today', today)
    const item = panels.signup[0]
    expect(item).toHaveProperty('name')
    expect(item).toHaveProperty('date')
    expect(item).toHaveProperty('countdown')
    expect(item).toHaveProperty('countdownCls')
    expect(item).toHaveProperty('projectId')
  })

  it('无法分类的事件不进入任何列', () => {
    const panels = buildDeadlinePanels(events, 'month', today)
    expect(panels.signup.find((i) => i.projectId === 4)).toBeUndefined()
    expect(panels.opening.find((i) => i.projectId === 4)).toBeUndefined()
    expect(panels.deposit.find((i) => i.projectId === 4)).toBeUndefined()
  })

  it('空 events 返回三列空数组', () => {
    const panels = buildDeadlinePanels([], 'month', today)
    expect(panels.signup).toEqual([])
    expect(panels.opening).toEqual([])
    expect(panels.deposit).toEqual([])
  })
})

describe('buildTodoCategoryCards', () => {
  it('admin 角色从四类数据源构建 4 张卡片', () => {
    const cards = buildTodoCategoryCards({
      role: 'admin',
      taskTodos: [
        { id: 1, title: '商务标编制', done: false, deadline: '2026-07-15', projectId: 100 },
      ],
      tenderTodos: [
        { id: 10, title: '智慧城市标讯', registrationDeadline: '2026-07-17' },
      ],
      projectTodos: [
        { id: 20, name: '智慧水务项目', status: '待立项' },
      ],
      resourceTodos: [
        { applicationId: 30, resourceLabel: 'CA 申请 - 王五', applicantName: '王五', applicationType: 'CA' },
      ],
    })

    expect(cards).toHaveLength(4)
    expect(cards[0].key).toBe('task')
    expect(cards[0].count).toBe(1)
    expect(cards[0].items[0].name).toBe('商务标编制')

    expect(cards[1].key).toBe('tender')
    expect(cards[1].count).toBe(1)
    expect(cards[1].items[0].name).toBe('智慧城市标讯')

    expect(cards[2].key).toBe('project')
    expect(cards[2].count).toBe(1)

    expect(cards[3].key).toBe('resource')
    expect(cards[3].count).toBe(1)
  })

  it('admin 角色每张卡片含 title/count/accent/items', () => {
    const cards = buildTodoCategoryCards({ role: 'admin' })
    expect(cards).toHaveLength(4)
    cards.forEach((card) => {
      expect(card).toHaveProperty('key')
      expect(card).toHaveProperty('title')
      expect(card).toHaveProperty('count')
      expect(card).toHaveProperty('accent')
      expect(card).toHaveProperty('items')
      expect(Array.isArray(card.items)).toBe(true)
    })
  })

  it('空数据源返回 count=0 且 items 为空数组', () => {
    const cards = buildTodoCategoryCards({ role: 'admin' })
    cards.forEach((card) => {
      expect(card.count).toBe(0)
      expect(card.items).toEqual([])
    })
  })

  it('items 显示全部（卡片内部滚动，不再截断）', () => {
    // CO-596: 移除 slice(0, MAX_TODO_CARD_ITEMS) 截断，4 个卡片改为固定高度+内部滚动
    const cards = buildTodoCategoryCards({
      role: 'admin',
      taskTodos: Array.from({ length: 10 }, (_, i) => ({
        id: i, title: `t${i}`, done: false, projectId: i + 1,
      })),
    })
    expect(cards[0].items).toHaveLength(10)
    // count 与 items.length 一致（修复 Bug 1：右上角数字与显示条数不符）
    expect(cards[0].count).toBe(10)
  })

  it('每条 item 含 name/rightText/id', () => {
    const cards = buildTodoCategoryCards({
      role: 'admin',
      taskTodos: [{ id: 1, title: '任务1', done: false, deadline: '2026-07-15', projectId: 100 }],
    })
    const item = cards[0].items[0]
    expect(item).toHaveProperty('name')
    expect(item).toHaveProperty('rightText')
    expect(item).toHaveProperty('id')
  })

  it('P0-5.1: taskTodos 中 projectId=null 的条目不进入 items', () => {
    const cards = buildTodoCategoryCards({
      role: 'admin',
      taskTodos: [
        { id: 1, title: '有项目任务', done: false, projectId: 100 },
        { id: 2, title: '无项目任务', done: false, projectId: null },
      ],
    })
    expect(cards[0].items).toHaveLength(1)
    expect(cards[0].items[0].id).toBe(1)
    // count 与 items.length 一致（修复 Bug 1：右上角数字与显示条数不符）
    expect(cards[0].count).toBe(1)
  })

  it('bid-Team 角色不显示 tender 卡片但显示 project 卡片', () => {
    const cards = buildTodoCategoryCards({
      role: 'bid-Team',
      taskTodos: [{ id: 1, title: 't1', done: false, projectId: 1 }],
      tenderTodos: [{ id: 10, title: '不应显示' }],
      projectTodos: [{ id: 20, name: '应显示' }],
      resourceTodos: [{ applicationId: 30, resourceLabel: 'r1' }],
    })
    expect(cards).toHaveLength(3)
    expect(cards.map((c) => c.key)).toEqual(['task', 'project', 'resource'])
  })

  it('bid-otherDept 角色只显示 task + resource 卡片', () => {
    const cards = buildTodoCategoryCards({
      role: 'bid-otherDept',
      taskTodos: [{ id: 1, title: 't1', done: false, projectId: 1 }],
      tenderTodos: [{ id: 10, title: '不应显示' }],
      projectTodos: [{ id: 20, name: '不应显示' }],
      resourceTodos: [{ applicationId: 30, resourceLabel: 'r1' }],
    })
    expect(cards).toHaveLength(2)
    expect(cards.map((c) => c.key)).toEqual(['task', 'resource'])
  })
})

describe('buildWelcomeStats', () => {
  it('构建 4 个统计数字', () => {
    const stats = buildWelcomeStats({
      pendingCount: 8,
      myProjectCount: 12,
      deadlineStats: {
        registrationDeadline: { todayCount: 3 },
        bidOpening: { todayCount: 37 },
      },
    })
    expect(stats).toHaveLength(4)
    expect(stats[0]).toEqual({ label: '待办任务', value: 8 })
    expect(stats[1]).toEqual({ label: '待办项目', value: 12 })
    expect(stats[2]).toEqual({ label: '报名截止', value: 3 })
    expect(stats[3]).toEqual({ label: '今日开标', value: 37 })
  })

  it('deadlineStats 为空时报名截止/今日开标为 0', () => {
    const stats = buildWelcomeStats({
      pendingCount: 5,
      myProjectCount: 2,
      deadlineStats: null,
    })
    expect(stats[2].value).toBe(0)
    expect(stats[3].value).toBe(0)
  })

  it('缺失字段安全降级为 0', () => {
    const stats = buildWelcomeStats({})
    expect(stats).toHaveLength(4)
    stats.forEach((s) => expect(s.value).toBe(0))
  })
})
