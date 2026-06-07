import { defineComponent, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const routerPush = vi.fn()
const routerBack = vi.fn()
const routeState = {
  params: { id: 'T001', fromList: true },
}

const getDetail = vi.fn()
const getAnalysis = vi.fn()
const getLatestScore = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush, back: routerBack }),
  useRoute: () => routeState,
}))

vi.mock('@/api', () => ({
  tendersApi: { getDetail },
  aiApi: { bid: { getAnalysis } },
  bidMatchScoringApi: { getLatestScore },
}))

const elMessage = {
  info: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}

vi.mock('element-plus', () => ({
  ElMessage: elMessage,
  ElMessageBox: {
    confirm: vi.fn(() => Promise.resolve()),
  },
}))

const { useAiAnalysisPage } = await import('./useAiAnalysisPage.js')

function createHarness() {
  return defineComponent({
    template: '<div />',
    setup() {
      return useAiAnalysisPage()
    },
  })
}

describe('useAiAnalysisPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.params = { id: 'T001', fromList: true }
    getDetail.mockResolvedValue({ success: true, data: { id: 'T001', title: '测试标讯' } })
    getAnalysis.mockResolvedValue({
      success: true,
      data: {
        winScore: 88,
        suggestion: '建议跟进',
        dimensionScores: [{ name: '动态分析维度', score: 80, description: '动态说明' }],
        risks: [],
        autoTasks: [],
      },
    })
    getLatestScore.mockResolvedValue({
      success: true,
      data: {
        id: 'S001',
        tenderId: 'T001',
        totalScore: 82,
        status: 'READY',
        modelVersion: '2026.04',
        dimensions: [{ key: 'budgetFit', name: '预算匹配', score: 82, weight: 100 }],
      },
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads tender info and ai analysis on mounted', async () => {
    const wrapper = mount(createHarness())
    await flushPromises()

    expect(getDetail).toHaveBeenCalledWith('T001')
    expect(getAnalysis).toHaveBeenCalledWith('T001')
    expect(getLatestScore).toHaveBeenCalledWith('T001')
    expect(wrapper.vm.tenderInfo.title).toBe('测试标讯')
    expect(wrapper.vm.analysisData.winScore).toBe(88)
    expect(wrapper.vm.matchScoreForDisplay.totalScore).toBe(82)
    expect(wrapper.vm.matchScoreForDisplay.dimensionSummaries[0].name).toBe('预算匹配')
  })

  it('reports backend error when analysis request fails', async () => {
    getAnalysis.mockResolvedValue({ success: false, msg: 'AI服务异常' })
    const wrapper = mount(createHarness())
    await flushPromises()

    expect(wrapper.vm.analysisData).toBe(null)
    expect(wrapper.vm.loadError).toBe('加载失败：AI服务异常')
    expect(elMessage.error).toHaveBeenCalledWith({
      message: '加载失败：AI服务异常',
      duration: 10000,
      showClose: true,
    })
  })

  it('does not present AI analysis dimensions as real match score when latest score is empty', async () => {
    getLatestScore.mockResolvedValue({ success: true, data: null })
    const wrapper = mount(createHarness())
    await flushPromises()

    expect(wrapper.vm.matchScoreForDisplay).toBe(null)
    expect(wrapper.vm.dimensionDetails).toEqual([])
  })

  it('clears parsing timer on unmount when parsing animation is enabled', async () => {
    vi.useFakeTimers()
    routeState.params = { id: 'T001' }
    const clearIntervalSpy = vi.spyOn(globalThis, 'clearInterval')

    const wrapper = mount(createHarness())
    await nextTick()
    expect(wrapper.vm.showParsingDialog).toBe(true)

    wrapper.unmount()
    expect(clearIntervalSpy).toHaveBeenCalled()
  })
})
