/**
 * 下载工具函数
 *
 * 提供通用的文件下载能力，支持从 Content-Disposition 头解析文件名。
 */
import { ElMessage } from 'element-plus'
import httpClient from '@/api/client.js'

/**
 * 从 Content-Disposition 头中解析文件名
 * @param {string} disposition - Content-Disposition 头的值
 * @param {string} fallbackName - 解析失败时的备用文件名
 * @returns {string} 解析到的文件名
 */
export function parseFilenameFromDisposition(disposition, fallbackName) {
  if (!disposition) return fallbackName
  // 优先匹配 RFC 5987 格式：filename*=UTF-8''encoded-name
  const rfc5987Match = disposition.match(/filename\*=UTF-8''(.+?)(?:;|$)/i)
  if (rfc5987Match) {
    try {
      return decodeURIComponent(rfc5987Match[1].trim())
    } catch {
      return rfc5987Match[1].trim()
    }
  }
  // 回退匹配普通格式：filename="name" 或 filename=name
  const simpleMatch = disposition.match(/filename="?([^";\n]+)"?/i)
  if (simpleMatch) {
    return simpleMatch[1].trim()
  }
  return fallbackName
}

/**
 * 通过隐藏的原生 <a> 标签触发浏览器原生流式下载
 *
 * 适用场景：大文件（>100MB 乃至 2GB+）下载、重定向至对象存储（如 OBS / S3 直链）的接口。
 * 优势：由浏览器原生下载引擎接管流式写入磁盘，支持断点续传，零 JS 内存开销，避免 Axios responseType: 'blob' 导致的内存暴涨崩溃及超时。
 *
 * @param {string} url - 下载地址
 * @param {string} [filename] - 期望的文件名（当响应头未提供 filename 时浏览器回退使用）
 */
export function triggerNativeDownload(url, filename) {
  if (!url || typeof window === 'undefined') return
  const a = document.createElement('a')
  a.href = url
  if (filename) {
    a.download = filename
  }
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

/**
 * 通过 fetch + blob 方式下载文件，支持从响应头获取文件名
 *
 * 适用场景：需要从 Content-Disposition 头读取真实文件名的下载。
 * 不适用场景：大文件（>100MB）下载，因为会将整个文件加载到内存。大文件请传入 options: { native: true }。
 *
 * @param {string} url - 下载地址
 * @param {string} fallbackName - 无法从响应头获取文件名时的备用名称
 * @param {Object} [options] - 下载配置项
 * @param {boolean} [options.native] - 是否使用原生 <a> 标签触发流式下载（推荐用于大文件）
 * @returns {Promise<void>}
 */
export async function downloadWithFilename(url, fallbackName, options = {}) {
  if (!url) return

  if (options?.native) {
    triggerNativeDownload(url, fallbackName)
    return
  }

  const apiUrl = normalizeApiDownloadUrl(url)

  if (apiUrl) {
    try {
      const response = await httpClient.get(apiUrl, {
        responseType: 'blob',
        timeout: 120000,
        skipGlobalErrorMessage: true
      })
      const disposition = response.headers?.['content-disposition'] || response.headers?.get?.('Content-Disposition') || ''
      const filename = parseFilenameFromDisposition(disposition, fallbackName)
      triggerBlobDownload(response.data, filename)
    } catch (error) {
      showApiDownloadError(error)
    }
    return
  }

  try {
    const resp = await fetch(url, { credentials: 'include' })
    if (!resp.ok) {
      window.open(url, '_blank')
      return
    }
    const disposition = resp.headers.get('Content-Disposition') || ''
    const filename = parseFilenameFromDisposition(disposition, fallbackName)
    const blob = await resp.blob()
    triggerBlobDownload(blob, filename)
  } catch {
    window.open(url, '_blank')
  }
}

export function normalizeApiDownloadUrl(url) {
  try {
    const parsed = new URL(url, window.location.origin)
    if (!parsed.pathname.startsWith('/api/')) {
      return ''
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`
  } catch {
    const rawUrl = String(url || '')
    return rawUrl.startsWith('/api/') ? rawUrl : ''
  }
}

export function showApiDownloadError(error) {
  const status = error?.response?.status
  if (status === 401 || status === 403) {
    ElMessage.error('登录已过期或访问入口不一致，请刷新页面并重新登录后下载')
    return
  }
  // 透传后端业务消息（如"投标文件已进入「XX」阶段，文件只读不可下载"）
  // 后端 ApiResponse 通过 @JsonProperty("msg") 输出消息字段
  const backendMsg = error?.response?.data?.msg
  if (backendMsg && typeof backendMsg === 'string') {
    ElMessage.error(backendMsg)
    return
  }
  ElMessage.error('文件下载失败，请稍后重试')
}

/**
 * 通过 blob URL 触发浏览器下载
 *
 * 适用场景：已有 blob 数据，需要触发下载。
 *
 * @param {Blob} blob - 文件 blob 数据
 * @param {string} filename - 下载文件名
 */
export function triggerBlobDownload(blob, filename) {
  if (!blob || typeof window === 'undefined') return
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // 延迟释放 blob URL，确保浏览器有足够时间开始下载
  setTimeout(() => URL.revokeObjectURL(url), 100)
}

/**
 * 判断 blob 是否实为 JSON 错误响应体。
 * 场景：HTTP 4xx/5xx 错误被 responseType:'blob' 包成 blob（如 403 无权限），
 * 若直接落盘会得到无法打开的假文件（如假 ZIP）。
 *
 * @param {Blob} blob - 待检测的响应体
 * @returns {boolean} content-type 为 application/json 时返回 true
 */
export function isJsonErrorBlob(blob) {
  return Boolean(blob && typeof blob.type === 'string' && blob.type.includes('application/json'))
}

/**
 * 从 JSON 错误体 blob 中读取错误消息，解析失败时兜底。
 *
 * @param {Blob} blob - JSON 错误响应体
 * @returns {Promise<string>} 错误消息文本
 */
export async function readJsonErrorMessage(blob) {
  try {
    const text = typeof blob.text === 'function'
      ? await blob.text()
      : await new Promise((resolve, reject) => {
        // 兜底：部分环境（如 happy-dom）Blob.text() 不可用，改走 FileReader
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result)
        reader.onerror = reject
        reader.readAsText(blob)
      })
    const body = JSON.parse(text)
    return body?.msg || body?.message || '服务端返回错误'
  } catch {
    return '服务端返回错误'
  }
}
