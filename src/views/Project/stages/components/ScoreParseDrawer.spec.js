import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreParseDrawer from './ScoreParseDrawer.vue'
import { scoreParseApi } from '@/api/modules/scoreParse.js'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  }
})

vi.mock('@/api/modules/scoreParse.js', () => ({
  scoreParseApi: {
    getLatestExtraction: vi.fn(),
    getExtraction: vi.fn(),
    activateExtraction: vi.fn(),
    getItems: vi.fn(),
    getReview: vi.fn(),
    submitReview: vi.fn(),
    triggerParse: vi.fn(),
    getParseStatus: vi.fn(),
    triggerScoring: vi.fn(),
    getScoringStatus: vi.fn(),
    getResults: vi.fn(),
  },
}))

// spec 041 真接口 DTO 形状（ScoreItemDTO / ScoreScoringResultsDTO）
const itemsFixture = [
  { id: 1, code: 'A1', dim: '技术方案', detail: '总体架构设计', weight: 10, scoreType: 'SUBJECTIVE', status: 'PENDING', estScore: null, estBasis: null },
  { id: 2, code: 'D1', dim: '资质业绩', detail: '系统集成一级', weight: 6, scoreType: 'OBJECTIVE', status: 'OK', estScore: 6, estBasis: '资质库匹配' },
]

const resultsFixture = {
  results: [
    { scoreItemId: 1, code: 'A1', scoreType: 'SUBJECTIVE', status: 'PENDING', actualScore: null, evidence: null, quote: null, missedReason: null, suggestion: '建议细化架构' },
    { scoreItemId: 2, code: 'D1', scoreType: 'OBJECTIVE', status: 'OK', actualScore: 6, evidence: '标书第 7 章资质证明', quote: '已取得系统集成一级', missedReason: null, suggestion: null },
  ],
  summary: null,
}

