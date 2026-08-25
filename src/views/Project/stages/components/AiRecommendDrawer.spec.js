import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AiRecommendDrawer from './AiRecommendDrawer.vue'
import { caseRecommendationApi } from '@/api/modules/caseRecommendation'

vi.mock('@/api/modules/caseRecommendation', () => ({
  caseRecommendationApi: {
    getScoreItems: vi.fn(),
    recommendCases: vi.fn(),
    getCaseDetail: vi.fn(),
    reuseCase: vi.fn(),
  },
}))

vi.mock('@/api/error-utils.js', () => ({
  notifyErrorUnlessRateLimit: vi.fn(),
}))

vi.mock('@/api/modules/projectLifecycle', () => ({
  projectLifecycleApi: {
    exportProjectArchive: vi.fn(),
  },
}))

import { projectLifecycleApi } from '@/api/modules/projectLifecycle'

const LONG_DETAIL = '技术方案实施方案完整可行，包含架构设计、部署方案与应急预案等关键要素'

const recommendItem = {
  id: 101,
  scoringPointTitle: '技术方案实施细则',
  responseText: '我们具备成熟的技术方案实施能力',
  responseTextSummary: '我们具备成熟的技术方案实施能力',
  sourceProjectName: '国家电网信息化项目',
  matchScore: 92,
  matchLabel: '优质',
  bidResult: 'WON',
  reuseCount: 3,
  createdAt: '2026-08-01 10:00:00',
  projectType: 'IT',
  customerType: 'GOV',
}

