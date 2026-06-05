// Input: httpClient and market insight endpoint
// Output: marketInsightApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 市场洞察 API
 * 真实 API 为唯一数据源
 */
import httpClient from '../client.js'

export const marketInsightApi = {
  async getInsight() {
    return httpClient.get('/api/market-insight/insight')
  }
}

export default marketInsightApi
