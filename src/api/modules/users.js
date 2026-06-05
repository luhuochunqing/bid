// Input: httpClient
// Output: usersApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import httpClient from '../client.js'

export const usersApi = {
  async search(query, limit = 10) {
    const { data } = await httpClient.get('/api/users/search', { params: { q: query, limit } })
    return data
  },

  async getTaskAssignmentCandidates(params = undefined) {
    const response = await httpClient.get('/api/tasks/assignment-candidates', { params })
    return response?.data || []
  }
}
