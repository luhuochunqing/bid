// Input: performance API module with mocked HTTP client
// Output: uploadAttachment + export endpoint coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

// Stub URL.createObjectURL / revokeObjectURL to capture blob input
let createdBlob = null
globalThis.URL.createObjectURL = vi.fn((blob) => {
  createdBlob = blob
  return 'blob:fake-url'
})
globalThis.URL.revokeObjectURL = vi.fn()

// Stub document.createElement('a').click to avoid jsdom navigation
const fakeLink = {
  href: '',
  download: '',
  click: vi.fn(),
  remove: vi.fn(),
  setAttribute: vi.fn((name, value) => { if (name === 'download') fakeLink.download = value })
}
globalThis.document.createElement = vi.fn((tag) => {
  if (tag === 'a') return fakeLink
  return {}
})
globalThis.document.body.appendChild = vi.fn()

import httpClient from '@/api/client'
import { performanceApi } from './performance.js'

describe('performanceApi.uploadAttachment', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uploads file as multipart with fileType field and returns server response', async () => {
    httpClient.post.mockResolvedValue({
      success: true,
      data: { fileName: '合同协议.pdf', fileUrl: '/data/performance-attachments/CONTRACT_AGREEMENT/abc.pdf' }
    })

    const file = new File(['pdf'], '合同协议.pdf', { type: 'application/pdf' })
    const result = await performanceApi.uploadAttachment(file, 'CONTRACT_AGREEMENT')

    expect(httpClient.post).toHaveBeenCalledTimes(1)
    const [url, body, config] = httpClient.post.mock.calls[0]
    expect(url).toBe('/api/knowledge/performance/attachments/upload')
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('fileType')).toBe('CONTRACT_AGREEMENT')
    expect(body.get('file')).toBe(file)
    expect(config.headers['Content-Type']).toBe('multipart/form-data')
    expect(result.data.fileName).toBe('合同协议.pdf')
  })
})

describe('performanceApi - export (CO-445)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    createdBlob = null
  })

  describe('batchExport', () => {
    it('uses response.data (not whole response) for Blob construction', async () => {
      const blobData = new Blob([1, 2, 3], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      httpClient.get.mockResolvedValue({
        data: blobData,
        headers: { 'content-disposition': 'attachment; filename=performance_export.xlsx' }
      })

      await performanceApi.batchExport()

      expect(createdBlob).toBeInstanceOf(Blob)
      expect(createdBlob).toBe(blobData)
      expect(createdBlob.size).toBe(3)
    })

    it('appends ids as repeated query params when ids array provided', async () => {
      httpClient.get.mockResolvedValue({ data: new Blob([1]), headers: {} })
      await performanceApi.batchExport({ ids: [10, 20] })

      const calledUrl = httpClient.get.mock.calls[0][0]
      expect(calledUrl).toContain('ids=10')
      expect(calledUrl).toContain('ids=20')
    })

    it('appends filter params (keyword, customerTypes) from searchForm', async () => {
      httpClient.get.mockResolvedValue({ data: new Blob([1]), headers: {} })
      await performanceApi.batchExport({
        keyword: '合同A',
        customerTypes: ['CENTRAL_SOE', 'LOCAL_SOE']
      })

      const calledUrl = httpClient.get.mock.calls[0][0]
      expect(calledUrl).toContain('keyword=')
      expect(calledUrl).toContain('customerTypes=CENTRAL_SOE')
      expect(calledUrl).toContain('customerTypes=LOCAL_SOE')
    })

    it('omits ids and filters when params is empty (full export)', async () => {
      httpClient.get.mockResolvedValue({ data: new Blob([1]), headers: {} })
      await performanceApi.batchExport()

      const calledUrl = httpClient.get.mock.calls[0][0]
      expect(calledUrl).not.toContain('?')
    })

    it('passes responseType: blob to httpClient.get', async () => {
      httpClient.get.mockResolvedValue({ data: new Blob([1]), headers: {} })
      await performanceApi.batchExport()

      const opts = httpClient.get.mock.calls[0][1]
      expect(opts).toEqual({ responseType: 'blob' })
    })
  })

  describe('batchExportZip', () => {
    it('uses response.data (not whole response) for Blob construction', async () => {
      const blobData = new Blob([1, 2, 3, 4], { type: 'application/zip' })
      httpClient.get.mockResolvedValue({
        data: blobData,
        headers: { 'content-disposition': 'attachment; filename="业绩台账_20260701.zip"' }
      })

      await performanceApi.batchExportZip()

      expect(createdBlob).toBeInstanceOf(Blob)
      expect(createdBlob).toBe(blobData)
      expect(createdBlob.size).toBe(4)
    })

    it('appends ids as repeated query params when ids array provided', async () => {
      httpClient.get.mockResolvedValue({ data: new Blob([1]), headers: {} })
      await performanceApi.batchExportZip({ ids: [5, 6] })

      const calledUrl = httpClient.get.mock.calls[0][0]
      expect(calledUrl).toContain('ids=5')
      expect(calledUrl).toContain('ids=6')
    })

    it('appends filter params from searchForm', async () => {
      httpClient.get.mockResolvedValue({ data: new Blob([1]), headers: {} })
      await performanceApi.batchExportZip({
        keyword: '测试',
        projectTypes: ['OFFICE']
      })

      const calledUrl = httpClient.get.mock.calls[0][0]
      expect(calledUrl).toContain('keyword=')
      expect(calledUrl).toContain('projectTypes=OFFICE')
    })
  })

  describe('downloadTemplate', () => {
    it('uses response.data (not whole response) for Blob construction', async () => {
      const blobData = new Blob([9, 9, 9], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      httpClient.get.mockResolvedValue({ data: blobData, headers: {} })

      await performanceApi.downloadTemplate()

      expect(createdBlob).toBeInstanceOf(Blob)
      expect(createdBlob).toBe(blobData)
      expect(createdBlob.size).toBe(3)
    })
  })
})
