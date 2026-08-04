// Input: 业绩合订本导出请求（ids/criteria/attachmentTypes）
// Output: 导出任务状态 + 下载 URL
// Pos: 前端 API 层（与 performance.js 中的 ZIP 导出分离，需求 §3：单独增加一个导出合订本的按钮）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import httpClient from '../client.js'

export const performanceBundleExportApi = {
  /**
   * 触发合订本导出任务（异步）
   * @param {Object} payload - { ids?, criteria?, attachmentTypes? }
   * @returns {Promise<{taskId: number}>}
   */
  async triggerExport(payload = {}) {
    const res = await httpClient.post('/api/knowledge/performance/bundle-export', payload)
    return res
  }
}

export default performanceBundleExportApi
