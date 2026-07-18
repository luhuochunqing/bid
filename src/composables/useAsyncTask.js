import { ref, computed, onUnmounted, getCurrentInstance } from 'vue'
import http from '@/api/client'

const TERMINAL_STATUSES = ['COMPLETED', 'FAILED']

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
  const downloadProgress = ref(0)
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

  function reset() {
    stopPolling()
    taskId.value = null
    status.value = ''
    totalCount.value = 0
    failureReason.value = ''
    summary.value = {}
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
    // - 实时更新下载进度（Content-Length + 累计已读字节）
    // - 流式消费响应体，避免一次性全量加载
    isDownloading.value = true
    downloadProgress.value = 0
    try {
      const response = await fetch(url, { credentials: 'include' })
      if (!response.ok) {
        throw new Error(`下载失败: HTTP ${response.status}`)
      }
      const total = parseInt(response.headers.get('Content-Length') || '0', 10)
      let loaded = 0
      const chunks = []
      const reader = response.body.getReader()
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        chunks.push(value)
        loaded += value.length
        if (total > 0) {
          // 卡在 99% 直到全部读完，避免提前显示 100% 但还没拼完 Blob
          downloadProgress.value = Math.min(99, Math.round((loaded / total) * 100))
        }
      }
      downloadProgress.value = 100
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
      isDownloading.value = false
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
