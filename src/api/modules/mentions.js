// Input: httpClient
// Output: mentionsApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import httpClient from '../client.js'

export const mentionsApi = {
  async create({ content, sourceEntityType, sourceEntityId, title }) {
    const { data } = await httpClient.post('/api/mentions', {
      content,
      sourceEntityType,
      sourceEntityId,
      title
    })
    return data
  }
}
