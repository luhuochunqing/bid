// Input: workbench schedule overview query params, deadline stats, project/resource todo queries
// Output: workbenchApi - explicit frontend adapter for /api/workbench/* (schedule overview, deadline stats, project todos, resource pending approvals)
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

  /**
   * CO-593: 工作台截止时间模块列表数据。
   * @param {string} period 时间筛选：today / week / month（默认 week）
   * @returns {Promise<{success: boolean, data: object}>}
   */
  async getDeadlineItems(period = 'week') {
    const response = await httpClient.get('/api/workbench/deadline-items', {
      params: { period },
    })
    return {
      success: response?.success === true,
      data: response?.data || {},
    }
  },

  /**
   * 工作台项目待办：按当前用户角色返回差异化项目列表。
   * 后端按角色分支查询（admin_lead / bid-Team / bid-projectLeader），其他角色返回空。
   * @returns {Promise<{success: boolean, data: Array}>}
   */
  async getProjectTodos() {
    try {
      const response = await httpClient.get('/api/workbench/project-todos')
      return {
        success: response?.success !== false,
        data: Array.isArray(response?.data) ? response.data : [],
      }
    } catch (error) {
      return { success: false, data: [], error }
    }
  },

  /**
   * 工作台资源待办：聚合待审批的账户借用申请 + CA 借用申请。
   * 后端按当前用户角色返回（管理员查全部，保管员查自己负责的）。
   * @returns {Promise<{success: boolean, data: Array}>}
   */
  async getResourcePendingApprovals() {
    try {
      const response = await httpClient.get('/api/workbench/resource-pending-approvals')
      return {
        success: response?.success !== false,
        data: Array.isArray(response?.data) ? response.data : [],
      }
    } catch (error) {
      return { success: false, data: [], error }
    }
  },
}

export default workbenchApi
