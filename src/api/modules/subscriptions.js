// Input: httpClient
// Output: subscriptionsApi — subscribe/unsubscribe/listMine/check
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import httpClient from '../client.js'

export const subscriptionsApi = {
  async subscribe(targetEntityType, targetEntityId) {
    const { data } = await httpClient.post('/api/subscriptions', {
      targetEntityType,
      targetEntityId
    })
    return data
  },

  async unsubscribe(targetEntityType, targetEntityId) {
    const { data } = await httpClient.delete('/api/subscriptions', {
      data: { targetEntityType, targetEntityId }
    })
    return data
  },

  async listMine(params = {}) {
    const { data } = await httpClient.get('/api/subscriptions/me', { params })
    return data
  },

  async check(targetEntityType, targetEntityId) {
    const safeType = encodeURIComponent(String(targetEntityType))
    const safeId = encodeURIComponent(String(targetEntityId))
    const { data } = await httpClient.get(
      `/api/entities/${safeType}/${safeId}/subscription`
    )
    return data
  }
}

export default subscriptionsApi
