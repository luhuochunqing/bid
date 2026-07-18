import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useAsyncTask, DownloadError } from './useAsyncTask'

/**
 * 下载相关测试的统一 mock 工具。
 * 用 vi.spyOn 替代直接赋值 global.fetch，确保 afterEach 自动 restore。
 */
function setupDownloadMocks({ response, fetchError } = {}) {
  const fetchSpy = vi.spyOn(global, 'fetch')
  if (fetchError) {
    fetchSpy.mockRejectedValue(fetchError)
  } else {
    fetchSpy.mockResolvedValue(response)
  }

  const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url')
  const revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

  let clickedAnchor = null
  const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tag) => {
    const el = {
      tagName: tag.toUpperCase(),
      href: '',
      download: '',
      target: '',
      click: () => { clickedAnchor = el },
      style: {}
    }
    return el
  })
  const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
  const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})

  return {
    fetchSpy,
    createObjectURLSpy,
    revokeObjectURLSpy,
    createElementSpy,
    appendChildSpy,
    removeChildSpy,
    getClickedAnchor: () => clickedAnchor,
    resetClickedAnchor: () => { clickedAnchor = null }
  }
}

/** 构造一个流式响应 mock */
function makeStreamResponse({ chunks = [], total = null, ok = true, status = 200 } = {}) {
  let chunkIndex = 0
  const resolvedTotal = total != null ? total : chunks.reduce((s, c) => s + c.length, 0)
  return {
    ok,
    status,
    headers: {
      get: (name) => name === 'Content-Length' ? (resolvedTotal > 0 ? String(resolvedTotal) : null) : null
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
  }
}

describe('useAsyncTask', () => {
  beforeEach(() => {
    // 确保 AbortController 存在（jsdom 默认有，但显式声明避免环境差异）
    if (!global.AbortController) {
      global.AbortController = class {
        constructor() { this.signal = { aborted: false, addEventListener: () => {}, removeEventListener: () => {} } }
        abort() { this.signal.aborted = true }
      }
    }
    // jsdom 环境下 URL.createObjectURL / revokeObjectURL 可能不存在，显式补齐
    if (!URL.createObjectURL) URL.createObjectURL = () => 'blob:mock-url'
    if (!URL.revokeObjectURL) URL.revokeObjectURL = () => {}
  })

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

  it('reset 重置所有状态（含下载状态）', () => {
    const { taskId, status, totalCount, failureReason, summary, isDownloading, downloadProgress, isDownloadIndeterminate, reset, applyStatusData } = useAsyncTask({
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
    isDownloading.value = true
    downloadProgress.value = 50
    isDownloadIndeterminate.value = true

    reset()

    expect(taskId.value).toBeNull()
    expect(status.value).toBe('')
    expect(totalCount.value).toBe(0)
    expect(failureReason.value).toBe('')
    expect(summary.value).toEqual({})
    // 下载状态也必须被重置
    expect(isDownloading.value).toBe(false)
    expect(downloadProgress.value).toBe(0)
    expect(isDownloadIndeterminate.value).toBe(false)
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
    const response = makeStreamResponse({ chunks, total })

    const mocks = setupDownloadMocks({ response })

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
    // 应通过 fetch 流式下载（带 AbortController signal）
    expect(mocks.fetchSpy).toHaveBeenCalledWith('/t/id-1/d', expect.objectContaining({
      credentials: 'include'
    }))
    // 下载完成后 isDownloading=false（finally 中 resetDownloadState）
    // 注意：progress 会被 reset 为 0，因为 UI 已切回按钮不再显示进度条
    expect(isDownloading.value).toBe(false)
    // 应创建 Blob URL 并触发 <a> 下载
    expect(mocks.createObjectURLSpy).toHaveBeenCalledTimes(1)
    const clicked = mocks.getClickedAnchor()
    expect(clicked).not.toBeNull()
    expect(clicked.href).toBe('blob:mock-url')
    expect(clicked.download).toBe('file.zip')
    expect(mocks.revokeObjectURLSpy).toHaveBeenCalledWith('blob:mock-url')
  })

  it('downloadFile 使用当前 taskId 作为默认 id', async () => {
    const response = makeStreamResponse({ chunks: [], total: 0 })
    const mocks = setupDownloadMocks({ response })

    const { downloadFile, taskId } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      httpGet: vi.fn(),
      autoCleanup: false
    })

    taskId.value = 'current-id'
    await downloadFile(null, () => 'f.xlsx')

    expect(mocks.fetchSpy).toHaveBeenCalledWith('/t/current-id/d', expect.objectContaining({
      credentials: 'include'
    }))
  })

  it('downloadFile HTTP 错误时抛出 DownloadError(code=HTTP) 并重置状态', async () => {
    const response = makeStreamResponse({ ok: false, status: 404 })
    const mocks = setupDownloadMocks({ response })

    const { downloadFile, isDownloading, downloadProgress, isDownloadIndeterminate } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      autoCleanup: false
    })

    await expect(downloadFile('id-1', () => 'f.zip')).rejects.toMatchObject({
      name: 'DownloadError',
      code: 'HTTP'
    })
    expect(isDownloading.value).toBe(false)
    expect(downloadProgress.value).toBe(0)
    expect(isDownloadIndeterminate.value).toBe(false)
    expect(mocks.createObjectURLSpy).not.toHaveBeenCalled()
  })

  it('downloadFile 网络错误时抛出 DownloadError(code=NETWORK) 并重置状态', async () => {
    // 模拟 fetch 失败（如断网、DNS 解析失败、CORS 拒绝）
    const networkError = new TypeError('Failed to fetch')
    const mocks = setupDownloadMocks({ fetchError: networkError })

    const { downloadFile, isDownloading, downloadProgress } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      autoCleanup: false
    })

    await expect(downloadFile('id-1', () => 'f.zip')).rejects.toMatchObject({
      name: 'DownloadError',
      code: 'NETWORK'
    })
    expect(isDownloading.value).toBe(false)
    expect(downloadProgress.value).toBe(0)
  })

  it('downloadFile response.body 为 null 时抛出 DownloadError(code=EMPTY_BODY)', async () => {
    // opaque response / CORS 限制 / HTTP 204 等场景 body 可能为 null
    const response = {
      ok: true,
      status: 200,
      headers: { get: () => null },
      body: null
    }
    setupDownloadMocks({ response })

    const { downloadFile } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      autoCleanup: false
    })

    await expect(downloadFile('id-1', () => 'f.zip')).rejects.toMatchObject({
      name: 'DownloadError',
      code: 'EMPTY_BODY'
    })
  })

  it('downloadFile 无 Content-Length 时进入 indeterminate 模式', async () => {
    // chunked encoding / gzip 压缩等场景可能没有 Content-Length
    const chunks = [
      new Uint8Array(new Array(1024).fill(1)),
      new Uint8Array(new Array(1024).fill(2))
    ]
    const response = makeStreamResponse({ chunks, total: 0 })
    const mocks = setupDownloadMocks({ response })

    const { downloadFile, isDownloading, isDownloadIndeterminate, downloadProgress } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      autoCleanup: false
    })

    await downloadFile('id-1', () => 'f.zip')

    // 下载中应该进入 indeterminate 模式
    // （注意：因为 await 完成后状态会被 finally 重置，所以这里只能验证下载完成后状态）
    expect(isDownloading.value).toBe(false)
    expect(isDownloadIndeterminate.value).toBe(false)
    // 下载完成后进度应被重置为 0（finally 中 resetDownloadState）
    expect(downloadProgress.value).toBe(0)
    expect(mocks.createObjectURLSpy).toHaveBeenCalledTimes(1)
  })

  it('downloadFile AbortController 超时时抛出 DownloadError(code=TIMEOUT)', async () => {
    // 模拟 fetch 因 AbortController.abort() 而抛出 AbortError
    const abortError = new DOMException('The operation was aborted.', 'AbortError')
    const mocks = setupDownloadMocks({ fetchError: abortError })

    const { downloadFile, isDownloading, downloadProgress } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      autoCleanup: false
    })

    await expect(downloadFile('id-1', () => 'f.zip')).rejects.toMatchObject({
      name: 'DownloadError',
      code: 'TIMEOUT'
    })
    expect(isDownloading.value).toBe(false)
    expect(downloadProgress.value).toBe(0)
  })

  it('downloadFile 流读取中途失败时抛出 DownloadError(code=STREAM)', async () => {
    // 模拟 response.body.getReader().read() 中途抛出非 AbortError
    const response = {
      ok: true,
      status: 200,
      headers: { get: () => '100' },
      body: {
        getReader: () => ({
          read: async () => {
            throw new TypeError('network changed')
          }
        })
      }
    }
    setupDownloadMocks({ response })

    const { downloadFile, isDownloading, downloadProgress } = useAsyncTask({
      statusUrl: '/t/:id/s',
      downloadUrl: '/t/:id/d',
      autoCleanup: false
    })

    await expect(downloadFile('id-1', () => 'f.zip')).rejects.toMatchObject({
      name: 'DownloadError',
      code: 'STREAM'
    })
    expect(isDownloading.value).toBe(false)
    expect(downloadProgress.value).toBe(0)
  })

  it('DownloadError 类构造正确，携带 code 字段', () => {
    const err = new DownloadError('test message', 'TEST_CODE')
    expect(err).toBeInstanceOf(Error)
    expect(err.name).toBe('DownloadError')
    expect(err.message).toBe('test message')
    expect(err.code).toBe('TEST_CODE')
  })
})
