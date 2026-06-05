// Input: httpClient for alert config API calls
// Output: alertConfigApi - qualification expiry alert config CRUD accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

export const alertConfigApi = {
  async getConfig() {
    return httpClient.get('/api/qualifications/alert-config')
  },

  async updateConfig(alertDays, enabled) {
    return httpClient.put('/api/qualifications/alert-config', {
      alertDays,
      enabled
    })
  }
}

export default alertConfigApi
