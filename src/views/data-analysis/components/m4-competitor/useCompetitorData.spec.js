import { describe, it, expect, vi, beforeEach } from 'vitest'

// mock API 层：竞品选项按后端口径降序返回（参与项目数最多的在前）
vi.mock('@/api/modules/dashboard.js', () => ({
  dashboardApi: {
    getTenderEntities: vi.fn(),
    getFilterOptions: vi.fn(),
    getCompetitorAnalysis: vi.fn(),
    getProjectNames: vi.fn()
  }
}))
// mock echarts：测试不涉及渲染，避免 jsdom 下初始化图表库
vi.mock('@/utils/echarts', () => ({ default: {} }))

import { dashboardApi } from '@/api/modules/dashboard.js'
import { useCompetitorData } from './composables/useCompetitorData.js'

// 12 个竞品选项（模拟后端按参与项目数降序）
const RANKED_OPTIONS = Array.from({ length: 12 }, (_, i) => ({
  label: `竞品${i + 1}`,
  value: `竞品${i + 1}`
}))

const setupMocks = (competitor) => {
  dashboardApi.getTenderEntities.mockResolvedValue({ data: [] })
  dashboardApi.getFilterOptions.mockResolvedValue({ data: { competitor } })
  // resetFilters/onXxxChange 会触发 fetchData，拒绝即可走 catch 分支，避免渲染副作用
  dashboardApi.getCompetitorAnalysis.mockRejectedValue(new Error('skip in test'))
}

describe('useCompetitorData 默认选中前 8（参与项目数最多）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('initOptions 后默认选中后端排名前 8 的竞品且顺序一致', async () => {
    setupMocks(RANKED_OPTIONS)
    const { initOptions, selectedCompetitors, competitorOptions } = useCompetitorData()

    await initOptions()

    expect(competitorOptions.value).toHaveLength(12)
    expect(selectedCompetitors.value).toHaveLength(8)
    expect(selectedCompetitors.value).toEqual(RANKED_OPTIONS.slice(0, 8).map((o) => o.value))
  })

  it('resetFilters 恢复为前 8 个（与初始默认一致）', async () => {
    setupMocks(RANKED_OPTIONS)
    const { initOptions, resetFilters, selectedCompetitors } = useCompetitorData()

    await initOptions()
    selectedCompetitors.value = ['竞品1']
    resetFilters()

    expect(selectedCompetitors.value).toEqual(RANKED_OPTIONS.slice(0, 8).map((o) => o.value))
  })

  it('竞品选项不足 8 个时全选', async () => {
    setupMocks(RANKED_OPTIONS.slice(0, 3))
    const { initOptions, selectedCompetitors } = useCompetitorData()

    await initOptions()

    expect(selectedCompetitors.value).toEqual(['竞品1', '竞品2', '竞品3'])
  })

  it('空响应时保留静态兜底名单（≤8 个）', async () => {
    setupMocks([])
    const { initOptions, selectedCompetitors } = useCompetitorData()

    await initOptions()

    expect(selectedCompetitors.value.length).toBeLessThanOrEqual(8)
    expect(selectedCompetitors.value.length).toBeGreaterThan(0)
  })
})
