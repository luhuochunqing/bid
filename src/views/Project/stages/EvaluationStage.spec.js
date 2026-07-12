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
  EvaluationEvidenceUpload: {
    template: '<div />',
    methods: {
      getPendingFileIds: () => [],
      clearPendingFileIds: () => {},
    },
  },
  ElCard: { template: '<section><slot name="header" /><slot /></section>' },
  ElInput: { props: ['disabled', 'modelValue'], template: '<input :disabled="disabled" />' },
  ElButton: { props: ['loading', 'disabled'], template: '<button :disabled="disabled"><slot /></button>' },
}

async function mountEvaluationStage() {
  const { default: EvaluationStage } = await import('./EvaluationStage.vue')
  const wrapper = mount(EvaluationStage, {
    props: { projectId: 133 },
    global: { stubs },
  })
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

// CO-550: 开标一览表已取消必填
describe('EvaluationStage handleSubmit 开标一览表非必填', () => {
  beforeEach(() => {
    mockUser.role = '/bidAdmin'
    mockUser.menuPermissions = []
  })

  it('未上传开标一览表时也能提交', async () => {
    const { projectLifecycleApi } = await import('@/api/modules/projectLifecycle.js')
    const wrapper = await mountEvaluationStage()
    wrapper.vm.targetSubStage = 'AWAITING_BOARD'
    wrapper.vm.evaluationNotes = '评标情况说明'
    wrapper.vm.evidenceDocIds = []
    wrapper.vm.evidenceUploadRef = { getPendingFileIds: () => [], clearPendingFileIds: () => {} }
    await wrapper.vm.handleSubmit()
    expect(projectLifecycleApi.advanceEvaluation).toHaveBeenCalled()
  })
})

// 防复发回归测试：状态没变、notes 改了时不应调用 updateEvaluationForm
// 历史背景：MVP 重构（commit f8b5bb5d7）残留了对 /form 端点的错位调用，
// 该端点 DTO 强制 5 字段必填且不接受 notes 字段，导致 400 校验失败。
describe('EvaluationStage 防复发：状态没变时不应调 /form 端点', () => {
  beforeEach(() => {
    mockUser.role = '/bidAdmin'
    mockUser.menuPermissions = []
  })

  it('状态没变、notes 改了时不应调用 updateEvaluationForm', async () => {
    const { projectLifecycleApi } = await import('@/api/modules/projectLifecycle.js')
    const wrapper = await mountEvaluationStage()
    // 模拟已加载的 view：subStage = IN_PROGRESS，notes = '旧 notes'
    wrapper.vm.view = { subStage: 'IN_PROGRESS', notes: '旧 notes' }
    // 用户没改状态，只改了 notes
    wrapper.vm.targetSubStage = 'IN_PROGRESS'
    wrapper.vm.evaluationNotes = '新 notes 内容'
    wrapper.vm.evidenceDocIds = [50]
    wrapper.vm.evidenceUploadRef = { getPendingFileIds: () => [], clearPendingFileIds: () => {} }

    projectLifecycleApi.updateEvaluationForm.mockClear()
    projectLifecycleApi.transitionEvaluationSubStage.mockClear()

    await wrapper.vm.handleSubmit()

    // 关键断言：不应调用 /form 端点（DTO 强制 5 字段必填，notes 字段不存在）
    expect(projectLifecycleApi.updateEvaluationForm).not.toHaveBeenCalled()
    // 状态没变也不应调 /sub-stage（policy 在 current==requested 时会 Deny）
    expect(projectLifecycleApi.transitionEvaluationSubStage).not.toHaveBeenCalled()
    // 但应继续推进到结果确认阶段
    expect(projectLifecycleApi.advanceEvaluation).toHaveBeenCalled()
  })

  it('状态变化时应调 /sub-stage 端点（携带 notes）', async () => {
    const { projectLifecycleApi } = await import('@/api/modules/projectLifecycle.js')
    const wrapper = await mountEvaluationStage()
    wrapper.vm.view = { subStage: 'IN_PROGRESS', notes: '旧 notes' }
    wrapper.vm.targetSubStage = 'AWAITING_BOARD'
    wrapper.vm.evaluationNotes = '新 notes'
    wrapper.vm.evidenceDocIds = [50]
    wrapper.vm.evidenceUploadRef = { getPendingFileIds: () => [], clearPendingFileIds: () => {} }

    projectLifecycleApi.updateEvaluationForm.mockClear()
    projectLifecycleApi.transitionEvaluationSubStage.mockClear()

    await wrapper.vm.handleSubmit()

    expect(projectLifecycleApi.transitionEvaluationSubStage).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ targetSubStage: 'AWAITING_BOARD', notes: '新 notes' })
    )
    expect(projectLifecycleApi.updateEvaluationForm).not.toHaveBeenCalled()
  })
})

// CO-495: 评标中阶段删除「评标中」选项
describe('EvaluationStage 评标状态选项 - CO-495', () => {
  beforeEach(() => {
    mockUser.role = '/bidAdmin'
    mockUser.menuPermissions = []
  })

  it('不渲染「评标中」选项', async () => {
    const wrapper = await mountEvaluationStage()
    const chips = wrapper.findAll('.status-chip')
    const labels = chips.map(c => c.text())
    expect(labels).not.toContain('评标中')
  })

  it('仍渲染其他 3 个评标状态选项', async () => {
    const wrapper = await mountEvaluationStage()
    const chips = wrapper.findAll('.status-chip')
    const labels = chips.map(c => c.text())
    // CO-571: 顺序调整为 评标结果已出，待上会 → 评标结果公示 → 评标结果已出
    expect(labels).toEqual([
      '评标结果已出，待上会',
      '评标结果公示',
      '评标结果已出',
    ])
  })
})
