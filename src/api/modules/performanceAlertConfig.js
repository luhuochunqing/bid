// Input: httpClient and performance alert config endpoints
// Output: performanceAlertConfigApi - alert config GET/PUT accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

export const performanceAlertConfigApi = {
  async getConfig() {
    return httpClient.get('/api/knowledge/performance/alert-config')
  },

  async updateConfig(alertDaysSoe, alertDaysDefault, enabled) {
    return httpClient.put('/api/knowledge/performance/alert-config', {
      alertDaysSoe,
      alertDaysDefault,
      enabled
    })
  }
}

export default performanceAlertConfigApi
