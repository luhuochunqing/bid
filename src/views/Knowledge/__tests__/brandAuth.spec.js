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
