// Input: httpClient, alerts service
// Output: alertRulesApi, alertHistoryApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 告警模块 API
 * 真实 API 告警访问层
 */
import httpClient from '../client.js'

function normalizeAlertRule(item) {
  return {
    id: item?.id,
    name: item?.name || '未命名规则',
    type: item?.type || 'DEADLINE',
    condition: item?.condition || 'LESS_THAN',
    threshold: Number(item?.threshold || 0),
    enabled: item?.enabled ?? true,
    actions: item?.actions || [],
    createdAt: item?.createdAt || item?.createTime || '',
    updatedAt: item?.updatedAt || '' }
}

function normalizeAlertHistory(item) {
  const resolved = item?.resolved === true
  const status = item?.status || (resolved ? 'RESOLVED' : item?.acknowledgedAt ? 'ACKNOWLEDGED' : 'ACTIVE')
  return {
    id: item?.id,
    ruleId: item?.ruleId || null,
    ruleName: item?.ruleName || '未知规则',
    alertType: item?.alertType || item?.type || 'SYSTEM',
    message: item?.message || item?.alertMessage || '',
    severity: item?.severity || item?.level || 'INFO',
    status,
    projectId: item?.projectId || null,
    projectName: item?.projectName || item?.relatedId || '',
    createdAt: item?.createdAt || item?.createTime || '',
    acknowledgedAt: item?.acknowledgedAt || null,
    resolvedAt: item?.resolvedAt || null }
}

export const alertRulesApi = {
  async getList() {
    const response = await httpClient.get('/api/alerts/rules')
    return { ...response, data: (response?.data || []).map(normalizeAlertRule) }
  },

  async getDetail(id) {
    const response = await httpClient.get(`/api/alerts/rules/${id}`)
    return { ...response, data: normalizeAlertRule(response?.data) }
  },

  async getEnabled() {
    const response = await httpClient.get('/api/alerts/rules/enabled')
    return { ...response, data: (response?.data || []).map(normalizeAlertRule) }
  },

  async create(data) {
    const response = await httpClient.post('/api/alerts/rules', data)
    return { ...response, data: normalizeAlertRule(response?.data) }
  },

  async update(id, data) {
    const response = await httpClient.put(`/api/alerts/rules/${id}`, data)
    return { ...response, data: normalizeAlertRule({ ...response?.data, ...data }) }
  },

  async delete(id) {
    return httpClient.delete(`/api/alerts/rules/${id}`)
  },

  async toggle(id) {
    const response = await httpClient.patch(`/api/alerts/rules/${id}/toggle`)
    return { ...response, data: normalizeAlertRule(response?.data) }
  } }

export const alertHistoryApi = {
  async getList(params = {}) {
    const response = await httpClient.get('/api/alerts/history', { params })
    return {
      ...response,
      data: response?.data?.content ? response.data.content.map(normalizeAlertHistory) : (response?.data || []).map(normalizeAlertHistory),
      total: response?.data?.totalElements || response?.data?.length || 0 }
  },

  async getDetail(id) {
    const response = await httpClient.get(`/api/alerts/history/${id}`)
    return { ...response, data: normalizeAlertHistory(response?.data) }
  },

  async getUnresolved(params = {}) {
    const response = await httpClient.get('/api/alerts/history/unresolved', { params })
    return {
      ...response,
      data: response?.data?.content
        ? response.data.content.map(normalizeAlertHistory)
        : (response?.data || []).map(normalizeAlertHistory),
      total: response?.data?.totalElements || response?.data?.length || 0,
    }
  },

  async acknowledge(id) {
    return httpClient.patch(`/api/alerts/history/${id}/acknowledge`)
  },

  async resolve(id) {
    return httpClient.post(`/api/alerts/history/${id}/resolve`)
  } }

export default {
  alertRules: alertRulesApi,
  alertHistory: alertHistoryApi }