describe('ScoreParseDrawer.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    scoreParseApi.getLatestExtraction.mockRejectedValue({ response: { status: 409, data: { msg: 'EXTRACTION_FEATURE_DISABLED' } } })
    scoreParseApi.getItems.mockResolvedValue({ data: { items: itemsFixture, summary: null } })
    scoreParseApi.getReview.mockResolvedValue({ data: { facts: [], audits: [] } })
    scoreParseApi.submitReview.mockResolvedValue({ data: { facts: [], audits: [{ action: 'RETAIN_ORIGINAL' }] } })
    scoreParseApi.triggerScoring.mockResolvedValue({ data: { taskId: 't-score', status: 'PENDING' } })
    scoreParseApi.getScoringStatus.mockResolvedValue({ data: { taskId: 't-score', status: 'COMPLETED', progress: 100, completedAt: '2026-08-15T14:30:00' } })
    scoreParseApi.getResults.mockResolvedValue({ data: resultsFixture })
  })

  it('shows an incomplete latest extraction even with zero active items, without enabling writes for a viewer', async () => {
    scoreParseApi.getItems.mockResolvedValue({ data: { items: [], meta: { lastParseStatus: 'COMPLETED' } } })
    scoreParseApi.getLatestExtraction.mockResolvedValue({ data: {
      taskId: 'latest', taskStatus: 'COMPLETED', snapshotStatus: 'FROZEN', payloadVersion: 1,
      schemes: [{ schemeId: 's', extractedCount: 1, extractedKnownTotal: 91, declaredTotal: 100, declaredTotalStatus: 'KNOWN' }],
      items: [{ itemId: 'partial', name: '已识别评分项', schemeId: 's', computable: true, effectiveMaximum: 91, ruleText: '规则原文' }],
      active: { fingerprint: 'fp', dataStatus: 'COHERENT' }, adoption: { adoptedItemIds: [] },
    } })
    const wrapper = mount(ScoreParseDrawer, { props: { projectId: 123, allowExtractionActions: false }, global: { stubs: {
      'el-drawer': { template: '<div><slot /></div>' }, 'el-dialog': true,
    } } })
    await wrapper.vm.open({ stage: 1, autoScore: false, autoParse: false })
    expect(wrapper.text()).toContain('已识别评分项')
    expect(wrapper.text()).toContain('9 分待核对')
    const panel = wrapper.findComponent({ name: 'ScoreExtractionPanel' })
    expect(panel.props('blocked')).toBe(true)
    panel.vm.$emit('activate', { schemeId: 's', selectedItemIds: ['partial'], reason: '未经授权' })
    await wrapper.vm.$nextTick()
    expect(scoreParseApi.activateExtraction).not.toHaveBeenCalled()
  })

  it('mounts and opens drawer in stage 2', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: {
        projectId: 123,
      },
      global: {
        stubs: {
          'el-drawer': {
            template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>',
            props: ['modelValue'],
          },
          'el-dialog': true,
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ stage: 2, autoScore: false })

    expect(wrapper.vm.visible).toBe(true)
    expect(wrapper.vm.currentStage).toBe(2)
    expect(wrapper.text()).toContain('招标文件解析')
    expect(wrapper.text()).toContain('投标文件评分')
    expect(wrapper.text()).toContain('总体架构设计')
    expect(wrapper.text()).toContain('系统集成一级')
  })

  it('supports stage 1 mode without actual score calculation', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: {
        projectId: 123,
      },
      global: {
        stubs: {
          'el-drawer': {
            template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>',
            props: ['modelValue'],
          },
          'el-dialog': true,
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false })

    expect(wrapper.vm.currentStage).toBe(1)
    expect(scoreParseApi.triggerScoring).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('尚未上传投标文件')
  })

  it('renders disabled button and prompt when in stage 1 without bid document', async () => {
    scoreParseApi.getItems.mockResolvedValue({ data: { items: itemsFixture, meta: { bidFileName: null } } })
    scoreParseApi.getResults.mockResolvedValue({ data: { results: [] } })

    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId: 123 },
      global: {
        stubs: {
          'el-drawer': { template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>', props: ['modelValue'] },
          'el-dialog': true,
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false })
    const buttons = wrapper.findAll('.section-actions button')
    const btn = buttons[1]
    expect(btn.attributes('disabled')).toBeDefined()
    expect(btn.text()).toContain('需先上传标书')
  })

  it('renders AI 实际打分 button when bid document exists but unscored', async () => {
    const emptyResults = [
      { scoreItemId: 1, code: 'A1', actualScore: null, status: 'PENDING', evidence: null, quote: null, suggestion: null },
      { scoreItemId: 2, code: 'D1', actualScore: null, status: 'PENDING', evidence: null, quote: null, suggestion: null },
    ]
    scoreParseApi.getItems.mockResolvedValue({ data: { items: itemsFixture, meta: { bidFileName: '投标书.pdf' } } })
    scoreParseApi.getResults.mockResolvedValue({ data: { results: emptyResults } })

    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId: 123 },
      global: {
        stubs: {
          'el-drawer': { template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>', props: ['modelValue'] },
          'el-dialog': true,
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ autoScore: false })
    const buttons = wrapper.findAll('.section-actions button')
    const btn = buttons[1]
    expect(btn.attributes('disabled')).toBeUndefined()
    expect(btn.text()).toBe('⚡ AI 实际打分')
    expect(wrapper.text()).toContain('已检测到投标文件，尚未打分')
  })

  it('renders 重新打分 button when already scored', async () => {
    scoreParseApi.getItems.mockResolvedValue({ data: { items: itemsFixture, meta: { bidFileName: '投标书.pdf' } } })
    scoreParseApi.getResults.mockResolvedValue({ data: resultsFixture })

    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId: 123 },
      global: {
        stubs: {
          'el-drawer': { template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>', props: ['modelValue'] },
          'el-dialog': true,
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ autoScore: false })
    const buttons = wrapper.findAll('.section-actions button')
    const btn = buttons[1]
    expect(btn.attributes('disabled')).toBeUndefined()
    expect(btn.text()).toBe('⚡ 重新打分')
  })

  it('renders decision status badge and review reason when present in meta', async () => {
    scoreParseApi.getItems.mockResolvedValue({
      data: {
        items: itemsFixture,
        meta: {
          decisionStatus: 'REVIEW_REQUIRED',
          reviewReason: '权重合计不符合预期',
        },
      },
    })
    scoreParseApi.getReview.mockResolvedValue({
      data: {
        facts: [
          {
            factId: 'fact-1',
            code: 'A1',
            dimension: '技术方案',
            weight: 10,
            sourceText: '总体架构设计',
            evidences: [{ cellId: 'c1', quotedText: '总体架构设计' }],
          },
        ],
        audits: [],
      },
    })
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId: 123 },
      global: {
        stubs: {
          'el-drawer': { template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>', props: ['modelValue'] },
          'el-dialog': true,
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ autoScore: false })
    const badge = wrapper.find('[data-test="score-parse-decision-status"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('待人工复核')
    expect(wrapper.text()).toContain('理由: 权重合计不符合预期')
    expect(wrapper.find('[data-test="score-parse-review-panel"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('人工复核闭环')
    expect(wrapper.text()).not.toContain('事实 1 条')
  })

  it('keeps review audit state internal when retention already exists', async () => {
    scoreParseApi.getItems.mockResolvedValue({
      data: {
        items: itemsFixture,
        meta: { decisionStatus: 'REVIEW_REQUIRED' },
      },
    })
    scoreParseApi.getReview.mockResolvedValue({
      data: { facts: [], audits: [{ action: 'RETAIN_ORIGINAL' }] },
    })

    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId: 123 },
      global: {
        stubs: {
          'el-drawer': { template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>', props: ['modelValue'] },
          'el-dialog': true,
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false })

    expect(scoreParseApi.getReview).toHaveBeenCalledWith(123)
    expect(wrapper.find('[data-test="score-parse-review-panel"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('已保留')
  })

  it('keeps pending review state internal when the review panel is hidden', async () => {
    scoreParseApi.getItems.mockResolvedValue({
      data: { items: itemsFixture, meta: { decisionStatus: 'REVIEW_REQUIRED' } },
    })
    scoreParseApi.getReview.mockResolvedValue({ data: { facts: [], audits: [] } })

    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId: 123 },
      global: {
        stubs: {
          'el-drawer': { template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>', props: ['modelValue'] },
          'el-dialog': true,
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false })

    expect(wrapper.find('[data-test="score-parse-review-panel"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="score-parse-decision-status"]').text()).toBe('待人工复核')
  })

  it('renders candidate review card when scoreItems is empty and candidateFacts exist', async () => {
    scoreParseApi.getItems.mockResolvedValue({
      data: { items: [], meta: { decisionStatus: 'REVIEW_REQUIRED', reviewReason: '待复核' } },
    })
    scoreParseApi.getReview.mockResolvedValue({
      data: {
        facts: [
          { factId: 'f-1', code: '1', dimension: '资质条件', weight: 20, sourceText: '具备资质得20分' },
          { factId: 'f-2', code: '2', dimension: '技术要求', weight: 80, sourceText: '方案完整得80分' },
        ],
        audits: [],
      },
    })

    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId: 123 },
      global: {
        stubs: {
          'el-drawer': { template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>', props: ['modelValue'] },
          'el-dialog': true,
          'el-button': true,
          'el-input': { template: '<span>{{ modelValue }}</span>', props: ['modelValue'] },
        },
      },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false, autoParse: false })

    const candidateCard = wrapper.find('[data-test="score-parse-candidate-review"]')
    expect(candidateCard.exists()).toBe(true)
    expect(candidateCard.text()).toContain('检测到待人工复核的候选评分标准')
    expect(candidateCard.text()).toContain('资质条件')
    expect(candidateCard.text()).toContain('技术要求')
    expect(wrapper.find('[data-test="btn-adopt-candidates"]').exists()).toBe(true)
  })

  it('renders latest pending candidates beside existing formal score items', async () => {
    scoreParseApi.getItems.mockResolvedValue({
      data: { items: itemsFixture, meta: { decisionStatus: 'REVIEW_REQUIRED', reviewReason: '最新解析待确认' } },
    })
    scoreParseApi.getReview.mockResolvedValue({
      data: { facts: [{ factId: 'new-fact', code: 'N1', dimension: '最新待核验项', weight: 7, sourceText: '原文证据' }], audits: [] },
    })
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId: 123 },
      global: { stubs: {
        'el-drawer': { template: '<div v-if="modelValue"><slot /></div>', props: ['modelValue'] },
        'el-dialog': true, 'el-button': true,
      } },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false, autoParse: false })

    expect(wrapper.text()).toContain('2 项')
    expect(wrapper.text()).toContain('已生效评分项 / 最新候选')
    expect(wrapper.find('[data-test="score-parse-candidate-review"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('最新解析待确认')
  })

  it('disables reparse button when scoringOverlayVisible is true', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId: 123 },
      global: {
        stubs: {
          'el-drawer': { template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>', props: ['modelValue'] },
          'el-dialog': true,
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false, autoParse: false })
    const reparseBtn = wrapper.find('[data-test="score-parse-reparse"]')
    expect(reparseBtn.attributes('disabled')).toBeUndefined()

    wrapper.vm.scoringOverlayVisible = true
    await wrapper.vm.$nextTick()
    expect(reparseBtn.attributes('disabled')).toBeDefined()
  })
})

