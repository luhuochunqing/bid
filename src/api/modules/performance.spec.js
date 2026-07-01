// Input: httpClient mock
// Output: performanceApi uploadAttachment multipart upload coverage
// Pos: src/api/modules/ - Frontend API module layer test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { performanceApi } from './performance.js'

vi.mock('../client.js', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '../client.js'

describe('performanceApi.uploadAttachment', () => {
  beforeEach(() => {
    httpClient.post.mockReset()
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
