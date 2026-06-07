/**
 * 待办任务 API 模块
 */

const { get, post, put } = require('../utils/request.js');

/**
 * 获取我的待办列表
 * @param {Object} params 查询参数
 * @returns {Promise<{list: Array, total: number}>}
 */
const getTodoList = (params = {}) => {
  return get('/api/tasks', {
    page: params.page || 1,
    size: params.size || 20,
    status: params.status || 'TODO',
    assigneeId: params.assigneeId
  });
};

/**
 * 获取待办详情
 * @param {number} id 待办ID
 * @returns {Promise<Object>}
 */
const getTodoDetail = (id) => {
  return get(`/api/tasks/${id}`);
};

/**
 * 完成待办
 * @param {number} id 待办ID
 * @param {string} remark 完成备注
 * @returns {Promise<Object>}
 */
const completeTodo = (id, remark) => {
  return post(`/api/tasks/${id}/complete`, { remark });
};

/**
 * 标记待办为进行中
 * @param {number} id 待办ID
 * @returns {Promise<Object>}
 */
const startTodo = (id) => {
  return put(`/api/tasks/${id}/start`);
};

/**
 * 获取待办数量统计
 * @returns {Promise<Object>}
 */
const getTodoStats = () => {
  return get('/api/tasks/stats');
};

/**
 * 获取我创建的待办列表
 * @param {Object} params 查询参数
 * @returns {Promise<{list: Array, total: number}>}
 */
const getCreatedTasks = (params = {}) => {
  return get('/api/tasks/created', {
    page: params.page || 1,
    size: params.size || 20
  });
};

module.exports = {
  getTodoList,
  getTodoDetail,
  completeTodo,
  startTodo,
  getTodoStats,
  getCreatedTasks
};
