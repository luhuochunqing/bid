// Input: httpClient and task id/comment payloads
// Output: taskActivityApi for task comments and history timeline
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

export const taskActivityApi = {
  async getActivity(taskId) {
    return httpClient.get(`/api/tasks/${taskId}/activity`)
  },

  async createComment(taskId, payload) {
    return httpClient.post(`/api/tasks/${taskId}/comments`, payload)
  },
}
