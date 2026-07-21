import { ref, computed, onUnmounted, getCurrentInstance } from 'vue'
import http from '@/api/client'
import { API_BASE_URL } from '@/api/config'

const TERMINAL_STATUSES = ['COMPLETED', 'FAILED']

// 下载超时：5 分钟无数据则中断（防止服务器挂起导致前端永远 hang）
const DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000

/**
 * 下载错误类型 — 区分网络错误 / HTTP 错误 / 用户取消 / 流读取错误，
 * 便于 UI 层根据 code 显示不同提示。
 */
export class DownloadError extends Error {
  constructor(message, code) {
    super(message)
    this.name = 'DownloadError'
    this.code = code
  }
}

function resolveUrl(urlTemplate, id) {
  if (typeof urlTemplate === 'function') return urlTemplate(id)
  return urlTemplate.replace(':id', id)
}

export function useAsyncTask(options = {}) {
  const {
    statusUrl,
    downloadUrl = null,
    submitFn = null,
    pollInterval = 2000,
    httpGet = http.get,
    autoCleanup = true,
    onStatusUpdate = null,
    onCompleted = null,
    onFailed = null
  } = options

  const taskId = ref(null)
  const status = ref('')
  const totalCount = ref(0)
  const failureReason = ref('')
  const summary = ref({})
  // 下载状态：独立于导出任务状态，仅 downloadFile 期间使用
  const isDownloading = ref(false)
  // 进度 0-100；-1 表示 indeterminate（无 Content-Length，无法计算进度）
  const downloadProgress = ref(0)
  const isDownloadIndeterminate = ref(false)
  let pollTimer = null

  const isRunning = computed(() => !!taskId.value && !TERMINAL_STATUSES.includes(status.value))
  const isCompleted = computed(() => status.value === 'COMPLETED')
  const isFailed = computed(() => status.value === 'FAILED')

  const buildStatusUrl = (id) => resolveUrl(statusUrl, id)
  const buildDownloadUrl = (id) => downloadUrl ? resolveUrl(downloadUrl, id) : ''

  function applyStatusData(data) {
    if (data.status != null) status.value = data.status
    if (data.totalCount != null) totalCount.value = data.totalCount
    if (data.failureReason) failureReason.value = data.failureReason
    if (data.resultSummary) summary.value = data.resultSummary
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function startPolling() {
    stopPolling()
    pollTimer = setInterval(async () => {
      if (!taskId.value) return
      try {
        const { data } = await httpGet(buildStatusUrl(taskId.value))
        applyStatusData(data)
        if (typeof onStatusUpdate === 'function') onStatusUpdate(data)
        if (TERMINAL_STATUSES.includes(data.status)) {
          stopPolling()
          if (data.status === 'COMPLETED' && typeof onCompleted === 'function') onCompleted(data)
          if (data.status === 'FAILED' && typeof onFailed === 'function') onFailed(data)
        }
      } catch {
        stopPolling()
      }
    }, pollInterval)
  }

  function resetDownloadState() {
    isDownloading.value = false
    downloadProgress.value = 0
    isDownloadIndeterminate.value = false
  }

  function reset() {
    stopPolling()
    taskId.value = null
    status.value = ''
    totalCount.value = 0
    failureReason.value = ''
    summary.value = {}
    resetDownloadState()
  }

  async function startTask(...args) {
    if (!submitFn) return
    reset()
    const result = await submitFn(...args)
    if (result?.taskId) {
      taskId.value = result.taskId
      status.value = 'PENDING'
      startPolling()
    }
    return result
  }

  function retry(...args) {
    reset()
    return startTask(...args)
  }

  async function downloadFile(id, filenameBuilder) {
    const url = buildDownloadUrl(id || taskId.value)
    if (!url) return
    const filename = typeof filenameBuilder === 'function'
      ? filenameBuilder(summary.value)
      : (filenameBuilder || `download_${Date.now()}`)
    // 大文件导出（ZIP 可达数百 MB）：用 fetch + ReadableStream 流式下载。
    // - 绕过 axios 30s 超时（fetch 默认无超时）
    // - AbortController 5 分钟超时，防止服务器挂起
    // - 实时更新下载进度（Content-Length + 累计已读字节）
    // - 流式消费响应体，避免一次性全量加载
    // - 必须拼接 API_BASE_URL：dev 模式下 baseURL 是 http://127.0.0.1:18089，
    //   若用相对路径 fetch，请求会打到 vite dev server（无 proxy）→ 返回 index.html
    //   → 用户下载到 405 字节 HTML 但文件名是 .zip → 解压报"格式不正确"
    const fullUrl = `${API_BASE_URL}${url}`
    isDownloading.value = true
    downloadProgress.value = 0
    isDownloadIndeterminate.value = false
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), DOWNLOAD_TIMEOUT_MS)
    try {
      let response
      try {
        response = await fetch(fullUrl, {
          credentials: 'include',
          signal: controller.signal
        })
      } catch (e) {
        if (e.name === 'AbortError') {
          throw new DownloadError('下载超时，请重试', 'TIMEOUT')
        }
        throw new DownloadError('网络错误，请检查网络连接', 'NETWORK')
      }
      if (!response.ok) {
        throw new DownloadError(`下载失败: HTTP ${response.status}`, 'HTTP')
      }
      if (!response.body) {
        // opaque response / CORS 限制 / 204 等场景，body 可能为 null
        throw new DownloadError('下载失败: 响应体为空', 'EMPTY_BODY')
      }
      const total = parseInt(response.headers.get('Content-Length') || '0', 10)
      if (total <= 0) {
        // 无 Content-Length（chunked encoding / gzip 等），进入 indeterminate 模式
        isDownloadIndeterminate.value = true
      }
      let loaded = 0
      const chunks = []
      const reader = response.body.getReader()
      while (true) {
        let result
        try {
          result = await reader.read()
        } catch (e) {
          if (e.name === 'AbortError') {
            throw new DownloadError('下载超时，请重试', 'TIMEOUT')
          }
          throw new DownloadError(`流读取失败: ${e.message}`, 'STREAM')
        }
        const { done, value } = result
        if (done) break
        chunks.push(value)
        loaded += value.length
        if (total > 0) {
          // 卡在 99% 直到全部读完，避免提前显示 100% 但还没拼完 Blob
          downloadProgress.value = Math.min(99, Math.round((loaded / total) * 100))
        } else {
          // indeterminate 模式：进度在 10-90 间循环跳动，让用户知道仍在下载
          const phase = Math.floor((loaded / 1024) % 9) * 10 + 10
          downloadProgress.value = phase
        }
      }
      downloadProgress.value = 100
      isDownloadIndeterminate.value = false
      const blob = new Blob(chunks)
      const blobUrl = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = blobUrl
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(blobUrl)
    } finally {
      clearTimeout(timeoutId)
      resetDownloadState()
    }
  }

  if (autoCleanup && getCurrentInstance()) {
    onUnmounted(stopPolling)
  }

  return {
    taskId,
    status,
    totalCount,
    failureReason,
    summary,
    isRunning,
    isCompleted,
    isFailed,
    isDownloading,
    downloadProgress,
    isDownloadIndeterminate,
    buildStatusUrl,
    buildDownloadUrl,
    startTask,
    stopPolling,
    reset,
    retry,
    downloadFile,
    applyStatusData
  }
}

export default useAsyncTask
