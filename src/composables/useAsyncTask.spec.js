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

  it('downloadFile 通过 fetch 流式下载并更新进度（绕过 axios 超时）', async () => {
    const mockHttpGet = vi.fn()
    const chunks = [
      new Uint8Array([1, 2, 3]),
      new Uint8Array([4, 5, 6, 7, 8, 9, 10])
    ]
    const total = chunks.reduce((s, c) => s + c.length, 0)
    let chunkIndex = 0
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      headers: {
        get: (name) => name === 'Content-Length' ? String(total) : null
      },
      body: {
        getReader: () => ({
          read: async () => {
            if (chunkIndex < chunks.length) {
              const value = chunks[chunkIndex++]
              return { done: false, value }
            }
            return { done: true, value: undefined }
          }
        })
      }
    })
    global.fetch = mockFetch

    const createdUrls = []
    const origCreateObjectURL = URL.createObjectURL
    URL.createObjectURL = (blob) => {
      const u = 'blob:mock-' + createdUrls.length
      createdUrls.push(u)
      return u
    }
    const origRevokeObjectURL = URL.revokeObjectURL
    URL.revokeObjectURL = vi.fn()

    let clickedAnchor = null
    const origCreateElement = document.createElement.bind(document)
    document.createElement = (tag) => {
      const el = origCreateElement(tag)
      if (tag === 'a') {
        Object.defineProperty(el, 'click', { value: () => { clickedAnchor = el } })
      }
      return el
    }

    const { downloadFile, isDownloading, downloadProgress } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      httpGet: mockHttpGet,
      autoCleanup: false
    })

    const promise = downloadFile('id-1', () => 'file.zip')
    // 进入下载状态时 isDownloading=true, progress=0
    expect(isDownloading.value).toBe(true)
    await promise

    // 不应调用 axios（避免大文件超时）
    expect(mockHttpGet).not.toHaveBeenCalled()
    // 应通过 fetch 流式下载
    expect(mockFetch).toHaveBeenCalledWith('/t/id-1/d', { credentials: 'include' })
    // 下载完成后 isDownloading=false, progress=100
    expect(isDownloading.value).toBe(false)
    expect(downloadProgress.value).toBe(100)
    // 应创建 Blob URL 并触发 <a> 下载
    expect(createdUrls).toHaveLength(1)
    expect(clickedAnchor).not.toBeNull()
    expect(clickedAnchor.href).toBe(createdUrls[0])
    expect(clickedAnchor.download).toBe('file.zip')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(createdUrls[0])

    global.fetch = undefined
    URL.createObjectURL = origCreateObjectURL
    URL.revokeObjectURL = origRevokeObjectURL
    document.createElement = origCreateElement
  })

  it('downloadFile 使用当前 taskId 作为默认 id', async () => {
    const mockHttpGet = vi.fn()
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      headers: { get: () => '0' },
      body: {
        getReader: () => ({
          read: async () => ({ done: true, value: undefined })
        })
      }
    })
    global.fetch = mockFetch
    const origCreateObjectURL = URL.createObjectURL
    URL.createObjectURL = () => 'blob:mock'
    const origRevokeObjectURL = URL.revokeObjectURL
    URL.revokeObjectURL = vi.fn()
    const origCreateElement = document.createElement.bind(document)
    document.createElement = (tag) => {
      const el = origCreateElement(tag)
      if (tag === 'a') {
        Object.defineProperty(el, 'click', { value: () => {} })
      }
      return el
    }

    const { downloadFile, taskId } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      httpGet: mockHttpGet,
      autoCleanup: false
    })

    taskId.value = 'current-id'
    await downloadFile(null, () => 'f.xlsx')

    expect(mockFetch).toHaveBeenCalledWith('/t/current-id/d', { credentials: 'include' })

    global.fetch = undefined
    URL.createObjectURL = origCreateObjectURL
    URL.revokeObjectURL = origRevokeObjectURL
    document.createElement = origCreateElement
  })

  it('downloadFile HTTP 错误时抛出异常并重置下载状态', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      headers: { get: () => null }
    })
    global.fetch = mockFetch
    const origCreateObjectURL = URL.createObjectURL
    URL.createObjectURL = vi.fn()
    const origRevokeObjectURL = URL.revokeObjectURL
    URL.revokeObjectURL = vi.fn()

    const { downloadFile, isDownloading, downloadProgress } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      autoCleanup: false
    })

    await expect(downloadFile('id-1', () => 'f.zip')).rejects.toThrow('下载失败: HTTP 404')
    expect(isDownloading.value).toBe(false)
    expect(downloadProgress.value).toBe(0)
    expect(URL.createObjectURL).not.toHaveBeenCalled()

    global.fetch = undefined
    URL.createObjectURL = origCreateObjectURL
    URL.revokeObjectURL = origRevokeObjectURL
  })
})
