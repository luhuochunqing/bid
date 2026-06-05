// Input: notificationsApi
// Output: useNotificationStore - Pinia store for notifications state
// Pos: src/stores/ - State management layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { defineStore } from 'pinia'
import { notificationsApi } from '@/api/modules/notifications.js'

export const useNotificationStore = defineStore('notifications', {
  state: () => ({
    notifications: [],
    unreadCount: 0,
    totalElements: 0,
    totalPages: 0,
    loading: false,
    error: null
  }),

  actions: {
    async fetchUnreadCount() {
      try {
        const result = await notificationsApi.getUnreadCount()
        this.unreadCount = result.count ?? 0
      } catch {
        this.unreadCount = 0
      }
    },

    async fetchNotifications(params = { page: 0, size: 10 }) {
      this.loading = true
      this.error = null
      try {
        const result = await notificationsApi.getNotifications(params)
        this.notifications = result.content ?? []
        this.totalElements = result.totalElements ?? 0
        this.totalPages = result.totalPages ?? 0
      } catch (err) {
        this.error = err.message
        this.notifications = []
      } finally {
        this.loading = false
      }
    },

    async markAsRead({ userNotificationId, notificationId }) {
      try {
        await notificationsApi.markAsRead(notificationId)
        const wasUnread = this.notifications.some(n => n.id === userNotificationId && !n.read)
        this.notifications = this.notifications.map(n =>
          n.id === userNotificationId ? { ...n, read: true } : n
        )
        if (wasUnread) {
          this.unreadCount = Math.max(0, this.unreadCount - 1)
        }
      } catch (err) {
        this.error = err.message
      }
    },

    async markAllAsRead() {
      try {
        await notificationsApi.markAllAsRead()
        this.notifications = this.notifications.map(n => ({ ...n, read: true }))
        this.unreadCount = 0
      } catch (err) {
        this.error = err.message
      }
    },

    resetState() {
      this.notifications = []
      this.unreadCount = 0
      this.totalElements = 0
      this.totalPages = 0
      this.loading = false
      this.error = null
    }
  }
})
