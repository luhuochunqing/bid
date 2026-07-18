import { describe, it, expect, vi, afterEach } from 'vitest'
import { useAsyncTask } from './useAsyncTask'

describe('useAsyncTask', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('初始状态为空', () => {
    const { taskId, status, totalCount, failureReason, summary, isRunning, isCompleted, isFailed } = useAsyncTask({
      statusUrl: (id) => `/api/tasks/${id}/status`,
      autoCleanup: false
    })

    expect(taskId.value).toBeNull()
    expect(status.value).toBe('')
    expect(totalCount.value).toBe(0)
    expect(failureReason.value).toBe('')
    expect(summary.value).toEqual({})
    expect(isRunning.value).toBe(false)
    expect(isCompleted.value).toBe(false)
    expect(isFailed.value).toBe(false)
  })

  it('startTask 调用 submitFn 并设置 PENDING 状态', async () => {
    const mockSubmit = vi.fn().mockResolvedValue({ taskId: 'task-123' })

    const { taskId, status, isRunning, startTask, stopPolling } = useAsyncTask({
      submitFn: mockSubmit,
      statusUrl: (id) => `/api/tasks/${id}/status`,
      httpGet: vi.fn(),
      pollInterval: 50,
      autoCleanup: false
    })

    await startTask()

    expect(mockSubmit).toHaveBeenCalledTimes(1)
    expect(taskId.value).toBe('task-123')
    expect(status.value).toBe('PENDING')
    expect(isRunning.value).toBe(true)

    stopPolling()
  })

  it('applyStatusData 手动应用状态数据', () => {
    const { status, totalCount, failureReason, summary, applyStatusData } = useAsyncTask({
      statusUrl: '/api/tasks/:id/status',
      autoCleanup: false
    })

    applyStatusData({
      status: 'COMPLETED',
      totalCount: 42,
      resultSummary: { foo: 'bar' }
    })

    expect(status.value).toBe('COMPLETED')
    expect(totalCount.value).toBe(42)
    expect(summary.value.foo).toBe('bar')
    expect(failureReason.value).toBe('')
  })

  it('applyStatusData 处理失败状态', () => {
    const { status, failureReason, isFailed, isRunning, isCompleted, applyStatusData } = useAsyncTask({
      statusUrl: '/api/tasks/:id/status',
      autoCleanup: false
    })

    applyStatusData({
      status: 'FAILED',
      failureReason: 'oops'
    })

    expect(status.value).toBe('FAILED')
    expect(failureReason.value).toBe('oops')
    expect(isFailed.value).toBe(true)
    expect(isRunning.value).toBe(false)
    expect(isCompleted.value).toBe(false)
  })

  it('reset 重置所有状态', () => {
    const { taskId, status, totalCount, failureReason, summary, reset, applyStatusData } = useAsyncTask({
      statusUrl: '/api/tasks/:id/status',
      autoCleanup: false
    })

    applyStatusData({
      status: 'COMPLETED',
      totalCount: 10,
      failureReason: '',
      resultSummary: { a: 1 }
    })
    taskId.value = 'abc'

    reset()

    expect(taskId.value).toBeNull()
    expect(status.value).toBe('')
    expect(totalCount.value).toBe(0)
    expect(failureReason.value).toBe('')
    expect(summary.value).toEqual({})
  })

  it('statusUrl 支持字符串模板', () => {
    const { buildStatusUrl } = useAsyncTask({
      statusUrl: '/api/tasks/:id/status',
      autoCleanup: false
    })

    expect(buildStatusUrl('abc')).toBe('/api/tasks/abc/status')
  })

  it('statusUrl 支持函数', () => {
    const { buildStatusUrl } = useAsyncTask({
      statusUrl: (id) => `/custom/${id}/state`,
      autoCleanup: false
    })

    expect(buildStatusUrl('xyz')).toBe('/custom/xyz/state')
  })

  it('downloadUrl 支持字符串模板', () => {
    const { buildDownloadUrl } = useAsyncTask({
      statusUrl: '/api/tasks/:id/status',
      downloadUrl: '/api/tasks/:id/download',
      autoCleanup: false
    })

    expect(buildDownloadUrl('xyz')).toBe('/api/tasks/xyz/download')
  })

  it('downloadUrl 支持函数', () => {
    const { buildDownloadUrl } = useAsyncTask({
      statusUrl: '/api/tasks/:id/status',
      downloadUrl: (id) => `/custom/${id}/file`,
      autoCleanup: false
    })

    expect(buildDownloadUrl('xyz')).toBe('/custom/xyz/file')
  })

  it('isRunning / isCompleted / isFailed 计算属性正确', () => {
    const { taskId, status, isRunning, isCompleted, isFailed } = useAsyncTask({
      statusUrl: '/x',
      autoCleanup: false
    })

    status.value = ''
    expect(isRunning.value).toBe(false)
    expect(isCompleted.value).toBe(false)
    expect(isFailed.value).toBe(false)

    taskId.value = 'abc'
    status.value = 'PENDING'
    expect(isRunning.value).toBe(true)
    expect(isCompleted.value).toBe(false)
    expect(isFailed.value).toBe(false)

    status.value = 'PROCESSING'
    expect(isRunning.value).toBe(true)

    status.value = 'COMPLETED'
    expect(isRunning.value).toBe(false)
    expect(isCompleted.value).toBe(true)
    expect(isFailed.value).toBe(false)

    status.value = 'FAILED'
    expect(isRunning.value).toBe(false)
    expect(isCompleted.value).toBe(false)
    expect(isFailed.value).toBe(true)
  })

  it('downloadFile 通过 <a> 原生导航触发下载（大文件不走 axios）', async () => {
    const mockHttpGet = vi.fn()

    let clickedAnchor = null
    const origCreateElement = document.createElement.bind(document)
    document.createElement = (tag) => {
      const el = origCreateElement(tag)
      if (tag === 'a') {
        Object.defineProperty(el, 'click', { value: () => { clickedAnchor = el } })
      }
      return el
    }
    const origAppendChild = document.body.appendChild.bind(document.body)
    document.body.appendChild = (el) => origAppendChild(el)
    const origRemoveChild = document.body.removeChild.bind(document.body)
    document.body.removeChild = (el) => origRemoveChild(el)

    const { downloadFile } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      httpGet: mockHttpGet,
      autoCleanup: false
    })

    await downloadFile('id-1', () => 'file.zip')

    // 不应调用 axios（避免大文件超时和内存双倍占用）
    expect(mockHttpGet).not.toHaveBeenCalled()
    // 应通过 <a> 原生导航触发下载
    expect(clickedAnchor).not.toBeNull()
    expect(clickedAnchor.href).toContain('/t/id-1/d')
    expect(clickedAnchor.download).toBe('file.zip')
    expect(clickedAnchor.target).toBe('_blank')

    document.createElement = origCreateElement
    document.body.appendChild = origAppendChild
    document.body.removeChild = origRemoveChild
  })

  it('downloadFile 使用当前 taskId 作为默认 id', async () => {
    const mockHttpGet = vi.fn()

    let clickedAnchor = null
    const origCreateElement = document.createElement.bind(document)
    document.createElement = (tag) => {
      const el = origCreateElement(tag)
      if (tag === 'a') {
        Object.defineProperty(el, 'click', { value: () => { clickedAnchor = el } })
      }
      return el
    }
    const origAppendChild = document.body.appendChild.bind(document.body)
    document.body.appendChild = (el) => origAppendChild(el)
    const origRemoveChild = document.body.removeChild.bind(document.body)
    document.body.removeChild = (el) => origRemoveChild(el)

    const { downloadFile, taskId } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      httpGet: mockHttpGet,
      autoCleanup: false
    })

    taskId.value = 'current-id'
    await downloadFile(null, () => 'f.xlsx')

    expect(mockHttpGet).not.toHaveBeenCalled()
    expect(clickedAnchor).not.toBeNull()
    expect(clickedAnchor.href).toContain('/t/current-id/d')
    expect(clickedAnchor.download).toBe('f.xlsx')

    document.createElement = origCreateElement
    document.body.appendChild = origAppendChild
    document.body.removeChild = origRemoveChild
  })
})
