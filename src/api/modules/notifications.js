// Input: httpClient
// Output: notificationsApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 通知模块 API
 * 真实 API 通知访问层
 */
import httpClient from '../client.js'

export const notificationsApi = {
  async getNotifications(params = {}) {
    const { data } = await httpClient.get('/api/notifications', { params })
    return data
  },

  async getUnreadCount() {
    const { data } = await httpClient.get('/api/notifications/unread-count')
    return data
  },

  async getNotificationDetail(id) {
    const { data } = await httpClient.get(`/api/notifications/${id}`)
    return data
  },

  async markAsRead(id) {
    const { data } = await httpClient.post(`/api/notifications/${id}/read`)
    return data
  },

  async markAllAsRead() {
    const { data } = await httpClient.post('/api/notifications/read-all')
    return data
  }
}
