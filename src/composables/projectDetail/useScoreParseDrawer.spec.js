import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useScoreParseDrawer } from './useScoreParseDrawer.js'
import { bidAgentApi } from '@/api/modules/bidAgent.js'
import { projectsApi } from '@/api/modules/projects.js'
import { ElMessageBox } from 'element-plus'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessageBox: {
      confirm: vi.fn(),
    },
    ElMessage: {
      success: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
      error: vi.fn(),
    },
  }
})

vi.mock('@/api/modules/bidAgent.js', () => ({
  bidAgentApi: {
    getFullAnalysis: vi.fn(),
    getQualificationMatch: vi.fn(),
    getScoringCriteria: vi.fn(),
  },
}))

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: {
    importScoreDraftsFromAnalysis: vi.fn(),
  },
}))

describe('useScoreParseDrawer.js', () => {
  const props = { projectId: 99 }
  let emit

  beforeEach(() => {
    vi.clearAllMocks()
    ElMessageBox.confirm.mockResolvedValue('confirm')
    emit = vi.fn()
    bidAgentApi.getFullAnalysis.mockResolvedValue({
      data: {
        sourceFileName: '招标文件.pdf',
        scoringCriteria: {
          items: [
            { itemNumber: 'A1', dimension: '技术方案', indicator: '架构设计', weight: 10, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审' },
            { itemNumber: 'D1', dimension: '资质业绩', indicator: '系统集成', weight: 6, status: 'ok', statusText: '✓ 满足', scoreType: '客观项', estScore: 6 },
          ],
        },
      },
    })
    bidAgentApi.getQualificationMatch.mockResolvedValue({ data: {} })
    bidAgentApi.getScoringCriteria.mockResolvedValue({
      data: {
        sourceFileName: '招标文件.pdf',
        structuredItems: [
          { itemNumber: 'A1', dimension: '技术方案', indicator: '架构设计', weight: 10, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审' },
          { itemNumber: 'D1', dimension: '资质业绩', indicator: '系统集成', weight: 6, status: 'ok', statusText: '✓ 满足', scoreType: '客观项', estScore: 6 },
        ],
      },
    })
    projectsApi.importScoreDraftsFromAnalysis.mockResolvedValue({
      data: { importedCount: 2 },
    })
  })

  it('initializes default states properly and opens drawer in stage 1', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    expect(drawer.visible.value).toBe(false)

    await drawer.open({ stage: 1 })

    expect(drawer.visible.value).toBe(true)
    expect(drawer.currentStage.value).toBe(1)
    expect(drawer.scoreItems.value.length).toBe(2)
    expect(drawer.totalWeight.value).toBe(16)
    expect(drawer.statsOkCount.value).toBe(1)
    expect(drawer.statsNeutralCount.value).toBe(1)
    expect(emit).toHaveBeenCalledWith('parsed', expect.any(Object))
  })

  it('handles detail modal inspection', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    const sampleItem = drawer.scoreItems.value[0]
    drawer.openDetail(sampleItem, null, 'est')

    expect(drawer.detailModalVisible.value).toBe(true)
    expect(drawer.detailMode.value).toBe('est')
    expect(drawer.selectedItem.value.code).toBe('A1')
  })

  it('handles importToDrafts API call', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    await drawer.importToDrafts()
    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(projectsApi.importScoreDraftsFromAnalysis).toHaveBeenCalledWith(99)
    expect(emit).toHaveBeenCalledWith('imported', expect.any(Object))
  })
})
