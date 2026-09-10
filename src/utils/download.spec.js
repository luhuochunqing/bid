import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest'
import { ElMessage } from 'element-plus'
import httpClient from '../api/client.js'
import { parseFilenameFromDisposition, triggerBlobDownload, triggerNativeDownload, downloadWithFilename, normalizeApiDownloadUrl, showApiDownloadError } from './download.js'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn()
  }
}))

vi.mock('../api/client.js', () => ({
  default: {
    get: vi.fn()
  }
}))

// Mock URL API for jsdom
beforeAll(() => {
  if (!URL.createObjectURL) {
    URL.createObjectURL = vi.fn(() => 'blob:mock-url')
  }
  if (!URL.revokeObjectURL) {
    URL.revokeObjectURL = vi.fn()
  }
})

describe('parseFilenameFromDisposition', () => {
  it('应解析 RFC 5987 格式的文件名', () => {
    const disposition = "attachment; filename*=UTF-8''%E6%8B%9B%E6%A0%87%E5%85%AC%E5%91%8A.pdf"
    expect(parseFilenameFromDisposition(disposition, 'fallback.pdf')).toBe('招标公告.pdf')
  })

  it('应解析普通格式的文件名（带引号）', () => {
    const disposition = 'attachment; filename="test file.pdf"'
    expect(parseFilenameFromDisposition(disposition, 'fallback.pdf')).toBe('test file.pdf')
  })

  it('应解析普通格式的文件名（不带引号）', () => {
    const disposition = 'attachment; filename=test.pdf'
    expect(parseFilenameFromDisposition(disposition, 'fallback.pdf')).toBe('test.pdf')
  })

  it('当 disposition 为空时应返回 fallback', () => {
    expect(parseFilenameFromDisposition('', 'fallback.pdf')).toBe('fallback.pdf')
    expect(parseFilenameFromDisposition(null, 'fallback.pdf')).toBe('fallback.pdf')
    expect(parseFilenameFromDisposition(undefined, 'fallback.pdf')).toBe('fallback.pdf')
  })

  it('当 disposition 不包含 filename 时应返回 fallback', () => {
    expect(parseFilenameFromDisposition('attachment', 'fallback.pdf')).toBe('fallback.pdf')
  })

  it('应优先使用 filename* 而非 filename', () => {
    const disposition = "attachment; filename=\"old.pdf\"; filename*=UTF-8''%E6%96%B0.pdf"
    expect(parseFilenameFromDisposition(disposition, 'fallback.pdf')).toBe('新.pdf')
  })
})

describe('triggerBlobDownload', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.mocked(httpClient.get).mockReset()
  })

  it('应创建 <a> 标签并触发点击', () => {
    const clickSpy = vi.fn()
    const appendSpy = vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    const removeSpy = vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})

    // mock createElement
    const originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      if (tag === 'a') {
        return { href: '', download: '', click: clickSpy }
      }
      return originalCreateElement(tag)
    })

    // mock URL.createObjectURL
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    const blob = new Blob(['test'], { type: 'text/plain' })
    triggerBlobDownload(blob, 'test.txt')

    expect(clickSpy).toHaveBeenCalled()
    expect(appendSpy).toHaveBeenCalled()
    expect(removeSpy).toHaveBeenCalled()
  })

  it('当 blob 为 null 时不应触发下载', () => {
    const clickSpy = vi.fn()
    vi.spyOn(document, 'createElement').mockReturnValue({ click: clickSpy })

    triggerBlobDownload(null, 'test.txt')
    expect(clickSpy).not.toHaveBeenCalled()
  })
})

