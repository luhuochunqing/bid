// Input: httpClient
// Output: organizationApi
// Pos: src/api/modules/ - Organization management API layer
// 维护声明:
//   - 维护人: [your-name]
//   - 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import httpClient from '../client.js'

export const organizationApi = {
  /**
   * 查询部门列表。
   * @param {string} [sourceApp] - 按来源应用过滤，避免多来源数据重复展示
   * @returns {Promise<Array<{departmentCode, departmentName, parentDepartmentCode, enabled}>>}
   */
  async listDepartments(sourceApp) {
    const params = sourceApp ? { sourceApp } : undefined
    const res = await httpClient.get('/api/admin/organization/departments', { params })
    return res?.data || res || []
  },

  /**
   * 分页查询用户列表。
   * @param {Object} params
   * @param {number} params.page - 页码，从1开始
   * @param {number} params.size - 每页大小
   * @param {string} [params.keyword] - 搜索关键词
   * @param {boolean} [params.enabled] - 启用状态筛选
   * @param {string} [params.departmentCode] - 部门编码筛选
   * @returns {Promise<{list: Array, totalCount: number, pageIndex: number, pageSize: number}>}
   */
  async listUsersPage(params = {}) {
    const res = await httpClient.get('/api/admin/users/page', { params })
    return res?.data || res || { list: [], totalCount: 0, pageIndex: 1, pageSize: 10 }
  },

  /**
   * 更新用户启用/禁用状态。
   * @param {number} id
   * @param {boolean} enabled
   * @returns {Promise<Object>}
   */
  async updateUserStatus(id, enabled) {
    return httpClient.patch(`/api/admin/users/${id}/status`, { enabled })
  },

  /**
   * 更新用户部门信息。
   * @param {number} id
   * @param {string} departmentCode
   * @param {string} departmentName
   * @returns {Promise<Object>}
   */
  async updateUserOrganization(id, departmentCode, departmentName) {
    return httpClient.put(`/api/admin/users/${id}/organization`, { departmentCode, departmentName })
  },
}

export default organizationApi
