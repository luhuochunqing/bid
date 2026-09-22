// Input: feesApi from fees.js, mocked httpClient
// Output: getByProject() method coverage — calls correct endpoint, returns raw response
// Pos: src/api/modules/__tests__/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

import httpClient from '@/api/client'
import { feesApi } from '../fees.js'

describe('feesApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getByProject()', () => {
    it('should call httpClient.get with /api/fees/project/{projectId}', async () => {
      const mockResponse = { success: true, data: [] }
      httpClient.get.mockResolvedValue(mockResponse)

      await feesApi.getByProject(123)

      expect(httpClient.get).toHaveBeenCalledOnce()
      expect(httpClient.get).toHaveBeenCalledWith('/api/fees/project/123')
    })

    it('should return the raw httpClient response', async () => {
      const mockResponse = { success: true, data: [{ id: 1, amount: 500 }] }
      httpClient.get.mockResolvedValue(mockResponse)

      const result = await feesApi.getByProject(42)

      expect(result).toBe(mockResponse)
    })
  })
})
