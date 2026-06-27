import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: { createTaskDeliverable: vi.fn(), updateTask: vi.fn(), updateTaskStatus: vi.fn() }
}))
vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: { approveBid: vi.fn(), rejectBid: vi.fn() }
}))

const mockUserState = { currentUser: { id: 1, name: '当前用户' } }
vi.mock('@/stores/user.js', () => ({
  useUserStore: () => mockUserState
}))

vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

import TaskBoardCard from './TaskBoardCard.vue'

const stubs = {
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': {
    props: ['disabled', 'type', 'size', 'plain', 'loading'],
    template: '<button :disabled="disabled" :data-testid="$attrs[\'data-testid\']"><slot /></button>'
  },
  'el-icon': { template: '<span />' },
  'el-dialog': { template: '<div v-if="modelValue"><slot /></div>', props: ['modelValue', 'title', 'width'] },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>', props: ['label'] },
  'el-input': { template: '<input />' },
  'el-upload': { template: '<div><slot /></div>' },
  ProjectDocumentTable: { template: '<div class="project-documents" />', props: ['projectId', 'readonly'] },
  TaskBoardTaskActions: {
    template: `
      <div class="task-board-task-actions" v-if="shouldShow">
        <button data-testid="deliverable-upload-btn" :disabled="!isAssignee">交付物上传</button>
        <button data-testid="submit-task-btn" :disabled="!isAssignee || !hasDeliverable">提交</button>
      </div>
    `,
    props: ['item'],
    emits: ['deliverable-changed'],
    computed: {
      shouldShow() {
        return this.item.type === 'TASK'
          && this.item.status === 'TODO'
      },
      isAssignee() {
        return this.item.assigneeId === 1
      },
      hasDeliverable() {
        return Array.isArray(this.item.deliverables) && this.item.deliverables.length > 0
      }
    }
  },
  TaskBoardBidReviewActions: {
    template: `
      <div class="task-board-bid-review-actions">
        <button data-testid="reject-bid-btn" :disabled="!isReviewer">驳回</button>
        <button data-testid="approve-bid-btn" :disabled="!isReviewer">通过审核</button>
      </div>
    `,
    props: ['item'],
    emits: ['deliverable-changed'],
    computed: {
      isReviewer() {
        return this.item.reviewerId === 1
      }
    }
  },
}

function createMockTask(overrides = {}) {
  return {
    type: 'TASK',
    id: 1,
    title: '测试任务',
    status: 'TODO',
    assigneeId: 1,
    deliverables: [],
    ...overrides,
  }
}

function createMockBidReview(overrides = {}) {
  return {
    type: 'BID_REVIEW',
    id: 1,
    title: '标书审核',
    status: 'REVIEW',
    projectId: 10,
    reviewerId: 1,
    ...overrides,
  }
}

function createWrapper(item) {
  return mount(TaskBoardCard, {
    props: { item, availableStatuses: [] },
    global: { stubs, plugins: [createPinia()] },
  })
}

