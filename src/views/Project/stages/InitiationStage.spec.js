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
import { useUserStore } from '@/stores/user.js'
import { ElMessage } from 'element-plus'

const stubs = {
  'el-card': { template: '<div><slot name="header" /><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { props: ['label'], template: '<div :data-label="label"><slot /><span>{{ label }}</span></div>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div><slot /></div>' },
  'el-input': { props: ['disabled'], template: '<input :disabled="disabled" />' },
  // 模拟 Element Plus InputNumber：非空值低于 :min 时 clamp 并回写。
  // 这是项目 1365 假值的真实路径——初值 0 + :min="1" 会被纠正成 1 并显示。
  // null/'' 保持空（isNil），不得 clamp。
  'el-input-number': {
    props: ['disabled', 'min', 'precision', 'modelValue'],
    emits: ['update:modelValue'],
    watch: {
      modelValue: {
        immediate: true,
        handler(v) {
          if (v == null || v === '') return
          if (this.min != null && Number(v) < this.min) {
            this.$emit('update:modelValue', this.min)
          }
        },
      },
    },
    template: '<input type="number" :disabled="disabled" :value="modelValue == null ? \'\' : modelValue" />',
  },
  'el-date-picker': { template: '<input type="datetime-local" />' },
  'el-upload': { template: '<div><slot name="tip" /><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': { props: ['disabled', 'loading', 'type', 'text'], template: '<button :disabled="disabled"><slot /></button>' },
  'el-cascader': { template: '<div><slot /></div>' },
  'el-divider': { template: '<div><slot /></div>' },
  'el-tooltip': { template: '<div><slot /></div>' },
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
    expect(wrapper.text()).toContain('客户电商年采购金额')
    expect(wrapper.text()).toContain('客户年营收')
    expect(wrapper.text()).toContain('招标文件不利项')
    expect(wrapper.text()).toContain('风险预判')
    expect(wrapper.text()).toContain('项目经理综合评估是否有兜底方案')
    expect(wrapper.text()).toContain('需要的支持及其他关键信息备注')
    expect(wrapper.text()).toContain('项目计划GAP附件')
  })

  it('calls submit when valid', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    projectLifecycleApi.submitInitiation.mockResolvedValue({ data: { projectId: 1 } })
    const wrapper = createWrapper()
    await flushPromises()
    wrapper.vm.form.needDeposit = 'NO'
    wrapper.vm.form.tenderDocumentId = 1  // CO-455: 招标文件必传
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

  // CO-455: 招标文件必传校验
  it('blocks submit when tender document not uploaded', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    const wrapper = createWrapper()
    await flushPromises()
    wrapper.vm.form.needDeposit = 'NO'
    wrapper.vm.form.tenderDocumentId = null  // 缺少招标文件
    wrapper.vm.submit()
    await flushPromises()
    expect(projectLifecycleApi.submitInitiation).not.toHaveBeenCalled()
  })

  // 回归：立项数值字段空值必须为 null（勿静默回填 0）。
  // 背景：测试环境项目 1365 库中 expected_bidders=NULL，表单初值 0 + blur 补 0 hack
  // 导致详情显示假值而列表如实显示 '-'，用户误判为数据不一致。
  it('initializes numeric fields to null so empty data never renders a misleading zero', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.form.expectedBidders).toBeNull()
    expect(wrapper.vm.form.annualEcommerceAmount).toBeNull()
    expect(wrapper.vm.form.customerRevenue).toBeNull()
    expect(wrapper.vm.form.annualRevenue).toBeNull()
    // depositAmount 保持 0 默认：needDeposit 切换由 onDepositChange 显式管理，且默认隐藏不产生误导
    expect(wrapper.vm.form.depositAmount).toBe(0)
  })

  it('exposes no focus/blur zero-backfill helpers and keeps empty values untouched', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.handleAmountFocus).toBeUndefined()
    expect(wrapper.vm.handleAmountBlur).toBeUndefined()
    wrapper.vm.form.expectedBidders = null
    expect(wrapper.vm.form.expectedBidders).toBeNull()
  })

  // 生产路径：application.yml default-property-inclusion=non_null 会省略 null 字段，
  // load() 的 Object.assign(form, data) 写不进这些 key。初值必须是 null，
  // 否则 0 会被 el-input-number :min="1" clamp 成 1，详情显示假数字。
  it('keeps omitted numeric keys as null after loading existing initiation (Jackson non_null)', async () => {
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: { ownerUnit: '中国石油', bidOpenTime: '2026-07-01T10:00:00', reviewStatus: 'DRAFT' },
    })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.form.expectedBidders).toBeNull()
    expect(wrapper.vm.form.annualEcommerceAmount).toBeNull()
    expect(wrapper.vm.form.annualRevenue).toBeNull()
    expect(wrapper.vm.form.customerRevenue).toBeNull()
    const biddersInput = wrapper.find('[data-label="计划入围供应商数量"] input')
    expect(biddersInput.exists()).toBe(true)
    expect(biddersInput.element.value).toBe('')
  })

  it('submits omitted numeric fields as null rather than a fake zero', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    projectLifecycleApi.submitInitiation.mockResolvedValue({ data: { projectId: 1 } })
    const wrapper = createWrapper()
    await flushPromises()
    wrapper.vm.form.needDeposit = 'NO'
    wrapper.vm.form.tenderDocumentId = 1
    await wrapper.vm.submit()
    await flushPromises()
    const payload = projectLifecycleApi.submitInitiation.mock.calls[0][1]
    expect(payload.expectedBidders).toBeNull()
    expect(payload.annualEcommerceAmount).toBeNull()
    expect(payload.annualRevenue).toBeNull()
    expect(payload.customerRevenue).toBeNull()
  })

  it('does not hard-limit bidding leader candidates to sales role', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    usersApi.search.mockResolvedValue([
      { id: 1, name: '销售负责人', employeeNumber: 'S001', roleCode: 'bid-projectLeader' },
      { id: 2, name: '投标专员', employeeNumber: 'B001', roleCode: 'bid-Team' },
    ])
    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.searchLeader('张')

    expect(wrapper.vm.leaderOptions.map(u => u.id)).toEqual([1, 2])
  })

  it('filters bidding assistant candidates to bid specialist role', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    usersApi.search.mockResolvedValue([
      { id: 1, name: '销售负责人', employeeNumber: 'S001', roleCode: 'bid-projectLeader' },
      { id: 2, name: '投标专员', employeeNumber: 'B001', roleCode: 'bid-Team' },
    ])
    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.searchAssistant('李')

    expect(wrapper.vm.assistantOptions).toHaveLength(1)
    expect(wrapper.vm.assistantOptions[0].id).toBe(2)
  })

  it('initializes customer info rows as empty (0 rows, dynamic from tender evaluation)', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.custFixedRows).toEqual([])
  })

  it('autoFillFromTender generates dynamic customer info rows from evaluation EAV', async () => {
    projectLifecycleApi.getInitiation.mockRejectedValue({ response: { status: 404 } })
    projectsApi.getDetail.mockResolvedValue({
      data: { id: 1, tenderId: 42 },
    })
    tendersApi.getDetail.mockResolvedValue({
      data: { id: 42, purchaserName: '测试单位', customerType: '政府' },
    })
    tendersApi.getEvaluation.mockResolvedValue({
      data: {
        evaluationCustomerInfos: [
          { roleKey: 'EXPERT_1', infoKey: 'NAME', value: '王专家' },
          { roleKey: 'EXPERT_1', infoKey: 'CONTACT_INFO', value: 'wang@test.com' },
          { roleKey: 'EXPERT_1', infoKey: 'TEL', value: '010-12345678' },
          { roleKey: 'PROJECT_HIGHEST_DECISION_MAKER', infoKey: 'NAME', value: '张总' },
          { roleKey: 'PROJECT_HIGHEST_DECISION_MAKER', infoKey: 'TENDENCY', value: '1' },
        ],
      },
    })
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.vm.custFixedRows).toHaveLength(2)
    const roles = wrapper.vm.custFixedRows.map(r => r.role).sort()
    expect(roles).toEqual(['专家1', '项目最高决策人'])
    const expert = wrapper.vm.custFixedRows.find(r => r.role === '专家1')
    expect(expert.name).toBe('王专家')
    expect(expert.contactInfo).toBe('wang@test.com')
    expect(expert.tel).toBe('010-12345678')
    const dm = wrapper.vm.custFixedRows.find(r => r.role === '项目最高决策人')
    expect(dm.name).toBe('张总')
    expect(dm.preference).toBe('1')
    // 无 TEL EAV 行时，tel 字段不生成（undefined）
    expect(dm.tel).toBeUndefined()
  })

  // 回归测试：PENDING_REVIEW 状态下字段和操作都必须保持锁定
  // 历史教训：PR !2091 曾改 locked 定义导致 PENDING_REVIEW 下连带解锁删除/上传/AI 评估等操作
  // 根因：locked 被字段编辑和操作权限 7 处共用，改一处会连锁影响
  // 这 5 个测试守护 locked 的原始语义，防止未来再走弯路
  describe('PENDING_REVIEW state — fields and operations all locked (locked 语义守护)', () => {
    async function mountPendingReview() {
      projectLifecycleApi.getInitiation.mockResolvedValue({
        data: {
          ownerUnit: '中国石油',
          bidOpenTime: '2026-07-01T10:00:00',
          reviewStatus: 'PENDING_REVIEW',
          evalPrefilled: false,
        },
      })
      const wrapper = createWrapper()
      await flushPromises()
      return wrapper
    }

    it('locks fields in PENDING_REVIEW (locked=true)', async () => {
      const wrapper = await mountPendingReview()
      // locked 控制字段编辑（:9 AdaptiveFormPage, :17 el-form, :249 fieldDisabled）
      expect(wrapper.vm.locked).toBe(true)
    })

    it('locks AI assessment button in PENDING_REVIEW (locked=true)', async () => {
      const wrapper = await mountPendingReview()
      // AI 评估按钮 :disabled="locked || uploadingDoc"
      expect(wrapper.vm.locked).toBe(true)
    })

    it('locks document upload in PENDING_REVIEW (locked=true, isApprovalMode=false)', async () => {
      const wrapper = await mountPendingReview()
      // upload :disabled="locked && !isApprovalMode"
      expect(wrapper.vm.locked).toBe(true)
      expect(wrapper.vm.isApprovalMode).toBe(false)
    })

    it('blocks document deletion in PENDING_REVIEW (handleBeforeRemove returns false)', async () => {
      const wrapper = await mountPendingReview()
      // handleBeforeRemove: if (locked.value && !isApprovalMode.value) return false
      expect(wrapper.vm.locked).toBe(true)
      expect(wrapper.vm.isApprovalMode).toBe(false)
      const result = wrapper.vm.handleBeforeRemove()
      expect(result).toBe(false)
    })

    it('locks all operations in APPROVED state (locked=true)', async () => {
      projectLifecycleApi.getInitiation.mockResolvedValue({
        data: {
          ownerUnit: '中国石油',
          bidOpenTime: '2026-07-01T10:00:00',
          reviewStatus: 'APPROVED',
          evalPrefilled: false,
        },
      })
      const wrapper = createWrapper()
      await flushPromises()
      // APPROVED 状态下：字段锁定 + 操作锁定
      expect(wrapper.vm.locked).toBe(true)
      const result = wrapper.vm.handleBeforeRemove()
      expect(result).toBe(false)
    })
  })

  // 审批模式（投标管理员/组长 + PENDING_REVIEW）下，expectedBidders / tenderAdverseItems 可编辑
  it('allows editing expectedBidders and tenderAdverseItems in approval mode', async () => {
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: { reviewStatus: 'PENDING_REVIEW', expectedBidders: 3, tenderAdverseItems: '不利项' },
    })
    const pinia = createPinia()
    setActivePinia(pinia)
    const userStore = useUserStore()
    userStore.currentUser = { id: 9, roleCode: 'bid-teamleader' }
    const wrapper = mount(InitiationStage, {
      props: { projectId: 1 },
      global: { stubs, plugins: [pinia] },
    })
    await flushPromises()

    const biddersInput = wrapper.find('[data-label="计划入围供应商数量"] input')
    const adverseInput = wrapper.find('[data-label="招标文件不利项"] input')
    expect(biddersInput.exists()).toBe(true)
    expect(adverseInput.exists()).toBe(true)
    expect(biddersInput.attributes('disabled')).toBeUndefined()
    expect(adverseInput.attributes('disabled')).toBeUndefined()
  })

  // 非投标管理员在 PENDING_REVIEW 下，expectedBidders 仍不可编辑
  it('keeps expectedBidders disabled for non-bid-manager in pending review', async () => {
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: { reviewStatus: 'PENDING_REVIEW', expectedBidders: 3, tenderAdverseItems: '不利项' },
    })
    const pinia = createPinia()
    setActivePinia(pinia)
    const userStore = useUserStore()
    userStore.currentUser = { id: 9, roleCode: 'bid-projectLeader' }
    const wrapper = mount(InitiationStage, {
      props: { projectId: 1 },
      global: { stubs, plugins: [pinia] },
    })
    await flushPromises()

    const biddersInput = wrapper.find('[data-label="计划入围供应商数量"] input')
    expect(biddersInput.attributes('disabled')).toBeDefined()
  })

  // spec 065：招标文件上传前置配额拦截（OBS 512MB / multipart 50MB，取环境对应值）
  it('rejects tender file exceeding upload quota before uploading', async () => {
    projectLifecycleApi.getInitiation.mockResolvedValue({ data: { reviewStatus: 'DRAFT' } })
    const wrapper = createWrapper()
    await flushPromises()
    const huge = { name: '超大招标.pdf', size: 600 * 1024 * 1024, type: 'application/pdf' }
    const result = await wrapper.vm.handleDocBeforeUpload(huge)
    expect(result).toBe(false)
    expect(ElMessage.error).toHaveBeenCalledWith(expect.stringContaining('超过限制'))
  })

  // 立项通过（APPROVED）后，有权限角色仍可修改入围家数和招标文件不利项，并支持保存同步
  it('allows editing expectedBidders and tenderAdverseItems in APPROVED state for bid manager and saves', async () => {
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: { reviewStatus: 'APPROVED', expectedBidders: 4, tenderAdverseItems: '原不利项' },
    })
    const pinia = createPinia()
    setActivePinia(pinia)
    const userStore = useUserStore()
    userStore.currentUser = { id: 10, roleCode: 'bid-teamleader' }
    const wrapper = mount(InitiationStage, {
      props: { projectId: 1 },
      global: { stubs, plugins: [pinia] },
    })
    await flushPromises()

    const biddersInput = wrapper.find('[data-label="计划入围供应商数量"] input')
    const adverseInput = wrapper.find('[data-label="招标文件不利项"] input')
    expect(biddersInput.exists()).toBe(true)
    expect(adverseInput.exists()).toBe(true)
    expect(biddersInput.attributes('disabled')).toBeUndefined()
    expect(adverseInput.attributes('disabled')).toBeUndefined()

    // 触发保存修改
    await wrapper.vm.saveApprovedFields()
    expect(projectLifecycleApi.updateInitiation).toHaveBeenCalledWith(1, {
      expectedBidders: 4,
      tenderAdverseItems: '原不利项',
    })
  })

  it('keeps expectedBidders and tenderAdverseItems disabled in APPROVED state for unauthorized role', async () => {
    projectLifecycleApi.getInitiation.mockResolvedValue({
      data: { reviewStatus: 'APPROVED', expectedBidders: 4, tenderAdverseItems: '原不利项' },
    })
    const pinia = createPinia()
    setActivePinia(pinia)
    const userStore = useUserStore()
    userStore.currentUser = { id: 20, roleCode: 'bid-Team' }
    const wrapper = mount(InitiationStage, {
      props: { projectId: 1 },
      global: { stubs, plugins: [pinia] },
    })
    await flushPromises()

    const biddersInput = wrapper.find('[data-label="计划入围供应商数量"] input')
    const adverseInput = wrapper.find('[data-label="招标文件不利项"] input')
    expect(biddersInput.attributes('disabled')).toBeDefined()
    expect(adverseInput.attributes('disabled')).toBeDefined()
  })
})
