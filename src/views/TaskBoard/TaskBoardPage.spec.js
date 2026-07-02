import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { computed, defineComponent } from 'vue'

// Mock API: dashboard for board items
const { mockDashboardItems } = vi.hoisted(() => ({
  mockDashboardItems: [
    { type: 'TASK', id: 1, title: '任务1', status: 'TODO', assigneeId: 1, projectId: 10, priority: 'HIGH' },
  ]
}))
vi.mock('@/api/modules/dashboard', () => ({
  tasksApi: {
    getBoardItems: vi.fn().mockResolvedValue({ data: mockDashboardItems }),
    updateStatus: vi.fn().mockResolvedValue({}),
  }
}))

// Mock API: tasks for detail
vi.mock('@/api/modules/tasks.js', () => ({
  tasksApi: {
    getTaskById: vi.fn().mockResolvedValue({
      data: {
        data: {
          id: 1,
          projectId: 10,
          title: '任务1',
          content: '这是详细说明内容',
          status: 'TODO',
          priority: 'HIGH',
          assigneeId: 1,
          assigneeName: '张三',
          completionNotes: '',
          attachments: [],
          deliverables: [],
        }
      }
    }),
  }
}))
vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: {
    updateTask: vi.fn().mockResolvedValue({}),
    updateTaskStatus: vi.fn().mockResolvedValue({}),
  }
}))
vi.mock('@/api/modules/taskDeliverables.js', () => ({
  createTaskDeliverable: vi.fn().mockResolvedValue({}),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

// CO-370: mock uploadTaskFilesWithFallback 以便测试 handleSubmitForReview 的调用契约
const mockUploadTaskFilesWithFallback = vi.fn().mockResolvedValue(true)
vi.mock('@/composables/projectDetail/taskAssigneePayload', () => ({
  uploadTaskFilesWithFallback: (...args) => mockUploadTaskFilesWithFallback(...args),
}))

// CO-474: mock taskBackendToCard 让 TaskBoardTaskDrawer 能独立测试
vi.mock('@/views/Project/project-utils.js', () => ({
  taskBackendToCard: (task) => ({ ...task, deliverableFiles: [] }),
}))

// CO-474: mock validateSubmitForReview 让 TaskBoardTaskDrawer 提交审核能跑通
vi.mock('@/composables/useTaskSubmissionValidation.js', () => ({
  validateSubmitForReview: () => ({ valid: true }),
}))

import TaskBoardPage from './TaskBoardPage.vue'

// 为 TaskForm 定义真实组件 stub，暴露 canDeliver computed 和 submitForReview 方法
const TaskFormStub = defineComponent({
  name: 'TaskFormStub',
  template: '<div class="task-form-stub"><slot /></div>',
  props: ['modelValue', 'mode'],
  setup(props, { expose }) {
    const canDeliver = computed(() => props.mode === 'view' && props.modelValue?.status === 'TODO')
    function submitForReview() {
      return {
        valid: true,
        data: {
          ...(props.modelValue || {}),
          status: 'REVIEW',
          deliverables: [{ id: 1, name: '交付物1.pdf' }],
          deliverableFiles: [],
          completionNotes: '已完成所有工作',
        }
      }
    }
    function submit() {
      return { valid: true, data: { ...(props.modelValue || {}) } }
    }
    function validate() {
      return ''
    }
    expose({ canDeliver, submitForReview, submit, validate })
    return { canDeliver, submitForReview, submit, validate }
  }
})

const stubs = {
  TaskBoardCard: {
    template: '<div class="task-card-stub" @click="$emit(\'task-click\', item)"><slot /></div>',
    props: ['item', 'availableStatuses'],
  },
  TaskForm: TaskFormStub,
  'el-drawer': {
    template: '<div class="el-drawer-stub" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'size', 'direction'],
  },
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': { props: ['disabled', 'type', 'size', 'plain', 'loading'], template: '<button :disabled="disabled"><slot /></button>' },
  'el-badge': { template: '<span><slot /></span>' },
  'el-empty': { template: '<div>暂无任务</div>' },
  'el-icon': { template: '<span />' },
  'el-alert': { template: '<div><slot /></div>' },
}

function createWrapper() {
  return mount(TaskBoardPage, {
    global: { stubs, plugins: [createPinia()] },
  })
}

describe('TaskBoardPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockDashboardItems.length = 1
    Object.assign(mockDashboardItems[0], {
      type: 'TASK', id: 1, title: '任务1', status: 'TODO', assigneeId: 1, projectId: 10,
    })
  })

  it('renders 3 columns', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    const columns = wrapper.findAll('.board-column')
    expect(columns.length).toBe(3)
  })

  it('renders task cards via TaskBoardCard stub', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    const cards = wrapper.findAll('.task-card-stub')
    expect(cards.length).toBe(1)
  })

  it('emits task-click from card and opens drawer', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    const card = wrapper.find('.task-card-stub')
    await card.trigger('click')
    await flushPromises()
    const drawer = wrapper.find('.el-drawer-stub')
    expect(drawer.exists()).toBe(true)
  })

  it('shows submit-review button when canDeliver is true', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    const card = wrapper.find('.task-card-stub')
    await card.trigger('click')
    await flushPromises()

    const footerBtns = wrapper.findAll('.el-drawer-stub button')
    const submitBtn = footerBtns.find(b => b.text().includes('提交审核'))
    expect(submitBtn).toBeDefined()
  })

  it('submit-review triggers API calls', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    const card = wrapper.find('.task-card-stub')
    await card.trigger('click')
    await flushPromises()

    // 点击提交审核按钮
    const footerBtns = wrapper.findAll('.el-drawer-stub button')
    const submitBtn = footerBtns.find(b => b.text().includes('提交审核'))
    expect(submitBtn).toBeDefined()
    await submitBtn.trigger('click')
    await flushPromises()

    const { projectsApi } = await import('@/api/modules/projects.js')
    // 应调用 updateTaskStatus 更新状态为 REVIEW
    expect(projectsApi.updateTaskStatus).toHaveBeenCalled()
  })
})

