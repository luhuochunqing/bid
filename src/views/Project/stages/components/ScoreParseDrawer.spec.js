import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreParseDrawer from './ScoreParseDrawer.vue'
import { scoreParseApi } from '@/api/modules/scoreParse.js'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessageBox: { confirm: vi.fn() },
    ElMessage: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
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
    scoreParseApi.getItems.mockResolvedValue({ data: { items: itemsFixture, summary: null } })
    scoreParseApi.triggerScoring.mockResolvedValue({ data: { taskId: 't-score', status: 'PENDING' } })
    scoreParseApi.getScoringStatus.mockResolvedValue({ data: { taskId: 't-score', status: 'COMPLETED', progress: 100, completedAt: '2026-08-15T14:30:00' } })
    scoreParseApi.getResults.mockResolvedValue({ data: resultsFixture })
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
})
