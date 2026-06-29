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

  it('T1: 投标专员角色但被指定为项目投标负责人(primaryLeadUserId 匹配) → 可编辑/可提交', async () => {
    mockUserStore.userRole = 'bid-Team'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: basePreview })
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1, primaryLeadUserId: 42 } })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isProjectLeader).toBe(true)
    expect(wrapper.vm.canEditDeposit).toBe(true)
    expect(wrapper.vm.canEditSummary).toBe(true)
    expect(wrapper.vm.canSubmitClosure).toBe(true)
  })

  it('T2: 投标专员角色但被指定为投标辅助人员(secondaryLeadUserId 匹配) → 可编辑/可提交', async () => {
    mockUserStore.userRole = 'bid-Team'
    projectLifecycleApi.getClosurePreview.mockResolvedValue({ data: basePreview })
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: { projectId: 1, secondaryLeadUserId: 42 } })
    const wrapper = mount(ClosureStage, { props: { projectId: 1 }, global: { stubs: elStubs } })
    await flushPromises()
    expect(wrapper.vm.isProjectLeader).toBe(true)
    expect(wrapper.vm.canSubmitClosure).toBe(true)
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
