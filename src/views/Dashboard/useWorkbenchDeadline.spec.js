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
})
