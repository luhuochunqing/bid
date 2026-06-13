/**
 * 请求封装模块
 * 基于 wx.request 封装，统一处理认证、错误和响应
 */

const app = getApp();

const request = (options) => {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token');

    wx.request({
      url: `${app.globalData.apiBaseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : '',
        ...options.header
      },
      timeout: options.timeout || 30000,
      success: (res) => {
        if (res.statusCode === 200) {
          if (res.data.code === 0 || res.data.success) {
            resolve(res.data.data);
          } else {
            wx.showToast({
              title: res.data.message || '请求失败',
              icon: 'none'
            });
            reject(res.data);
          }
        } else if (res.statusCode === 401) {
          wx.removeStorageSync('token');
          wx.reLaunch({ url: '/pages/login/login' });
          reject({ message: '未授权，请重新登录' });
        } else {
          wx.showToast({
            title: '服务器错误',
            icon: 'none'
          });
          reject({ message: `服务器错误: ${res.statusCode}` });
        }
      },
      fail: (err) => {
        wx.showToast({
          title: '网络请求失败',
          icon: 'none'
        });
        reject(err);
      }
    });
  });
};

/**
 * GET 请求
 */
const get = (url, params, options = {}) => {
  return request({
    url,
    method: 'GET',
    data: params,
    ...options
  });
};

/**
 * POST 请求
 */
const post = (url, data, options = {}) => {
  return request({
    url,
    method: 'POST',
    data,
    ...options
  });
};

/**
 * PUT 请求
 */
const put = (url, data, options = {}) => {
  return request({
    url,
    method: 'PUT',
    data,
    ...options
  });
};

/**
 * DELETE 请求
 */
const del = (url, params, options = {}) => {
  return request({
    url,
    method: 'DELETE',
    data: params,
    ...options
  });
};

module.exports = {
  request,
  get,
  post,
  put,
  delete: del
};
