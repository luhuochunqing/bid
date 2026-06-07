// Input: httpClient
// Output: crmApi - CRM 商机查询、标讯回传和对接人查询
// Pos: src/api/modules/ - CRM integration API layer (对接客户真实 CRM API)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

export const crmApi = {
  /**
   * 分页查询 CRM 商机列表（代理客户 POST /customer-chance/page-list）。
   * @param {Object} params - 分页查询条件
   * @param {number} params.pageIndex - 页码，从 1 开始
   * @param {number} params.pageSize - 每页大小
   * @param {Object} [params.body] - 筛选条件
   * @param {string[]} [params.body.groupName] - 集团名称
   * @param {string} [params.body.name] - 商机名称
   * @param {string} [params.body.code] - 商机编号
   * @param {number[]} [params.body.projectStatus] - 项目状态
   * @param {number[]} [params.body.projectRisk] - 项目风险
   * @param {number} [params.body.cooperationStatus] - 合作状态
   * @param {string} [params.body.evaluationStartTime] - 评标开始时间
   * @param {string} [params.body.evaluationEndTime] - 评标结束时间
   * @param {string[]} [params.body.projectLeaderName] - 项目负责人
   * @param {string} [params.body.projectLeaderNo] - 项目负责人工号
   * @param {string} [params.body.updateStartAt] - 更新开始时间
   * @param {string} [params.body.updateEndAt] - 更新结束时间
   * @param {boolean} [params.body.selectAll] - 全选
   * @param {number[]} [params.body.selectList] - 正选
   * @param {number[]} [params.body.notSelectList] - 反选
   * @param {number} [params.body.timeSort] - 评标时间排序（1/null=正序, 2=倒序）
   * @returns {Promise<{data: {list: Array, totalCount: number, pageSize: number, pageIndex: number}}>}
   */
  async searchOpportunities(params) {
    return httpClient.post('/api/xiyu/crm/chances/page-list', params)
  },

  /**
   * 标讯回传（代理客户 POST /customer-chance/bidInfoSync）。
   * @param {Array<{name: string, code: string, status: number, statusEditor: string, statusEditTime: string, feedback: string}>} bidInfoList
   * @returns {Promise<{data: null}>}
   */
  async bidInfoSync(bidInfoList) {
    return httpClient.post('/api/xiyu/crm/chances/bid-info-sync', { bidInfoList })
  },

  /**
   * 按商机 ID 查询对接人列表（代理客户 POST /contact-person-info/page-list）。
   * @param {number} ccId - 商机 ID
   * @returns {Promise<{data: Array}>}
   */
  async getContactPersons(ccId) {
    return httpClient.post('/api/xiyu/crm/chances/contact-persons', ccId)
  },
}

export default crmApi
