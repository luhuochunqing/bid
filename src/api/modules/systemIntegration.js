// Input: httpClient and system integration endpoint payloads
// Output: weComIntegrationApi and organizationIntegrationApi for real backend integration operations
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const BASE = '/api/admin/integrations/wecom'
const ORG_BASE = '/api/integrations/organization'

export const weComIntegrationApi = {
  async getConfig() {
    return httpClient.get(BASE)
  },

  async saveConfig(payload) {
    return httpClient.put(BASE, payload)
  },

  async testConnection() {
    return httpClient.post(`${BASE}/test`)
  },

  async sendTestMessage(payload = {}) {
    return httpClient.post(`${BASE}/send-test`, payload).then(r => r.data)
  },
}

export const organizationIntegrationApi = {
  async getOperationsStatus() {
    return httpClient.get(`${ORG_BASE}/operations/status`)
  },

  async startSyncRun(payload = {}) {
    return httpClient.post(`${ORG_BASE}/sync-runs`, payload)
  },

  async resyncUser(userId) {
    return httpClient.post(`${ORG_BASE}/resync/users/${encodeURIComponent(userId)}`)
  },

  async resyncDepartment(deptId) {
    return httpClient.post(`${ORG_BASE}/resync/departments/${encodeURIComponent(deptId)}`)
  },

  async replayDeadLetter(eventKey) {
    return httpClient.post(`${ORG_BASE}/operations/dead-letters/${encodeURIComponent(eventKey)}/replay`)
  },

  async testDirectoryConnection(payload = {}) {
    return httpClient.post(`${ORG_BASE}/directory/test`, payload)
  },
}

export default weComIntegrationApi
