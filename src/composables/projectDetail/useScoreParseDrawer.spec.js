import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useScoreParseDrawer } from './useScoreParseDrawer.js'
import { bidAgentApi } from '@/api/modules/bidAgent.js'
import { projectsApi } from '@/api/modules/projects.js'
import { ElMessageBox, ElMessage } from 'element-plus'

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
    evaluateBidScore: vi.fn(),
    getBidScoreEvaluation: vi.fn(),
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
    bidAgentApi.evaluateBidScore.mockResolvedValue({
      data: {
        projectId: 99,
        bidFileName: '西域投标文件.pdf',
        scoreTime: '2026-08-15 15:00:00',
        actualTotalScore: 6,
        items: [
          { code: 'A1', actualScore: null, status: 'PENDING_EXPERT', isSubjective: true, basis: '需专家评审', quote: null, suggestion: '补充技术先进性阐述' },
          { code: 'D1', actualScore: 6, status: 'SATISFIED', isSubjective: false, basis: '匹配系统集成证书', quote: '第 3 章资质证明', suggestion: '' },
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

  it('sets empty scoreItems when backend returns no criteria items (PRD §5.3 empty state contract)', async () => {
    bidAgentApi.getFullAnalysis.mockResolvedValue({ data: {} })
    bidAgentApi.getScoringCriteria.mockResolvedValue({ data: { structuredItems: [] } })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    expect(drawer.scoreItems.value).toEqual([])
    expect(drawer.totalWeight.value).toBe(0)
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

  it('calls real evaluateBidScore API in stage 2 scoring', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2 })

    expect(bidAgentApi.evaluateBidScore).toHaveBeenCalledWith(99)
    expect(drawer.scored.value).toBe(true)
    expect(drawer.scoreResults.value['D1'].score).toBe(6)
    expect(drawer.actualTotalScore.value).toBe(6)
  })

  it('supports runScoring with async runner adapter in stage 2', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2 })

    const customRunner = vi.fn().mockResolvedValue()
    await drawer.runScoring({ runner: customRunner })

    expect(customRunner).toHaveBeenCalled()
    expect(drawer.scored.value).toBe(true)
    expect(ElMessage.success).toHaveBeenCalledWith('AI 实际打分完成')
  })

  it('supports exportReport with popup or blob fallback', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2 })

    // Simulate popup blocked (window.open returns null)
    const originalOpen = window.open
    window.open = vi.fn().mockReturnValue(null)

    drawer.exportReport()
    expect(ElMessage.success).toHaveBeenCalledWith('已导出报告文件')

    window.open = originalOpen
  })
})
