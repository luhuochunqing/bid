// Input: projectId (ref/computed/getter) + 业务上下文 (uploaderId/uploaderName/documentCategory)
// Output: { obsUpload, customUpload } — customUpload 符合 el-upload :http-request 签名
// Pos: src/composables/ - 项目文档 OBS 直传 + multipart 回退公共 composable
// 修复 APISIX 网关 413：大文件直传 OBS 绕过网关，小文件仍走 multipart

import { watch } from 'vue'
import { useObsUpload } from '@/composables/useObsUpload.js'
import { tryObsDirectUpload, isObsEnabled } from '@/composables/useObsUploadFallback.js'
import { uploadDocument } from '@/api/modules/projectDocuments.js'

/**
 * 创建项目文档上传 composable。
 *
 * 用法：
 *   const { obsUpload, customUpload } = useObsProjectDocumentUpload(
 *     () => props.projectId,
 *     {
 *       uploaderId: () => userStore.currentUser?.id,
 *       uploaderName: () => userStore.userName,
 *     }
 *   )
 *   // 模板：<el-upload :http-request="customUpload" :data="{ documentCategory: 'BID' }">
 *
 * @param {Function|import('vue').Ref} projectIdRef - projectId getter/ref
 * @param {Object} [ctx] - 业务上下文
 * @param {Function} [ctx.uploaderId] - () => number | string
 * @param {Function} [ctx.uploaderName] - () => string
 * @returns {{ obsUpload: Object, customUpload: Function }}
 */
export function useObsProjectDocumentUpload(projectIdRef, ctx = {}) {
  const obsUpload = useObsUpload()

  /**
   * el-upload :http-request 兼容的自定义上传函数。
   * OBS 启用时先直传 OBS（绕过 APISIX 网关），失败回退到 multipart。
   *
   * ⚠️ 重要：不要手动调用 options.onSuccess/options.onError！
   * el-upload 的 doUpload 会执行 `request.then(options.onSuccess, options.onError)`，
   * 如果 customUpload 内部也手动调用，会导致 onSuccess/onError 被双重触发，
   * 引发 el-upload 内部状态混乱（如 [ElUpload] file to be removed not found）。
   * 正确做法：return response / throw err，让 el-upload 的 Promise 链自动处理。
   *
   * @param {Object} options - el-upload http-request options
   * @param {File} options.file - 待上传文件
   * @param {Object} options.data - el-upload :data 额外字段（documentCategory 等）
   * @param {Function} [options.onProgress] - el-upload 进度回调，接收 { percent: number }
   * @returns {Promise<any>} 成功 resolve(response)，失败 reject(err)
   */
  async function customUpload(options) {
    const projectId = typeof projectIdRef === 'function' ? projectIdRef() : projectIdRef.value
    const file = options.file
    const extraData = options.data || {}

    // OBS 直传时把 obsUpload.progressPercent 同步给 el-upload，UI 显示百分比进度
    // 使用 useObsUpload 已暴露的 progressPercent（0-100 computed），避免重复转换
    let stopProgressWatch = null
    if (isObsEnabled && typeof options.onProgress === 'function') {
      stopProgressWatch = watch(obsUpload.progressPercent, (percent) => {
        options.onProgress({ percent })
      })
    }

    try {
      // OBS 直传：成功返回 obs-direct:{uploadId}，失败/未启用返回 null
      const obsFileUrl = isObsEnabled ? await tryObsDirectUpload(obsUpload, file) : null

      // OBS 失败回退 multipart 时，重置进度避免进度条停在中间值（如 50%）后突然跳到 100%
      if (isObsEnabled && !obsFileUrl && obsUpload.progress) {
        obsUpload.progress.value = 0
      }

      const formData = new FormData()
      formData.set('name', file.name)
      formData.set('size', `${Math.max(1, Math.round((file.size || 1024 * 1024) / 1024 / 1024))}MB`)
      formData.set('fileType', file.type || 'application/octet-stream')
      if (extraData.documentCategory) formData.set('documentCategory', extraData.documentCategory)
      if (extraData.linkedEntityType) formData.set('linkedEntityType', extraData.linkedEntityType)
      if (extraData.linkedEntityId) formData.set('linkedEntityId', extraData.linkedEntityId)
      const uploaderId = ctx.uploaderId?.()
      const uploaderName = ctx.uploaderName?.()
      if (uploaderId != null) formData.set('uploaderId', String(uploaderId))
      if (uploaderName) formData.set('uploaderName', uploaderName)

      // OBS 模式：带 fileUrl（走 JSON 变体）；传统模式：带 file（走 multipart）
      if (obsFileUrl) {
        formData.set('fileUrl', obsFileUrl)
      } else {
        formData.set('file', file, file.name)
      }

      // uploadDocument 内部根据 formData 是否有 'file' key 自动选择 multipart/JSON
      // httpClient response 拦截器已 unwrap response.data → 返回后端 body { success, data, msg }
      // ⚠️ return response 让 el-upload Promise 链自动调用 onSuccess，禁止手动调用
      // ⚠️ UI 提示（ElMessage.success）由调用方负责，composable 只负责业务逻辑
      return await uploadDocument(projectId, formData)
    } finally {
      if (stopProgressWatch) stopProgressWatch()
    }
  }

  return { obsUpload, customUpload }
}
