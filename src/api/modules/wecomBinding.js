// Input: httpClient
// Output: wecomBindingApi — admin-only CRUD for user→WeCom userid mapping
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import httpClient from '../client.js'

const BASE = (userId) => `/api/admin/users/${encodeURIComponent(userId)}/wecom-binding`

export const wecomBindingApi = {
  async get(userId) {
    const { data } = await httpClient.get(BASE(userId))
    return data
  },

  async bind(userId, wecomUserId) {
    const { data } = await httpClient.put(BASE(userId), { wecomUserId })
    return data
  },

  async unbind(userId) {
    const { data } = await httpClient.delete(BASE(userId))
    return data
  }
}

export default wecomBindingApi
