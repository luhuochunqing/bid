import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AiRecommendDrawer from './AiRecommendDrawer.vue'
import { caseRecommendationApi } from '@/api/modules/caseRecommendation'

vi.mock('@/api/modules/caseRecommendation', () => ({
  caseRecommendationApi: {
    getScoreItems: vi.fn(),
    recommendCases: vi.fn(),
    reuseCase: vi.fn(),
  },
}))

vi.mock('@/api/error-utils.js', () => ({
  notifyErrorUnlessRateLimit: vi.fn(),
}))

describe('AiRecommendDrawer.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    navigator.clipboard = {
      writeText: vi.fn().mockResolvedValue(undefined),
    }
  })

  const globalStubs = {
    'el-drawer': {
      props: ['modelValue', 'title'],
      emits: ['open'],
      template: `<div class="el-drawer-mock" v-if="modelValue"><slot /></div>`,
      mounted() {
        if (this.modelValue) this.$emit('open')
      },
    },
    'el-dialog': {
      props: ['modelValue'],
      template: `<div class="el-dialog-mock" v-if="modelValue"><slot /><slot name="footer" /></div>`,
    },
    'el-select': { template: `<div><slot /></div>` },
    'el-option-group': { template: `<div><slot /></div>` },
    'el-option': { template: `<div />` },
    'el-input': { template: `<input />` },
    'el-tag': { template: `<span><slot /></span>` },
    'el-button': { template: `<button @click="$emit('click')"><slot /></button>` },
    'el-empty': { template: `<div class="el-empty-mock" />` },
    'el-alert': { template: `<div class="el-alert-mock"><slot name="title" /></div>` },
  }

  it('loads score items and triggers preliminary recommendations on open', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      { id: 1, code: '3.1', dim: '技术评分', detail: '技术方案实施方案完整可行' },
    ])
    caseRecommendationApi.recommendCases.mockResolvedValue({
      total: 1,
      items: [
        {
          id: 101,
          scoringPointTitle: '技术方案实施细则',
          responseText: '我们具备成熟的技术方案实施能力',
          sourceProjectName: '国家电网信息化项目',
          matchScore: 92,
          matchLabel: '优质',
          bidResult: 'WON',
          reuseCount: 3,
        },
      ],
    })

    const wrapper = mount(AiRecommendDrawer, {
      props: {
        projectId: 10,
        modelValue: true,
      },
      global: { stubs: globalStubs },
    })

    await flushPromises()

    expect(caseRecommendationApi.getScoreItems).toHaveBeenCalledWith(10)
    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledWith(10, {
      scoreItemId: 1,
      keyword: '',
      page: 0,
      size: 20,
    })
    expect(wrapper.text()).toContain('技术方案实施细则')
    expect(wrapper.text()).toContain('国家电网信息化项目')
    expect(wrapper.text()).not.toMatch(/复用\s+\d+\s*次/)
    expect(wrapper.text()).not.toMatch(/复用次数/)
  })

  it('copies text and calls reuseCase on handleReuse', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([])
    caseRecommendationApi.recommendCases.mockResolvedValue({
      total: 1,
      items: [
        {
          id: 202,
          scoringPointTitle: '售后服务承诺',
          responseText: '7x24小时响应',
          sourceProjectName: '政务云运维项目',
          matchScore: 88,
          matchLabel: '优质',
          reuseCount: 1,
        },
      ],
    })

    const wrapper = mount(AiRecommendDrawer, {
      props: {
        projectId: 10,
        modelValue: true,
      },
      global: { stubs: globalStubs },
    })

    await flushPromises()

    const reuseButtons = wrapper.findAll('button').filter(b => b.text().includes('复用'))
    expect(reuseButtons.length).toBeGreaterThan(0)
    await reuseButtons[0].trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('7x24小时响应')
    expect(caseRecommendationApi.reuseCase).toHaveBeenCalledWith(202)
  })

  it('shows empty-score alert and falls back when getScoreItems fails', async () => {
    caseRecommendationApi.getScoreItems.mockRejectedValue(new Error('500'))
    caseRecommendationApi.recommendCases.mockResolvedValue({ total: 0, items: [] })

    const wrapper = mount(AiRecommendDrawer, {
      props: {
        projectId: 10,
        modelValue: true,
      },
      global: { stubs: globalStubs },
    })

    await flushPromises()

    expect(wrapper.find('.el-alert-mock').exists()).toBe(true)
  })
})
