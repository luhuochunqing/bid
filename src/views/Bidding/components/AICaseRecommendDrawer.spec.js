import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import AICaseRecommendDrawer from './AICaseRecommendDrawer.vue'
import { caseRecommendationApi } from '@/api/modules/caseRecommendation'

vi.mock('@/api/modules/caseRecommendation', () => ({
  caseRecommendationApi: {
    getScoreItems: vi.fn(),
    recommendCases: vi.fn(),
    getCaseDetail: vi.fn(),
    reuseCase: vi.fn()
  }
}))

const commonStubs = {
  'el-drawer': {
    template: '<div class="el-drawer-stub"><slot name="header"/><slot/></div>'
  },
  'el-alert': {
    template: '<div class="el-alert-stub"><slot name="title"/><slot/></div>'
  },
  'el-descriptions': true,
  'el-descriptions-item': true,
  'el-badge': true,
  'el-select': true,
  'el-option': true,
  'el-input': true,
  'el-button': true,
  'el-dialog': true,
  'el-tag': true,
  'el-radio-group': true,
  'el-radio-button': true,
  'el-cascader': true,
  'el-form': true,
  'el-form-item': true,
  'el-divider': true
}

describe('AICaseRecommendDrawer.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      { id: 1, code: 'A1', dim: '技术方案', detail: '系统架构高可用设计', targetChapter: '第3章 技术方案' }
    ])
    caseRecommendationApi.recommendCases.mockResolvedValue({
      total: 1,
      items: [
        {
          id: 101,
          scoringPointTitle: '系统高可用架构',
          scoringCategory: '技术方案',
          matchScore: 92,
          matchLabel: '优质',
          matchReasons: ['评分项条款高度契合'],
          matchedKeywords: ['高可用', '架构'],
          requirementRaw: '提供高可用部署方案',
          responseText: '采用双活高可用部署方案',
          responseTextSummary: '采用双活高可用部署方案',
          sourceProjectName: '测试项目A',
          bidResult: 'WON',
          reuseCount: 3,
          isPinned: false
        }
      ]
    })
  })

  it('renders drawer and loads recommendations when visible', async () => {
    const wrapper = mount(AICaseRecommendDrawer, {
      props: {
        modelValue: true,
        projectId: 123
      },
      global: {
        directives: {
          loading: () => {}
        },
        stubs: commonStubs
      }
    })

    await flushPromises()
    expect(caseRecommendationApi.getScoreItems).toHaveBeenCalledWith(123)
    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledWith(123, expect.any(Object))

    expect(wrapper.text()).toContain('AI 智能推荐案例')
    expect(wrapper.text()).toContain('系统高可用架构')
    expect(wrapper.text()).toContain('92%')
    expect(wrapper.text()).toContain('优质')
    expect(wrapper.text()).toContain('3 次复用')
  })

  it('triggers search when keyword changes and search button is clicked', async () => {
    const wrapper = mount(AICaseRecommendDrawer, {
      props: {
        modelValue: true,
        projectId: 123
      },
      global: {
        directives: {
          loading: () => {}
        },
        stubs: commonStubs
      }
    })

    await flushPromises()
    wrapper.vm.searchKeyword = '双活'
    await wrapper.vm.handleSearch()
    await flushPromises()

    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledWith(123, {
      scoreItemId: null,
      keyword: '双活',
      page: 0,
      size: 20
    })
  })

  it('handles reuse event and increments local reuse count', async () => {
    const wrapper = mount(AICaseRecommendDrawer, {
      props: {
        modelValue: true,
        projectId: 123
      },
      global: {
        directives: {
          loading: () => {}
        },
        stubs: commonStubs
      }
    })

    await flushPromises()
    const caseItem = wrapper.vm.recommendedCases[0]
    expect(caseItem).toBeDefined()
    wrapper.vm.activeCase = caseItem
    wrapper.vm.handleReuseSuccess({
      caseData: caseItem,
      targetChapter: '第3章 技术方案',
      reuseMode: 'APPEND'
    })

    expect(caseItem.reuseCount).toBe(4)
    expect(wrapper.emitted('case-reused')).toBeTruthy()
  })

  // PRD 4.2：复用定位弹窗默认选中当前评分项对应章节
  it('maps selected score item to default chapter via targetChapter then dim fallback', async () => {
    const wrapper = mount(AICaseRecommendDrawer, {
      props: {
        modelValue: true,
        projectId: 123
      },
      global: {
        directives: {
          loading: () => {}
        },
        stubs: commonStubs
      }
    })

    await flushPromises()
    wrapper.vm.selectedScoreItemId = 1
    // 显式 targetChapter 优先
    expect(wrapper.vm.currentDefaultChapter).toBe('第3章 技术方案')

    // 无 targetChapter 时按 dim 类别回退到内置章节树
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      { id: 2, code: 'B1', dim: '商务评审', detail: '资质业绩' }
    ])
    wrapper.vm.scoreItems = [{ id: 2, code: 'B1', dim: '商务评审', detail: '资质业绩' }]
    wrapper.vm.selectedScoreItemId = 2
    expect(wrapper.vm.currentDefaultChapter).toBe('第1章 商务标响应')

    // 未知类别 → 空，由用户手选
    wrapper.vm.scoreItems = [{ id: 3, code: 'C1', dim: '未知维度', detail: '' }]
    wrapper.vm.selectedScoreItemId = 3
    expect(wrapper.vm.currentDefaultChapter).toBe('')
  })
})
