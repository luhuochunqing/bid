import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useWorkbenchTodos } from '@/views/Dashboard/useWorkbenchTodos.js'

describe('useWorkbenchTodos', () => {
  const normalizeTaskTodo = (task) => ({
    id: task.id,
    title: task.title,
    priority: 'high',
    deadline: '今天',
    done: task.status === 'COMPLETED',
    type: 'task',
    sourceType: 'task',
    rawStatus: task.status,
  })

  const normalizeAlertTodo = (alert) => ({
    id: `alert-${alert.id}`,
    sourceId: alert.id,
    title: alert.message,
    priority: 'urgent',
    deadline: '今天',
    done: false,
    type: 'warning',
    sourceType: 'alert',
  })

  it('loads API tasks and unresolved alert todos', async () => {
    const tasksApi = { getMine: vi.fn().mockResolvedValue({ data: [{ id: 1, title: '补材料', status: 'PENDING' }] }) }
    const alertHistoryApi = { getUnresolved: vi.fn().mockResolvedValue({ data: [{ id: 9, message: '保证金到期' }] }) }
    const todos = useWorkbenchTodos({
      tasksApi,
      alertHistoryApi,
      assigneeIdRef: ref(7),
      normalizeTaskTodo,
      normalizeAlertTodo,
    })

    await todos.loadTodos()

    expect(tasksApi.getMine).toHaveBeenCalledWith(7)
    expect(alertHistoryApi.getUnresolved).toHaveBeenCalledWith({ page: 0, size: 8 })
    expect(todos.priorityTodos.value.map((item) => item.title)).toEqual(['保证金到期', '补材料'])
    expect(todos.pendingCount.value).toBe(2)
  })

  it('clears task todos when assignee is missing', async () => {
    const tasksApi = { getMine: vi.fn() }
    const todos = useWorkbenchTodos({ tasksApi, assigneeIdRef: ref(null), normalizeTaskTodo })

    const result = await todos.loadPriorityTodos()

    expect(result).toEqual([])
    expect(tasksApi.getMine).not.toHaveBeenCalled()
  })

  it('skips alert todos when current role cannot read alert history', async () => {
    const alertHistoryApi = { getUnresolved: vi.fn() }
    const todos = useWorkbenchTodos({
      alertHistoryApi,
      canLoadAlertTodosRef: ref(false),
      normalizeAlertTodo,
    })

    const result = await todos.loadAlertTodos()

    expect(result).toEqual([])
    expect(todos.alertTodoItems.value).toEqual([])
    expect(alertHistoryApi.getUnresolved).not.toHaveBeenCalled()
  })

  it('falls back to empty arrays when load APIs reject', async () => {
    const todos = useWorkbenchTodos({
      tasksApi: { getMine: vi.fn().mockRejectedValue(new Error('tasks down')) },
      alertHistoryApi: { getUnresolved: vi.fn().mockRejectedValue(new Error('alerts down')) },
      assigneeIdRef: ref(1),
      normalizeTaskTodo,
      normalizeAlertTodo,
    })

    await todos.loadTodos()

    expect(todos.apiTodoItems.value).toEqual([])
    expect(todos.alertTodoItems.value).toEqual([])
  })

  it('acknowledges warning todos then reloads alerts', async () => {
    const message = { success: vi.fn(), error: vi.fn() }
    const alertHistoryApi = {
      getUnresolved: vi.fn().mockResolvedValue({ data: [] }),
      acknowledge: vi.fn().mockResolvedValue({ success: true }),
    }
    const todos = useWorkbenchTodos({
      tasksApi: { complete: vi.fn() },
      alertHistoryApi,
      message,
      normalizeAlertTodo,
    })

    await todos.handleTaskComplete({ type: 'warning', sourceId: 3, title: '风险告警', done: false })

    expect(alertHistoryApi.acknowledge).toHaveBeenCalledWith(3)
    expect(alertHistoryApi.getUnresolved).toHaveBeenCalledWith({ page: 0, size: 8 })
    expect(message.success).toHaveBeenCalledWith('完成任务: 风险告警')
  })

  it('marks API task todos complete after successful status update', async () => {
    const message = { success: vi.fn(), error: vi.fn() }
    const task = { id: 5, type: 'task', title: '提交方案', done: false }
    const tasksApi = { complete: vi.fn().mockResolvedValue({ success: true, data: { status: 'COMPLETED' } }) }
    const todos = useWorkbenchTodos({ tasksApi, message })

    await todos.handleTaskComplete(task)

    expect(tasksApi.complete).toHaveBeenCalledWith(5)
    expect(task.done).toBe(true)
    expect(task.rawStatus).toBe('COMPLETED')
    expect(message.success).toHaveBeenCalledWith('完成任务: 提交方案')
  })
})

  it('sets badge and sourceType for bid_review tasks', async () => {
    const tasksApi = { getMine: vi.fn().mockResolvedValue({ data: [{ id: 3, title: '标书评审-智慧交通', status: 'PENDING', type: 'bid_review' }] }) }
    const todos = useWorkbenchTodos({
      tasksApi,
      alertHistoryApi: { getUnresolved: vi.fn() },
      assigneeIdRef: ref(7),
      canLoadAlertTodosRef: ref(false),
    })

    await todos.loadTodos()

    const bidReviewItems = todos.priorityTodos.value.filter(t => t.sourceType === 'bid_review')
    expect(bidReviewItems).toHaveLength(1)
    expect(bidReviewItems[0].badge).toBe('标书评审')
    expect(bidReviewItems[0].type).toBe('bid_review')
    expect(todos.bidReviewCount.value).toBe(1)
  })

  it('bidReviewCount returns 0 when no bid_review todos', async () => {
    const tasksApi = { getMine: vi.fn().mockResolvedValue({ data: [{ id: 1, title: '补材料', status: 'PENDING' }] }) }
    const todos = useWorkbenchTodos({
      tasksApi,
      alertHistoryApi: { getUnresolved: vi.fn() },
      assigneeIdRef: ref(7),
      canLoadAlertTodosRef: ref(false),
    })

    await todos.loadTodos()

    expect(todos.bidReviewCount.value).toBe(0)
  })
