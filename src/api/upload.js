// Input: projectId, tenderId 等业务标识 + 文件数据
// Output: uploadApi — 统一定义所有文件上传端点 URL 和上传函数，消除前端硬编码的后端 404
// Pos: src/api/ — API 基础设施层，禁止在组件中硬编码 /api/upload
// 维护声明: 新增业务上传类型时需同步更新 UPLOAD_ENDPOINTS + ProjectFileUpload.vue 的 businessType 文档

import httpClient from './client.js'

const UPLOAD_ENDPOINTS = {
  projectDocument: (projectId) => `/api/projects/${projectId}/documents`,
  projectWorkflow: (projectId) => `/api/projects/${projectId}/workflow/documents`,
  tenderEvaluation: (tenderId) => `/api/tenders/${tenderId}/evaluation/documents`,
  qualificationAttachment: () => '/api/qualifications/attachment',
  brandAuthAttachment: () => '/api/brand-auth/attachment',
  caseAttachment: () => '/api/cases/attachment',
  bidResultAttachment: () => '/api/bid-results/attachment',
}

/**
 * 根据业务类型和业务 ID 获取上传端点 URL
 * @param {'projectDocument'|'projectWorkflow'|'tenderEvaluation'|'qualificationAttachment'|'brandAuthAttachment'|'caseAttachment'|'bidResultAttachment'} businessType
 * @param {string|number} [businessId]
 * @returns {string} 完整的上传 URL
 */
export function getUploadUrl(businessType, businessId) {
  const fn = UPLOAD_ENDPOINTS[businessType]
  if (!fn) {
    console.error(`[uploadApi] 不支持的 businessType: "${businessType}"`)
    return ''
  }
  return fn(businessId)
}

let _tokenStore = null

export function registerTokenStore(store) {
  _tokenStore = store
}

export function getUploadHeaders() {
  const token = _tokenStore?.value?.token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * 上传文件到指定业务模块
 */
export async function uploadFile(businessType, businessId, file, metadata = {}) {
  const url = getUploadUrl(businessType, businessId)
  if (!url) throw new Error(`[uploadApi] 无法获取上传 URL，businessType=${businessType}`)
  const formData = new FormData()
  formData.set('file', file)
  Object.entries(metadata).forEach(([k, v]) => { if (v != null) formData.set(k, v) })
  return httpClient.post(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data', ...getUploadHeaders() },
  })
}

export default { getUploadUrl, getUploadHeaders, uploadFile, registerTokenStore }
