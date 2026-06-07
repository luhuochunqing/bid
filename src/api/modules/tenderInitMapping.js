// Input: httpClient
// Output: tenderInitApi - 标讯→立项枚举值映射查询
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 标讯→立项枚举值映射 API
 * 用于 auto-fill 时标讯自由文本转立项枚举值。
 */
import httpClient from '../client.js'

export const tenderInitApi = {
  /** 标讯→立项枚举值映射 (GET /api/project/tender-init-mapping) */
  async getMapping() {
    return httpClient.get('/api/project/tender-init-mapping')
  }
}
