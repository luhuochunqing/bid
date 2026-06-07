// Input: alertHistoryApi from alerts.js, mocked httpClient
// Output: getUnresolved() method coverage — normalized unresolved alerts
// Pos: src/api/modules/__tests__/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

import httpClient from '@/api/client'
import { alertHistoryApi } from '../alerts.js'

describe('alertHistoryApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getUnresolved()', () => {
    it('should call httpClient.get with /api/alerts/history/unresolved when no params provided', async () => {
      const mockResponse = { success: true, data: [] }
      httpClient.get.mockResolvedValue(mockResponse)

      const result = await alertHistoryApi.getUnresolved()

      expect(httpClient.get).toHaveBeenCalledOnce()
      expect(httpClient.get).toHaveBeenCalledWith('/api/alerts/history/unresolved', { params: {} })
      expect(result).toStrictEqual({ success: true, data: [], total: 0 })
    })

    it('should pass pagination params when provided', async () => {
      const mockResponse = { success: true, data: [{ id: '1' }] }
      httpClient.get.mockResolvedValue(mockResponse)
      const params = { page: 0, size: 10 }

      const result = await alertHistoryApi.getUnresolved(params)

      expect(httpClient.get).toHaveBeenCalledOnce()
      expect(httpClient.get).toHaveBeenCalledWith('/api/alerts/history/unresolved', { params })
      expect(result).toMatchObject({
        success: true,
        total: 1,
        data: [
          expect.objectContaining({
            id: '1',
            status: 'ACTIVE',
          }),
        ],
      })
    })

    it('should normalize unresolved payload shape', async () => {
      const mockResponse = { success: true, data: [{ id: '1', rawField: 'raw' }] }
      httpClient.get.mockResolvedValue(mockResponse)

      const result = await alertHistoryApi.getUnresolved()

      expect(result).toStrictEqual({
        success: true,
        total: 1,
        data: [
          {
            id: '1',
            ruleId: null,
            ruleName: '未知规则',
            alertType: 'SYSTEM',
            message: '',
            severity: 'INFO',
            status: 'ACTIVE',
            projectId: null,
            projectName: '',
            createdAt: '',
            acknowledgedAt: null,
            resolvedAt: null,
          },
        ],
      })
    })
  })
})
