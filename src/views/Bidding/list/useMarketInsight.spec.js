import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useMarketInsight } from './useMarketInsight.js'
import { marketInsightApi } from '@/api/modules/marketInsight.js'

vi.mock('@/api/modules/marketInsight.js', () => ({
  marketInsightApi: { getInsight: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    info: vi.fn(),
    success: vi.fn(),
  },
}))

describe('useMarketInsight', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads market insight from the authenticated backend API', async () => {
    marketInsightApi.getInsight.mockResolvedValue({
      success: true,
      data: {
        industryTrends: [{ industry: '智慧园区', count: 2, amount: 32000000, growth: 18, trend: 'up', hotLevel: 4, color: 'purple' }],
        purchaserPatterns: [{ name: '华东客户', industry: '智慧园区', frequency: 2, period: '4月', avgBudget: 1600, opportunity: 2 }],
        forecastTips: [{ text: '智慧园区需求上升', color: '#67c23a' }],
      },
    })

    const insight = useMarketInsight()
    await insight.refreshTrendData()
    await nextTick()

    expect(marketInsightApi.getInsight).toHaveBeenCalledTimes(1)
    expect(insight.industryTrends.value[0]).toMatchObject({ industry: '智慧园区', count: 2, amount: 3200 })
    expect(insight.potentialOpportunities.value[0]).toMatchObject({ purchaser: '华东客户', budget: 1600 })
    expect(insight.forecastTips.value).toEqual([{ text: '智慧园区需求上升', color: '#67c23a' }])
  })

  it('clears stale defaults when the backend insight request fails', async () => {
    marketInsightApi.getInsight.mockRejectedValue(new Error('network down'))
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})

    const insight = useMarketInsight()
    await insight.refreshTrendData()

    expect(insight.industryTrends.value).toEqual([])
    expect(insight.potentialOpportunities.value).toEqual([])
    expect(insight.industryInsight.value).toBe('市场洞察加载失败，请稍后重试')
    expect(consoleError).not.toHaveBeenCalled()
    consoleError.mockRestore()
  })
})
