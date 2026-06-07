// Input: alerts API module with mocked HTTP client
// Output: alert rule normalization coverage for real backend fields
// Pos: src/api/modules/ - Alerts API unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '@/api/client'
import { alertRulesApi } from './alerts.js'

describe('alertRulesApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getList(): normalizes real backend alert rule fields', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: [
        {
          id: 9,
          name: '保证金退还提醒',
          type: 'DEPOSIT_RETURN',
          condition: 'LESS_THAN',
          threshold: 7,
          enabled: true
        }
      ]
    })

    const result = await alertRulesApi.getList()

    expect(httpClient.get).toHaveBeenCalledWith('/api/alerts/rules')
    expect(result.data).toEqual([
      expect.objectContaining({
        id: 9,
        type: 'DEPOSIT_RETURN',
        condition: 'LESS_THAN',
        threshold: 7,
        enabled: true
      })
    ])
  })
})
