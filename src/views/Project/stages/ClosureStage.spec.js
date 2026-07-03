// Input: ClosureStage mounted with stubbed lifecycle API and stubbed Element Plus
// Output: 蓝图 §3.3.1.6 结项闸门 — canSubmit 根据保证金退回状态和子字段决定
// Pos: src/views/Project/stages/ - 6-stage UI tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const mockUserStore = {
  userRole: 'bid-projectLeader',
  currentUser: { id: 42 },
}

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getClosurePreview: vi.fn(),
    getDrafting: vi.fn(),
    submitClosure: vi.fn(),
    approveClosure: vi.fn(),
    rejectClosure: vi.fn(),
    rebidProject: vi.fn(),
  },
}))
vi.mock('@/api/modules/knowledge.js', () => ({
  casesApi: {
    checkPrecipitationReadiness: vi.fn().mockResolvedValue({
      data: {
        canPrecipitate: false,
        missingItems: [],
      },
    }),
    precipitateCases: vi.fn(),
  },
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useRouter: () => ({ push: vi.fn() }) }
})

vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => mockUserStore,
}))

import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import ClosureStage from './ClosureStage.vue'

const elStubs = {
  'el-card': { template: '<div><slot name="header" /><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div><slot /></div>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-input': { template: '<input />' },
  'el-input-number': { template: '<input type="number" />' },
  'el-date-picker': { template: '<input type="datetime-local" />' },
  'el-alert': { template: '<div class="alert"><slot /></div>' },
  'el-button': {
    props: ['disabled', 'loading', 'type'],
    template: '<button :disabled="disabled" :data-disabled="disabled"><slot /></button>',
  },
  'el-descriptions': { template: '<div><slot /></div>' },
  'el-descriptions-item': { template: '<div><slot /></div>' },
  'el-dialog': { template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
  'el-upload': { template: '<div class="upload-stub"><slot /><slot name="tip" /></div>' },
}

describe('ClosureStage — 蓝图 §3.3.1.6 deposit-return gate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUserStore.userRole = 'bid-projectLeader'
    mockUserStore.currentUser = { id: 42 }
    // 默认 drafting 视图无 leads；CO-392 用例会覆盖为指定负责人
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1 } })
  })

  it('submit disabled when hasDeposit && status NOT_RETURNED', async () => {
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: {
        projectId: 1,
        hasDeposit: true,
        depositReturnStatus: 'NOT_RETURNED',
        canClose: true,
        reviewStatus: 'DRAFT',
        blockingReasons: [],
      },
    })
    const wrapper = mount(ClosureStage, {
      props: { projectId: 1 },
      global: { stubs: elStubs },
    })
    await flushPromises()
    wrapper.vm.form.depositReturnStatus = 'NOT_RETURNED'
    await flushPromises()
    expect(wrapper.vm.canSubmit).toBe(false)
  })

  it('submit enabled only when FULLY_RETURNED + date + evidence', async () => {
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: {
        projectId: 1,
        hasDeposit: true,
        depositReturnStatus: 'NOT_RETURNED',
        canClose: true,
        reviewStatus: 'DRAFT',
        blockingReasons: [],
      },
    })
    const wrapper = mount(ClosureStage, {
      props: { projectId: 1 },
      global: { stubs: elStubs },
    })
    await flushPromises()
    wrapper.vm.form.depositReturnStatus = 'FULLY_RETURNED'
    expect(wrapper.vm.canSubmit).toBe(false) // missing date+evidence
    wrapper.vm.form.depositReturnDate = '2026-05-08T10:00:00'
    wrapper.vm.form.depositReturnEvidenceId = 99
    await flushPromises()
    expect(wrapper.vm.canSubmit).toBe(true)
  })

  it('submit enabled when TRANSFERRED_TO_FEE with amount + evidence', async () => {
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: {
        projectId: 1,
        hasDeposit: true,
        depositReturnStatus: 'NA',
        canClose: true,
        reviewStatus: 'DRAFT',
        blockingReasons: [],
      },
    })
    const wrapper = mount(ClosureStage, {
      props: { projectId: 1 },
      global: { stubs: elStubs },
    })
    await flushPromises()
    wrapper.vm.form.depositReturnStatus = 'TRANSFERRED_TO_FEE'
    expect(wrapper.vm.canSubmit).toBe(false) // missing fields
    wrapper.vm.form.transferAmount = 200
    wrapper.vm.form.depositReturnEvidenceId = 99
    await flushPromises()
    expect(wrapper.vm.canSubmit).toBe(true)
  })

  it('submit enabled when no deposit', async () => {
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: { projectId: 1, hasDeposit: false, canClose: true, reviewStatus: 'DRAFT', blockingReasons: [] },
    })
    const wrapper = mount(ClosureStage, {
      props: { projectId: 1 },
      global: { stubs: elStubs },
    })
    await flushPromises()
    expect(wrapper.vm.canSubmit).toBe(true)
  })

  it('bid_lead can see approve button when PENDING', async () => {
    mockUserStore.userRole = 'bid-TeamLeader'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: {
        projectId: 1,
        hasDeposit: false,
        canClose: true,
        reviewStatus: 'PENDING',
        blockingReasons: [],
      },
    })
    const wrapper = mount(ClosureStage, {
      props: { projectId: 1 },
      global: { stubs: elStubs },
    })
    await flushPromises()
    expect(wrapper.vm.canApprove).toBe(true)
  })

  it('sales cannot approve', async () => {
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: {
        projectId: 1,
        hasDeposit: false,
        canClose: true,
        reviewStatus: 'PENDING',
        blockingReasons: [],
      },
    })
    const wrapper = mount(ClosureStage, {
      props: { projectId: 1 },
      global: { stubs: elStubs },
    })
    await flushPromises()
    expect(wrapper.vm.canApprove).toBe(false)
  })

  it('handleEvidenceUploadSuccess sets evidenceId', async () => {
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: { projectId: 1, hasDeposit: true, canClose: true, reviewStatus: 'DRAFT', blockingReasons: [] },
    })
    const wrapper = mount(ClosureStage, {
      props: { projectId: 1 },
      global: { stubs: elStubs },
    })
    await flushPromises()
    // simulate upload success callback
    wrapper.vm.handleEvidenceUploadSuccess({ data: { id: 123 } })
    expect(wrapper.vm.form.depositReturnEvidenceId).toBe(123)
  })

  it('beforeUpload rejects invalid type', async () => {
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: { projectId: 1, hasDeposit: false, canClose: true, reviewStatus: 'DRAFT', blockingReasons: [] },
    })
    const wrapper = mount(ClosureStage, {
      props: { projectId: 1 },
      global: { stubs: elStubs },
    })
    await flushPromises()
    const badFile = new File([''], 'test.exe', { type: 'application/x-msdownload' })
    expect(wrapper.vm.beforeUpload(badFile)).toBe(false)
    const goodFile = new File([''], 'test.pdf', { type: 'application/pdf' })
    expect(wrapper.vm.beforeUpload(goodFile)).toBe(true)
  })
})

