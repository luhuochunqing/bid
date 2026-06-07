// Input: httpClient
// Output: crmOpportunityApi - CRM 商机查询与关联 API
// Pos: src/api/modules/ - CRM integration API layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

/**
 * 查询 CRM 商机列表（按招标主体+报名截止时间+开标时间匹配）。
 * @param {Object} params - { purchaserName, registrationDeadline, bidOpeningTime }
 * @returns {Promise}
 */
async function searchCrmOpportunities(params = {}) {
  return httpClient.get('/api/xiyu/crm/opportunities', { params })
}

/**
 * 关联标讯与 CRM 商机。
 * @param {number} id 标讯 ID
 * @param {string} opportunityId CRM 商机 ID
 * @returns {Promise}
 */
async function linkCrmOpportunity(id, opportunityId) {
  return httpClient.post(`/api/tenders/${id}/link-opportunity`, { opportunityId })
}

export const crmOpportunityApi = {
  searchCrmOpportunities,
  linkCrmOpportunity,
}

export default crmOpportunityApi
