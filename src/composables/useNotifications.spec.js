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

    const { startPolling, stopPolling, _resetForTest } = useNotifications({ pollingInterval: 30000, autoStart: false })
    _resetForTest()
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

    const { startPolling, stopPolling, _resetForTest } = useNotifications({ pollingInterval: 30000, autoStart: false })
    _resetForTest()
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

    const { startPolling, stopPolling, _resetForTest } = useNotifications({ pollingInterval: 30000, autoStart: false })
    _resetForTest()
    startPolling()

    // Initial fetch at start uses silentRateLimit config
    await vi.advanceTimersByTimeAsync(0)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)
    expect(notificationsApi.getUnreadCount).toHaveBeenLastCalledWith({ silentRateLimit: true })

    // During 60s backoff, the 30s interval tick is skipped
    await vi.advanceTimersByTimeAsync(30000)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    // After backoff expires, polling resumes
    await vi.advanceTimersByTimeAsync(30000)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(2)

    const store = useNotificationStore()
    expect(store.unreadCount).toBe(3)

    stopPolling()
    vi.useRealTimers()
  })

  it('passes silentRateLimit config to suppress global toast while polling', async () => {
    vi.useFakeTimers()
    notificationsApi.getUnreadCount.mockResolvedValue({ count: 0 })

    const { startPolling, stopPolling, _resetForTest } = useNotifications({ pollingInterval: 30000, autoStart: false })
    _resetForTest()
    startPolling()

    await vi.advanceTimersByTimeAsync(0)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledWith({ silentRateLimit: true })

    stopPolling()
    vi.useRealTimers()
  })

  it('permanently stops polling on 403', async () => {
    vi.useFakeTimers()
    const err403 = { response: { status: 403 } }
    notificationsApi.getUnreadCount.mockRejectedValueOnce(err403)

    const { startPolling, stopPolling, _resetForTest } = useNotifications({ pollingInterval: 30000, autoStart: false })
    _resetForTest()
    startPolling()

    // Initial fetch triggers 403
    await vi.advanceTimersByTimeAsync(0)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    // Polling should be stopped — advancing time should not trigger more fetches
    await vi.advanceTimersByTimeAsync(60000)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    stopPolling()
    vi.useRealTimers()
  })

  it('CO-605: multiple instances share single polling timer', async () => {
    vi.useFakeTimers()
    notificationsApi.getUnreadCount.mockResolvedValue({ count: 0 })

    const instance1 = useNotifications({ pollingInterval: 30000, autoStart: false })
    const instance2 = useNotifications({ pollingInterval: 30000, autoStart: false })
    instance1._resetForTest()

    instance1.startPolling()
    instance2.startPolling()

    // 两个实例都 startPolling，但只有首个实例触发 tick + setInterval
    await vi.advanceTimersByTimeAsync(0)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    // 推进一个周期：仍然只有一个 setInterval 在跑，只触发一次
    notificationsApi.getUnreadCount.mockClear()
    await vi.advanceTimersByTimeAsync(30000)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    // 第一个实例 stopPolling 不应停止全局轮询（还有活跃实例）
    instance1.stopPolling()
    notificationsApi.getUnreadCount.mockClear()
    await vi.advanceTimersByTimeAsync(30000)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(1)

    // 最后一个实例 stopPolling 才真正停止全局轮询
    instance2.stopPolling()
    notificationsApi.getUnreadCount.mockClear()
    await vi.advanceTimersByTimeAsync(60000)
    expect(notificationsApi.getUnreadCount).toHaveBeenCalledTimes(0)

    vi.useRealTimers()
  })
})
