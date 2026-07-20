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
  const actions = useInitiationStageActions({
    props: { projectId: 1 },
    emit: vi.fn(),
    form,
    custFixedRows: ref([]),
    bidDocFiles: ref([]),
    planGapFiles: ref([]),
    userStore: { currentUser: { id: 1 }, userName: '张三' },
    projectLifecycleApi,
    projectsApi: { getDetail: vi.fn(), getDocuments: vi.fn() },
    tendersApi: { getDetail: vi.fn(), getEvaluation: vi.fn() },
    usersApi: { getByIds: vi.fn() },
    projectsState: createState(),
    leaderOptions: ref([]),
    assistantOptions: ref([]),
  })
  return { actions, form, projectLifecycleApi }
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

describe('useInitiationStageActions.load — 客户营收字段回填（回归 !564）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 后端 InitiationViewDto 只有 annualRevenue（@Deprecated），无 customerRevenue；
  // 前端 form.customerRevenue 必须从 data.annualRevenue 回填，否则详情页"客户营收"输入框显示 0。
  it('data.annualRevenue 有值时应回填到 form.customerRevenue', async () => {
    const { actions, form, projectLifecycleApi } = setup({
      ownerUnit: '测试单位',
      customerType: 'CENTRAL_SOE',
    })
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: {
        projectId: 1,
        ownerUnit: '测试单位',
        customerType: 'CENTRAL_SOE',
        annualRevenue: 12.5,
        // 后端不返回 customerRevenue（InitiationViewDto 无此字段）
      },
    })

    await actions.load()

    expect(form.customerRevenue).toBe(12.5)
    expect(form.annualRevenue).toBe(12.5)
  })

  it('data.annualRevenue 为空时不应覆盖 form.customerRevenue 已有值', async () => {
    const { actions, form, projectLifecycleApi } = setup({
      ownerUnit: '测试单位',
      customerType: 'CENTRAL_SOE',
      customerRevenue: 99.9,
    })
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: {
        projectId: 1,
        ownerUnit: '测试单位',
        customerType: 'CENTRAL_SOE',
        annualRevenue: null,
      },
    })

    await actions.load()

    // 已有值不应被 null 覆盖
    expect(form.customerRevenue).toBe(99.9)
  })

  it('data.annualRevenue 与 form.customerRevenue 同时为 null 时保持 null', async () => {
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
        annualRevenue: null,
      },
    })

    await actions.load()

    expect(form.customerRevenue).toBeNull()
  })
})
