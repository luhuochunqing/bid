// Input: useInitiationStageActions.submit() 校验逻辑
// Output: CO-540 — needDeposit=YES 时，保证金缴纳截止日期必填且不能早于当前日期
// Pos: src/views/Project/stages/ - 立项阶段 actions 单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { reactive, ref } from 'vue'

vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { prompt: vi.fn() },
}))

import { ElMessage } from 'element-plus'
import { useInitiationStageActions } from './useInitiationStageActions.js'

// 构造最小可用的 projectsState，仅含 submit 路径用到的响应式引用
function createState() {
  return {
    existing: ref(false),
    saving: ref(false),
    submitting: ref(false),
    approving: ref(false),
    rejecting: ref(false),
    uploadingDoc: ref(false),
    errorMsg: ref(''),
    reviewStatus: ref(''),
    fieldLocked: ref(false),
    approvalForm: reactive({ biddingLeaderId: null, biddingAssistantId: null }),
    evalPrefilled: ref(false),
  }
}

function makeForm(overrides = {}) {
  return reactive({
    projectName: '测试项目',
    ownerUnit: '测试单位',
    tenderDocumentId: 1,
    needDeposit: 'NO',
    depositAmount: 0,
    depositPaymentMethod: '',
    depositDueDate: null,
    ...overrides,
  })
}

function setup(formOverrides = {}) {
  const form = makeForm(formOverrides)
  const projectLifecycleApi = {
    submitInitiation: vi.fn().mockResolvedValue({ data: { projectId: 1 } }),
    updateInitiation: vi.fn().mockResolvedValue({ data: { projectId: 1 } }),
    getInitiation: vi.fn(),
  }
  const projectsApi = { getDetail: vi.fn(), getDocuments: vi.fn() }
  const tendersApi = { getDetail: vi.fn(), getEvaluation: vi.fn() }
  const actions = useInitiationStageActions({
    props: { projectId: 1 },
    emit: vi.fn(),
    form,
    custFixedRows: ref([]),
    bidDocFiles: ref([]),
    planGapFiles: ref([]),
    userStore: { currentUser: { id: 1 }, userName: '张三' },
    projectLifecycleApi,
    projectsApi,
    tendersApi,
    usersApi: { getByIds: vi.fn() },
    projectsState: createState(),
    leaderOptions: ref([]),
    assistantOptions: ref([]),
  })
  return { actions, form, projectLifecycleApi, projectsApi, tendersApi }
}

describe('useInitiationStageActions.submit — CO-540 保证金缴纳截止日期校验', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('needDeposit=NO 时不校验缴纳截止日期，放行提交', async () => {
    const { actions, projectLifecycleApi } = setup({ needDeposit: 'NO' })
    await actions.submit()
    expect(projectLifecycleApi.submitInitiation).toHaveBeenCalled()
  })

  it('needDeposit=YES 且缺少缴纳方式时拦截（回归保护）', async () => {
    const { actions, projectLifecycleApi } = setup({
      needDeposit: 'YES',
      depositPaymentMethod: '',
      depositDueDate: null,
    })
    await actions.submit()
    expect(projectLifecycleApi.submitInitiation).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('请选择保证金缴纳方式')
  })

  // CO-540: 缴纳截止日期必填
  it('needDeposit=YES 但未选缴纳截止日期时拦截', async () => {
    const { actions, projectLifecycleApi } = setup({
      needDeposit: 'YES',
      depositPaymentMethod: 'WIRE',
      depositDueDate: null,
    })
    await actions.submit()
    expect(projectLifecycleApi.submitInitiation).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('请选择保证金缴纳截止日期')
  })

  // CO-540: 缴纳截止日期不能早于当前日期
  it('needDeposit=YES 且缴纳截止日期早于当前日期时拦截', async () => {
    const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000)
      .toISOString()
      .slice(0, 19)
    const { actions, projectLifecycleApi } = setup({
      needDeposit: 'YES',
      depositPaymentMethod: 'WIRE',
      depositDueDate: yesterday,
    })
    await actions.submit()
    expect(projectLifecycleApi.submitInitiation).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('保证金缴纳截止日期不能早于当前日期')
  })

  // CO-540: 合法（未来）缴纳截止日期放行
  it('needDeposit=YES 且缴纳截止日期为未来时放行提交', async () => {
    const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000)
      .toISOString()
      .slice(0, 19)
    const { actions, projectLifecycleApi } = setup({
      needDeposit: 'YES',
      depositPaymentMethod: 'WIRE',
      depositDueDate: tomorrow,
    })
    await actions.submit()
    expect(projectLifecycleApi.submitInitiation).toHaveBeenCalledWith(1, expect.any(Object))
  })
})

