// Input: httpClient, task data models
// Output: tasksApi - task retrieval and deletion accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

export const tasksApi = {
  async getTaskById(taskId) {
    return httpClient.get(`/api/tasks/${taskId}`)
  },

  async deleteTask(taskId) {
    return httpClient.delete(`/api/tasks/${taskId}`)
  },
}
