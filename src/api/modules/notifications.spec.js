// Input: notifications.js API module
// Output: unit tests for notificationsApi
// Pos: src/api/modules/ - API module test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock the client module
vi.mock('../client.js', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

import client from '../client.js'
import { notificationsApi } from './notifications.js'

describe('notificationsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getNotifications', () => {
    it('fetches paginated notifications for current user', async () => {
      const mockResponse = { data: { content: [], totalElements: 0 } }
      client.get.mockResolvedValue(mockResponse)

      const result = await notificationsApi.getNotifications({ page: 0, size: 10 })

      expect(client.get).toHaveBeenCalledWith('/api/notifications', { params: { page: 0, size: 10 } })
      expect(result).toEqual(mockResponse.data)
    })
  })

  describe('getUnreadCount', () => {
    it('fetches unread notification count', async () => {
      client.get.mockResolvedValue({ data: { count: 5 } })

      const result = await notificationsApi.getUnreadCount()

      expect(client.get).toHaveBeenCalledWith('/api/notifications/unread-count')
      expect(result).toEqual({ count: 5 })
    })
  })

  describe('markAsRead', () => {
    it('marks a single notification as read', async () => {
      client.post.mockResolvedValue({ data: { success: true } })

      const result = await notificationsApi.markAsRead(42)

      expect(client.post).toHaveBeenCalledWith('/api/notifications/42/read')
      expect(result).toEqual({ success: true })
    })
  })

  describe('markAllAsRead', () => {
    it('marks all notifications as read', async () => {
      client.post.mockResolvedValue({ data: { success: true } })

      const result = await notificationsApi.markAllAsRead()

      expect(client.post).toHaveBeenCalledWith('/api/notifications/read-all')
      expect(result).toEqual({ success: true })
    })
  })

  describe('getNotificationDetail', () => {
    it('fetches single notification detail', async () => {
      const mockDetail = { id: 1, title: 'Test', body: 'Body' }
      client.get.mockResolvedValue({ data: mockDetail })

      const result = await notificationsApi.getNotificationDetail(1)

      expect(client.get).toHaveBeenCalledWith('/api/notifications/1')
      expect(result).toEqual(mockDetail)
    })
  })
})