describe('versioned drawer coexistence', () => {
  it('renders failed latest candidates alongside current active table and historical scoring', async () => {
    scoreParseApi.getLatestExtraction.mockResolvedValue({ data: {
      taskId: 'new-failed', taskStatus: 'FAILED', snapshotStatus: 'FROZEN', payloadVersion: 1,
      schemes: [], items: [{ itemId: 'unknown', name: '待核验新候选', schemeId: '', computable: false, diagnostics: ['WEIGHT_UNKNOWN'] }],
      active: { taskId: 'old', fingerprint: 'old-fp' }, adoption: { adoptedItemIds: [], remainingItemIds: ['unknown'] },
    } })
    scoreParseApi.getExtraction.mockResolvedValue({ data: { taskId: 'old', active: { fingerprint: 'old-fp' }, schemes: [] } })
    scoreParseApi.getItems.mockResolvedValue({ data: { items: itemsFixture, parseTime: '2026-09-20T12:00:00', meta: { lastParseStatus: 'FAILED', lastParseError: '新文件失败' } } })
    scoreParseApi.getResults.mockResolvedValue({ data: { results: [{ scoreItemId: 2, status: 'PENDING', actualScore: 5, reuseKind: 'LEGACY_UNVERIFIED' }] } })
    const w = mount(ScoreParseDrawer, { props: { projectId: 123, showExtractionReview: true }, global: { stubs: { 'el-drawer': { template: '<div><slot /></div>' }, 'el-dialog': true, 'el-alert': { props: ['description'], template: '<div>{{ description }}</div>' } } } })
    await w.vm.open({ stage: 2, autoParse: false })
    expect(w.text()).toContain('待核验新候选')
    expect(w.text()).toContain('总体架构设计')
    expect(w.text()).toContain('历史评分，需重新评分')
    expect(w.find('[data-test="activate-extraction"]').attributes('disabled')).toBeDefined()
  })
})

