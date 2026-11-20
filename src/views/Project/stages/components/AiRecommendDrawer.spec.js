import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AiRecommendDrawer from './AiRecommendDrawer.vue'
import { caseRecommendationApi } from '@/api/modules/caseRecommendation'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import ScoreItemSelect from '@/components/common/ScoreItemSelect.vue'

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
    getArchiveFiles: vi.fn(),
  },
}))

vi.mock('@/api/modules/projectDocuments', () => ({
  getDocuments: vi.fn().mockResolvedValue({ data: [] }),
  getDocumentDownloadUrl: vi.fn(),
}))

// 当前用户角色（标书原文角色闸门用）：默认非 sales，保证既有下载用例不受影响
const { mockUserState } = vi.hoisted(() => ({ mockUserState: { currentUser: { roleCode: 'bid-Team' } } }))
vi.mock('@/stores/user', () => ({
  useUserStore: () => mockUserState,
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
    mockUserState.currentUser = { roleCode: 'bid-Team' }
    document.body.innerHTML = ''
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
    'el-option-group': { props: ['label'], template: `<div class="el-option-group-mock"><slot /></div>` },
    'el-option': { props: ['label', 'value'], template: `<div class="el-option-mock"><slot /></div>` },
    'el-tooltip': {
      props: ['content', 'disabled', 'popperClass'],
      template: `<span class="el-tooltip-mock" :data-content="content"><slot /><slot name="content" /></span>`,
    },
    'el-popover': { template: `<span class="el-popover-mock"><slot name="reference" /><slot /></span>` },
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
    'el-image': { props: ['src'], template: `<img :src="src" class="el-image-mock" />` },
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

  const evidenceResponse = { total: 0, items: [], matchedCertificates: [], evidenceReviewRequired: true }
  const evidenceScoreItem = { id: 739, code: '1', dim: '自营 SKU 数量', detail: '按数量评分，提供系统截图' }

  it('requests manual verification without showing the ordinary case empty state for proof-only items', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([evidenceScoreItem])
    caseRecommendationApi.recommendCases.mockResolvedValue(evidenceResponse)
    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.find('.el-alert-mock').text()).toMatch(/缺少可核验.*人工核验/)
    expect(wrapper.find('.cases-container').exists()).toBe(false)
  })

  it('presents compound clause cases separately as historical references alongside the evidence warning', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      { ...evidenceScoreItem, detail: '按数量评分，提供系统截图；另按实施方案评分' },
    ])
    caseRecommendationApi.recommendCases.mockResolvedValue({
      ...evidenceResponse,
      total: 1,
      items: [recommendItem],
      matchedCertificates: [{ id: 7, name: '质量管理体系认证' }],
    })
    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.find('.el-alert-mock').text()).toMatch(/人工核验/)
    const headings = wrapper.findAll('.section-label-row').map(row => row.text())
    expect(headings).toEqual(expect.arrayContaining([expect.stringContaining('历史案例参考')]))
    expect(wrapper.find('.recommend-grid .case-card').text()).toContain('技术方案实施细则')
    expect(wrapper.find('.cert-recommend-section').text()).toContain('质量管理体系认证')
  })

  it('keeps the latest recommendation when an earlier proof response arrives after switching back to a plan', async () => {
    const planResponse = { total: 1, items: [recommendItem], matchedCertificates: [], evidenceReviewRequired: false }
    let completeProofRequest
    const pendingProof = new Promise(resolve => { completeProofRequest = resolve })
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      { id: 1, code: '3.1', dim: '技术评分', detail: LONG_DETAIL },
      evidenceScoreItem,
    ])
    caseRecommendationApi.recommendCases.mockImplementation(
      (_projectId, { scoreItemId }) => scoreItemId === evidenceScoreItem.id ? pendingProof : Promise.resolve(planResponse),
    )
    const wrapper = mountDrawer()
    await flushPromises()
    const select = wrapper.findComponent(ScoreItemSelect)
    select.vm.$emit('update:modelValue', evidenceScoreItem.id)
    select.vm.$emit('change', evidenceScoreItem.id)
    await wrapper.vm.$nextTick()
    select.vm.$emit('update:modelValue', 1)
    select.vm.$emit('change', 1)
    await flushPromises()
    completeProofRequest(evidenceResponse)
    await flushPromises()

    expect(wrapper.find('.el-alert-mock').exists()).toBe(false)
    expect(wrapper.find('.recommend-grid .case-card').text()).toContain('技术方案实施细则')
  })

  it('loads score items and triggers preliminary recommendations on open', async () => {
    const wrapper = await mockOpenWithCases([recommendItem])

    expect(caseRecommendationApi.getScoreItems).toHaveBeenCalledWith(10, { silentError: false })
    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledWith(10, {
      scoreItemId: 1,
      keyword: '',
      page: 0,
      size: 20,
    })
    expect(wrapper.text()).toContain('技术方案实施细则')
    expect(wrapper.find('.section-label-row').text()).toContain('历史案例参考')
  })

  it('shows only serial and score item name in options, original text in tooltip and preview', async () => {
    const wrapper = await mockOpenWithCases([recommendItem])

    const optionLine = wrapper.find('.score-option-line')
    expect(optionLine.text()).toBe('3.1 技术评分')
    expect(optionLine.text()).not.toContain(LONG_DETAIL)

    const optionTooltip = optionLine.element.closest('.el-tooltip-mock')
    expect(optionTooltip).not.toBeNull()
    expect(optionTooltip.getAttribute('data-content')).toBe(LONG_DETAIL)

    expect(wrapper.findAll('.el-option-group-mock')).toHaveLength(0)
    expect(wrapper.find('.score-item-detail-preview').text()).toContain(LONG_DETAIL)
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
    // 预检：档案有附件才走导出下载（无附件时应提示“原文未归档”并中止）
    projectLifecycleApi.getArchiveFiles.mockResolvedValue({ data: { totalElements: 2 } })
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

      expect(projectLifecycleApi.getArchiveFiles).toHaveBeenCalledWith(55, 1)
      expect(projectLifecycleApi.exportProjectArchive).toHaveBeenCalledWith(55)
      // triggerBlobDownload 会基于 res.data 重新包装 Blob，断言收到 ZIP 类型 Blob 而非原始 AxiosResponse
      expect(createObjectURL).toHaveBeenCalledTimes(1)
      const blobArg = createObjectURL.mock.calls[0][0]
      expect(blobArg).toBeInstanceOf(Blob)
      expect(blobArg.type).toBe('application/zip')
      expect(document.body.textContent).toContain('标书原文下载成功')
    } finally {
      clickSpy.mockRestore()
    }
  })

  it('bid archive without files warns 原文未归档 and skips export download', async () => {
    projectLifecycleApi.getArchiveFiles.mockResolvedValue({ data: { totalElements: 0 } })
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    try {
      caseRecommendationApi.getCaseDetail.mockResolvedValue(caseDetailResponse)
      const wrapper = await mockOpenWithCases([recommendItem])

      await wrapper.find('.recommend-grid .case-card').trigger('click')
      await flushPromises()
      await wrapper.find('.mock-open-bid').trigger('click')
      await flushPromises()

      expect(projectLifecycleApi.exportProjectArchive).not.toHaveBeenCalled()
      expect(clickSpy).not.toHaveBeenCalled()
      expect(document.body.textContent).toContain('标书原文未归档')
    } finally {
      clickSpy.mockRestore()
    }
  })

  it('blocks bid-projectLeader from downloading bid source: shows 无权限下载 and calls no download API', async () => {
    mockUserState.currentUser = { roleCode: 'bid-projectLeader' }
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    try {
      caseRecommendationApi.getCaseDetail.mockResolvedValue(caseDetailResponse)
      const wrapper = await mockOpenWithCases([recommendItem])

      await wrapper.find('.recommend-grid .case-card').trigger('click')
      await flushPromises()
      await wrapper.find('.mock-open-bid').trigger('click')
      await flushPromises()

      expect(projectLifecycleApi.getArchiveFiles).not.toHaveBeenCalled()
      expect(projectLifecycleApi.exportProjectArchive).not.toHaveBeenCalled()
      expect(clickSpy).not.toHaveBeenCalled()
      expect(document.body.textContent).toContain('无权限下载')
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

  it('renders scoring rule explanation tooltip trigger and content', async () => {
    const wrapper = await mockOpenWithCases([recommendItem])

    expect(wrapper.find('.help-hint').exists()).toBe(true)
    expect(wrapper.text()).toContain('评分依据说明')
    expect(wrapper.text()).toContain('匹配度计算规则（满分 100 分）')
    expect(wrapper.text()).toContain('业务子主题深度对齐：最高 25 分')
    expect(wrapper.text()).toContain('评分条款深度契合：最高 25 分')
    expect(wrapper.text()).toContain('专属客户/项目实体：最高 20 分')
    expect(wrapper.text()).toContain('评分项标题核心匹配：最高 20 分')
    expect(wrapper.text()).toContain('评分项类别一致：最高 10 分')
  })

  it('renders the evidence warning when the qualification lookup has no usable certificate', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue({
      data: [
        { id: 301, code: '1', dim: '1 管理体系', detail: '具有 ISO9001 质量管理体系认证、ISO14001环境管理体系认证，提供证书复印件' }
      ]
    })
    caseRecommendationApi.recommendCases.mockResolvedValue({ total: 0, items: [], matchedCertificates: [], evidenceReviewRequired: true })

    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.find('.qualification-notice').exists()).toBe(false)
    expect(wrapper.text()).toContain('请人工核验原始材料')
    expect(wrapper.find('.recommend-grid .case-card').exists()).toBe(false)
  })

  it('keeps valid recommendations below 50 and displays the server total', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      { id: 401, code: 'BIZ_FIN', dim: '经营及财务状况', detail: '根据经营及财务状况综合评分' },
    ])
    caseRecommendationApi.recommendCases.mockResolvedValue({
      total: 165,
      items: [
        { ...recommendItem, id: 4011, scoringPointTitle: '经营能力案例', matchScore: 32 },
        { ...recommendItem, id: 4012, scoringPointTitle: '财务状况案例', matchScore: 37 },
      ],
    })

    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.findAll('.recommend-grid .case-card')).toHaveLength(2)
    expect(wrapper.text()).toContain('32%')
    expect(wrapper.text()).toContain('37%')
    expect(wrapper.text()).toContain('165 个结果')
  })

  it('does not treat generic certification wording in a quality plan as a certificate item', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      {
        id: 402,
        code: 'TECH_QUAL',
        dim: '质量管控',
        detail: '要求提供产品质量管控方案，涉及强制认证产品质量管理',
      },
    ])
    caseRecommendationApi.recommendCases.mockResolvedValue({ total: 81, items: [recommendItem] })

    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.find('.qualification-notice').exists()).toBe(false)
    expect(wrapper.find('.recommend-grid .case-card').exists()).toBe(true)
  })

  it('renders enterprise matched certificates with scanned images and enables info/url copy', async () => {
    const mockCert = {
      id: 88,
      name: '质量管理体系认证证书（ISO 9001）',
      certificateNo: '00121Q31234R0M',
      issuer: '中国质量认证中心',
      expiryDate: '2028-12-31',
      status: 'VALID',
      certScope: '工业品供应链集成管理服务',
      fileUrl: 'https://oss.ehsy.com/certs/iso9001.jpg',
      matchScore: 95,
      matchReasons: ['资质证书名称精准匹配', '证书在有效期内'],
    }

    caseRecommendationApi.getScoreItems.mockResolvedValue([
      {
        id: 501,
        code: 'Q_1',
        dim: '企业资质',
        detail: '具有 ISO 9001 质量管理体系认证证书，并在有效期内',
      },
    ])
    caseRecommendationApi.recommendCases.mockResolvedValue({
      total: 1,
      items: [],
      matchedCertificates: [mockCert],
    })

    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.find('.cert-recommend-section').exists()).toBe(true)
    expect(wrapper.text()).toContain('质量管理体系认证证书（ISO 9001）')
    expect(wrapper.text()).toContain('00121Q31234R0M')
    expect(wrapper.text()).toContain('中国质量认证中心')
    expect(wrapper.find('.cert-thumbnail').exists()).toBe(true)

    // 测试复制证书信息
    const copyInfoBtn = wrapper.findAll('button').find(b => b.text().includes('复制证书信息'))
    expect(copyInfoBtn).toBeDefined()
    await copyInfoBtn.trigger('click')
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
      expect.stringContaining('质量管理体系认证证书（ISO 9001）')
    )

    // 测试复制图片链接
    const copyUrlBtn = wrapper.findAll('button').find(b => b.text().includes('复制图片链接'))
    expect(copyUrlBtn).toBeDefined()
    await copyUrlBtn.trigger('click')
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('https://oss.ehsy.com/certs/iso9001.jpg')
  })

  it('keeps plan references visible when its requirement mentions a test report', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      { id: 1441, code: '44', dim: '系统安全方案', detail: '提供安全实施方案并附测评报告' },
    ])
    caseRecommendationApi.recommendCases.mockResolvedValue({ total: 1, items: [recommendItem] })

    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.find('.qualification-notice').exists()).toBe(false)
    expect(wrapper.find('.recommend-grid .case-card').exists()).toBe(true)
  })

  it('recognizes 等保备案证明 and 第三级及以上等级保护测评 as qualification item when certs match', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([
      { id: 1440, code: '43', dim: '等保备案证明', detail: '第三级及以上等级保护测评' },
    ])
    caseRecommendationApi.recommendCases.mockResolvedValue({
      total: 1,
      items: [],
      evidenceReviewRequired: false,
      matchedCertificates: [{ id: 30, name: '信息系统安全等级保护备案证明(三级)' }],
    })

    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.find('.qualification-notice').exists()).toBe(true)
    expect(wrapper.find('.qualification-notice').text()).toContain('该评分项为资质证书类要求')
    expect(wrapper.find('.cert-recommend-section').text()).toContain('信息系统安全等级保护备案证明(三级)')
    expect(wrapper.text()).not.toContain('历史案例参考')
  })

  it('auto-refreshes score items and retries once when recommend returns 404 for a stale scoreItemId', async () => {
    const stale404 = Object.assign(new Error('评分项 not found: 739'), { response: { status: 404 } })
    caseRecommendationApi.getScoreItems
      .mockResolvedValueOnce([{ id: 739, code: 'item-2', dim: '服务能力', detail: '配送服务细则' }])
      .mockResolvedValueOnce([
        { id: 789, code: 'item-2', dim: '服务能力', detail: '售后服务细则' },
        { id: 796, code: 'item-3', dim: '服务能力', detail: '配送服务细则' },
      ])
    caseRecommendationApi.recommendCases
      .mockRejectedValueOnce(stale404)
      .mockResolvedValueOnce(evidenceResponse)

    const wrapper = mountDrawer()
    await flushPromises()

    expect(caseRecommendationApi.getScoreItems).toHaveBeenCalledTimes(2)
    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledTimes(2)
    expect(caseRecommendationApi.recommendCases).toHaveBeenNthCalledWith(1, 10, expect.objectContaining({ scoreItemId: 739 }))
    expect(caseRecommendationApi.recommendCases).toHaveBeenNthCalledWith(2, 10, expect.objectContaining({ scoreItemId: 796 }))
    expect(wrapper.find('.el-alert-mock').text()).toMatch(/缺少可核验.*人工核验/)
    expect(wrapper.find('.cases-container').exists()).toBe(false)
  })

  it('reports a failed 404 recovery in the drawer without a duplicate toast', async () => {
    caseRecommendationApi.getScoreItems
      .mockResolvedValueOnce([{ id: 739, dim: '服务能力', detail: '配送服务细则' }])
      .mockRejectedValueOnce({ response: { status: 503, data: { msg: '评分项服务暂不可用' } } })
    caseRecommendationApi.recommendCases.mockRejectedValueOnce({
      response: { status: 404, data: { msg: '评分项不存在' } },
    })

    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.get('.recommend-error-alert').text()).toContain('评分项服务暂不可用')
    expect(caseRecommendationApi.getScoreItems).toHaveBeenNthCalledWith(2, 10, { silentError: true })
    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledTimes(1)
    expect(notifyErrorUnlessRateLimit).not.toHaveBeenCalled()
    expect(wrapper.find('.cases-container').exists()).toBe(false)
  })

  it('renders explicit error alert instead of fake 0-result empty state on non-404 failure', async () => {
    caseRecommendationApi.getScoreItems.mockResolvedValue([evidenceScoreItem])
    caseRecommendationApi.recommendCases.mockRejectedValue(
      Object.assign(new Error('Request failed with status code 503'), { response: { status: 503, data: { msg: '服务暂不可用' } } }),
    )

    const wrapper = mountDrawer()
    await flushPromises()

    expect(wrapper.find('.recommend-error-alert').exists()).toBe(true)
    expect(wrapper.find('.recommend-error-alert').text()).toContain('服务暂不可用')
    expect(wrapper.find('.cases-container').exists()).toBe(false)
  })

  it('asks for a new selection when multiple refreshed items match the stale item', async () => {
    caseRecommendationApi.getScoreItems
      .mockResolvedValueOnce([{ id: 739, code: '2', dim: '商品丰富性', detail: '按 SKU 数量排序' }])
      .mockResolvedValueOnce([
        { id: 796, code: '2', dim: '商品丰富性', detail: '按 SKU 数量排序' },
        { id: 797, code: '3', dim: '商品丰富性', detail: '按 SKU 数量排序' },
      ])
    caseRecommendationApi.recommendCases.mockRejectedValueOnce({ response: { status: 404 } })

    const wrapper = mountDrawer()
    await flushPromises()

    expect(caseRecommendationApi.recommendCases).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.recommend-error-alert').text()).toContain('请重新选择评分项')
  })

  it.each([
    ['before', true],
    ['after', false],
  ])('requires reselection when keyword debounce expires %s the 404 refresh completes', async (_, timerFirst) => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const previousItem = { id: 739, code: 'item-2', dim: '服务能力', detail: '配送服务细则' }
    const nextItem = { ...previousItem, id: 789, detail: '售后服务细则' }
    let finishRefresh
    const refreshedItems = new Promise(resolve => { finishRefresh = resolve })
    caseRecommendationApi.getScoreItems
      .mockResolvedValueOnce([previousItem])
      .mockReturnValueOnce(refreshedItems)
    caseRecommendationApi.recommendCases
      .mockRejectedValueOnce({ response: { status: 404 } })
      .mockResolvedValue({ total: 1, items: [recommendItem] })

    const wrapper = mountDrawer()
    try {
      await flushPromises()
      expect(caseRecommendationApi.getScoreItems).toHaveBeenCalledTimes(2)
      await wrapper.get('input').setValue('电网')

      if (timerFirst) await vi.advanceTimersByTimeAsync(300)
      finishRefresh([nextItem])
      await flushPromises()
      if (!timerFirst) await vi.advanceTimersByTimeAsync(300)

      expect(caseRecommendationApi.recommendCases).toHaveBeenCalledTimes(1)
      expect(wrapper.get('.recommend-error-alert').text()).toContain('请重新选择评分项')
      expect(wrapper.find('.cases-container').exists()).toBe(false)

      const select = wrapper.getComponent(ScoreItemSelect)
      select.vm.$emit('update:modelValue', nextItem.id)
      select.vm.$emit('change', nextItem.id)
      await flushPromises()

      expect(caseRecommendationApi.recommendCases).toHaveBeenCalledTimes(2)
      expect(caseRecommendationApi.recommendCases).toHaveBeenLastCalledWith(10, {
        scoreItemId: nextItem.id, keyword: '电网', page: 0, size: 20,
      })
      expect(wrapper.find('.recommend-error-alert').exists()).toBe(false)
    } finally {
      wrapper.unmount()
      vi.useRealTimers()
    }
  })

})

