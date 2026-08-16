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
    scoreParseApi.getItems.mockResolvedValue({ data: { items: REAL_ITEMS, summary: null, sourceFileName: '国家级数据中心扩容项目招标文件.pdf', parseTime: '2026-08-15T14:00:00' } })
    scoreParseApi.triggerParse.mockResolvedValue({ data: { taskId: 't-parse', status: 'PENDING' } })
    scoreParseApi.getParseStatus.mockResolvedValue({ data: { taskId: 't-parse', status: 'COMPLETED', progress: 100, completedAt: '2026-08-15T14:00:00' } })
    scoreParseApi.triggerScoring.mockResolvedValue({ data: { taskId: 't-score', status: 'PENDING' } })
    scoreParseApi.getScoringStatus.mockResolvedValue({ data: { taskId: 't-score', status: 'COMPLETED', progress: 100, completedAt: '2026-08-15T15:00:00' } })
    scoreParseApi.getResults.mockResolvedValue({ data: { results: REAL_RESULTS, summary: null, bidFileName: '西域投标文件_v3.pdf', scoreTime: '2026-08-15T15:00:00' } })
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
    expect(drawer.scoreItems.value[0].estScore).toBe('待确认')
    expect(drawer.scoreItems.value[1].scoreType).toBe('客观项')
    expect(drawer.scoreItems.value[1].estScore).toBe(6)
    expect(emit).toHaveBeenCalledWith('parsed', expect.any(Object))
  })

  it('auto-parses initiation tender when drawer opens with no items', async () => {
    scoreParseApi.getItems
      .mockResolvedValueOnce({ data: { items: [], summary: null, meta: { lastParseStatus: null } } })
      .mockResolvedValueOnce({ data: { items: REAL_ITEMS, summary: null } })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    expect(scoreParseApi.triggerParse).toHaveBeenCalledWith(99, { source: 'AUTO' })
    expect(scoreParseApi.getParseStatus).toHaveBeenCalled()
    expect(drawer.scoreItems.value.length).toBe(2)
  })

  it('does not auto-parse when lastParseStatus is FAILED and shows lastParseError', async () => {
    scoreParseApi.getItems.mockResolvedValue({
      data: {
        items: [],
        summary: null,
        meta: { lastParseStatus: 'FAILED', lastParseError: '立项招标文件无法读取' },
      },
    })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    expect(scoreParseApi.triggerParse).not.toHaveBeenCalled()
    expect(drawer.error.value).toBe('立项招标文件无法读取')
  })

  it('follows in-flight PENDING parse when drawer opens with empty items', async () => {
    scoreParseApi.getItems
      .mockResolvedValueOnce({ data: { items: [], summary: null, meta: { lastParseStatus: 'PENDING' } } })
      .mockResolvedValueOnce({ data: { items: REAL_ITEMS, summary: null, meta: { lastParseStatus: 'COMPLETED' } } })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    expect(scoreParseApi.triggerParse).toHaveBeenCalledWith(99, { source: 'AUTO' })
    expect(scoreParseApi.getParseStatus).toHaveBeenCalled()
    expect(drawer.scoreItems.value.length).toBe(2)
  })

  it('follows in-flight PROCESSING parse when drawer opens with empty items', async () => {
    scoreParseApi.getItems
      .mockResolvedValueOnce({ data: { items: [], summary: null, meta: { lastParseStatus: 'PROCESSING' } } })
      .mockResolvedValueOnce({ data: { items: REAL_ITEMS, summary: null, meta: { lastParseStatus: 'COMPLETED' } } })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    expect(scoreParseApi.triggerParse).toHaveBeenCalledWith(99, { source: 'AUTO' })
    expect(scoreParseApi.getParseStatus).toHaveBeenCalled()
  })

  it('does not auto-parse when lastParseStatus is COMPLETED even if items are empty', async () => {
    scoreParseApi.getItems.mockResolvedValue({
      data: {
        items: [],
        summary: null,
        meta: { lastParseStatus: 'COMPLETED', lastParseError: null },
      },
    })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    expect(scoreParseApi.triggerParse).not.toHaveBeenCalled()
    expect(drawer.error.value).toBe('')
  })

  it('keeps empty items when auto-parse finds no initiation tender', async () => {
    scoreParseApi.getItems.mockResolvedValue({ data: { items: [], summary: null } })
    scoreParseApi.triggerParse.mockRejectedValue({ response: { data: { msg: '请先在立项阶段上传招标文件' } } })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    expect(drawer.scoreItems.value).toEqual([])
    expect(drawer.error.value).toBe('请先在立项阶段上传招标文件')
  })

  it('fills source info bar from items meta (R007/R022: file names no longer stuck at em-dash)', async () => {
    scoreParseApi.getItems.mockResolvedValue({
      data: {
        items: REAL_ITEMS,
        summary: null,
        meta: {
          sourceFileName: '招标文件-v3.pdf',
          parseTime: '2026-08-16T10:00:00',
          bidFileName: '投标文件-终稿.docx',
          scoreTime: '2026-08-16T11:30:00',
        },
      },
    })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    expect(drawer.sourceFileName.value).toBe('招标文件-v3.pdf')
    expect(drawer.parseTime.value).toContain('10:00:00')
    expect(drawer.bidFileName.value).toBe('投标文件-终稿.docx')
    expect(drawer.scoreTime.value).toContain('11:30:00')
  })

  it('shows circuit hint when meta.circuitOpen is true', async () => {
    scoreParseApi.getItems.mockResolvedValue({
      data: {
        items: REAL_ITEMS,
        summary: null,
        meta: { circuitOpen: true },
      },
    })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    expect(drawer.circuitHint.value).toBe('自动路径已停，请检查文件后手点重新解析或重新打分')
  })

  it('keeps em-dash placeholders when meta is absent (no fake data fallback)', async () => {
    scoreParseApi.getItems.mockResolvedValue({ data: { items: REAL_ITEMS, summary: null } })
    scoreParseApi.getResults.mockResolvedValue({ data: { results: [] } })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    expect(drawer.sourceFileName.value).toBe('—')
    expect(drawer.bidFileName.value).toBe('—')
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
    scoreParseApi.getResults
      .mockResolvedValueOnce({ data: { results: [], summary: null } })
      .mockResolvedValueOnce({ data: { results: REAL_RESULTS, summary: null } })
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2, autoScore: true })

    expect(scoreParseApi.triggerScoring).toHaveBeenCalledWith(99, expect.objectContaining({ source: 'AUTO', scope: 'ALL' }))
    expect(scoreParseApi.getScoringStatus).toHaveBeenCalledWith(99)
    expect(scoreParseApi.getResults).toHaveBeenCalledWith(99)
    expect(drawer.scored.value).toBe(true)
    expect(drawer.scoreResults.value['D1'].score).toBe(6)
    expect(drawer.scoreResults.value['D1'].basis).toBe('标书第 3 章提供系统集成证书复印件')
    expect(drawer.scoreResults.value['A1'].evalText).toBe('待确认')
    expect(drawer.actualTotalScore.value).toBe(6)
  })

  it('populates sourceFileName/bidFileName from API responses (P1 fix: no hardcoded file names)', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2 })

    // sourceFileName/parseTime from getItems response
    expect(drawer.sourceFileName.value).toBe('国家级数据中心扩容项目招标文件.pdf')
    expect(drawer.parseTime.value).toContain('2026')

    // bidFileName/scoreTime from getResults response
    expect(drawer.bidFileName.value).toBe('西域投标文件_v3.pdf')
    expect(drawer.scoreTime.value).toContain('2026')
  })

  it('keeps em-dash fallback when API omits file metadata', async () => {
    scoreParseApi.getItems.mockResolvedValue({ data: { items: REAL_ITEMS, summary: null } })
    scoreParseApi.getResults.mockResolvedValue({ data: { results: REAL_RESULTS, summary: null } })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2 })

    expect(drawer.sourceFileName.value).toBe('—')
    // bidFileName stays '—' since API didn't return it
    // (scoreTime gets overwritten by pollTask completedAt, that's expected)
  })

  it('surfaces backend FAILED status as scoring error', async () => {
    scoreParseApi.getResults.mockResolvedValue({ data: { results: [], summary: null } })
    scoreParseApi.getScoringStatus.mockResolvedValue({
      data: { taskId: 't-score', status: 'FAILED', errorMessage: '投标文件未上传' },
    })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2, autoScore: true })

    expect(drawer.scoreResults.value).toEqual({})
    expect(drawer.error.value).toBe('') // runScoring 自捕获错误并 toast，不污染 error
  })

  it('renders all items as pending when backend writes fallback results on scoring failure', async () => {
    const fallbackResults = [
      { scoreItemId: 1, code: 'A1', actualScore: null, status: 'PENDING', evidence: null, quote: null, missedReason: '投标文件解析失败，无法完成打分，请检查文件内容或重新上传' },
      { scoreItemId: 2, code: 'D1', actualScore: null, status: 'PENDING', evidence: null, quote: null, missedReason: '投标文件解析失败，无法完成打分，请检查文件内容或重新上传' },
    ]
    scoreParseApi.getItems.mockResolvedValue({
      data: { items: REAL_ITEMS, meta: { bidFileName: '损坏标书.pdf' } },
    })
    scoreParseApi.getScoringStatus.mockResolvedValue({
      data: { taskId: 't-score', status: 'FAILED', errorMessage: '投标文件解析失败，无法完成打分，请检查文件内容或重新上传' },
    })
    scoreParseApi.getResults.mockResolvedValue({
      data: { results: fallbackResults },
    })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2, autoScore: true })

    expect(drawer.scored.value).toBe(true)
    expect(drawer.scoreResults.value['A1'].evalText).toBe('待确认')
    expect(drawer.scoreResults.value['A1'].missedReason).toContain('投标文件解析失败')
  })

  it('reparse triggers real parse task and refreshes items (FR-021)', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 1 })

    await drawer.reparse()

    expect(scoreParseApi.triggerParse).toHaveBeenCalledWith(99, { source: 'MANUAL' })
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

  it('correctly identifies empty unscored results as hasResults=false and scored=false', async () => {
    const emptyResults = [
      { scoreItemId: 1, code: 'A1', actualScore: null, status: 'PENDING', evidence: null, quote: null, suggestion: null },
      { scoreItemId: 2, code: 'D1', actualScore: null, status: 'PENDING', evidence: null, quote: null, suggestion: null },
    ]
    scoreParseApi.getItems.mockResolvedValue({
      data: {
        items: REAL_ITEMS,
        meta: { bidFileName: '西域投标文件.pdf' },
      },
    })
    scoreParseApi.getResults.mockResolvedValue({
      data: { results: emptyResults },
    })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open()

    expect(drawer.scored.value).toBe(false)
    expect(drawer.currentStage.value).toBe(2)
  })

  it('correctly identifies scored results as hasResults=true and scored=true', async () => {
    scoreParseApi.getItems.mockResolvedValue({
      data: {
        items: REAL_ITEMS,
        meta: { bidFileName: '西域投标文件.pdf' },
      },
    })
    scoreParseApi.getResults.mockResolvedValue({
      data: { results: REAL_RESULTS },
    })

    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open()

    expect(drawer.scored.value).toBe(true)
    expect(drawer.currentStage.value).toBe(2)
  })

  it('submits ITEMS scope and selected ids when rescoring', async () => {
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2 })
    drawer.scoringScope.value = 'ITEMS'
    drawer.selectedItemIds.value = [1, 2]

    await drawer.runScoring({ auto: false, scope: 'ITEMS', itemIds: [1, 2] })

    expect(scoreParseApi.triggerScoring).toHaveBeenCalledWith(99, {
      source: 'MANUAL',
      scope: 'ITEMS',
      itemIds: [1, 2],
    })
  })

  it('shows skip hint and does not poll when file is unchanged', async () => {
    scoreParseApi.triggerScoring.mockResolvedValue({
      data: { taskId: 't-skip', status: 'COMPLETED', outcome: 'SKIPPED', hint: '文件未变化' },
    })
    const drawer = useScoreParseDrawer(props, emit)
    await drawer.open({ stage: 2 })

    await drawer.runScoring({ auto: false, scope: 'ITEMS', itemIds: [1] })

    expect(scoreParseApi.triggerScoring).toHaveBeenCalledWith(99, expect.objectContaining({
      scope: 'ITEMS',
      itemIds: [1],
    }))
    expect(scoreParseApi.getScoringStatus).not.toHaveBeenCalled()
    expect(ElMessage.info).toHaveBeenCalledWith('文件未变化')
    expect(drawer.scoringHint.value).toBe('文件未变化')
    expect(drawer.scored.value).toBe(true)
  })
})
