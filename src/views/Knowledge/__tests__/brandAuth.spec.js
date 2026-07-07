// Input: brandAuth API module
// Output: coverage for exportZip URL parameter construction
// Pos: src/views/Knowledge/__tests__/ — API module tests

import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock httpClient before importing brandAuth
vi.mock('../../../api/client.js', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: new Blob() }),
    post: vi.fn().mockResolvedValue({ data: {} }),
    put: vi.fn().mockResolvedValue({ data: {} })
  }
}))

import httpClient from '../../../api/client.js'
import { brandAuthApi } from '../../../api/modules/brandAuth.js'

describe('brandAuthApi.exportZip', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('URL 参数包含 authorizationType', async () => {
    await brandAuthApi.exportZip({ authorizationType: 'MANUFACTURER' })
    const url = httpClient.get.mock.calls[0][0]
    expect(url).toContain('authorizationType=MANUFACTURER')
  })

  it('URL 参数包含 attachmentTypes（多值）', async () => {
    await brandAuthApi.exportZip({
      authorizationType: 'AGENT',
      attachmentTypes: ['AUTH_DOC', 'SUPPLEMENTARY']
    })
    const url = httpClient.get.mock.calls[0][0]
    expect(url).toContain('attachmentTypes=AUTH_DOC')
    expect(url).toContain('attachmentTypes=SUPPLEMENTARY')
  })

  it('authorizationType 为空时不传该参数', async () => {
    await brandAuthApi.exportZip({})
    const url = httpClient.get.mock.calls[0][0]
    expect(url).not.toContain('authorizationType=')
  })
})

describe('brandAuthApi.importExcel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('FormData 请求显式设置 Content-Type 为 multipart/form-data', async () => {
    const fakeFile = new File(['test'], 'test.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    await brandAuthApi.importExcel(fakeFile)

    expect(httpClient.post).toHaveBeenCalledTimes(1)
    const [, data, config] = httpClient.post.mock.calls[0]
    expect(data).toBeInstanceOf(FormData)
    // CO-512: 必须显式设置 multipart/form-data，否则被全局默认 application/json 覆盖导致 415
    expect(config?.headers?.['Content-Type']).toBe('multipart/form-data')
  })
})