describe('active source time provenance', () => {
  const createDrawer = () => mount(ScoreParseDrawer, { props: { projectId: 123, showExtractionReview: true }, global: { stubs: {
    'el-drawer': { template: '<div><slot /></div>' }, 'el-dialog': true,
  } } })
  const latest = (taskId = null) => ({ taskId: 'new', taskStatus: 'COMPLETED', extractedAt: '2026-09-27T12:00:00',
    snapshotStatus: taskId ? 'FROZEN' : 'LEGACY_UNKNOWN', payloadVersion: taskId ? 1 : 0, schemes: [], items: [],
    active: { taskId, fingerprint: 'fp' }, adoption: { adoptedItemIds: [] } })
  beforeEach(() => {
    vi.resetAllMocks()
    scoreParseApi.getResults.mockResolvedValue({ data: { results: [] } })
    scoreParseApi.getItems.mockResolvedValue({ data: { items: itemsFixture, meta: { parseTime: '2026-09-27T12:00:00', lastParseStatus: 'COMPLETED' } } })
  })
  it('cold opens and reopens legacy active with unknown source time despite a new task timestamp', async () => {
    scoreParseApi.getLatestExtraction.mockResolvedValue({ data: latest() })
    const w = createDrawer()
    for (let attempt = 0; attempt < 2; attempt++) {
      await w.vm.open({ autoParse: false, autoScore: false })
      expect(w.text()).toContain('生效规则来源时间 未知')
      expect(w.text()).toContain('总体架构设计')
      expect(w.text()).toContain('当前使用的是历史版本；最新提取结果尚未切换。')
      w.vm.visible = false
    }
    expect(scoreParseApi.triggerParse).not.toHaveBeenCalled()
    expect(scoreParseApi.triggerScoring).not.toHaveBeenCalled()
  })
  it('keeps known versioned active time separate from latest snapshot time on cold open and reopen', async () => {
    scoreParseApi.getLatestExtraction.mockResolvedValue({ data: latest('old') })
    scoreParseApi.getExtraction.mockResolvedValue({ data: { ...latest('old'), taskId: 'old', schemes: [{ schemeId: 's', activeCount: 2, declaredTotal: 100, declaredTotalStatus: 'KNOWN' }] } })
    scoreParseApi.getItems.mockResolvedValue({ data: { items: itemsFixture, meta: { parseTime: '2026-09-20T12:00:00', lastParseStatus: 'COMPLETED' } } })
    const w = createDrawer()
    for (let attempt = 0; attempt < 2; attempt++) {
      await w.vm.open({ autoParse: false, autoScore: false })
      expect(w.text()).toContain('生效规则来源时间 ' + new Date('2026-09-20T12:00:00').toLocaleString('zh-CN', { hour12: false }))
      expect(w.text()).not.toContain('生效规则来源时间 ' + new Date('2026-09-27T12:00:00').toLocaleString('zh-CN', { hour12: false }))
      w.vm.visible = false
    }
  })

  it('hides extraction review details while retaining the formal score table for customers', async () => {
    scoreParseApi.getLatestExtraction.mockResolvedValue({ data: {
      taskId: 'latest', taskStatus: 'COMPLETED', snapshotStatus: 'FROZEN', payloadVersion: 1,
      schemes: [{ schemeId: 's', extractedCount: 2, extractedKnownTotal: 43, declaredTotal: 100, declaredTotalStatus: 'KNOWN' }],
      items: [{ itemId: 'candidate', name: '内部候选', schemeId: 's', computable: true, effectiveMaximum: 43 }],
      active: { taskId: 'latest', dataStatus: 'MISMATCHED', fingerprint: 'fp' }, adoption: { adoptedItemIds: [] },
    } })
    const w = mount(ScoreParseDrawer, { props: { projectId: 123, showExtractionReview: false, allowExtractionActions: true }, global: { stubs: { 'el-drawer': { template: '<div><slot /></div>' }, 'el-dialog': true } } })
    await w.vm.open({ stage: 1, autoParse: false })
    expect(w.text()).toContain('总体架构设计')
    expect(w.text()).toContain('合计')
    expect(w.text()).not.toContain('内部候选')
    expect(w.text()).not.toContain('本次解析仍有')
    expect(w.find('[data-test="activate-extraction"]').exists()).toBe(false)
    expect(w.find('[data-test="score-parse-reparse"]').exists()).toBe(true)
  })
})