describe('triggerNativeDownload', () => {
  it('应创建隐藏 <a> 标签设置 href 与 download 并触发点击与移除', () => {
    const clickSpy = vi.fn()
    const appendSpy = vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    const removeSpy = vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})

    const mockAnchor = { href: '', download: '', style: {}, click: clickSpy }
    const originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      if (tag === 'a') return mockAnchor
      return originalCreateElement(tag)
    })

    triggerNativeDownload('/api/projects/1/documents/2/download', 'test.docx')

    expect(mockAnchor.href).toBe('/api/projects/1/documents/2/download')
    expect(mockAnchor.download).toBe('test.docx')
    expect(mockAnchor.style.display).toBe('none')
    expect(appendSpy).toHaveBeenCalledWith(mockAnchor)
    expect(clickSpy).toHaveBeenCalled()
    expect(removeSpy).toHaveBeenCalledWith(mockAnchor)
  })

  it('当 url 为空时不应创建标签或触发点击', () => {
    const clickSpy = vi.fn()
    const createSpy = vi.spyOn(document, 'createElement')

    triggerNativeDownload('', 'test.docx')
    triggerNativeDownload(null, 'test.docx')

    expect(createSpy).not.toHaveBeenCalled()
    expect(clickSpy).not.toHaveBeenCalled()
  })

  it('未传 filename 时不设置 download 属性', () => {
    const clickSpy = vi.fn()
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})

    const mockAnchor = { href: '', style: {}, click: clickSpy }
    const originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      if (tag === 'a') return mockAnchor
      return originalCreateElement(tag)
    })

    triggerNativeDownload('https://example.com/file.pdf')

    expect(mockAnchor.href).toBe('https://example.com/file.pdf')
    expect(mockAnchor.download).toBeUndefined()
    expect(clickSpy).toHaveBeenCalled()
  })
})

describe('normalizeApiDownloadUrl', () => {
  it('应将绝对内部 API URL 归一为相对路径，避免跨 Host 丢失 Cookie', () => {
    expect(normalizeApiDownloadUrl('https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=x')).toBe('/api/doc-insight/download?fileUrl=x')
    expect(normalizeApiDownloadUrl('http://172.16.38.78:8080/api/doc-insight/download?fileUrl=x')).toBe('/api/doc-insight/download?fileUrl=x')
    expect(normalizeApiDownloadUrl('/api/doc-insight/download?fileUrl=x')).toBe('/api/doc-insight/download?fileUrl=x')
  })

  it('非 API URL 不应归一化', () => {
    expect(normalizeApiDownloadUrl('https://example.com/file.pdf')).toBe('')
  })
})

describe('downloadWithFilename', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.mocked(httpClient.get).mockReset()
    vi.mocked(ElMessage.error).mockReset()
  })

  it('当 url 为空时不应发起请求', async () => {
    const fetchSpy = vi.spyOn(global, 'fetch')
    await downloadWithFilename('', 'test.txt')
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('当 fetch 失败时应 fallback 到 window.open', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: false,
      status: 404,
    })
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => {})

    await downloadWithFilename('http://example.com/file', 'test.txt')
    expect(openSpy).toHaveBeenCalledWith('http://example.com/file', '_blank')
  })

  it('当 fetch 成功时应从 Content-Disposition 解析文件名并下载', async () => {
    const blob = new Blob(['test'], { type: 'text/plain' })
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      headers: {
        get: (name) => {
          if (name === 'Content-Disposition') return "attachment; filename*=UTF-8''%E6%B5%8B%E8%AF%95.txt"
          return null
        }
      },
      blob: () => Promise.resolve(blob)
    })

    const clickSpy = vi.fn()
    vi.spyOn(document, 'createElement').mockReturnValue({
      href: '', download: '', click: clickSpy
    })
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    await downloadWithFilename('http://example.com/file', 'fallback.txt')
    expect(clickSpy).toHaveBeenCalled()
  })

  it('API 下载应走 httpClient 以携带登录态', async () => {
    const blob = new Blob(['test'], { type: 'text/plain' })
    vi.mocked(httpClient.get).mockResolvedValue({
      data: blob,
      headers: {
        'content-disposition': "attachment; filename*=UTF-8''%E6%A0%87%E8%AE%AF.pdf"
      }
    })
    const fetchSpy = vi.spyOn(global, 'fetch')

    const clickSpy = vi.fn()
    vi.spyOn(document, 'createElement').mockReturnValue({
      href: '', download: '', click: clickSpy
    })
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    await downloadWithFilename('http://172.16.38.78:8080/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Fx', 'fallback.pdf')

    expect(httpClient.get).toHaveBeenCalledWith(
      '/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Fx',
      expect.objectContaining({ responseType: 'blob', timeout: 120000 })
    )
    expect(fetchSpy).not.toHaveBeenCalled()
    expect(clickSpy).toHaveBeenCalled()
  })

  it('绝对域名 API 下载应归一为当前同源相对路径', async () => {
    const blob = new Blob(['test'], { type: 'text/plain' })
    vi.mocked(httpClient.get).mockResolvedValue({
      data: blob,
      headers: {}
    })
    const fetchSpy = vi.spyOn(global, 'fetch')

    vi.spyOn(document, 'createElement').mockReturnValue({
      href: '', download: '', click: vi.fn()
    })
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    await downloadWithFilename('https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Fx', 'fallback.pdf')

    expect(httpClient.get).toHaveBeenCalledWith(
      '/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Fx',
      expect.objectContaining({ responseType: 'blob', timeout: 120000 })
    )
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('API 下载认证失败不应打开 Whitelabel 页面', async () => {
    vi.mocked(httpClient.get).mockRejectedValue({ response: { status: 403 } })
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => {})

    await downloadWithFilename('https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=x', 'fallback.pdf')

    expect(openSpy).not.toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith('登录已过期或访问入口不一致，请刷新页面并重新登录后下载')
  })

  it('当 options.native 为 true 时应走原生 <a> 标签下载，避免 blob 占用内存', async () => {
    const clickSpy = vi.fn()
    const appendSpy = vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    const removeSpy = vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})

    const mockAnchor = { href: '', download: '', style: {}, click: clickSpy }
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      if (tag === 'a') return mockAnchor
      return document.createElement(tag)
    })

    const fetchSpy = vi.spyOn(global, 'fetch')

    await downloadWithFilename('/api/projects/1374/documents/100/download', 'big_tender.docx', { native: true })

    expect(mockAnchor.href).toBe('/api/projects/1374/documents/100/download')
    expect(mockAnchor.download).toBe('big_tender.docx')
    expect(clickSpy).toHaveBeenCalled()
    expect(appendSpy).toHaveBeenCalledWith(mockAnchor)
    expect(removeSpy).toHaveBeenCalledWith(mockAnchor)
    expect(httpClient.get).not.toHaveBeenCalled()
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})

