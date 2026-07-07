import { ref, computed } from 'vue'

// CO-528: 按工号去重 errorDetails，合并错误原因
// 同一人员可能有多条错误（如姓名+性别都为空），UI 应展示为一行
function dedupeByEmployeeNumber(details) {
  if (!Array.isArray(details) || details.length === 0) return []
  const map = new Map()
  for (const e of details) {
    const key = e.employeeNumber || `row-${e.rowNumber}`
    if (!map.has(key)) {
      map.set(key, { ...e, errorMessage: [e.errorMessage || ''] })
    } else {
      map.get(key).errorMessage.push(e.errorMessage || '')
    }
  }
  return Array.from(map.values()).map(e => ({
    ...e,
    errorMessage: e.errorMessage.filter(Boolean).join('；')
  }))
}

export function usePersonnelBatchTask({ startApi, pollApi, pollInterval = 2000 }) {
  const taskId = ref(null)
  const status = ref('')
  const progressPercent = ref(0)
  const progressText = ref('')
  const errorMessage = ref('')
  const totalCount = ref(0)
  const successCount = ref(0)
  const failCount = ref(0)
  // CO-528: 任务终态时附带 errorDetails（失败人员列表），供弹窗内联展示
  const errorDetails = ref([])
  const active = ref(false)
  let pollTimer = null

  // CO-469 第二轮修复：状态机覆盖后端全部 5 种状态 + 2 种 fallback 状态
  // 后端 ImportTaskStatus 枚举：PENDING / PROCESSING / COMPLETED / PARTIAL_SUCCESS / FAILED
  // 后端 getProgress fallback：UNKNOWN（Redis 不可用）/ NOT_FOUND（任务不存在）
  // 原仅处理 COMPLETED/FAILED → 部分失败场景必死循环
  const isProcessing = computed(() => status.value === 'PROCESSING' || status.value === 'PENDING')
  const isCompleted = computed(() => status.value === 'COMPLETED' || status.value === 'PARTIAL_SUCCESS')
  const isFailed = computed(() => status.value === 'FAILED' || status.value === 'UNKNOWN' || status.value === 'NOT_FOUND')

  // CO-528: 按工号去重的失败人员列表（与后端 failureCount 计数维度一致）
  const uniqueErrorDetails = computed(() => dedupeByEmployeeNumber(errorDetails.value))

  function reset() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    taskId.value = null
    status.value = ''
    progressPercent.value = 0
    progressText.value = ''
    errorMessage.value = ''
    totalCount.value = 0
    successCount.value = 0
    failCount.value = 0
    errorDetails.value = []
    active.value = false
  }

  async function startTask(payload) {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
    active.value = true
    try {
      const res = await startApi(payload)
      const task = res?.data || {}
      taskId.value = task.taskId
      status.value = 'PROCESSING'
      progressPercent.value = 0
      progressText.value = '正在处理...'

      pollTimer = setInterval(async () => {
        try {
          const progress = await pollApi(taskId.value)
          const info = progress?.data || {}
          // CO-469: 字段名对齐后端 record（ImportProgressInfo / ExportProgress）
          // 后端返回 percent/message/failureCount，不是 progressPercent/progressText/failCount/errorMessage
          progressPercent.value = info.percent ?? 0
          progressText.value = info.message || '处理中...'

          // CO-469 第二轮：覆盖后端全部状态，避免 PARTIAL_SUCCESS/UNKNOWN/NOT_FOUND 死循环
          // - COMPLETED / PARTIAL_SUCCESS：停止轮询，标记完成态（UI 显示失败行数 + 下载错误报告）
          // - FAILED / UNKNOWN / NOT_FOUND：停止轮询，标记失败态
          // - PENDING / PROCESSING：继续轮询
          if (info.status === 'COMPLETED' || info.status === 'PARTIAL_SUCCESS') {
            clearInterval(pollTimer)
            pollTimer = null
            status.value = info.status
            totalCount.value = info.totalCount ?? 0
            successCount.value = info.successCount ?? 0
            failCount.value = info.failureCount ?? 0
            // CO-528: 读取后端附带的 errorDetails（失败人员列表）
            errorDetails.value = Array.isArray(info.errorDetails) ? info.errorDetails : []
          } else if (info.status === 'FAILED' || info.status === 'UNKNOWN' || info.status === 'NOT_FOUND') {
            clearInterval(pollTimer)
            pollTimer = null
            status.value = info.status
            errorMessage.value = info.message || '任务失败'
            // CO-528: 失败态也读取 errorDetails（如有），供弹窗展示失败人员列表
            errorDetails.value = Array.isArray(info.errorDetails) ? info.errorDetails : []
          }
        } catch {
          // poll failure does not interrupt
        }
      }, pollInterval)
    } catch (e) {
      status.value = 'FAILED'
      errorMessage.value = e?.response?.data?.message || '任务创建失败'
    } finally {
      active.value = false
    }
  }

  return {
    taskId,
    status,
    progressPercent,
    progressText,
    errorMessage,
    totalCount,
    successCount,
    failCount,
    errorDetails,
    uniqueErrorDetails,
    active,
    isProcessing,
    isCompleted,
    isFailed,
    startTask,
    reset
  }
}