// CO-392: 结项阶段投标负责人/辅助人员内容显示与管理员一致
// 根因：isProjectLeader 仅按角色 code 判断，未认项目级 primaryLeadUserId/secondaryLeadUserId。
// 数据来源对齐 DraftingStage —— 从 getDrafting() 取 leads（ProjectLeadAssignment 表）。
describe('ClosureStage — CO-392 项目级负责人识别', () => {
  const basePreview = { projectId: 1, hasDeposit: false, canClose: true, reviewStatus: 'DRAFT', blockingReasons: [] }

  beforeEach(() => {
    vi.clearAllMocks()
    mockUserStore.currentUser = { id: 42 }
  })

  it('T1: 投标专员角色但被指定为项目投标负责人(primaryLeadUserId 匹配) → isProjectLeader=true，但不可编辑/不可提交(提交仅归 bid-projectLeader)', async () => {
    mockUserStore.userRole = 'bid-Team'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: basePreview })
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1, primaryLeadUserId: 42 } })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isProjectLeader).toBe(true)
    // 新矩阵：投标辅助(无论是否项目级负责人)4 字段只读、不可提交
    expect(wrapper.vm.canEditDeposit).toBe(false)
    expect(wrapper.vm.canEditSummary).toBe(false)
    expect(wrapper.vm.canSubmitClosure).toBe(false)
  })

  it('T2: 投标专员角色但被指定为投标辅助人员(secondaryLeadUserId 匹配) → isProjectLeader=true，但不可编辑/不可提交', async () => {
    mockUserStore.userRole = 'bid-Team'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: basePreview })
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1, secondaryLeadUserId: 42 } })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isProjectLeader).toBe(true)
    expect(wrapper.vm.canSubmitClosure).toBe(false)
  })

  it('T3: 非管理/非负责人角色且不是该项目负责人/辅助人员 → 无编辑/提交权限', async () => {
    // bid-administration 既不在 isBidManager 也不在 isProjectLeader，可隔离验证 leads 逻辑
    mockUserStore.userRole = 'bid-administration'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: basePreview })
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1, primaryLeadUserId: 999, secondaryLeadUserId: 888 } })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isProjectLeader).toBe(false)
    expect(wrapper.vm.canEditDeposit).toBe(false)
    expect(wrapper.vm.canSubmitClosure).toBe(false)
  })

  it('T4: 角色 bid-projectLeader 仍直接视为负责人(保持角色判断兼容，不回退)', async () => {
    mockUserStore.userRole = 'bid-projectLeader'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: basePreview })
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1 } })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isProjectLeader).toBe(true)
  })

  it('T5: ID 类型安全 —— 后端 Long 与前端 string 比较不误判', async () => {
    mockUserStore.userRole = 'bid-Team'
    mockUserStore.currentUser = { id: 42 }
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: basePreview })
    // 后端字段可能是 number 也可能是 string（HTTP 传输），统一按 String 比较
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1, primaryLeadUserId: '42' } })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isProjectLeader).toBe(true)
  })
})

