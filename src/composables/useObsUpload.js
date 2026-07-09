// Input: File 对象 + 业务参数
// Output: 上传状态 + 进度 + 控制函数
// Pos: src/composables/ - OBS 直传上传 composable
import { ref, computed } from 'vue'
import { requestUploadToken, notifyUploadCompleted } from '../api/files.js'

/**
 * OBS 直传上传 composable
 *
 * 流程：
 * 1. 调用后端 /api/files/upload-token 获取临时凭证
 * 2. 用 OBS BrowserJS SDK 分片上传文件到 OBS
 * 3. 上传完成后回调后端 /api/files/{uploadId}/completed
 *
 * @param {Object} options
 * @param {number} [options.chunkSize=5*1024*1024] 分片大小（字节），默认 5MB
 * @param {number} [options.maxConcurrency=3] 并发上传数
 * @param {string} [options.businessType] 业务类型
 */
export function useObsUpload(options = {}) {
  const chunkSize = options.chunkSize ?? 5 * 1024 * 1024
  const maxConcurrency = options.maxConcurrency ?? 3
  const businessType = options.businessType

  const uploading = ref(false)
  const progress = ref(0)
  const currentFile = ref(null)
  const uploadId = ref(null)
  const error = ref(null)
  const completedFile = ref(null)

  const progressPercent = computed(() => Math.round(progress.value * 100))

  let obsClient = null
  let cancelled = false

  /**
   * 动态加载 OBS BrowserJS SDK
   */
  async function ensureObsClient(temporaryCredentials, obsEndpoint) {
    if (obsClient && !cancelled) return obsClient
    const ObsClient = (await import('esdk-obs-browserjs')).default
    obsClient = new ObsClient({
      access_key_id: temporaryCredentials.accessKey,
      secret_access_key: temporaryCredentials.secretKey,
      security_token: temporaryCredentials.securityToken,
      server: obsEndpoint,
    })
    return obsClient
  }

  /**
   * 上传文件
   * @param {File} file
   * @returns {Promise<{uploadId: string, objectKey: string}>}
   */
  async function upload(file) {
    if (uploading.value) {
      throw new Error('已有上传任务进行中')
    }

    uploading.value = true
    progress.value = 0
    currentFile.value = file
    error.value = null
    completedFile.value = null
    uploadId.value = null
    cancelled = false

    try {
      const tokenResp = await requestUploadToken({
        fileName: file.name,
        fileSize: file.size,
        mimeType: file.type,
        businessType,
      })

      uploadId.value = tokenResp.uploadId
      const client = await ensureObsClient(
        {
          accessKey: tokenResp.accessKey,
          secretKey: tokenResp.secretKey,
          securityToken: tokenResp.securityToken,
        },
        tokenResp.endpoint,
      )

      const result = await client.uploadFile({
        Bucket: tokenResp.bucket,
        Key: tokenResp.objectKey,
        SourceFile: file,
        PartSize: chunkSize,
        ProgressCallback: (uploaded, total) => {
          progress.value = uploaded / total
        },
      })

      if (result.CommonMsg.Status >= 300) {
        throw new Error(
          `OBS 上传失败: ${result.CommonMsg.Message || result.CommonMsg.Status}`,
        )
      }

      const etag = result.InterfaceResult?.ETag || ''
      await notifyUploadCompleted(uploadId.value, {
        objectKey: tokenResp.objectKey,
        etag,
        bucket: tokenResp.bucket,
      })

      completedFile.value = {
        uploadId: uploadId.value,
        objectKey: tokenResp.objectKey,
        bucket: tokenResp.bucket,
        fileName: file.name,
        fileSize: file.size,
      }

      return completedFile.value
    } catch (err) {
      if (!cancelled) {
        error.value = err
      }
      throw err
    } finally {
      uploading.value = false
      if (cancelled) {
        obsClient = null
      }
    }
  }

  function cancel() {
    cancelled = true
    if (obsClient) {
      try {
        obsClient.close()
      } catch (e) {
        // ignore
      }
    }
    obsClient = null
    uploading.value = false
    progress.value = 0
  }

  function reset() {
    uploading.value = false
    progress.value = 0
    currentFile.value = null
    uploadId.value = null
    error.value = null
    completedFile.value = null
    cancelled = false
    obsClient = null
  }

  return {
    uploading,
    progress,
    progressPercent,
    currentFile,
    uploadId,
    error,
    completedFile,
    upload,
    cancel,
    reset,
  }
}

export default useObsUpload
