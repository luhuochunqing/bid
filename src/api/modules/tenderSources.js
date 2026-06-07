// Input: httpClient and tender source configuration endpoints
// Output: tenderSourcesApi - tender source configuration API accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 标讯源配置模块 API
 * 真实 API 为唯一数据源
 */
import httpClient from '../client.js'

const BASE = '/api/tender-sources'

export const tenderSourcesApi = {
  /**
   * 获取标讯源配置（从后端）。
   * @returns {Promise<{success: boolean, data: Object}>}
   */
  async getConfig() {
    return httpClient.get(`${BASE}/config`)
  },

  /**
   * 保存标讯源配置到后端。
   * @param {Object} payload - 配置对象
   * @returns {Promise<{success: boolean, data: Object}>}
   */
  async saveConfig(payload) {
    return httpClient.put(`${BASE}/config`, payload)
  },

  /**
   * 测试标讯源连接
   * @param {Object} params - { platform, apiEndpoint, apiKey }
   * @returns {Promise<{success: boolean, message: string, data: {success: boolean, message: string}}>}
   */
  async testConnection(params) {
    return httpClient.post(`${BASE}/test-connection`, params)
  }
}

export default tenderSourcesApi