// 结项 4 字段（保证金退回情况/退回日期/凭证文件/项目总结）编辑+提交权 矩阵：
//   投标项目负责人(bid-projectLeader) → 编辑+提交，不审核（职责分离）
//   投标管理员(/bidAdmin)/投标组长(bid-TeamLeader) → 只审核，4 字段只读
//   该项目投标辅助(bid-Team 匹配 primaryLead/secondaryLead) → 只审核，4 字段只读
// 纠正 CO-403：CO-403 把编辑权错配给管理员/组长，导致能编辑的人提交不了、能提交的人编辑不了。
// 本次修复：isClosureEditor 改指投标项目负责人；canSubmitClosure 仅投标项目负责人；
// canApprove 改用 admin//bidAdmin/bid-TeamLeader + isProjectLeader（项目级 lead 匹配）。
describe('ClosureStage — 结项编辑/提交/审核权矩阵', () => {
  const basePreview = { projectId: 1, hasDeposit: true, canClose: true, reviewStatus: 'DRAFT', blockingReasons: [] }
  const pendingPreview = { ...basePreview, reviewStatus: 'PENDING' }

  beforeEach(() => {
    vi.clearAllMocks()
    mockUserStore.currentUser = { id: 42 }
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1 } })
  })

  it('C1: 投标项目负责人 bid-projectLeader → 可编辑保证金/总结 + 可提交 + 不可审核', async () => {
    mockUserStore.userRole = 'bid-projectLeader'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: basePreview })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isClosureEditor).toBe(true)
    expect(wrapper.vm.canEditDeposit).toBe(true)
    expect(wrapper.vm.canEditSummary).toBe(true)
    expect(wrapper.vm.canSubmitClosure).toBe(true)
    // 职责分离：提交人不能审核（即使状态为 PENDING）
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: pendingPreview })
    wrapper.vm.preview = pendingPreview
    await flushPromises()
    expect(wrapper.vm.canApprove).toBe(false)
  })

  it('C2: 投标管理员 /bidAdmin → 4 字段只读 + 无提交按钮 + 可审核(PENDING)', async () => {
    mockUserStore.userRole = '/bidAdmin'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: pendingPreview })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isClosureEditor).toBe(false)
    expect(wrapper.vm.canEditDeposit).toBe(false)
    expect(wrapper.vm.canEditSummary).toBe(false)
    expect(wrapper.vm.canSubmitClosure).toBe(false)
    expect(wrapper.vm.canApprove).toBe(true)
  })

  it('C3: 投标组长 bid-TeamLeader → 4 字段只读 + 无提交按钮 + 可审核(PENDING)', async () => {
    mockUserStore.userRole = 'bid-TeamLeader'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: pendingPreview })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isClosureEditor).toBe(false)
    expect(wrapper.vm.canEditDeposit).toBe(false)
    expect(wrapper.vm.canEditSummary).toBe(false)
    expect(wrapper.vm.canSubmitClosure).toBe(false)
    expect(wrapper.vm.canApprove).toBe(true)
  })

  it('C4: 投标辅助 bid-Team（非该项目负责人/辅助）→ 全只读 + 不可审核', async () => {
    mockUserStore.userRole = 'bid-Team'
    mockUserStore.currentUser = { id: 42 }
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: pendingPreview })
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1, primaryLeadUserId: 999 } })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isProjectLeader).toBe(false)
    expect(wrapper.vm.isClosureEditor).toBe(false)
    expect(wrapper.vm.canEditDeposit).toBe(false)
    expect(wrapper.vm.canSubmitClosure).toBe(false)
    expect(wrapper.vm.canApprove).toBe(false)
  })

  it('C5: 投标辅助 bid-Team（被项目级指定为 secondaryLead）→ 4 字段只读 + 无提交按钮 + 可审核(PENDING)', async () => {
    mockUserStore.userRole = 'bid-Team'
    mockUserStore.currentUser = { id: 42 }
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: pendingPreview })
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1, secondaryLeadUserId: 42 } })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    // CO-392: 作为项目级辅助人员 isProjectLeader=true
    expect(wrapper.vm.isProjectLeader).toBe(true)
    // 但 4 字段只读 + 无提交按钮
    expect(wrapper.vm.isClosureEditor).toBe(false)
    expect(wrapper.vm.canEditDeposit).toBe(false)
    expect(wrapper.vm.canEditSummary).toBe(false)
    expect(wrapper.vm.canSubmitClosure).toBe(false)
    // 作为该项目投标辅助可审核
    expect(wrapper.vm.canApprove).toBe(true)
  })

  it('C6: 投标项目负责人在已结项(APPROVED)后 → 4 字段只读 + 不可提交', async () => {
    mockUserStore.userRole = 'bid-projectLeader'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: { ...basePreview, reviewStatus: 'APPROVED' } })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isClosureEditor).toBe(true)
    expect(wrapper.vm.canEditDeposit).toBe(false)
    expect(wrapper.vm.canEditSummary).toBe(false)
    expect(wrapper.vm.canSubmitClosure).toBe(false)
  })
})

