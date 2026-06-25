import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useNotifications } from './useNotifications.js'
import { notificationsApi } from '@/api/modules/notifications.js'
import { useNotificationStore } from '@/stores/notifications'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/modules/notifications.js', () => ({
  notificationsApi: {
    getUnreadCount: vi.fn(),
  },
}))

describe('useNotifications', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('polls on start and fetches unread count', async () => {
    // Use fake timers
    vi.useFakeTimers()
    notificationsApi.getUnreadCount.mockResolvedValue({ count: 5 })

    const { startPolling, stopPolling } = useNotifications({ pollingInterval: 30000, autoStart: false })
    startPolling()

    // First tick triggers fetch
    await vi.advanceTimersByTimeAsync(0)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    const store = useNotificationStore()
    expect(store.unreadCount).toBe(5)

    // Advance by pollingInterval: second tick fires
    notificationsApi.getUnreadCount.mockClear()
    await vi.advanceTimersByTimeAsync(30000)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    stopPolling()
    vi.useRealTimers()
  })

  it('stopPolling prevents further fetches', async () => {
    vi.useFakeTimers()
    notificationsApi.getUnreadCount.mockResolvedValue({ count: 0 })

    const { startPolling, stopPolling } = useNotifications({ pollingInterval: 30000, autoStart: false })
    startPolling()
    await vi.advanceTimersByTimeAsync(0)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    // Stop polling
    stopPolling()
    notificationsApi.getUnreadCount.mockClear()

    // Advance enough time for many intervals
    await vi.advanceTimersByTimeAsync(600000)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(0)

    vi.useRealTimers()
  })

  it('backoffs 60s on 429 then resumes', async () => {
    vi.useFakeTimers()
    const err429 = { response: { status: 429 } }
    notificationsApi.getUnreadCount
      .mockRejectedValueOnce(err429)
      .mockResolvedValueOnce({ count: 3 })

    const { startPolling, stopPolling } = useNotifications({ pollingInterval: 30000, autoStart: false })
    startPolling()

    // Tick 0: 429 triggers backoff
    await vi.advanceTimersByTimeAsync(0)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    // During backoff (60s), next 30s tick should be skipped
    await vi.advanceTimersByTimeAsync(60000)
    // By now one interval has passed (30s tick skipped due backoff), another 30s passed
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(3)

    const store = useNotificationStore()
    expect(store.unreadCount).toBe(3)

    stopPolling()
    vi.useRealTimers()
  })

  it('stops polling on 403 from initial fetch', async () => {
    vi.useFakeTimers()
    const err403 = { response: { status: 403 } }
    notificationsApi.getUnreadCount.mockRejectedValue(err403)

    const { startPolling, stopPolling } = useNotifications({ pollingInterval: 30000, autoStart: false })
    startPolling()

    // Initial fetch hits 403
    await vi.advanceTimersByTimeAsync(0)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    // Advance past multiple intervals — no more calls because polling was stopped
    await vi.advanceTimersByTimeAsync(120000)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    stopPolling()
    vi.useRealTimers()
  })
})
