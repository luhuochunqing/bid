// Input: 文件对象 + obsUpload 实例 + 后端 API 调用函数
// Output: { formData, obsFileUrl } 或回退后的 API 调用结果
// Pos: src/composables/ - OBS 直传 + 415 回退公共函数
// D3-2 修复：消除 useProjectDetailBidAgent / useProjectDetailTaskActions / useProjectDetailBidAgent 三处重复

const OBS_DIRECT_PREFIX = 'obs-direct:'
export const isObsEnabled = import.meta.env.VITE_OBS_ENABLED === 'true'

// 集中导出文件大小限制（数值 + 文案），避免分散在多处硬编码导致不一致
export const MAX_FILE_SIZE_BYTES = (isObsEnabled ? 500 : 50) * 1024 * 1024
export const MAX_FILE_SIZE_TEXT = isObsEnabled ? '500MB' : '50MB'

/**
 * 尝试 OBS 直传；失败时返回 null 让调用方走 multipart。
 *
 * @param {import('@/composables/useObsUpload.js').ObsUpload} obsUpload - useObsUpload 返回的实例
 * @param {File|Blob} file - 待上传文件
 * @returns {Promise<string|null>} 成功返回 obs-direct:{uploadId}，失败返回 null
 */
export async function tryObsDirectUpload(obsUpload, file) {
  if (!isObsEnabled) return null
  try {
    const completed = await obsUpload.upload(file)
    return OBS_DIRECT_PREFIX + completed.uploadId
  } catch (err) {
    console.warn('OBS 直传失败，回退到 multipart:', err?.message || err)
    return null
  }
}

/**
 * 构造用于上传的 FormData：若 obsFileUrl 存在则走 fileUrl 模式，否则走 multipart 文件模式。
 *
 * @param {File|Blob} file - 原始文件
 * @param {string|null} obsFileUrl - OBS 直传返回的 URL（obs-direct:{uploadId}），null 表示走 multipart
 * @param {string} [fallbackName='招标文件'] - 当文件名为空时的兜底名
 * @returns {FormData}
 */
export function buildUploadFormData(file, obsFileUrl, fallbackName = '招标文件') {
  const formData = new FormData()
  const fileName = file?.name || fallbackName
  if (obsFileUrl) {
    formData.set('fileUrl', obsFileUrl)
    formData.set('fileName', fileName)
    formData.set('fileType', file?.type || 'application/octet-stream')
  } else {
    formData.set('file', file, fileName)
  }
  return formData
}

/**
 * 调用后端 API：OBS 直传模式下若后端返回 415（不支持 fileUrl），自动回退到 multipart 重试。
 *
 * @param {File|Blob} file - 原始文件
 * @param {string|null} obsFileUrl - OBS 直传 URL，null 表示走 multipart
 * @param {Function} apiCall - (formData) => Promise<response>，由调用方提供
 * @param {string} [fallbackName='招标文件']
 * @returns {Promise<any>} API 响应
 */
export async function callApiWithObsFallback(file, obsFileUrl, apiCall, fallbackName = '招标文件') {
  const formData = buildUploadFormData(file, obsFileUrl, fallbackName)
  try {
    return await apiCall(formData)
  } catch (apiErr) {
    if (!obsFileUrl || apiErr?.response?.status !== 415) throw apiErr
    // 后端不支持 fileUrl 参数（415 Unsupported Media Type），回退到 multipart
    const fallbackFormData = buildUploadFormData(file, null, fallbackName)
    return await apiCall(fallbackFormData)
  }
}
