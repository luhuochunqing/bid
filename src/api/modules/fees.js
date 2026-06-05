// Input: httpClient and fee service endpoints
// Output: feesApi - fee application, approval, and refund accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 费用管理模块 API
 * 真实 API 为唯一数据源
 */
import httpClient from '../client.js'

export const feesApi = {
  async getList(params) {
    return httpClient.get('/api/fees', { params })
  },

  async getByProject(projectId) {
    return httpClient.get(`/api/fees/project/${projectId}`)
  },

  async getDetail(id) {
    return httpClient.get(`/api/fees/${id}`)
  },

  async create(data) {
    return httpClient.post('/api/fees', data)
  },

  async pay(id, paymentData) {
    return httpClient.post(`/api/fees/${id}/pay`, paymentData)
  },

  async return(id) {
    return httpClient.post(`/api/fees/${id}/return`)
  },

  async getStatistics() {
    return httpClient.get('/api/fees/statistics')
  }
}

export default feesApi
