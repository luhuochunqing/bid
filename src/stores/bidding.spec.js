// Input: bidding store, mocked tenders API
// Output: store state and action regression coverage
// Pos: stores/测试 - bidding store spec
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useBiddingStore } from './bidding'
import { tendersApi } from '@/api'

// Mock API
vi.mock('@/api', () => ({
  tendersApi: {
    getList: vi.fn()
  },
}))

describe('Bidding Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('初始状态应该正确', () => {
    const store = useBiddingStore()
    expect(store.tenders).toEqual([])
    expect(store.todos).toEqual([])
  })

  it('getters: highPriorityTenders 应该只返回评分 >= 85 的标讯', () => {
    const store = useBiddingStore()
    store.tenders = [
      { id: 1, aiScore: 90 },
      { id: 2, aiScore: 70 },
      { id: 3, aiScore: 85 }
    ]
    expect(store.highPriorityTenders).has.length(2)
    expect(store.highPriorityTenders.map(t => t.id)).contains(1, 3)
  })

  it('actions: getTenders 成功时应该更新 tenders', async () => {
    const store = useBiddingStore()
    const tenderRows = [{ id: 1, title: '测试标讯', status: 'following' }]
    tendersApi.getList.mockResolvedValue({ success: true, data: tenderRows })

    await store.getTenders()

    expect(store.tenders).toEqual([{ id: 1, title: '测试标讯', status: 'TRACKING' }])
    expect(tendersApi.getList).toHaveBeenCalledOnce()
  })

  it('actions: getTenders 应该把筛选条件交给后端查询', async () => {
    const store = useBiddingStore()
    const filters = {
      keyword: 'GPU',
      region: '上海',
      industry: '数据中心',
      purchaserName: '西域采购',
      publishDateFrom: '2026-04-01',
      publishDateTo: '2026-04-30',
      aiScoreMin: 90
    }
    tendersApi.getList.mockResolvedValue({ success: true, data: [] })

    await store.getTenders(filters)

    expect(tendersApi.getList).toHaveBeenCalledWith(filters)
  })

  it('actions: updateTenderStatus 应该更新本地状态', () => {
    const store = useBiddingStore()
    store.tenders = [{ id: '100', status: 'PENDING' }]

    store.updateTenderStatus('100', 'following')

    expect(store.tenders[0].status).toBe('TRACKING')
  })

  it('actions: setCalendar 应该覆盖日历状态', () => {
    const store = useBiddingStore()
    const events = [{ id: 1, date: '2026-04-21', type: 'deadline' }]

    store.setCalendar(events)

    expect(store.calendar).toEqual(events)
  })
})
