import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// 可变 mock 状态：每个测试用例修改 role 后 mount 组件
const mockUser = { role: '/bidAdmin', menuPermissions: [] }

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({
    get userRole() { return mockUser.role },
    hasPermission: (key) => mockUser.menuPermissions.includes(key),
    currentUser: mockUser,
  }),
}))

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getEvaluation: vi.fn(() => Promise.resolve({ data: {} })),
    transitionEvaluationSubStage: vi.fn(),
    updateEvaluationForm: vi.fn(),
    attachEvaluationEvidence: vi.fn(),
    advanceEvaluation: vi.fn(),
  },
}))

vi.mock('@/constants/projectStages.js', () => ({
  STAGE_TRANSITION_MAP: { EVALUATING: 'RESULT_CONFIRM' },
}))

vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))

const stubs = {
  EvaluationEvidenceUpload: { template: '<div />' },
  ElCard: { template: '<section><slot name="header" /><slot /></section>' },
  ElInput: { props: ['disabled', 'modelValue'], template: '<input :disabled="disabled" />' },
  ElButton: { props: ['loading', 'disabled'], template: '<button :disabled="disabled"><slot /></button>' },
}

async function mountEvaluationStage() {
  const { default: EvaluationStage } = await import('./EvaluationStage.vue')
  const wrapper = mount(EvaluationStage, { global: { stubs } })
  // 等待 onMounted 中的 API 调用完成
  await nextTick()
  await nextTick()
  return wrapper
}

describe('EvaluationStage editable 权限', () => {
  beforeEach(() => {
    mockUser.role = '/bidAdmin'
    mockUser.menuPermissions = []
  })

  it('投标管理员(/bidAdmin)可编辑', async () => {
    mockUser.role = '/bidAdmin'
    const wrapper = await mountEvaluationStage()
    expect(wrapper.find('.btn-container').exists()).toBe(true)
  })

  it('投标组长(bid-TeamLeader)可编辑', async () => {
    mockUser.role = 'bid-TeamLeader'
    const wrapper = await mountEvaluationStage()
    expect(wrapper.find('.btn-container').exists()).toBe(true)
  })

  it('投标负责人(bid-projectLeader)可编辑', async () => {
    mockUser.role = 'bid-projectLeader'
    const wrapper = await mountEvaluationStage()
    expect(wrapper.find('.btn-container').exists()).toBe(true)
  })

  it('投标专员(bid-Team)可编辑', async () => {
    mockUser.role = 'bid-Team'
    const wrapper = await mountEvaluationStage()
    expect(wrapper.find('.btn-container').exists()).toBe(true)
  })

  it('系统管理员(admin)可编辑', async () => {
    mockUser.role = 'admin'
    const wrapper = await mountEvaluationStage()
    expect(wrapper.find('.btn-container').exists()).toBe(true)
  })

  it('行政人员(bid-administration)不可编辑', async () => {
    mockUser.role = 'bid-administration'
    const wrapper = await mountEvaluationStage()
    expect(wrapper.find('.btn-container').exists()).toBe(false)
  })

  it('跨部门协同人员(bid-otherDept)不可编辑', async () => {
    mockUser.role = 'bid-otherDept'
    const wrapper = await mountEvaluationStage()
    expect(wrapper.find('.btn-container').exists()).toBe(false)
  })

  it('OSS 用户无 menuPermissions 也可编辑（bid_lead 角色判断不依赖权限点）', async () => {
    mockUser.role = 'bid-TeamLeader'
    mockUser.menuPermissions = []
    const wrapper = await mountEvaluationStage()
    expect(wrapper.find('.btn-container').exists()).toBe(true)
  })
})
