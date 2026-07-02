import { ref, computed } from 'vue'

export function usePersonnelBatchTask({ startApi, pollApi, pollInterval = 2000 }) {
  const taskId = ref(null)
  const status = ref('')
  const progressPercent = ref(0)
  const progressText = ref('')
  const errorMessage = ref('')
  const totalCount = ref(0)
  const successCount = ref(0)
  const failCount = ref(0)
  const active = ref(false)
  let pollTimer = null

  const isProcessing = computed(() => status.value === 'PROCESSING')
  const isCompleted = computed(() => status.value === 'COMPLETED')
  const isFailed = computed(() => status.value === 'FAILED')

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

          if (info.status === 'COMPLETED') {
            clearInterval(pollTimer)
            pollTimer = null
            status.value = 'COMPLETED'
            totalCount.value = info.totalCount ?? 0
            successCount.value = info.successCount ?? 0
            failCount.value = info.failureCount ?? 0
          } else if (info.status === 'FAILED') {
            clearInterval(pollTimer)
            pollTimer = null
            status.value = 'FAILED'
            errorMessage.value = info.message || '任务失败'
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
    active,
    isProcessing,
    isCompleted,
    isFailed,
    startTask,
    reset
  }
}