// CO-370 场景2+3: TaskBoardPage handleSubmitForReview 必须走 uploadTaskFilesWithFallback
// （而不是旧的 FormData + createTaskDeliverable 直传）
describe('CO-370 TaskBoardPage handleSubmitForReview', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockUploadTaskFilesWithFallback.mockResolvedValue(true)
  })

  it('场景2+3: 提交审核时调用 uploadTaskFilesWithFallback（而非直接 FormData createTaskDeliverable）', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    const card = wrapper.find('.task-card-stub')
    await card.trigger('click')
    await flushPromises()

    const footerBtns = wrapper.findAll('.el-drawer-stub button')
    const submitBtn = footerBtns.find(b => b.text().includes('提交审核'))
    await submitBtn.trigger('click')
    await flushPromises()

    // 必须调用 uploadTaskFilesWithFallback
    expect(mockUploadTaskFilesWithFallback).toHaveBeenCalledTimes(1)
    // 第1个参数必须包含 assigneeId 以便后续权限校验
    const taskArg = mockUploadTaskFilesWithFallback.mock.calls[0][0]
    expect(taskArg).toEqual(expect.objectContaining({
      id: 1,
      projectId: 10,
      assigneeId: 1,
    }))
    // 第3个参数是 ctx，必须包含 projectStore / projectId / userStore
    const ctxArg = mockUploadTaskFilesWithFallback.mock.calls[0][2]
    expect(ctxArg).toEqual(expect.objectContaining({
      projectId: 10,
      projectStore: expect.anything(),
      userStore: expect.anything(),
    }))
    // 第4个参数是错误提示 map，必须包含 deliverables 错误文案
    const errMsgArg = mockUploadTaskFilesWithFallback.mock.calls[0][3]
    expect(errMsgArg).toEqual(expect.objectContaining({
      deliverables: expect.any(String),
      attachments: expect.any(String),
    }))

    // 不应再调用旧的 FormData 直传 createTaskDeliverable
    const { createTaskDeliverable } = await import('@/api/modules/taskDeliverables.js')
    expect(createTaskDeliverable).not.toHaveBeenCalled()
  })

  it('场景2+3: uploadTaskFilesWithFallback 返回 false 时不继续更新状态', async () => {
    mockUploadTaskFilesWithFallback.mockResolvedValue(false)
    const wrapper = createWrapper()
    await flushPromises()
    const card = wrapper.find('.task-card-stub')
    await card.trigger('click')
    await flushPromises()

    const footerBtns = wrapper.findAll('.el-drawer-stub button')
    const submitBtn = footerBtns.find(b => b.text().includes('提交审核'))
    await submitBtn.trigger('click')
    await flushPromises()

    const { projectsApi } = await import('@/api/modules/projects.js')
    expect(projectsApi.updateTaskStatus).not.toHaveBeenCalled()
  })
})