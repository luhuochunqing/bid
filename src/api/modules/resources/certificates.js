// Input: BAR certificate API responses and certificate payloads
// Output: barCertificatesApi - normalized certificate accessors
// Pos: src/api/modules/resources/ - Certificate API submodule
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { httpClient, formatDate, invalidIdMessage, isNumericId, pageContent } from '@/api/modules/resources/shared'

function normalizeCertificateStatus(status) {
  const value = String(status || '').toUpperCase()
  if (value === 'BORROWED') return 'borrowed'
  if (value === 'EXPIRED') return 'expired'
  if (value === 'DISABLED') return 'disabled'
  return 'available'
}

function normalizeCertificate(item = {}) {
  return {
    id: item.id,
    type: item.type || '',
    provider: item.provider || '',
    serialNo: item.serialNo || '',
    holder: item.holder || '',
    location: item.location || '',
    expiryDate: formatDate(item.expiryDate),
    status: normalizeCertificateStatus(item.status),
    borrower: item.currentBorrower || '',
    borrowProjectId: item.currentProjectId || null,
    borrowPurpose: item.borrowPurpose || '',
    expectedReturn: formatDate(item.expectedReturnDate),
    remark: item.remark || '',
    raw: item
  }
}

export const barCertificatesApi = {
  async getList(siteId) {
    if (!isNumericId(siteId)) return Promise.resolve(invalidIdMessage('bar certificate'))
    const response = await httpClient.get(`/api/resources/bar-assets/${siteId}/certificates`)
    const { content } = pageContent(response)
    return { ...response, data: content.map(normalizeCertificate) }
  },

  async create(siteId, data) {
    if (!isNumericId(siteId)) return Promise.resolve(invalidIdMessage('bar asset'))
    const response = await httpClient.post(`/api/resources/bar-assets/${siteId}/certificates`, data)
    return { ...response, data: normalizeCertificate(response?.data) }
  },

  async update(siteId, certificateId, data) {
    if (!isNumericId(siteId) || !isNumericId(certificateId)) return Promise.resolve(invalidIdMessage('bar certificate'))
    const response = await httpClient.put(`/api/resources/bar-assets/${siteId}/certificates/${certificateId}`, data)
    return { ...response, data: normalizeCertificate(response?.data) }
  },

  async delete(siteId, certificateId) {
    if (!isNumericId(siteId) || !isNumericId(certificateId)) return Promise.resolve(invalidIdMessage('bar certificate'))
    return httpClient.delete(`/api/resources/bar-assets/${siteId}/certificates/${certificateId}`)
  },

  async borrow(siteId, certificateId, data) {
    if (!isNumericId(siteId) || !isNumericId(certificateId)) return Promise.resolve(invalidIdMessage('bar certificate'))
    const response = await httpClient.post(`/api/resources/bar-assets/${siteId}/certificates/${certificateId}/borrow`, data)
    return { ...response, data: normalizeCertificate(response?.data) }
  },

  async return(siteId, certificateId, data = {}) {
    if (!isNumericId(siteId) || !isNumericId(certificateId)) return Promise.resolve(invalidIdMessage('bar certificate'))
    const response = await httpClient.post(`/api/resources/bar-assets/${siteId}/certificates/${certificateId}/return`, data)
    return { ...response, data: normalizeCertificate(response?.data) }
  },

  async getBorrowRecords(siteId, certificateId) {
    if (!isNumericId(siteId) || !isNumericId(certificateId)) return Promise.resolve(invalidIdMessage('bar certificate'))
    return httpClient.get(`/api/resources/bar-assets/${siteId}/certificates/${certificateId}/borrow-records`)
  }
}