describe('useInitiationStageActions.load — 客户营收字段回填（回归 d1994a3fa）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 后端 InitiationViewDto 同时返回 annualRevenue（@Deprecated，向后兼容）
  // 和 customerRevenue（新字段，值同 annualRevenue）。
  // 前端 form.customerRevenue 直接消费 data.customerRevenue，无需 hack 映射。
  // 详情见 d1994a3fa 回归教训（lessons §74）。

  it('data.customerRevenue 有值时应直接回填到 form.customerRevenue（覆盖初始 null）', async () => {
    const { actions, form, projectLifecycleApi } = setup({
      ownerUnit: '测试单位',
      customerType: 'CENTRAL_SOE',
      customerRevenue: null,
    })
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: {
        projectId: 1,
        ownerUnit: '测试单位',
        customerType: 'CENTRAL_SOE',
        annualRevenue: 12.5,
        customerRevenue: 12.5,
      },
    })

    await actions.load()

    // 关键回归断言：form.customerRevenue 必须被后端值覆盖，不能停留在初始空值
    expect(form.customerRevenue).toBe(12.5)
    expect(form.annualRevenue).toBe(12.5)
  })

  it('data.customerRevenue 为 null 时 form.customerRevenue 回退为 null（覆盖初始值）', async () => {
    const { actions, form, projectLifecycleApi } = setup({
      ownerUnit: '测试单位',
      customerType: 'CENTRAL_SOE',
    })
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: {
        projectId: 1,
        ownerUnit: '测试单位',
        customerType: 'CENTRAL_SOE',
        annualRevenue: null,
        customerRevenue: null,
      },
    })

    await actions.load()

    // Object.assign 会用 null 覆盖初始值，符合"无值即空"语义
    expect(form.customerRevenue).toBeNull()
  })

  it('Jackson 省略 null 字段时 load 不得把 expectedBidders 留成假 0', async () => {
    const { actions, form, projectLifecycleApi } = setup({
      ownerUnit: '测试单位',
      expectedBidders: null,
      annualEcommerceAmount: null,
      annualRevenue: null,
      customerRevenue: null,
    })
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: {
        projectId: 1,
        ownerUnit: '中国石油',
        reviewStatus: 'DRAFT',
      },
    })

    await actions.load()

    expect(form.expectedBidders).toBeNull()
    expect(form.annualEcommerceAmount).toBeNull()
    expect(form.annualRevenue).toBeNull()
    expect(form.customerRevenue).toBeNull()
  })
})

describe('useInitiationStageActions.buildPayload — 空值保持 null', () => {
  it('buildPayload 原样带出 null 数值字段，不回填 0', () => {
    const { actions } = setup({
      expectedBidders: null,
      annualEcommerceAmount: null,
      annualRevenue: null,
      customerRevenue: null,
    })
    const payload = actions.buildPayload()
    expect(payload.expectedBidders).toBeNull()
    expect(payload.annualEcommerceAmount).toBeNull()
    expect(payload.annualRevenue).toBeNull()
    expect(payload.customerRevenue).toBeNull()
  })

  it('customerRevenue 为 0 时 annualRevenue 必须落成 0（不得被 || 吞成 null）', () => {
    const { actions } = setup({
      customerRevenue: 0,
      annualRevenue: null,
    })
    expect(actions.buildPayload().annualRevenue).toBe(0)
  })
})

describe('useInitiationStageActions.load — 缺 key 归一 + 评估表 0 不预填', () => {
  it('load 省略数值字段时把 form 里已有的假 0 归一成 null', async () => {
    const { actions, form, projectLifecycleApi } = setup({
      ownerUnit: '测试单位',
      expectedBidders: 0,
      annualEcommerceAmount: 0,
      annualRevenue: 0,
      customerRevenue: 0,
    })
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: { projectId: 1, ownerUnit: '中国石油', reviewStatus: 'DRAFT' },
    })
    await actions.load()
    expect(form.expectedBidders).toBeNull()
    expect(form.annualEcommerceAmount).toBeNull()
    expect(form.annualRevenue).toBeNull()
    expect(form.customerRevenue).toBeNull()
  })

  it('404 预填 plannedShortlistedCount=0 时 expectedBidders 保持 null（不写入 0 以免 min clamp 成 1）', async () => {
    const { actions, form, projectLifecycleApi, projectsApi, tendersApi } = setup({
      expectedBidders: null,
    })
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    projectsApi.getDetail.mockResolvedValue({ data: { tenderId: 2300 } })
    tendersApi.getDetail.mockResolvedValue({ data: {} })
    tendersApi.getEvaluation.mockResolvedValue({
      data: { evaluationBasic: { plannedShortlistedCount: 0 } },
    })
    await actions.load()
    expect(form.expectedBidders).toBeNull()
  })

  it('404 预填 plannedShortlistedCount=3 时写入 expectedBidders', async () => {
    const { actions, form, projectLifecycleApi, projectsApi, tendersApi } = setup({
      expectedBidders: null,
    })
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    projectsApi.getDetail.mockResolvedValue({ data: { tenderId: 2300 } })
    tendersApi.getDetail.mockResolvedValue({ data: {} })
    tendersApi.getEvaluation.mockResolvedValue({
      data: { evaluationBasic: { plannedShortlistedCount: 3 } },
    })
    await actions.load()
    expect(form.expectedBidders).toBe(3)
  })
})