const caseDetailResponse = {
  data: {
    id: 101,
    scoringPointTitle: '技术方案实施细则',
    requirementRaw: '要求技术方案完整可行',
    responseText: '我们具备成熟的技术方案实施能力',
    sourceProjectId: 55,
    sourceProjectName: '国家电网信息化项目',
    ownerUnit: '某省电力公司',
    createdAt: '2026-08-01 10:00:00',
    bidResult: 'WON',
    customerType: 'GOV',
    projectType: 'IT',
  },
}

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
    'el-select': { template: `<div><slot /></div>` },
    'el-option-group': { props: ['label'], template: `<div><slot /></div>` },
    'el-option': { props: ['label', 'value'], template: `<div class="el-option-mock"><slot /></div>` },
    'el-tooltip': { props: ['content'], template: `<span class="el-tooltip-mock"><slot /></span>` },
    'el-input': {
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template: `<input :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />`,
    },
    'el-tag': { template: `<span><slot /></span>` },
    'el-button': { template: `<button @click="$emit('click', $event)"><slot /></button>` },
    'el-empty': { template: `<div class="el-empty-mock" />` },
    'el-alert': { template: `<div class="el-alert-mock"><slot name="title" /></div>` },
    'el-card': { template: `<div class="el-card-mock"><slot /></div>` },
    'el-icon': { template: `<i><slot /></i>` },
    CaseDetailDialog: {
      props: ['modelValue', 'caseData', 'loading'],
      emits: ['update:modelValue', 'reuse', 'go-source', 'open-bid'],
      template: `<div class="detail-dialog-mock" v-if="modelValue">{{ caseData?.ownerUnit }}<button class="mock-open-bid" @click="$emit('open-bid', caseData)">标书原文</button></div>`,
    },
  }

  const mountDrawer = (overrides = {}) =>
    mount(AiRecommendDrawer, {
      props: { projectId: 10, modelValue: true, ...overrides.props },
      global: { stubs: globalStubs },
    })

  const mockOpenWithCases = async (items) => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      { id: 1, code: '3.1', dim: '技术评分', detail: LONG_DETAIL },
    ])
    caseRecommendationApi.recommendCases.mockResolvedValue({ total: items.length, items })
    const wrapper = mountDrawer()
    await flushPromises()
    return wrapper
  }

  it('loads score items and triggers preliminary recommendations on open', async () => {
    const wrapper = await mockOpenWithCases([recommendItem])

    expect(caseRecommendationApi.getScoreItems).toHaveBeenCalledWith(10)
    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledWith(10, {
      scoreItemId: 1,
      keyword: '',
      page: 0,
      size: 20,
    })
    expect(wrapper.text()).toContain('技术方案实施细则')
  })

  it('renders score option full detail text accessible via tooltip', async () => {
    const wrapper = await mockOpenWithCases([recommendItem])

    const optionText = wrapper.find('.el-tooltip-mock').text()
    expect(optionText).toContain(LONG_DETAIL)
    expect(wrapper.text()).not.toContain(`${LONG_DETAIL.slice(0, 24)}...`)
  })

  it('renders matched cases as case-library cards with match degree prefix and created time', async () => {
    const wrapper = await mockOpenWithCases([recommendItem])

    expect(wrapper.find('.recommend-grid .case-card').exists()).toBe(true)
    expect(wrapper.text()).toContain('匹配度')
    expect(wrapper.text()).toContain('92%')
    expect(wrapper.text()).toContain('创建时间：')
    expect(wrapper.text()).not.toMatch(/来源[:：]/)
    expect(wrapper.text()).not.toMatch(/复用\s+\d+\s*次/)
    expect(wrapper.text()).not.toMatch(/复用次数/)
  })

  it('opens case-library detail dialog with full detail fetched by caseId', async () => {
    caseRecommendationApi.getCaseDetail.mockResolvedValue(caseDetailResponse)
    const wrapper = await mockOpenWithCases([recommendItem])

    await wrapper.find('.recommend-grid .case-card').trigger('click')
    await flushPromises()

    expect(caseRecommendationApi.getCaseDetail).toHaveBeenCalledWith(101)
    const detailMock = wrapper.find('.detail-dialog-mock')
    expect(detailMock.exists()).toBe(true)
    expect(detailMock.text()).toContain('某省电力公司')
  })

  it('server-filters by keyword when score item selected and keeps semantic matches without client-side killing', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      { id: 1, code: '3.1', dim: '技术评分', detail: LONG_DETAIL },
    ])
    // 返回项各字段均不含「电网」：若客户端仍叠加过滤会误杀该语义匹配结果，卡片应保留
    caseRecommendationApi.recommendCases.mockResolvedValue({
      total: 1,
      items: [{ ...recommendItem, sourceProjectName: '某省电力公司信息化项目' }],
    })
    const wrapper = mountDrawer()
    await flushPromises()

    await wrapper.find('input').setValue('电网')
    await vi.waitFor(() => {
      expect(caseRecommendationApi.recommendCases).toHaveBeenLastCalledWith(10, {
        scoreItemId: 1,
        keyword: '电网',
        page: 0,
        size: 20,
      })
    })
    await flushPromises()

    expect(wrapper.find('.recommend-grid .case-card').exists()).toBe(true)
  })

  it('downloads bid archive zip from the blob payload (res.data), not the raw response', async () => {
    const blobPayload = new Blob(['zip-bytes'], { type: 'application/zip' })
    projectLifecycleApi.exportProjectArchive.mockResolvedValue({ data: blobPayload })
    const createObjectURL = vi.fn(() => 'blob:mock-url')
    const revokeObjectURL = vi.fn()
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    window.URL.createObjectURL = createObjectURL
    window.URL.revokeObjectURL = revokeObjectURL

    try {
      caseRecommendationApi.getCaseDetail.mockResolvedValue(caseDetailResponse)
      const wrapper = await mockOpenWithCases([recommendItem])

      await wrapper.find('.recommend-grid .case-card').trigger('click')
      await flushPromises()
      await wrapper.find('.mock-open-bid').trigger('click')
      await flushPromises()

      expect(projectLifecycleApi.exportProjectArchive).toHaveBeenCalledWith(55)
      expect(createObjectURL).toHaveBeenCalledWith(blobPayload)
      expect(document.body.textContent).toContain('标书原文下载成功')
    } finally {
      clickSpy.mockRestore()
    }
  })

  it('copies text and calls reuseCase on card reuse button', async () => {
    const wrapper = await mockOpenWithCases([
      { ...recommendItem, id: 202, scoringPointTitle: '售后服务承诺', responseText: '7x24小时响应' },
    ])

    const reuseButtons = wrapper.findAll('button').filter(b => b.text().includes('复用'))
    expect(reuseButtons.length).toBeGreaterThan(0)
    await reuseButtons[0].trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('7x24小时响应')
    expect(caseRecommendationApi.reuseCase).toHaveBeenCalledWith(202)
  })

  it('shows empty-score alert and falls back when getScoreItems fails', async () => {
    caseRecommendationApi.getScoreItems.mockRejectedValue(new Error('500'))
    caseRecommendationApi.recommendCases.mockResolvedValue({ total: 0, items: [] })

    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.find('.el-alert-mock').exists()).toBe(true)
  })
})
