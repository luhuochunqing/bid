import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: { createTaskDeliverable: vi.fn(), updateTask: vi.fn(), updateTaskStatus: vi.fn() }
}))
vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: { approveBid: vi.fn(), rejectBid: vi.fn() }
}))

// 用户 store mock
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
  'el-dialog': {
    template: '<div v-if="modelValue" class="el-dialog-stub"><div class="dialog-title">{{ title }}</div><slot /></div>',
    props: ['modelValue', 'title', 'width'],
  },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div class="el-form-item-stub"><span class="form-label">{{ label }}</span><slot /></div>', props: ['label'] },
  'el-input': { template: '<input />' },
  'el-upload': { template: '<div><slot /></div>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<span />' },
  'ProjectDocumentTable': { template: '<div class="project-documents-stub" />' },
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
      type: 'BID_REVIEW', id: 1, title: '标书审核', status: 'REVIEW', projectId: 10, reviewerId: 999,
    }
    const wrapper = createWrapper(item)
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const rejectBtn = buttons.find(b => b.text().includes('驳回'))
    const approveBtn = buttons.find(b => b.text().includes('通过审核'))
    expect(rejectBtn?.attributes('disabled')).toBeDefined()
    expect(approveBtn?.attributes('disabled')).toBeDefined()
  })

  it('BID_REVIEW audit buttons are enabled when current user is reviewer', async () => {
    const item = {
      type: 'BID_REVIEW', id: 1, title: '标书审核', status: 'REVIEW', projectId: 10, reviewerId: 1,
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
      type: 'TASK', id: 1, title: '任务1', status: 'TODO', assigneeId: 1, deliverables: [{ name: 'file.pdf' }],
    }
    const wrapper = createWrapper(item)
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const uploadBtn = buttons.find(b => b.text().includes('上传交付物'))
    expect(uploadBtn?.attributes('disabled')).toBeUndefined()
  })

  it('TASK action buttons are disabled when current user is not assignee', async () => {
    const item = {
      type: 'TASK', id: 1, title: '任务1', status: 'TODO', assigneeId: 999, deliverables: [],
    }
    const wrapper = createWrapper(item)
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const uploadBtn = buttons.find(b => b.text().includes('上传交付物'))
    expect(uploadBtn?.attributes('disabled')).toBeDefined()
  })

  it('hasDeliverable returns true when deliverables array has items', async () => {
    const item = {
      type: 'TASK', id: 1, title: '任务1', status: 'TODO', assigneeId: 1, deliverables: [{ name: 'file.pdf' }],
    }
    const wrapper = createWrapper(item)
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const submitBtn = buttons.find(b => b.text().includes('提交'))
    expect(submitBtn?.attributes('disabled')).toBeUndefined()
  })

  // === 评论2：独立上传交付物弹窗测试 ===
  it('upload dialog opens when clicking 上传交付物 button', async () => {
    const item = {
      type: 'TASK', id: 1, title: '任务1', status: 'TODO', assigneeId: 1, deliverables: [],
    }
    const wrapper = createWrapper(item)
    await flushPromises()

    // dialog 还未打开时应有且仅有一个可见的 dialog（提交任务弹窗，v-if 为 false）
    let visibleDialogs = wrapper.findAll('.el-dialog-stub')
    expect(visibleDialogs.length).toBe(0)

    // 点击上传交付物按钮
    const uploadBtn = wrapper.findAll('button').find(b => b.text().includes('上传交付物'))
    await uploadBtn.trigger('click')
    await flushPromises()

    // 弹窗应该可见
    visibleDialogs = wrapper.findAll('.el-dialog-stub')
    expect(visibleDialogs.length).toBe(1)
  })

  it('upload dialog title is 上传交付物', async () => {
    const item = {
      type: 'TASK', id: 1, title: '任务1', status: 'TODO', assigneeId: 1, deliverables: [],
    }
    const wrapper = createWrapper(item)
    await flushPromises()

    const uploadBtn = wrapper.findAll('button').find(b => b.text().includes('上传交付物'))
    await uploadBtn.trigger('click')
    await flushPromises()

    // 弹窗 title 属性值应为"上传交付物"
    const visibleDialog = wrapper.find('.el-dialog-stub')
    expect(visibleDialog.find('.dialog-title').text()).toBe('上传交付物')
  })

  it('upload dialog contains form labels 交付物名称, 交付物类型, 上传文件', async () => {
    const item = {
      type: 'TASK', id: 1, title: '任务1', status: 'TODO', assigneeId: 1, deliverables: [],
    }
    const wrapper = createWrapper(item)
    await flushPromises()

    const uploadBtn = wrapper.findAll('button').find(b => b.text().includes('上传交付物'))
    await uploadBtn.trigger('click')
    await flushPromises()

    // 可见 dialog 内的表单标签
    const labels = wrapper.findAll('.form-label').map(el => el.text())
    expect(labels).toContain('交付物名称')
    expect(labels).toContain('交付物类型')
    expect(labels).toContain('上传文件')
  })

  it('upload dialog renders form fields (name input, type select, file upload)', async () => {
    const item = {
      type: 'TASK', id: 1, title: '任务1', status: 'TODO', assigneeId: 1, deliverables: [],
    }
    const wrapper = createWrapper(item)
    await flushPromises()

    const uploadBtn = wrapper.findAll('button').find(b => b.text().includes('上传交付物'))
    await uploadBtn.trigger('click')
    await flushPromises()

    // 确认三个表单标签都存在
    const labels = wrapper.findAll('.form-label').map(el => el.text())
    expect(labels).toContain('交付物名称')
    expect(labels).toContain('交付物类型')
    expect(labels).toContain('上传文件')
  })
})