import { computed, onBeforeUnmount, onMounted, ref, unref } from 'vue'
import { casesApi } from '@/api/modules/knowledge.js'

const ACTIVE = new Set(['PENDING', 'PROCESSING'])

export function useCasePrecipitationTask(projectId) {
  const task = ref(null)
  const requesting = ref(false)
  let timer = null
  let disposed = false

  const precipitating = computed(() => requesting.value || ACTIVE.has(task.value?.status))
  const statusText = computed(() => {
    if (task.value?.status === 'PENDING') return '正在排队沉淀'
    if (task.value?.status === 'PROCESSING') return task.value.stage || '正在沉淀'
    if (task.value?.status === 'FAILED') return task.value.message || '沉淀失败，请重试'
    if (task.value?.status === 'COMPLETED') return `沉淀完成（${task.value.caseCount ?? 0} 条）`
    return ''
  })

  function clearPoll() {
    if (timer) window.clearTimeout(timer)
    timer = null
  }

  function schedulePoll() {
    clearPoll()
    if (disposed || !ACTIVE.has(task.value?.status) || document.hidden) return
    timer = window.setTimeout(refresh, 2000)
  }

  async function refresh() {
    try {
      const latest = await casesApi.getPrecipitationTask(unref(projectId))
      if (!disposed) task.value = latest
    } finally {
      schedulePoll()
    }
    return task.value
  }

  async function trigger() {
    requesting.value = true
    try {
      const response = await casesApi.precipitateCases(unref(projectId))
      const data = response?.data || response
      task.value = { ...(task.value || {}), taskId: data.taskId, status: data.status }
      schedulePoll()
      return data
    } finally {
      requesting.value = false
    }
  }

  function onVisibilityChange() {
    if (document.hidden) clearPoll()
    else refresh()
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', onVisibilityChange)
    refresh()
  })
  onBeforeUnmount(() => {
    disposed = true
    clearPoll()
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return { task, precipitating, statusText, refresh, trigger }
}
