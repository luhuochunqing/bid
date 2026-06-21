// Input: InitiationStage mounted with stubbed lifecycle API and stubbed Element Plus
// Output: PRD §4.3 4-section layout — basic info grid, bidding info, customer table, AI assessment
// Pos: src/views/Project/stages/ - 6-stage UI tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { setActivePinia } from 'pinia'

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getInitiation: vi.fn(),
    submitInitiation: vi.fn(),
    updateInitiation: vi.fn(),
  },
}))
vi.mock('@/api/modules/ai.js', () => ({
  scoreAnalysisApi: { generatePreview: vi.fn() },
}))
vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import InitiationStage from './InitiationStage.vue'

const stubs = {
  'el-card': { template: '<div><slot name="header" /><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { props: ['label'], template: '<div><slot /><span>{{ label }}</span></div>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-input-number': { template: '<input type="number" />' },
  'el-date-picker': { template: '<input type="datetime-local" />' },
  'el-upload': { template: '<div><slot name="tip" /><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': { props: ['disabled', 'loading', 'type', 'text'], template: '<button :disabled="disabled"><slot /></button>' },
  'el-cascader': { template: '<div><slot /></div>' },
  'el-divider': { template: '<div><slot /></div>' },
}

function createWrapper() {
  return mount(InitiationStage, {
    props: { projectId: 1 },
    global: { stubs, plugins: [createPinia()] },
  })
}

describe('InitiationStage — PRD §4.3 4-section layout', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders 3 section cards', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    const wrapper = createWrapper()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('投标信息')
    expect(text).toContain('客户信息')
    expect(text).toContain('AI 风险评估')
  })

  it('renders bidding info fields aligned with evaluation', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.text()).toContain('是否需要保证金')
    expect(wrapper.text()).toContain('计划入围供应商数量')
    expect(wrapper.text()).toContain('电商MRO+办公流水金额')
    expect(wrapper.text()).toContain('客户营收')
    expect(wrapper.text()).toContain('招标文件不利项')
    expect(wrapper.text()).toContain('风险预判')
    expect(wrapper.text()).toContain('项目经理综合评估是否有兜底方案')
    expect(wrapper.text()).toContain('需要的支持及其他关键信息备注')
    expect(wrapper.text()).toContain('项目计划GAP')
  })

  it('calls submit when valid', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    projectLifecycleApi.submitInitiation.mockResolvedValue({ data: { projectId: 1 } })
    const wrapper = createWrapper()
    await flushPromises()
    wrapper.vm.form.needDeposit = 'NO'
    await wrapper.vm.submit()
    await flushPromises()
    expect(projectLifecycleApi.submitInitiation).toHaveBeenCalledWith(1, expect.any(Object))
  })

  it('loads existing data on mount', async () => {
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: { ownerUnit: '中国石油', bidOpenTime: '2026-07-01T10:00:00', reviewStatus: 'DRAFT' },
    })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.form.ownerUnit).toBe('中国石油')
    expect(wrapper.vm.form.bidOpenTime).toBe('2026-07-01T10:00:00')
  })

  it('blocks submit when deposit required but method not selected', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    const wrapper = createWrapper()
    await flushPromises()
    wrapper.vm.form.needDeposit = 'YES'
    wrapper.vm.form.depositPaymentMethod = ''
    wrapper.vm.submit()
    await flushPromises()
    expect(projectLifecycleApi.submitInitiation).not.toHaveBeenCalled()
  })

  it('clears 0 default on focus and restores 0 on blur for amount fields', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    const wrapper = createWrapper()
    await flushPromises()

    const fields = ['depositAmount', 'annualEcommerceAmount', 'customerRevenue']
    for (const key of fields) {
      wrapper.vm.form[key] = 0
      wrapper.vm.handleAmountFocus(key)
      expect(wrapper.vm.form[key]).toBeNull()
      wrapper.vm.handleAmountBlur(key)
      expect(wrapper.vm.form[key]).toBe(0)
    }
  })

  it('does not clear non-zero amount on focus', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    const wrapper = createWrapper()
    await flushPromises()
    wrapper.vm.form.depositAmount = 123.45
    wrapper.vm.handleAmountFocus('depositAmount')
    expect(wrapper.vm.form.depositAmount).toBe(123.45)
  })
})
