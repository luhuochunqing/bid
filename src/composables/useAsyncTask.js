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
    // 大文件导出（ZIP 可达数百 MB）：用浏览器原生导航下载，避免 axios 超时和内存双倍占用。
    // axios blob 模式会把整个响应体加载到内存再触发下载，大文件场景不适用。
    const filename = typeof filenameBuilder === 'function'
      ? filenameBuilder(summary.value)
      : (filenameBuilder || `download_${Date.now()}`)
    // 通过带 download 属性的 <a> 触发浏览器原生下载，绕过 axios 超时
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.target = '_blank'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
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
