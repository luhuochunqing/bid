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
vi.mock('@/api/modules/users.js', () => ({
  usersApi: { search: vi.fn() },
}))
vi.mock('@/api/modules/ai.js', () => ({
  scoreAnalysisApi: { generatePreview: vi.fn() },
}))
vi.mock('@/api/modules/tenders.js', () => ({
  tendersApi: { getDetail: vi.fn(), getEvaluation: vi.fn() },
}))
vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: { getDetail: vi.fn(), getDocuments: vi.fn() },
}))
vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { usersApi } from '@/api/modules/users.js'
import { tendersApi } from '@/api/modules/tenders.js'
import { projectsApi } from '@/api/modules/projects.js'
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
  'UserPicker': { name: 'UserPicker', template: '<div class="user-picker-stub"></div>' },
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

  it('uses UserPicker for user selection without local search methods', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    const wrapper = createWrapper()
    await flushPromises()

    // 不再有本地 searchLeader/searchAssistant 方法，所有用户搜索走 UserPicker 组件
    // 支持按姓名、工号、拼音远程搜索，不再预加载100人候选列表
    expect(typeof wrapper.vm.searchLeader).toBe('undefined')
    expect(typeof wrapper.vm.searchAssistant).toBe('undefined')
    expect(typeof wrapper.vm.leaderOptions).toBe('undefined')
    expect(typeof wrapper.vm.assistantOptions).toBe('undefined')
  })

  it('CO-323: 客户信息矩阵初始为 0 行（非 14 行固定空行）', async () => {
    // CO-323: 立项页客户信息矩阵改成 0 行 × 14 列，和标讯完全一致
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    projectsApi.getDetail.mockResolvedValue({ data: { tenderId: null } })
    const wrapper = createWrapper()
    await flushPromises()
    // 无标讯评估数据时，客户信息矩阵应为 0 行
    expect(wrapper.vm.custFixedRows).toHaveLength(0)
  })

  it('CO-323: autoFillFromTender 客户信息矩阵只生成有数据的行', async () => {
    // CO-323: 标讯转项目后，客户信息矩阵完全由标讯带过来，只展示有数据的行
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    projectsApi.getDetail.mockResolvedValue({ data: { tenderId: 100 } })
    tendersApi.getDetail.mockResolvedValue({ data: { purchaserName: '测试采购方' } })
    tendersApi.getEvaluation.mockResolvedValue({
      data: {
        evaluationCustomerInfos: [
          { roleKey: 'EXPERT_1', infoKey: 'NAME', value: '王五' },
          { roleKey: 'EXPERT_1', infoKey: 'CONTACT_INFO', value: 'wangwu@test.com' },
          { roleKey: 'PROJECT_HIGHEST_DECISION_MAKER', infoKey: 'NAME', value: '张三' },
        ],
      },
    })
    const wrapper = createWrapper()
    await flushPromises()
    // CO-323: 只有 2 个角色有数据，应该只有 2 行（不是 14 行）
    expect(wrapper.vm.custFixedRows).toHaveLength(2)
    // 按 ROW_ROLE_MAP 顺序，PROJECT_HIGHEST_DECISION_MAKER 在 EXPERT_1 前面
    expect(wrapper.vm.custFixedRows[0].role).toBe('项目最高决策人')
    expect(wrapper.vm.custFixedRows[0].name).toBe('张三')
    expect(wrapper.vm.custFixedRows[1].role).toBe('专家1')
    expect(wrapper.vm.custFixedRows[1].name).toBe('王五')
    expect(wrapper.vm.custFixedRows[1].contactInfo).toBe('wangwu@test.com')
  })
})
