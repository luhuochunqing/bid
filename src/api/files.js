// Input: 后端 /api/files/* 接口
// Output: filesApi — OBS 直传相关 API 封装
// Pos: src/api/ — API 基础设施层

import httpClient from './client.js'

/**
 * 申请 OBS 临时上传凭证
 * @param {Object} params
 * @param {string} params.fileName 原始文件名
 * @param {number} params.fileSize 文件大小（字节）
 * @param {string} [params.mimeType] MIME 类型
 * @param {string} [params.businessType] 业务类型（预留）
 * @returns {Promise<Object>} 临时凭证 + uploadId + bucket + objectKey
 */
export function requestUploadToken({ fileName, fileSize, mimeType, businessType }) {
  return httpClient.post('/api/files/upload-token', {
    fileName,
    fileSize,
    mimeType,
    businessType,
  }).then(res => res.data)
}

/**
 * 通知后端上传完成
 * @param {string} uploadId
 * @param {Object} params
 * @param {string} params.objectKey OBS 对象 key（必填）
 * @param {string} [params.etag] OBS 返回的 ETag
 * @param {string} params.bucket OBS 桶名（必填）
 * @returns {Promise<void>}
 */
export function notifyUploadCompleted(uploadId, { objectKey, etag, bucket }) {
  return httpClient.post(`/api/files/${uploadId}/completed`, {
    objectKey,
    etag,
    bucket,
  }).then(res => res.data)
}

/**
 * 获取 OBS 预签名下载 URL
 * @param {string} uploadId
 * @param {number} [expireSeconds=300] 下载链接有效期（秒）
 * @returns {Promise<{url: string, expiresAt: string}>}
 */
export function getDownloadUrl(uploadId, expireSeconds = 300) {
  return httpClient.get(`/api/files/${uploadId}/download-url`, {
    params: { expireSeconds },
  }).then(res => res.data)
}

export default { requestUploadToken, notifyUploadCompleted, getDownloadUrl }
