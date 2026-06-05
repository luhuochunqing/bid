// Input: notifications.js Pinia store
// Output: unit tests for useNotificationStore
// Pos: src/stores/ - State management test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/modules/notifications.js', () => ({
  notificationsApi: {
    getNotifications: vi.fn(),
    getUnreadCount: vi.fn(),
    getNotificationDetail: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn()
  }
}))

import { useNotificationStore } from './notifications.js'
import { notificationsApi } from '@/api/modules/notifications.js'

describe('useNotificationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('initializes with zero unread count', () => {
    const store = useNotificationStore()
    expect(store.unreadCount).toBe(0)
    expect(store.notifications).toEqual([])
    expect(store.loading).toBe(false)
  })

  describe('fetchUnreadCount', () => {
    it('updates unreadCount from API', async () => {
      notificationsApi.getUnreadCount.mockResolvedValue({ count: 7 })
      const store = useNotificationStore()

      await store.fetchUnreadCount()

      expect(store.unreadCount).toBe(7)
    })

    it('sets unreadCount to 0 on API error', async () => {
      notificationsApi.getUnreadCount.mockRejectedValue(new Error('Network'))
      const store = useNotificationStore()

      await store.fetchUnreadCount()

      expect(store.unreadCount).toBe(0)
    })
  })

  describe('fetchNotifications', () => {
    it('loads paginated notifications', async () => {
      const stubResponse = {
        content: [{ id: 1, title: 'Test' }],
        totalElements: 1,
        totalPages: 1
      }
      notificationsApi.getNotifications.mockResolvedValue(stubResponse)
      const store = useNotificationStore()

      await store.fetchNotifications({ page: 0, size: 10 })

      expect(store.notifications).toEqual(stubResponse.content)
      expect(store.totalElements).toBe(1)
      expect(store.loading).toBe(false)
    })
  })

  describe('markAsRead', () => {
    it('marks notification read and decrements unread count', async () => {
      notificationsApi.markAsRead.mockResolvedValue({ success: true })
      const store = useNotificationStore()
      store.unreadCount = 3
      store.notifications = [
        { id: 1, notificationId: 42, read: false, title: 'Test' }
      ]

      await store.markAsRead({ userNotificationId: 1, notificationId: 42 })

      expect(notificationsApi.markAsRead).toHaveBeenCalledWith(42)
      expect(store.notifications[0].read).toBe(true)
      expect(store.unreadCount).toBe(2)
    })

    it('does not decrement below zero', async () => {
      notificationsApi.markAsRead.mockResolvedValue({ success: true })
      const store = useNotificationStore()
      store.unreadCount = 0
      store.notifications = [{ id: 1, notificationId: 42, read: false }]

      await store.markAsRead({ userNotificationId: 1, notificationId: 42 })

      expect(store.unreadCount).toBe(0)
    })
  })

  describe('markAllAsRead', () => {
    it('marks all as read and resets unread count', async () => {
      notificationsApi.markAllAsRead.mockResolvedValue({ success: true })
      const store = useNotificationStore()
      store.unreadCount = 5
      store.notifications = [
        { id: 1, read: false },
        { id: 2, read: false }
      ]

      await store.markAllAsRead()

      expect(store.unreadCount).toBe(0)
      expect(store.notifications.every(n => n.read)).toBe(true)
    })
  })
})
