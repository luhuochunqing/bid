// Input: httpClient and log query parameters from audit/operation log views
// Output: auditApi - full audit log and personal operation log retrieval functions
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

export const auditApi = {
  async getAuditLogs(params = {}) {
    return httpClient.get('/api/audit', { params })
  },
  async getOperationLogs(params = {}) {
    return httpClient.get('/api/audit/my', { params })
  },
  async getLogs(params = {}) {
    return httpClient.get('/api/audit', { params })
  },
}

export default auditApi
