// Input: 业务类型 + 成功提示文案
// Output: obsUpload 实例（供 ObsUploadProgress 组件使用）+ tryUpload 方法
// Pos: src/views/Bidding/list/composables/ - 标讯模块 OBS 直传共享 composable
import { ElMessage } from 'element-plus'
import { useObsUpload } from '@/composables/useObsUpload.js'
import { tryObsDirectUpload } from '@/composables/useObsUploadFallback.js'

/**
 * 标讯模块 OBS 直传共享 composable。
 *
 * VITE_OBS_ENABLED=true 时启用直传；失败时 tryUpload 返回 false 让调用方回退到 multipart。
 * obsUpload 实例暴露给调用方，用于传递给 ObsUploadProgress 组件显示进度。
 *
 * @param {string} businessType - OBS 业务类型
 * @param {string} successMessage - 上传成功提示文案
 */
export function useTenderObsUpload(businessType, successMessage = '文件已上传至 OBS') {
  const obsUpload = useObsUpload({ businessType })

  /**
   * 尝试 OBS 直传：成功返回 true，失败返回 false。
   * VITE_OBS_ENABLED != 'true' 时直接返回 false（走 multipart）。
   * 成功时将 obs-direct:{uploadId} 写入 attachments[fileIndex] 的 url/fileUrl。
   */
  async function tryUpload(uploadFile, attachments, fileIndex) {
    const obsFileUrl = await tryObsDirectUpload(obsUpload, uploadFile)
    if (!obsFileUrl) return false
    if (fileIndex >= 0 && attachments[fileIndex]) {
      const att = attachments[fileIndex]
      if (uploadFile.type) att.fileType = uploadFile.type
      att.url = obsFileUrl
      att.fileUrl = obsFileUrl
    }
    ElMessage.success(successMessage)
    return true
  }

  return { obsUpload, tryUpload }
}
