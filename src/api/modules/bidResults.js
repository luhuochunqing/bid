// Input: httpClient and bid result delivery APIs
// Output: bidResultsApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const normalizeOverview = (data = {}) => ({
  lastSyncTime: data?.lastSyncTime || '',
  pendingCount: Number(data?.pendingCount || 0),
  uploadPending: Number(data?.uploadPending || 0),
  competitorCount: Number(data?.competitorCount || 0) })

const normalizeFetchResult = (item = {}) => ({
  id: item?.id,
  source: item?.source || '',
  tenderId: item?.tenderId ?? null,
  projectId: item?.projectId ?? null,
  projectName: item?.projectName || '',
  result: item?.result || 'lost',
  amount: item?.amount ?? null,
  fetchTime: item?.fetchTime || '',
  status: item?.status || 'pending' })

const normalizeReminder = (item = {}) => ({
  id: item?.id,
  projectId: item?.projectId ?? null,
  projectName: item?.projectName || '',
  owner: item?.owner || '',
  ownerId: item?.ownerId ?? null,
  lastResultId: item?.lastResultId ?? null,
  type: item?.type || 'report',
  status: item?.status || 'pending',
  remindTime: item?.remindTime || '',
  lastReminderComment: item?.lastReminderComment || '' })

const normalizeCompetitorReport = (item = {}) => ({
  company: item?.company || '',
  skuCount: item?.skuCount || '',
  category: item?.category || '',
  discount: item?.discount || '',
  payment: item?.payment || '',
  winRate: item?.winRate || '',
  projectCount: Number(item?.projectCount || 0),
  trend: item?.trend || 'flat' })

const normalizeDetail = (data = {}) => ({
  id: data?.id,
  source: data?.source || '',
  tenderId: data?.tenderId ?? null,
  projectId: data?.projectId ?? null,
  projectName: data?.projectName || '',
  result: data?.result || 'lost',
  amount: data?.amount ?? null,
  status: data?.status || 'pending',
  fetchTime: data?.fetchTime || '',
  ignoredReason: data?.ignoredReason || '',
  ownerName: data?.ownerName || '',
  reminderTypes: Array.isArray(data?.reminderTypes) ? data.reminderTypes : [] })

export const bidResultsApi = {
  async getOverview() {
    const response = await httpClient.get('/api/bid-results/overview')
    return { ...response, data: normalizeOverview(response?.data) }
  },
  async sync() {
    return httpClient.post('/api/bid-results/sync')
  },
  async fetch() {
    return httpClient.post('/api/bid-results/fetch')
  },
  async getFetchResults() {
    const response = await httpClient.get('/api/bid-results/fetch-results')
    return { ...response, data: Array.isArray(response?.data) ? response.data.map(normalizeFetchResult) : [] }
  },
  async confirm(id) {
    const response = await httpClient.post(`/api/bid-results/fetch-results/${id}/confirm`)
    return { ...response, data: normalizeFetchResult(response?.data) }
  },
  async ignore(id, comment = '') {
    return httpClient.post(`/api/bid-results/fetch-results/${id}/ignore`, { comment })
  },
  async confirmBatch(ids = []) {
    return httpClient.post('/api/bid-results/fetch-results/confirm-batch', { ids })
  },
  async getReminders() {
    const response = await httpClient.get('/api/bid-results/reminders')
    return { ...response, data: Array.isArray(response?.data) ? response.data.map(normalizeReminder) : [] }
  },
  async sendReminder(resultId, comment = '') {
    const response = await httpClient.post('/api/bid-results/reminders/send', { resultId, comment })
    return { ...response, data: normalizeReminder(response?.data) }
  },
  async sendReminderBatch(ids = [], comment = '') {
    return httpClient.post('/api/bid-results/reminders/send-batch', { ids, comment })
  },
  async getDetail(id) {
    const response = await httpClient.get(`/api/bid-results/${id}`)
    return { ...response, data: normalizeDetail(response?.data) }
  },
  async getCompetitorReport() {
    const response = await httpClient.get('/api/bid-results/competitor-report')
    return { ...response, data: Array.isArray(response?.data) ? response.data.map(normalizeCompetitorReport) : [] }
  } }

export default bidResultsApi
