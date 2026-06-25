import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: { createTaskDeliverable: vi.fn(), updateTask: vi.fn(), updateTaskStatus: vi.fn() }
}))
vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: { approveBid: vi.fn(), rejectBid: vi.fn() }
}))

// 用户 store mock：提取为可变对象，支持按测试配置不同用户身份
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
  'el-button': { props: ['disabled', 'type', 'size', 'plain', 'loading'], template: '<button :disabled="disabled"><slot /></button>' },
  'el-icon': { template: '<span />' },
  'el-dialog': { template: '<div v-if="modelValue"><slot /></div>', props: ['modelValue', 'title', 'width'] },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>', props: ['label'] },
  'el-input': { template: '<input />' },
  'el-upload': { template: '<div><slot /></div>' },
  ProjectDocumentTable: { template: '<div class="project-documents-stub" />' },
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
    const item = {
      type: 'BID_REVIEW',
      id: 1,
      title: '标书审核',
      status: 'REVIEW',
      projectId: 10,
      reviewerId: 999,  // 非当前用户
    }
    const wrapper = createWrapper(item)
    await flushPromises()
    const buttons = wrapper.findAll('button')
    // 驳回和通过审核按钮应该 disabled
    const rejectBtn = buttons.find(b => b.text().includes('驳回'))
    const approveBtn = buttons.find(b => b.text().includes('通过审核'))
    expect(rejectBtn?.attributes('disabled')).toBeDefined()
    expect(approveBtn?.attributes('disabled')).toBeDefined()
  })

  it('BID_REVIEW audit buttons are enabled when current user is reviewer', async () => {
    const item = {
      type: 'BID_REVIEW',
      id: 1,
      title: '标书审核',
      status: 'REVIEW',
      projectId: 10,
      reviewerId: 1,  // 当前用户 id=1
    }
    const wrapper = createWrapper(item)
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const rejectBtn = buttons.find(b => b.text().includes('驳回'))
    const approveBtn = buttons.find(b => b.text().includes('通过审核'))
    expect(rejectBtn?.attributes('disabled')).toBeUndefined()
    expect(approveBtn?.attributes('disabled')).toBeUndefined()
  })

  it('TASK action buttons are enabled when current user is assignee', async () => {
    const item = {
      type: 'TASK',
      id: 1,
      title: '任务1',
      status: 'TODO',
      assigneeId: 1,  // 当前用户 id=1
      deliverables: [{ name: 'file.pdf' }],
    }
    const wrapper = createWrapper(item)
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const uploadBtn = buttons.find(b => b.text().includes('交付物上传'))
    expect(uploadBtn?.attributes('disabled')).toBeUndefined()
  })

  it('TASK action buttons are disabled when current user is not assignee', async () => {
    const item = {
      type: 'TASK',
      id: 1,
      title: '任务1',
      status: 'TODO',
      assigneeId: 999,  // 非当前用户
      deliverables: [],
    }
    const wrapper = createWrapper(item)
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const uploadBtn = buttons.find(b => b.text().includes('交付物上传'))
    expect(uploadBtn?.attributes('disabled')).toBeDefined()
  })

it('hasDeliverable returns true when deliverables array has items', async () => {
	    const item = {
	      type: 'TASK',
	      id: 1,
	      title: '任务1',
	      status: 'TODO',
	      assigneeId: 1,
	      deliverables: [{ name: 'file.pdf' }],
	    }
	    const wrapper = createWrapper(item)
	    await flushPromises()
	    const buttons = wrapper.findAll('button')
	    const submitBtn = buttons.find(b => b.text().includes('提交'))
	    // assigneeId=1 且有 deliverables，提交按钮应可点击
	    expect(submitBtn?.attributes('disabled')).toBeUndefined()
	  })

	  it('点击卡片根元素 emit task-click', async () => {
	    const item = {
	      type: 'TASK',
	      id: 1,
	      title: '任务1',
	      status: 'TODO',
	      assigneeId: 1,
	    }
	    const wrapper = createWrapper(item)
	    await flushPromises()
	    await wrapper.find('.task-card').trigger('click')
	    expect(wrapper.emitted('task-click')).toBeTruthy()
	    expect(wrapper.emitted('task-click')[0]).toEqual([item])
	  })

	  it('点击内部按钮不触发 task-click', async () => {
	    const item = {
	      type: 'TASK',
	      id: 1,
	      title: '任务1',
	      status: 'TODO',
	      assigneeId: 1,
	      deliverables: [{ name: 'file.pdf' }],
	    }
	    const wrapper = createWrapper(item)
	    await flushPromises()
	    // 点击"提交"按钮，不应触发 task-click
	    const submitBtn = wrapper.findAll('button').find(b => b.text().includes('提交'))
	    await submitBtn.trigger('click')
	    expect(wrapper.emitted('task-click')).toBeFalsy()
	  })
})
