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

    expect(router.push).toHaveBeenCalledWith('/project/88')
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
})
