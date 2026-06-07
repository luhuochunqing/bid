// Input: ClosureStage mounted with stubbed lifecycle API and stubbed Element Plus
// Output: 蓝图 §3.3.1.6 结项闸门 — canSubmit 根据保证金退回状态和子字段决定
// Pos: src/views/Project/stages/ - 6-stage UI tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const mockUserStore = {
  userRole: 'sales',
}

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getClosurePreview: vi.fn(),
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
    mockUserStore.userRole = 'sales'
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
    mockUserStore.userRole = 'bid_lead'
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
