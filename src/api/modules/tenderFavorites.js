// Input: httpClient, tender favorite endpoints
// Output: tenderFavoritesApi - favorite toggle, list, check accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

export const tenderFavoritesApi = {
  /**
   * 切换收藏状态（已收藏则取消，未收藏则添加）
   * @param {number} tenderId
   * @returns {Promise<{favorited: boolean}>}
   */
  async toggleFavorite(tenderId) {
    return httpClient.post(`/api/tender-favorites/${tenderId}`)
  },

  /**
   * 获取当前用户收藏的所有标讯ID列表
   * @returns {Promise<{ids: number[]}>}
   */
  async getFavoriteIds() {
    return httpClient.get('/api/tender-favorites/ids')
  },

  /**
   * 分页获取收藏标讯列表（含详情）
   * @param {{ page?: number, size?: number }} params
   * @returns {Promise}
   */
  async getMyFavorites(params = {}) {
    return httpClient.get('/api/tender-favorites', { params })
  },

  /**
   * 取消收藏
   * @param {number} tenderId
   */
  async removeFavorite(tenderId) {
    return httpClient.delete(`/api/tender-favorites/${tenderId}`)
  },

  /**
   * 检查是否已收藏
   * @param {number} tenderId
   * @returns {Promise<{favorited: boolean}>}
   */
  async checkFavorited(tenderId) {
    return httpClient.get(`/api/tender-favorites/check/${tenderId}`)
  }
}

export default tenderFavoritesApi
