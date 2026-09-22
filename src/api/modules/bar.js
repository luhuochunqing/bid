// Input: httpClient and BAR resource endpoints
// Output: barAssetsApi
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * BAR资产管理模块 API
 * 真实 API 为唯一数据源
 */
import httpClient from '../client.js'

function normalizeAsset(item) {
  return {
    id: item?.id,
    name: item?.name || '未命名资产',
    category: item?.category || 'OTHER',
    serialNumber: item?.serialNumber || '',
    purchaseDate: item?.purchaseDate || '',
    status: item?.status || 'ACTIVE',
    owner: item?.owner || '',
    location: item?.location || '',
    value: item?.value || 0,
    description: item?.description || '',
    createdAt: item?.createdAt || '',
    updatedAt: item?.updatedAt || ''
  }
}

function normalizeCertificate(item) {
  return {
    id: item?.id,
    assetId: item?.assetId || null,
    assetName: item?.assetName || '',
    certificateType: item?.certificateType || 'LICENSE',
    certificateNumber: item?.certificateNumber || '',
    issueDate: item?.issueDate || '',
    expiryDate: item?.expiryDate || '',
    issuingAuthority: item?.issuingAuthority || '',
    status: item?.status || 'VALID',
    createdAt: item?.createdAt || '',
    updatedAt: item?.updatedAt || ''
  }
}

export const barAssetsApi = {
  async getAssets(params = {}) {
    const response = await httpClient.get('/api/resources/bar-assets', { params })
    return {
      ...response,
      data: response?.data?.content ? response.data.content.map(normalizeAsset) : (response?.data || []).map(normalizeAsset),
      total: response?.data?.totalElements || response?.data?.length || 0,
    }
  },

  async getAsset(id) {
    const response = await httpClient.get(`/api/resources/bar-assets/${id}`)
    return { ...response, data: normalizeAsset(response?.data) }
  },

  async createAsset(data) {
    const response = await httpClient.post('/api/resources/bar-assets', data)
    return { ...response, data: normalizeAsset(response?.data) }
  },

  async updateAsset(id, data) {
    const response = await httpClient.put(`/api/resources/bar-assets/${id}`, data)
    return { ...response, data: normalizeAsset({ ...response?.data, ...data }) }
  },

  async deleteAsset(id) {
    return httpClient.delete(`/api/resources/bar-assets/${id}`)
  },

  async getCertificates(params = {}) {
    const response = await httpClient.get('/api/resources/bar-certificates', { params })
    return {
      ...response,
      data: response?.data?.content ? response.data.content.map(normalizeCertificate) : (response?.data || []).map(normalizeCertificate),
      total: response?.data?.totalElements || response?.data?.length || 0,
    }
  },

  async getCertificate(id) {
    const response = await httpClient.get(`/api/resources/bar-certificates/${id}`)
    return { ...response, data: normalizeCertificate(response?.data) }
  },

  async createCertificate(data) {
    const response = await httpClient.post('/api/resources/bar-certificates', data)
    return { ...response, data: normalizeCertificate(response?.data) }
  },

  async updateCertificate(id, data) {
    const response = await httpClient.put(`/api/resources/bar-certificates/${id}`, data)
    return { ...response, data: normalizeCertificate({ ...response?.data, ...data }) }
  },

  async deleteCertificate(id) {
    return httpClient.delete(`/api/resources/bar-certificates/${id}`)
  },

  async getCertificatesByAsset(assetId) {
    const response = await httpClient.get(`/api/resources/bar-assets/${assetId}/certificates`)
    return {
      ...response,
      data: (response?.data || []).map(normalizeCertificate),
    }
  },
}

export default {
  barAssets: barAssetsApi,
}
