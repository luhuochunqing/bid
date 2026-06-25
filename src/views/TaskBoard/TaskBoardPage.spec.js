import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { ref, computed, defineComponent } from 'vue'

// Mock composable
const mockItems = [
  { type: 'TASK', id: 1, title: '任务1', status: 'TODO', assigneeId: 1, projectId: 10, priority: 'HIGH' },
]
const mockLoadTasks = vi.fn()
vi.mock('@/views/TaskBoard/composables/useTaskBoard.js', () => ({
  useTaskBoard: () => ({
    items: mockItems,
    loading: false,
    error: '',
    columns: [
      { key: 'TODO', title: '待开始', color: '#909399' },
      { key: 'REVIEW', title: '待审核', color: '#e6a23c' },
      { key: 'COMPLETED', title: '已完成', color: '#67c23a' },
    ],
    availableStatuses: [{ code: 'TODO', name: '待开始' }],
    getTasksByStatus: (key) => key === 'TODO' ? mockItems : [],
    handleStatusChange: vi.fn(),
    handleDeliverableChanged: vi.fn(),
    loadTasks: mockLoadTasks,
  })
}))

// Mock API modules
vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: {
    createTaskDeliverable: vi.fn().mockResolvedValue({}),
    updateTask: vi.fn().mockResolvedValue({}),
    updateTaskStatus: vi.fn().mockResolvedValue({}),
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
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
      return { valid: true, data: { ...(props.modelValue || {}), status: 'REVIEW' } }
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
    global: {
      stubs,
      plugins: [createPinia()],
      directives: {  // #8 P2 mock v-loading 避免 warn 噪音
        loading: vi.fn(),
      },
    },
  })
}

describe('TaskBoardPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockItems.length = 1
    Object.assign(mockItems[0], {
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
    // #9 P2 参数级断言：应调用 updateTaskStatus 更新状态为 REVIEW
    expect(projectsApi.updateTaskStatus).toHaveBeenCalledWith(10, 1, 'REVIEW')
    // item 无 deliverableFiles，所以 updateTask 和 createTaskDeliverable 不会被调用
    expect(projectsApi.updateTask).not.toHaveBeenCalled()
    expect(projectsApi.createTaskDeliverable).not.toHaveBeenCalled()
  })
})