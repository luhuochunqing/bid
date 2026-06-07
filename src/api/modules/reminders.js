// Input: httpClient, tender reminder endpoints
// Output: remindersApi - reminder CRUD accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

export const remindersApi = {
  /**
   * 获取标讯的所有提醒设置
   */
  async getReminders(tenderId) {
    return httpClient.get(`/api/tenders/${tenderId}/reminders`)
  },

  /**
   * 获取单个提醒设置详情
   */
  async getReminderById(tenderId, reminderId) {
    return httpClient.get(`/api/tenders/${tenderId}/reminders/${reminderId}`)
  },

  /**
   * 创建提醒设置
   */
  async createReminder(tenderId, data) {
    return httpClient.post(`/api/tenders/${tenderId}/reminders`, data)
  },

  /**
   * 更新提醒设置
   */
  async updateReminder(tenderId, reminderId, data) {
    return httpClient.put(`/api/tenders/${tenderId}/reminders/${reminderId}`, data)
  },

  /**
   * 删除提醒设置
   */
  async deleteReminder(tenderId, reminderId) {
    return httpClient.delete(`/api/tenders/${tenderId}/reminders/${reminderId}`)
  },

  /**
   * 切换提醒启用状态
   */
  async toggleReminder(tenderId, reminderId) {
    return httpClient.post(`/api/tenders/${tenderId}/reminders/${reminderId}/toggle`)
  }
}

export default remindersApi
