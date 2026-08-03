// 业绩合订本导出 API
// 与 performance.js 中的 ZIP 导出分离（需求 §3：单独增加一个导出合订本的按钮）
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
  },

  /**
   * 查询导出任务状态
   * @param {number} taskId
   */
  async getTaskStatus(taskId) {
    return httpClient.get(`/api/knowledge/performance/bundle-export/tasks/${taskId}/status`)
  },

  /**
   * 列出导出任务
   * @param {number} page
   * @param {number} size
   */
  async listTasks(page = 0, size = 15) {
    return httpClient.get('/api/knowledge/performance/bundle-export/tasks', {
      params: { page, size }
    })
  },

  /**
   * 下载导出文件（通过 useAsyncTask.downloadFile 流式下载，不直接调用此方法）
   */
  getDownloadUrl(taskId) {
    return `/api/knowledge/performance/bundle-export/tasks/${taskId}/download`
  }
}

export default performanceBundleExportApi
