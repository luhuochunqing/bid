// Input: dashboardApi from dashboard.js, mocked httpClient
// Output: dashboard API method coverage for summary and customer type analytics
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn() }
}))

import httpClient from '@/api/client'
import { dashboardApi } from './dashboard.js'

describe('dashboardApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getSummary()', () => {
    it('on success: returns normalized data with success=true', async () => {
      httpClient.get.mockResolvedValue({
        success: true,
        data: {
          totalTenders: 42,
          activeProjects: 7,
          pendingTasks: 3,
          totalBudget: 1500000,
          successRate: 68.5
        }
      })

      const result = await dashboardApi.getSummary()

      expect(httpClient.get).toHaveBeenCalledOnce()
      expect(httpClient.get).toHaveBeenCalledWith('/api/analytics/summary')

      expect(result.success).toBe(true)
      expect(result.data).toEqual({
        totalTenders: expect.any(Number),
        activeProjects: expect.any(Number),
        pendingTasks: expect.any(Number),
        totalBudget: expect.any(Number),
        successRate: expect.any(Number)
      })
      expect(result.data.totalTenders).toBe(42)
      expect(result.data.activeProjects).toBe(7)
      expect(result.data.pendingTasks).toBe(3)
      expect(result.data.totalBudget).toBe(1500000)
      expect(result.data.successRate).toBe(68.5)
    })

    it('on API failure: returns zero-value fallback with success=false', async () => {
      httpClient.get.mockRejectedValue(new Error('Network error'))

      const result = await dashboardApi.getSummary()

      expect(httpClient.get).toHaveBeenCalledOnce()
      expect(httpClient.get).toHaveBeenCalledWith('/api/analytics/summary')

      expect(result.success).toBe(false)
      expect(result.data).toEqual({
        totalTenders: 0,
        activeProjects: 0,
        pendingTasks: 0,
        totalBudget: 0,
        successRate: 0
      })
    })
  })

  describe('customer type analytics', () => {
    it('getCustomerTypes(): calls the customer type analytics endpoint', async () => {
      httpClient.get.mockResolvedValue({
        success: true,
        data: {
          totalProjectCount: 2,
          dimensions: [
            { customerType: '政府客户', projectCount: 1 },
            { customerType: '未分类', projectCount: 1 }
          ]
        }
      })

      const result = await dashboardApi.getCustomerTypes()

      expect(httpClient.get).toHaveBeenCalledOnce()
      expect(httpClient.get).toHaveBeenCalledWith('/api/analytics/customer-types', { params: {} })
      expect(result.success).toBe(true)
      expect(result.data.dimensions).toHaveLength(2)
    })

    it('getCustomerTypeDrillDown(): passes customer type drill-down params', async () => {
      httpClient.get.mockResolvedValue({
        success: true,
        data: [
          {
            projectId: 1,
            projectName: '历史客户类型空值项目',
            customerType: '未分类'
          }
        ]
      })

      const params = { customerType: '未分类', startDate: '2026-04-01', endDate: '2026-04-30' }
      const result = await dashboardApi.getCustomerTypeDrillDown(params)

      expect(httpClient.get).toHaveBeenCalledOnce()
      expect(httpClient.get).toHaveBeenCalledWith('/api/analytics/drilldown/customer-type', { params })
      expect(result.data[0].customerType).toBe('未分类')
    })
  })
})