describe('TaskBoardCard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockUserState.currentUser = { id: 1, name: '当前用户' }
    vi.clearAllMocks()
  })

  it('BID_REVIEW audit buttons are disabled when current user is not reviewer', async () => {
    const item = createMockBidReview({ reviewerId: 999 })
    const wrapper = createWrapper(item)
    await flushPromises()
    const rejectBtn = wrapper.find('[data-testid="reject-bid-btn"]')
    const approveBtn = wrapper.find('[data-testid="approve-bid-btn"]')
    expect(rejectBtn.attributes('disabled')).toBeDefined()
    expect(approveBtn.attributes('disabled')).toBeDefined()
  })

  it('BID_REVIEW audit buttons are enabled when current user is reviewer', async () => {
    const item = createMockBidReview({ reviewerId: 1 })
    const wrapper = createWrapper(item)
    await flushPromises()
    const rejectBtn = wrapper.find('[data-testid="reject-bid-btn"]')
    const approveBtn = wrapper.find('[data-testid="approve-bid-btn"]')
    expect(rejectBtn.attributes('disabled')).toBeUndefined()
    expect(approveBtn.attributes('disabled')).toBeUndefined()
  })

  it('TASK action buttons are enabled when current user is assignee', async () => {
    const item = createMockTask({ assigneeId: 1, deliverables: [{ name: 'file.pdf' }] })
    const wrapper = createWrapper(item)
    await flushPromises()
    const uploadBtn = wrapper.find('[data-testid="deliverable-upload-btn"]')
    expect(uploadBtn.attributes('disabled')).toBeUndefined()
  })

  it('TASK action buttons are disabled when current user is not assignee', async () => {
    const item = createMockTask({ assigneeId: 999, deliverables: [] })
    const wrapper = createWrapper(item)
    await flushPromises()
    const uploadBtn = wrapper.find('[data-testid="deliverable-upload-btn"]')
    expect(uploadBtn.attributes('disabled')).toBeDefined()
  })

  it('hasDeliverable returns true when deliverables array has items', async () => {
    const item = createMockTask({ assigneeId: 1, deliverables: [{ name: 'file.pdf' }] })
    const wrapper = createWrapper(item)
    await flushPromises()
    const submitBtn = wrapper.find('[data-testid="submit-task-btn"]')
    expect(submitBtn.attributes('disabled')).toBeUndefined()
  })

  it('TASK in REVIEW status should NOT show upload/submit buttons for assignee', async () => {
    const item = createMockTask({ status: 'REVIEW', assigneeId: 1, deliverables: [{ name: 'file.pdf' }] })
    const wrapper = createWrapper(item)
    await flushPromises()
    const uploadBtn = wrapper.find('[data-testid="deliverable-upload-btn"]')
    const submitBtn = wrapper.find('[data-testid="submit-task-btn"]')
    expect(uploadBtn.exists()).toBe(false)
    expect(submitBtn.exists()).toBe(false)
  })

  it('TASK in COMPLETED status should NOT show upload/submit buttons for assignee', async () => {
    const item = createMockTask({ status: 'COMPLETED', assigneeId: 1, deliverables: [{ name: 'file.pdf' }] })
    const wrapper = createWrapper(item)
    await flushPromises()
    const uploadBtn = wrapper.find('[data-testid="deliverable-upload-btn"]')
    const submitBtn = wrapper.find('[data-testid="submit-task-btn"]')
    expect(uploadBtn.exists()).toBe(false)
    expect(submitBtn.exists()).toBe(false)
  })

  it('TASK in TODO status should show upload/submit buttons for assignee', async () => {
    const item = createMockTask({ status: 'TODO', assigneeId: 1, deliverables: [{ name: 'file.pdf' }] })
    const wrapper = createWrapper(item)
    await flushPromises()
    const uploadBtn = wrapper.find('[data-testid="deliverable-upload-btn"]')
    const submitBtn = wrapper.find('[data-testid="submit-task-btn"]')
    expect(uploadBtn.attributes('disabled')).toBeUndefined()
    expect(submitBtn.attributes('disabled')).toBeUndefined()
  })

  // === CO-338 恢复：卡片点击 emit task-click + 按钮冒泡隔离 ===
  it('emits task-click when card root is clicked', async () => {
    const item = createMockTask()
    const wrapper = createWrapper(item)
    await flushPromises()
    await wrapper.find('.task-card').trigger('click')
    expect(wrapper.emitted('task-click')).toBeTruthy()
    expect(wrapper.emitted('task-click')[0]).toEqual([item])
  })

  it('does NOT emit task-click when an action button is clicked (bubble isolated)', async () => {
    const item = createMockTask({ assigneeId: 1, deliverables: [{ name: 'file.pdf' }] })
    const wrapper = createWrapper(item)
    await flushPromises()
    await wrapper.find('[data-testid="submit-task-btn"]').trigger('click')
    expect(wrapper.emitted('task-click')).toBeFalsy()
  })

  it('wraps BID_REVIEW ProjectDocumentTable in overflow-x container', async () => {
    const item = createMockBidReview({ reviewerId: 1 })
    const wrapper = createWrapper(item)
    await flushPromises()
    expect(wrapper.find('.bid-review-documents').exists()).toBe(true)
  })
})
