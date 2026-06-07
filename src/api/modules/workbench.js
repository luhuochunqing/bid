// Input: workbench schedule overview query params, deadline stats
// Output: workbenchApi - explicit frontend adapter for workbench schedule overview & deadline stats
// Pos: src/api/modules/ - Feature API module for workbench
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

function formatDateParam(value) {
  if (!value) return ''
  if (typeof value === 'string') return value.slice(0, 10)
  return new Date(value).toISOString().slice(0, 10)
}

function normalizeScheduleOverview(response = {}) {
  const payload = response?.data || {}
  return {
    ...response,
    data: {
      start: payload.start || '',
      end: payload.end || '',
      assigneeId: payload.assigneeId ?? null,
      total: Number(payload.total || 0),
      urgent: Number(payload.urgent || 0),
      events: Array.isArray(payload.events) ? payload.events : [],
    },
  }
}

export const workbenchApi = {
  async getScheduleOverview({ start, end, assigneeId } = {}) {
    const response = await httpClient.get('/api/workbench/schedule-overview', {
      params: {
        start: formatDateParam(start),
        end: formatDateParam(end),
        assigneeId: assigneeId || undefined,
      },
    })
    return normalizeScheduleOverview(response)
  },

  async getDeadlineStats() {
    const response = await httpClient.get('/api/workbench/deadline-stats')
    return {
      success: response?.success === true,
      data: response?.data || {},
    }
  },
}

export default workbenchApi
