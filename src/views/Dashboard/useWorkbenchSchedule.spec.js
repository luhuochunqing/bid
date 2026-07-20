import { computed } from 'vue'
import { describe, expect, it, vi, beforeEach } from 'vitest'

const { getScheduleOverview } = vi.hoisted(() => ({
  getScheduleOverview: vi.fn(),
}))

vi.mock('@/api/modules/workbench.js', () => ({
  workbenchApi: {
    getScheduleOverview,
  },
}))

import { useWorkbenchSchedule } from '@/views/Dashboard/useWorkbenchSchedule.js'

describe('useWorkbenchSchedule', () => {
  const router = { push: vi.fn() }
  const assigneeIdRef = computed(() => 7)

  beforeEach(() => {
    getScheduleOverview.mockReset()
    router.push.mockReset()
  })

  it('loads schedule overview from the workbench endpoint and normalizes events', async () => {
    const onEventsLoaded = vi.fn()
    getScheduleOverview.mockResolvedValue({
      data: {
        events: [
          {
            id: 1,
            eventDate: '2026-04-12',
            eventType: 'DEADLINE',
            title: '项目截标',
            projectId: 99,
            isUrgent: true,
          },
        ],
      },
    })

    const schedule = useWorkbenchSchedule({ router, assigneeIdRef, onEventsLoaded })
    const events = await schedule.loadScheduleOverview()

    expect(getScheduleOverview).toHaveBeenCalledTimes(1)
    expect(events[0]).toMatchObject({
      id: 1,
      date: '2026-04-12',
      type: 'deadline',
      projectId: 99,
      urgent: true,
    })
    expect(onEventsLoaded).toHaveBeenCalledWith(events)
  })

  it('routes project-linked calendar actions to project detail', () => {
    const schedule = useWorkbenchSchedule({ router, assigneeIdRef })

    schedule.handleCalendarAction({ projectId: 88 })

    expect(router.push).toHaveBeenCalledWith({ name: 'ProjectDetail', params: { id: '88' } })
  })

  it('routes tender event with real id to tender detail page', () => {
    const schedule = useWorkbenchSchedule({ router, assigneeIdRef })

    schedule.handleCalendarAction({ id: 123, type: 'opening', title: '某标讯开标' })

    expect(router.push).toHaveBeenCalledWith('/bidding/123')
  })

  it('routes tender event with demo id to bidding list page', () => {
    const schedule = useWorkbenchSchedule({ router, assigneeIdRef })

    schedule.handleCalendarAction({ id: '-demo-1', type: 'deadline', title: 'demo 标讯' })

    expect(router.push).toHaveBeenCalledWith('/bidding')
  })

  it('routes non-tender event without projectId to /project with query', () => {
    const schedule = useWorkbenchSchedule({ router, assigneeIdRef })

    schedule.handleCalendarAction({ id: 5, type: 'review', date: '2026-04-22' })

    expect(router.push).toHaveBeenCalledWith({
      path: '/project',
      query: { calendarDate: '2026-04-22', calendarType: 'review' },
    })
  })

  it('matches tender event by event.type (lowercase) not event.eventType (uppercase)', () => {
    // 回归测试：normalizeCalendarEvent 返回 eventType='OPENING'（大写）+ type='opening'（小写）
    // handleCalendarAction 必须按小写 type 匹配 TENDER_EVENT_TYPES
    const schedule = useWorkbenchSchedule({ router, assigneeIdRef })

    schedule.handleCalendarAction({ id: 999, eventType: 'OPENING', type: 'opening' })

    expect(router.push).toHaveBeenCalledWith('/bidding/999')
  })

  it('syncs selectedDateKey to the nearest upcoming event', async () => {
    getScheduleOverview.mockResolvedValue({
      data: {
        events: [
          {
            id: 1,
            eventDate: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
            eventType: 'DEADLINE',
            title: '最近节点',
            projectId: 42,
            isUrgent: true,
          },
          {
            id: 2,
            eventDate: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
            eventType: 'REVIEW',
            title: '后续节点',
            projectId: 43,
            isUrgent: false,
          },
        ],
      },
    })

    const schedule = useWorkbenchSchedule({ router, assigneeIdRef })
    await schedule.loadScheduleOverview()
    schedule.syncSelectedDate()

    expect(schedule.selectedDateKey.value).toBe(schedule.upcomingCalendarEvents.value[0].date)
  })

  it('syncSelectedDate with keepCalendarDate keeps calendarDate and picks first event of current month', async () => {
    // 翻月场景：用户主动翻到目标月份，syncSelectedDate 不应重置 calendarDate
    const targetDate = new Date('2026-08-15T00:00:00')
    getScheduleOverview.mockResolvedValue({
      data: {
        events: [
          {
            id: 1,
            eventDate: '2026-08-20',
            eventType: 'OPENING',
            title: '8月开标',
            projectId: 42,
            isUrgent: false,
          },
          {
            id: 2,
            eventDate: '2026-08-05',
            eventType: 'DEADLINE',
            title: '8月截止',
            projectId: 43,
            isUrgent: false,
          },
        ],
      },
    })

    const schedule = useWorkbenchSchedule({ router, assigneeIdRef })
    schedule.calendarDate.value = targetDate
    await schedule.loadScheduleOverview()
    schedule.syncSelectedDate({ keepCalendarDate: true })

    // calendarDate 必须保持不变（不被重置到事件日期）
    expect(schedule.calendarDate.value.getTime()).toBe(targetDate.getTime())
    // selectedDateKey 应选当前月份最近的事件（8月5日）
    expect(schedule.selectedDateKey.value).toBe('2026-08-05')
  })

  it('syncSelectedDate with keepCalendarDate keeps calendarDate and falls back to current date when no events in month', async () => {
    // 翻月场景：当前月无事件，calendarDate 仍保持，selectedDateKey fallback 到当前 calendarDate
    const targetDate = new Date('2026-09-15T00:00:00')
    getScheduleOverview.mockResolvedValue({
      data: {
        events: [
          {
            id: 1,
            eventDate: '2026-10-10',
            eventType: 'OPENING',
            title: '10月开标',
            projectId: 42,
            isUrgent: false,
          },
        ],
      },
    })

    const schedule = useWorkbenchSchedule({ router, assigneeIdRef })
    schedule.calendarDate.value = targetDate
    await schedule.loadScheduleOverview()
    schedule.syncSelectedDate({ keepCalendarDate: true })

    // calendarDate 必须保持不变
    expect(schedule.calendarDate.value.getTime()).toBe(targetDate.getTime())
    // selectedDateKey fallback 到当前 calendarDate（仅影响日历格子高亮，无业务差异）
    expect(schedule.selectedDateKey.value).toBe('2026-09-15')
  })
})
