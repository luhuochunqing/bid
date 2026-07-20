import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useWorkbenchDeadline } from '@/views/Dashboard/useWorkbenchDeadline.js'

describe('useWorkbenchDeadline', () => {
  const sampleResponse = {
    success: true,
    data: {
      registrationDeadline: { todayCount: 2, weekCount: 5, monthCount: 12 },
      bidOpening: { todayCount: 1, weekCount: 3, monthCount: 8 },
      depositDeadline: { todayCount: 0, weekCount: 2, monthCount: 6 },
    },
  }

  it('starts in idle state (not loading) so cards do not stick on a spinner', () => {
    const workbenchApi = { getDeadlineStats: vi.fn() }
    const composable = useWorkbenchDeadline({ workbenchApi })

    expect(composable.deadlineMetricsLoading.value).toBe(false)
    expect(composable.deadlineMetricsError.value).toBe('')
    expect(composable.deadlineMetrics.value).toEqual([])
    expect(workbenchApi.getDeadlineStats).not.toHaveBeenCalled()
  })

  it('loadDeadlineStats sets loading true during the call and false after, on success', async () => {
    const workbenchApi = { getDeadlineStats: vi.fn().mockResolvedValue(sampleResponse) }
    const composable = useWorkbenchDeadline({
      workbenchApi,
      menuPermissionsRef: ref(['analytics']),
    })

    const promise = composable.loadDeadlineStats()
    expect(composable.deadlineMetricsLoading.value).toBe(true)
    await promise

    expect(composable.deadlineMetricsLoading.value).toBe(false)
    expect(composable.deadlineMetricsError.value).toBe('')
    // admin permission → 4 cards
    expect(composable.deadlineMetrics.value).toHaveLength(4)
    expect(composable.deadlineMetrics.value[0]).toMatchObject({
      key: 'reg_today',
      value: '2',
    })
  })

  it('records an error message and clears loading when api returns success:false', async () => {
    const workbenchApi = { getDeadlineStats: vi.fn().mockResolvedValue({ success: false }) }
    const composable = useWorkbenchDeadline({ workbenchApi })

    await composable.loadDeadlineStats()

    expect(composable.deadlineMetricsLoading.value).toBe(false)
    expect(composable.deadlineMetricsError.value).toBe('截止节点数据暂时不可用')
  })

  it('records a retry-prompt error message and clears loading when api rejects', async () => {
    const workbenchApi = { getDeadlineStats: vi.fn().mockRejectedValue(new Error('boom')) }
    const composable = useWorkbenchDeadline({ workbenchApi })

    await composable.loadDeadlineStats()

    expect(composable.deadlineMetricsLoading.value).toBe(false)
    expect(composable.deadlineMetricsError.value).toBe('截止节点数据暂时不可用，请稍后重试')
  })

  it('normalizes string-typed counts and missing fields from the API', async () => {
    const workbenchApi = {
      getDeadlineStats: vi.fn().mockResolvedValue({
        success: true,
        data: {
          registrationDeadline: { todayCount: '7' }, // string + missing week/month
          // bidOpening / depositDeadline missing entirely
        },
      }),
    }
    const composable = useWorkbenchDeadline({
      workbenchApi,
      menuPermissionsRef: ref(['analytics']),
    })

    await composable.loadDeadlineStats()

    expect(composable.deadlineStats.value.registrationDeadline.todayCount).toBe(7)
    expect(composable.deadlineStats.value.registrationDeadline.weekCount).toBe(0)
    expect(composable.deadlineStats.value.bidOpening.todayCount).toBe(0)
    expect(composable.deadlineStats.value.depositDeadline.monthCount).toBe(0)
  })

  // ==================== CO-593: deadline items + race condition guard ====================

  it('loadDeadlineItems fetches items and exposes them via deadlinePanels', async () => {
    const workbenchApi = {
      getDeadlineItems: vi.fn().mockResolvedValue({
        success: true,
        data: {
          registrationDeadline: [{ id: 10, name: '标讯A', date: '2026-05-17', targetId: 10, targetType: 'tender' }],
          bidOpening: [],
          depositDeadline: [],
        },
      }),
    }
    const composable = useWorkbenchDeadline({ workbenchApi })

    await composable.loadDeadlineItems('week')

    expect(composable.deadlineItems.value.registrationDeadline).toHaveLength(1)
    expect(composable.deadlineItems.value.registrationDeadline[0]).toMatchObject({
      name: '标讯A', date: '2026-05-17', targetType: 'tender',
    })
    expect(composable.deadlinePanels.value.signup).toHaveLength(1)
    expect(composable.deadlinePanels.value.opening).toEqual([])
    expect(composable.deadlinePanels.value.deposit).toEqual([])
    expect(composable.deadlineItemsLoading.value).toBe(false)
  })

  it('loadDeadlineItems discards stale response when a newer request supersedes it', async () => {
    // 模拟用户快速切换 Tab：today 请求先发出但后完成，week 请求后发出但先完成
    // 期望：最终 deadlineItems 显示 week 的数据，today 的结果被丢弃
    const todayData = { registrationDeadline: [{ id: 1, name: '今天条目', date: '2026-05-17', targetId: 1, targetType: 'tender' }] }
    const weekData = { registrationDeadline: [{ id: 2, name: '本周条目', date: '2026-05-18', targetId: 2, targetType: 'tender' }] }

    let resolveToday
    let resolveWeek
    const workbenchApi = {
      getDeadlineItems: vi.fn().mockImplementation((period) => {
        if (period === 'today') {
          return new Promise((resolve) => { resolveToday = () => resolve({ success: true, data: todayData }) })
        }
        return new Promise((resolve) => { resolveWeek = () => resolve({ success: true, data: weekData }) })
      }),
    }
    const composable = useWorkbenchDeadline({ workbenchApi })

    // 1. 发起 today 请求（pending）
    const todayPromise = composable.loadDeadlineItems('today')
    // 2. 立即发起 week 请求（today 还没完成 → today 的 requestId 已过时）
    const weekPromise = composable.loadDeadlineItems('week')

    // 3. week 请求先完成
    resolveWeek()
    await weekPromise

    // 此时 UI 应显示 week 数据
    expect(composable.deadlineItems.value.registrationDeadline[0].name).toBe('本周条目')
    expect(composable.deadlineItemsLoading.value).toBe(false)

    // 4. today 请求后完成（应被丢弃，不覆盖 week 的数据）
    resolveToday()
    await todayPromise

    // 关键断言：UI 仍显示 week 数据，未被 today 覆盖
    expect(composable.deadlineItems.value.registrationDeadline[0].name).toBe('本周条目')
    expect(composable.deadlineItemsLoading.value).toBe(false)
  })
})
