import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

vi.mock('@/api', () => ({
  dashboardApi: {
    getEnhancedOverview: vi.fn()
  }
}))
vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn() }
}))

import { dashboardApi } from '@/api'
import { useAnalyticsData } from './useAnalyticsData.js'

const cardByKey = (cards, key) => cards.find((c) => c.key === key)

describe('useAnalyticsData M0 关键指标口径文案', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('投标数卡片使用四状态口径文案', async () => {
    dashboardApi.getEnhancedOverview.mockResolvedValue({
      data: { totalCount: 10, biddingCount: 3, wonCount: 4, notWonCount: 2, winRate: 66.7, todayNewCount: 1 }
    })
    const { kpiCards, loadM0Data } = useAnalyticsData()

    await loadM0Data()

    const total = cardByKey(kpiCards.value, 'totalCount')
    expect(total.label).toBe('投标数')
    expect(total.foot).toBe('项目数（投标中+评标中+已中标+未中标）')
  })

  it('中标率卡片使用（已中标+未中标）分母文案', async () => {
    dashboardApi.getEnhancedOverview.mockResolvedValue({
      data: { totalCount: 10, biddingCount: 3, wonCount: 4, notWonCount: 2, winRate: 66.7, todayNewCount: 1 }
    })
    const { kpiCards, loadM0Data } = useAnalyticsData()

    await loadM0Data()

    const winRate = cardByKey(kpiCards.value, 'winRate')
    expect(winRate.foot).toBe('中标数/（已中标+未中标）项目数')
  })

  it('接口失败 fallback 卡片同样使用新文案', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    dashboardApi.getEnhancedOverview.mockRejectedValue(new Error('boom'))
    const { kpiCards, loadM0Data } = useAnalyticsData()

    await loadM0Data()

    expect(cardByKey(kpiCards.value, 'totalCount').label).toBe('投标数')
    expect(cardByKey(kpiCards.value, 'totalCount').foot).toBe('项目数（投标中+评标中+已中标+未中标）')
    expect(cardByKey(kpiCards.value, 'winRate').foot).toBe('中标数/（已中标+未中标）项目数')
  })
})
