// Input: tasks/alerts APIs, assignee ref, message dependency
// Output: workbench todo state/actions composable for Dashboard Workbench
// Pos: src/views/Dashboard/ - dashboard feature composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { tasksApi as defaultTasksApi } from '@/api/modules/dashboard.js'
import { alertHistoryApi as defaultAlertHistoryApi } from '@/api/modules/alerts.js'
import { normalizeAlertForTodo } from '@/views/Dashboard/workbench-utils.js'
import { mergePriorityTodos, normalizeApiTodo } from '@/views/Dashboard/workbench-core.js'

const noopMessage = {
  success: () => {},
  error: () => {},
}

export function useWorkbenchTodos({
  tasksApi = defaultTasksApi,
  alertHistoryApi = defaultAlertHistoryApi,
  assigneeIdRef,
  canLoadAlertTodosRef = ref(true),
  message = noopMessage,
  normalizeTaskTodo = normalizeApiTodo,
  normalizeAlertTodo = normalizeAlertForTodo,
} = {}) {
  const apiTodoItems = ref([])
  const alertTodoItems = ref([])
  const todosError = ref('')

  const priorityTodos = computed(() => mergePriorityTodos(alertTodoItems.value, apiTodoItems.value, 8))
  const pendingCount = computed(() => priorityTodos.value.filter((todo) => !todo.done).length)
  const completedTodoCount = computed(() => priorityTodos.value.filter((todo) => todo.done).length)

  const bidReviewCount = computed(() => priorityTodos.value.filter((todo) => todo.sourceType === 'bid_review').length)

  const loadPriorityTodos = async () => {
    const assigneeId = assigneeIdRef?.value
    if (!assigneeId) {
      apiTodoItems.value = []
      return []
    }

    try {
      const result = await tasksApi.getMine(assigneeId)
      apiTodoItems.value = Array.isArray(result?.data)
        ? result.data.map(normalizeTaskTodo)
        : []
    } catch {
      apiTodoItems.value = []
      todosError.value = '任务待办加载失败，请稍后重试'
    }
    return apiTodoItems.value
  }

  const loadAlertTodos = async () => {
    if (!canLoadAlertTodosRef.value) {
      alertTodoItems.value = []
      return []
    }

    try {
      const result = await alertHistoryApi.getUnresolved({ page: 0, size: 8 })
      alertTodoItems.value = Array.isArray(result?.data)
        ? result.data.map(normalizeAlertTodo)
        : []
    } catch {
      alertTodoItems.value = []
      todosError.value = '预警待办加载失败，请稍后重试'
    }
    return alertTodoItems.value
  }

  const loadTodos = async () => {
    todosError.value = ''
    const results = await Promise.allSettled([loadAlertTodos(), loadPriorityTodos()])
    return results.map((result) => result.value || [])
  }

  const completeWarningTodo = async (task) => {
    if (!canLoadAlertTodosRef.value) return

    try {
      const result = await alertHistoryApi.acknowledge(task.sourceId)
      if (!result?.success) {
        throw new Error(result?.msg || '确认告警失败')
      }
      await loadAlertTodos()
      message.success?.(`完成任务: ${task.title}`)
    } catch (error) {
      message.error?.(error?.message || '确认告警失败')
    }
  }

  const completeApiTodo = async (task) => {
    try {
      const result = await tasksApi.complete(task.id)
      if (!result?.success) {
        throw new Error(result?.msg || '更新待办状态失败')
      }
      task.done = true
      task.rawStatus = result?.data?.status || 'COMPLETED'
      message.success?.(`完成任务: ${task.title}`)
    } catch (error) {
      message.error?.(error?.message || '更新待办状态失败')
    }
  }

  const handleTaskComplete = async (task) => {
    if (!task || task.done) return

    if (task.type === 'warning') {
      await completeWarningTodo(task)
      return
    }

    await completeApiTodo(task)
  }

  return {
    apiTodoItems,
    alertTodoItems,
    priorityTodos,
    pendingCount,
    completedTodoCount,
    bidReviewCount,
    todosError,
    loadPriorityTodos,
    loadAlertTodos,
    loadTodos,
    handleTaskComplete,
  }
}