describe('showApiDownloadError', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.mocked(ElMessage.error).mockReset()
  })

  it('401 应提示重新登录', () => {
    showApiDownloadError({ response: { status: 401 } })
    expect(ElMessage.error).toHaveBeenCalledWith('登录已过期或访问入口不一致，请刷新页面并重新登录后下载')
  })

  it('403 应提示重新登录', () => {
    showApiDownloadError({ response: { status: 403 } })
    expect(ElMessage.error).toHaveBeenCalledWith('登录已过期或访问入口不一致，请刷新页面并重新登录后下载')
  })

  it('409 + 后端业务消息应透传给用户（CO-442）', () => {
    // 后端 ApiResponse 通过 @JsonProperty("msg") 输出消息字段
    // 场景：投标文件已进入「评标」阶段，文件只读不可下载
    showApiDownloadError({
      response: {
        status: 409,
        data: { success: false, code: 409, msg: '投标文件已进入「评标」阶段，文件只读不可下载' }
      }
    })
    expect(ElMessage.error).toHaveBeenCalledWith('投标文件已进入「评标」阶段，文件只读不可下载')
  })

  it('400 + 后端业务消息应透传给用户', () => {
    showApiDownloadError({
      response: {
        status: 400,
        data: { success: false, code: 400, msg: '文件不存在或已被删除' }
      }
    })
    expect(ElMessage.error).toHaveBeenCalledWith('文件不存在或已被删除')
  })

  it('无后端消息时应回退到通用错误提示', () => {
    showApiDownloadError({ response: { status: 500 } })
    expect(ElMessage.error).toHaveBeenCalledWith('文件下载失败，请稍后重试')
  })

  it('无 response 时应回退到通用错误提示', () => {
    showApiDownloadError(new Error('Network Error'))
    expect(ElMessage.error).toHaveBeenCalledWith('文件下载失败，请稍后重试')
  })

  it('后端 msg 字段为非字符串时应回退到通用错误提示', () => {
    showApiDownloadError({
      response: {
        status: 409,
        data: { success: false, code: 409, msg: { nested: 'object' } }
      }
    })
    expect(ElMessage.error).toHaveBeenCalledWith('文件下载失败，请稍后重试')
  })
})
