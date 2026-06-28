// Input: httpClient
// Output: usersApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import httpClient from '../client.js'
import { normalizeUserOption } from './userNormalizers.js'

export const usersApi = {
  async search(query, limit = 10, requestOptions = {}) {
    const { data } = await httpClient.get('/api/users/search', {
      params: { q: query, limit },
      ...requestOptions,
    })
    const users = Array.isArray(data) ? data : []
    return users.map(normalizeUserOption)
  },

  /**
   * @deprecated 使用 getAssignableCandidates({ context: 'task' }) 替代
   * 该方法将在后续版本移除
   */
  async getTaskAssignmentCandidates(params = undefined) {
    const response = await httpClient.get('/api/tasks/assignment-candidates', { params })
    return response?.data || []
  },

  /**
   * 获取可指派候选人列表（统一端点）
   * @param {Object} params - 查询参数
   * @param {string} params.context - 业务场景：'task' 或 'tender'
   * @param {string} [params.deptCode] - 可选：按部门码过滤
   * @param {string} [params.roleCode] - 可选：按角色码过滤
   * @param {Object} [requestOptions] - 可选：axios 请求配置（如 signal）
   * @returns {Promise<Array>} 候选人列表
   */
  async getAssignableCandidates(params = {}, requestOptions = {}) {
    const { context, deptCode, roleCode } = params
    const response = await httpClient.get('/api/users/assignable-candidates', {
      params: { context, deptCode, roleCode },
      ...requestOptions,
    })
    const candidates = Array.isArray(response?.data) ? response.data : []
    return candidates.map(normalizeUserOption)
  },

  /**
   * CO-384: 批量按 ID 查询用户，用于补查已分配人员姓名（如投标辅助人员回显）。
   * 返回结构与 search/getAssignableCandidates 一致，已过 normalizeUserOption。
   * @param {Array<number>} ids - 用户 ID 数组
   * @returns {Promise<Array>} 用户列表
   */
  async getByIds(ids = []) {
    if (!Array.isArray(ids) || ids.length === 0) return []
    const response = await httpClient.get('/api/users/batch', {
      params: { ids: ids.join(',') },
    })
    const users = Array.isArray(response?.data) ? response.data : []
    return users.map(normalizeUserOption)
  }
}
