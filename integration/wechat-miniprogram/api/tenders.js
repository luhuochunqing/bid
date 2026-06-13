/**
 * 标讯 API 模块
 * 封装标讯相关的后端 API 调用
 */

const { get, post } = require('../utils/request.js');

/**
 * 获取标讯列表
 * @param {Object} params 查询参数
 * @param {number} params.page 页码
 * @param {number} params.size 每页数量
 * @param {string} params.status 状态筛选
 * @param {string} params.keyword 关键词搜索
 * @returns {Promise<{list: Array, total: number}>}
 */
const getTenderList = (params = {}) => {
  return get('/api/tenders', {
    page: params.page || 1,
    size: params.size || 20,
    status: params.status,
    keyword: params.keyword
  });
};

/**
 * 获取标讯详情
 * @param {number} id 标讯ID
 * @returns {Promise<Object>}
 */
const getTenderDetail = (id) => {
  return get(`/api/tenders/${id}`);
};

/**
 * 获取标讯状态列表
 * @returns {Promise<Array>}
 */
const getTenderStatuses = () => {
  return get('/api/tenders/statuses');
};

/**
 * 标记标讯为已读
 * @param {number} id 标讯ID
 */
const markAsRead = (id) => {
  return post(`/api/tenders/${id}/read`);
};

/**
 * 收藏标讯
 * @param {number} id 标讯ID
 */
const favoriteTender = (id) => {
  return post(`/api/tenders/${id}/favorite`);
};

/**
 * 取消收藏标讯
 * @param {number} id 标讯ID
 */
const unfavoriteTender = (id) => {
  return post(`/api/tenders/${id}/unfavorite`);
};

/**
 * 获取商机预测
 * @param {number} purchaserHash 业主哈希
 * @returns {Promise<Object>}
 */
const getMarketPrediction = (purchaserHash) => {
  return get(`/api/market-prediction/${purchaserHash}`);
};

/**
 * 获取我关注的标讯列表
 * @param {Object} params 查询参数
 * @returns {Promise<{list: Array, total: number}>}
 */
const getFavoriteTenders = (params = {}) => {
  return get('/api/tenders/favorites', {
    page: params.page || 1,
    size: params.size || 20
  });
};

module.exports = {
  getTenderList,
  getTenderDetail,
  getTenderStatuses,
  markAsRead,
  favoriteTender,
  unfavoriteTender,
  getMarketPrediction,
  getFavoriteTenders
};
