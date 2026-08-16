import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useScoreParseDrawer } from './useScoreParseDrawer.js'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
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

vi.mock('@/api/modules/scoreParse.js', () => ({
  scoreParseApi: {
    getItems: vi.fn(),
    triggerParse: vi.fn(),
    getParseStatus: vi.fn(),
    triggerScoring: vi.fn(),
    getScoringStatus: vi.fn(),
    getResults: vi.fn(),
  },
}))

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: {
    importScoreDraftsFromAnalysis: vi.fn(),
  },
}))

// spec 041 真接口 DTO 形状（ScoreItemDTO / ScoreScoringResultsDTO）
const REAL_ITEMS = [
  { id: 1, code: 'A1', dim: '技术方案', detail: '架构设计科学合理', weight: 10, scoreType: 'SUBJECTIVE', status: 'PENDING', estScore: null, estBasis: null, kbHit: null },
  { id: 2, code: 'D1', dim: '资质业绩', detail: '具备系统集成证书', weight: 6, scoreType: 'OBJECTIVE', status: 'OK', estScore: 6, estBasis: '知识库命中系统集成资质', kbHit: true },
]

const REAL_RESULTS = [
  { scoreItemId: 1, code: 'A1', dim: '技术方案', detail: '架构设计科学合理', weight: 10, scoreType: 'SUBJECTIVE', status: 'PENDING', actualScore: null, evidence: null, quote: null, missedReason: null, suggestion: '补充技术先进性阐述' },
  { scoreItemId: 2, code: 'D1', dim: '资质业绩', detail: '具备系统集成证书', weight: 6, scoreType: 'OBJECTIVE', status: 'OK', actualScore: 6, evidence: '标书第 3 章提供系统集成证书复印件', quote: '第 3 章资质证明', missedReason: null, suggestion: null },
]

describe('useScoreParseDrawer.js', () => {
  const props = { projectId: 99 }
  let emit

  beforeEach(() => {
    vi.clearAllMocks()
    ElMessageBox.confirm.mockResolvedValue('confirm')
    emit = vi.fn()
    scoreParseApi.getItems.mockResolvedValue({ data: { items: REAL_ITEMS, summary: null } })
    scoreParseApi.triggerParse.mockResolvedValue({ data: { taskId: 't-parse', status: 'PENDING' } })
    scoreParseApi.getParseStatus.mockResolvedValue({ data: { taskId: 't-parse', status: 'COMPLETED', progress: 100, completedAt: '2026-08-15T14:00:00' } })
    scoreParseApi.triggerScoring.mockResolvedValue({ data: { taskId: 't-score', status: 'PENDING' } })
    scoreParseApi.getScoringStatus.mockResolvedValue({ data: { taskId: 't-score', status: 'COMPLETED', progress: 100, completedAt: '2026-08-15T15:00:00' } })
    scoreParseApi.getResults.mockResolvedValue({ data: { results: REAL_RESULTS, summary: null } })
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
    // 真接口状态枚举映射：OK→ok / PENDING→neutral；OBJECTIVE/SUBJECTIVE→客观项/主观项
    expect(drawer.scoreItems.value[0].scoreType).toBe('主观项')
    expect(drawer.scoreItems.value[0].estScore).toBe('待评审')
    expect(drawer.scoreItems.value[1].scoreType).toBe('客观项')
    expect(drawer.scoreItems.value[1].estScore).toBe(6)
    expect(emit).toHaveBeenCalledWith('parsed', expect.any(Object))
  })

  it('sets empty scoreItems when backend returns no items (PRD §5.3 empty state contract)', async () => {
    scoreParseApi.getItems.mockResolvedValue({ data: { items: [], summary: null } })

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

  it('runs stage 2 scoring via real async scoring API (trigger → poll → results)', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2 })

    expect(scoreParseApi.triggerScoring).toHaveBeenCalledWith(99)
    expect(scoreParseApi.getScoringStatus).toHaveBeenCalledWith(99)
    expect(scoreParseApi.getResults).toHaveBeenCalledWith(99)
    expect(drawer.scored.value).toBe(true)
    expect(drawer.scoreResults.value['D1'].score).toBe(6)
    expect(drawer.scoreResults.value['D1'].basis).toBe('标书第 3 章提供系统集成证书复印件')
    expect(drawer.scoreResults.value['A1'].evalText).toBe('待专家评审')
    expect(drawer.actualTotalScore.value).toBe(6)
  })

  it('surfaces backend FAILED status as scoring error', async () => {
    scoreParseApi.getScoringStatus.mockResolvedValue({
      data: { taskId: 't-score', status: 'FAILED', errorMessage: '投标文件未上传' },
    })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2 })

    // FAILED 时轮询中断，不拉取结果、不填充假得分
    expect(scoreParseApi.getResults).not.toHaveBeenCalled()
    expect(drawer.scoreResults.value).toEqual({})
    expect(drawer.error.value).toBe('') // runScoring 自捕获错误并 toast，不污染 error
  })

  it('reparse triggers real parse task and refreshes items (FR-021)', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    await drawer.reparse()

    expect(scoreParseApi.triggerParse).toHaveBeenCalledWith(99)
    expect(scoreParseApi.getItems).toHaveBeenCalledTimes(2)
    expect(ElMessage.success).toHaveBeenCalledWith('已重新解析评分标准')
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