// 二次招标按钮可见性：流标项目 alreadyClosed=true 但 reviewStatus 非 APPROVED 时也应显示
describe('ClosureStage — 二次招标按钮可见性', () => {
  const basePreview = { projectId: 1, hasDeposit: false, canClose: true, reviewStatus: 'DRAFT', blockingReasons: [] }

  beforeEach(() => {
    vi.clearAllMocks()
    mockUserStore.userRole = 'bid-projectLeader'
    mockUserStore.currentUser = { id: 42 }
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1 } })
  })

  it('流标项目 alreadyClosed=true 无 APPROVED 状态 → 二次招标按钮可见', async () => {
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: { ...basePreview, alreadyClosed: true, reviewStatus: 'DRAFT' },
    })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    // 模板中 v-if="preview?.reviewStatus === 'APPROVED' || preview?.alreadyClosed"
    // alreadyClosed=true 应让按钮区域渲染
    const rebidBtn = wrapper.findAll('button').find(b => b.text().includes('二次招标'))
    expect(rebidBtn).toBeTruthy()
  })

  it('未结项项目 alreadyClosed=false 且 reviewStatus 非 APPROVED → 二次招标按钮不可见', async () => {
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: { ...basePreview, alreadyClosed: false, reviewStatus: 'DRAFT' },
    })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    const rebidBtn = wrapper.findAll('button').find(b => b.text().includes('二次招标'))
    expect(rebidBtn).toBeFalsy()
  })

  it('结项审批通过 reviewStatus=APPROVED → 二次招标按钮可见', async () => {
    projectLifecycleApi.getClosurePreview.mockResolvedValue({
      data: { ...basePreview, alreadyClosed: true, reviewStatus: 'APPROVED' },
    })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    const rebidBtn = wrapper.findAll('button').find(b => b.text().includes('二次招标'))
    expect(rebidBtn).toBeTruthy()
  })
})
