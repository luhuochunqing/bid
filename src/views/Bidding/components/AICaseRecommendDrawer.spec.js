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
  'el-select': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div class="el-select-stub"><slot /><button class="select-pick-btn" @click="$emit(\'update:modelValue\', 1)" /></div>'
  },
  'el-option': { props: ['label', 'value'], template: '<div class="el-option-stub"><slot /></div>' },
  'el-tooltip': {
    props: ['content', 'disabled', 'popperClass'],
    template: '<span class="el-tooltip-mock" :data-content="content"><slot /></span>'
  },
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
          responseTextSummary: '采用双活高可用部署方案\n保障高可用',
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
    expect(caseRecommendationApi.getScoreItems).toHaveBeenCalledWith(123, { silentError: false })
    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledWith(123, expect.any(Object))

    expect(wrapper.text()).toContain('AI 智能推荐案例')
    expect(wrapper.text()).toContain('系统高可用架构')
    expect(wrapper.text()).toContain('92%')
    expect(wrapper.text()).toContain('优质')
    expect(wrapper.find('.summary-content').html()).toContain('<br>')
    expect(wrapper.find('.summary-content .kw-hl').exists()).toBe(true)
    expect(wrapper.text()).not.toMatch(/次复用/)
    expect(wrapper.text()).not.toMatch(/复用次数/)
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
        stubs: {
          ...commonStubs,
          'el-input': {
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: `<div><input :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" /><slot name="append" /></div>`
          },
          'el-button': { template: '<button type="button"><slot /></button>' }
        }
      }
    })

    await flushPromises()
    await wrapper.get('.keyword-row input').setValue('双活')
    await wrapper.get('.keyword-row button').trigger('click')
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

  it('shows only serial and score item name in options, original text in tooltip and preview', async () => {
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
    await wrapper.find('.select-pick-btn').trigger('click')

    // CSS 宽度规则挂在 .item-score-select .score-item-select 上，DOM 结构回归时在此失败
    expect(wrapper.find('.item-score-select .score-item-select').exists()).toBe(true)

    const optionItem = wrapper.find('.score-option-line')
    expect(optionItem.text()).toBe('A1 技术方案')
    expect(optionItem.text()).not.toContain('系统架构高可用设计')
    expect(wrapper.find('.el-tooltip-mock').attributes('data-content')).toBe('系统架构高可用设计')
    expect(wrapper.find('.score-item-detail-preview').text()).toContain('系统架构高可用设计')
  })

  it('shows evidenceReviewRequired warning and hides empty state when proof-only item returns no cases', async () => {
    caseRecommendationApi.recommendCases.mockResolvedValue({
      total: 0,
      items: [],
      evidenceReviewRequired: true
    })

    const wrapper = mount(AICaseRecommendDrawer, {
      props: { modelValue: true, projectId: 123, defaultScoreItemId: 1 },
      global: { directives: { loading: () => {} }, stubs: commonStubs }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('当前推荐缺少可核验的证明材料，请人工核验原始材料。')
    expect(wrapper.find('.empty-wrap').exists()).toBe(false)
  })

  it('auto-refreshes score items and retries once on 404 stale scoreItemId', async () => {
    const stale404 = Object.assign(new Error('评分项 not found: 739'), { response: { status: 404 } })
    caseRecommendationApi.getScoreItems
      .mockResolvedValueOnce([{ id: 739, code: '2', dim: '商品丰富性', detail: '按 SKU 数量排序' }])
      .mockResolvedValueOnce([{ id: 796, code: '', dim: '商品丰富性', detail: '按 SKU 数量排序' }])
    caseRecommendationApi.recommendCases
      .mockRejectedValueOnce(stale404)
      .mockResolvedValueOnce({ total: 0, items: [], evidenceReviewRequired: true })

    const wrapper = mount(AICaseRecommendDrawer, {
      props: { modelValue: true, projectId: 123, defaultScoreItemId: 739 },
      global: { directives: { loading: () => {} }, stubs: commonStubs }
    })
    await flushPromises()

    expect(caseRecommendationApi.getScoreItems).toHaveBeenCalledTimes(2)
    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledTimes(2)
    expect(caseRecommendationApi.recommendCases).toHaveBeenNthCalledWith(2, 123, expect.objectContaining({ scoreItemId: 796 }))
    expect(wrapper.text()).toContain('当前推荐缺少可核验的证明材料，请人工核验原始材料。')
  })

  it('reports the failed score item refresh after a recommendation 404', async () => {
    caseRecommendationApi.getScoreItems
      .mockResolvedValueOnce([{ id: 739, dim: '服务能力', detail: '配送服务细则' }])
      .mockRejectedValueOnce({ response: { status: 503, data: { msg: '评分项服务暂不可用' } } })
    caseRecommendationApi.recommendCases.mockRejectedValueOnce({
      response: { status: 404, data: { msg: '评分项不存在' } },
    })

    const wrapper = mount(AICaseRecommendDrawer, {
      props: { modelValue: true, projectId: 123, defaultScoreItemId: 739 },
      global: { directives: { loading: () => {} }, stubs: commonStubs }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('评分项服务暂不可用')
    expect(caseRecommendationApi.getScoreItems).toHaveBeenNthCalledWith(2, 123, { silentError: true })
    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledTimes(1)
  })

  it('asks for a new selection when the refreshed list no longer contains the stale item', async () => {
    caseRecommendationApi.getScoreItems
      .mockResolvedValueOnce([{ id: 739, code: '2', dim: '商品丰富性' }])
      .mockResolvedValueOnce([{ id: 789, code: '1', dim: '降价率' }])
    caseRecommendationApi.recommendCases.mockRejectedValueOnce({ response: { status: 404 } })

    const wrapper = mount(AICaseRecommendDrawer, {
      props: { modelValue: true, projectId: 123, defaultScoreItemId: 739 },
      global: { directives: { loading: () => {} }, stubs: commonStubs }
    })
    await flushPromises()

    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('请重新选择评分项')
  })

  it.each([
    ['配送服务细则', '售后服务细则'],
    ['配送服务细则', ''],
    ['—', '—']
  ])('asks for a new selection when a reused code has details %j and %j', async (previousDetail, detail) => {
    caseRecommendationApi.getScoreItems
      .mockResolvedValueOnce([{ id: 739, code: 'item-2', dim: '服务能力', detail: previousDetail }])
      .mockResolvedValueOnce([{ id: 796, code: 'item-2', dim: '服务能力', detail }])
    caseRecommendationApi.recommendCases.mockRejectedValueOnce({ response: { status: 404 } })

    const wrapper = mount(AICaseRecommendDrawer, {
      props: { modelValue: true, projectId: 123, defaultScoreItemId: 739 },
      global: { directives: { loading: () => {} }, stubs: commonStubs }
    })
    await flushPromises()

    expect(caseRecommendationApi.getScoreItems).toHaveBeenCalledTimes(2)
    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('请重新选择评分项')
  })
})
