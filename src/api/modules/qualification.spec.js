// Input: qualification API module with mocked HTTP client
// Output: qualification module coverage for list normalization and borrow fallback
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '@/api/client'
import { qualificationsApi } from './qualification.js'

describe('qualificationsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getList(): calls the real qualifications endpoint and normalizes records', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: [
        {
          id: 12,
          name: '高新技术企业证书',
          type: 'CONSTRUCTION',
          issueDate: '2026-01-02T08:00:00Z',
          expiryDate: '2999-01-01T00:00:00Z',
          issuer: '科技局',
          certificateNo: 'GX-001'
        }
      ]
    })

    const result = await qualificationsApi.getList({ name: '高新' })

    expect(httpClient.get).toHaveBeenCalledWith('/api/knowledge/qualifications')
    expect(result.success).toBe(true)
    expect(result.data).toHaveLength(1)
    expect(result.data[0]).toMatchObject({
      id: 12,
      name: '高新技术企业证书',
      type: 'enterprise',
      issueDate: '2026-01-02',
      expiryDate: '2999-01-01',
      issuer: '科技局',
      certificateNo: 'GX-001',
      status: 'valid'
    })
  })

  it('createBorrow(): returns feature-unavailable when backend borrow endpoint is missing', async () => {
    httpClient.post.mockRejectedValue({
      response: { status: 404 }
    })

    const result = await qualificationsApi.createBorrow(12, {
      borrower: '小王',
      projectId: '9',
      purpose: 'bidding'
    })

    expect(httpClient.post).toHaveBeenCalledWith('/api/knowledge/qualifications/12/borrow', {
      borrower: '小王',
      department: '',
      projectId: '9',
      purpose: 'bidding',
      expectedReturnDate: '',
      remark: ''
    })
    expect(result).toMatchObject({
      success: false,
      code: 'FEATURE_UNAVAILABLE',
      feature: 'qualificationBorrow'
    })
  })

  it('createBorrow(): includes projectId so backend project access guard can validate scope', async () => {
    httpClient.post.mockResolvedValue({ success: true, data: { id: 88 } })

    await qualificationsApi.createBorrow(12, {
      borrower: '小王',
      projectId: 9,
      purpose: 'bidding'
    })

    expect(httpClient.post).toHaveBeenCalledWith('/api/knowledge/qualifications/12/borrow', {
      borrower: '小王',
      department: '',
      projectId: 9,
      purpose: 'bidding',
      expectedReturnDate: '',
      remark: ''
    })
  })
})
