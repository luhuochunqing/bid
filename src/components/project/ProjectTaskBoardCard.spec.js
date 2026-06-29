import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import { useProjectStore } from '@/stores/project'
import ProjectTaskBoardCard from './ProjectTaskBoardCard.vue'

vi.mock('@/api/modules/taskStatusDict.js', () => ({
  taskStatusDictApi: {
    list: vi.fn().mockResolvedValue({
      success: true,
      data: [
        { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false },
        { code: 'COMPLETED', name: '已完成', category: 'CLOSED', color: '#67c23a', sortOrder: 40, initial: false, terminal: true },
      ],
    }),
  },
}))

const baseStubs = {
  ElCard: {
    template: '<section><slot name="header" /><slot /></section>',
  },
  ElButton: {
    template: '<button v-bind="$attrs" type="button"><slot /></button>',
  },
  ElIcon: {
    template: '<span><slot /></span>',
  },
  TaskBoard: {
    name: 'TaskBoard',
    emits: ['task-click', 'status-change', 'generate-tasks', 'add-deliverable', 'remove-deliverable', 'submit-to-document'],
    template: '<div class="task-board-stub" />',
  },
  ElDrawer: {
    name: 'ElDrawer',
    props: ['modelValue', 'title', 'size', 'direction'],
    emits: ['update:modelValue'],
    template: '<aside v-if="modelValue" class="el-drawer-stub"><header>{{ title }}</header><slot /><footer><slot name="footer" /></footer></aside>',
  },
  TaskForm: {
    name: 'TaskForm',
    props: ['modelValue', 'mode'],
    emits: ['update:modelValue', 'submit', 'attachment-preview'],
    template: '<div class="task-form-stub" />',
    methods: {
      submit() {
        return { valid: true, data: { ...this.modelValue } }
      },
    },
  },
}

describe('ProjectTaskBoardCard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const userStore = useUserStore()
    userStore.currentUser = { id: 1, role: 'admin' }
  })
  it('exposes independent tender breakdown entry separately from score draft decomposition', async () => {
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [],
        projectId: 12,
      },
      global: {
        stubs: baseStubs,
      },
    })

    await wrapper.find('[data-test="tender-breakdown-button"]').trigger('click')
    await wrapper.find('[data-test="score-draft-button"]').trigger('click')

    expect(wrapper.emitted('tender-breakdown')).toHaveLength(1)
    expect(wrapper.emitted('score-draft-decompose')).toHaveLength(1)
  })

  it('keeps task board header actions visually separated by action purpose', () => {
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [],
        projectId: 12,
      },
      global: {
        stubs: baseStubs,
      },
    })

    expect(wrapper.find('[data-test="tender-breakdown-button"]').classes()).toEqual(
      expect.arrayContaining(['header-action', 'header-action--tender']),
    )
    expect(wrapper.find('[data-test="score-draft-button"]').classes()).toEqual(
      expect.arrayContaining(['header-action', 'header-action--score']),
    )
  })

  it('opens drawer in create mode when add-task button clicked', async () => {
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    await wrapper.find('[data-test="add-task-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.vm.drawerVisible).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('create')
    expect(wrapper.vm.editingTask).toEqual({})
    // Regression: the button must NOT emit the legacy `add-task` event,
    // which would trigger the parent's placeholder-creation path and
    // produce a duplicate task alongside the drawer save.
    expect(wrapper.emitted('add-task')).toBeFalsy()
  })

  it('opens drawer in view mode when TaskBoard emits task-click for an existing task', async () => {
    // Regression for IJSVX7 问题一：已创建任务表单仍可编辑
    // 修复前：drawerMode='edit' → 表单字段可改
    // 修复后：drawerMode='view' → TaskForm readonly，所有字段只读
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [{ id: 1, name: 'T', status: 'TODO' }],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    const board = wrapper.findComponent({ name: 'TaskBoard' })
    board.vm.$emit('task-click', { id: 1, name: 'T', status: 'TODO' })
    await flushPromises()

    expect(wrapper.vm.drawerVisible).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('view')
    expect(wrapper.vm.editingTask.id).toBe(1)
    expect(wrapper.vm.editingTask.name).toBe('T')
  })

  it('hides the save action button when drawer opens in view mode', async () => {
    // Regression for IJSVX7：view mode 下"保存"按钮必须隐藏，
    // 否则分配人/执行人仍可通过底部按钮触发 mode='view' 下的提交。
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [{ id: 2, name: 'Locked', status: 'TODO' }],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    const board = wrapper.findComponent({ name: 'TaskBoard' })
    board.vm.$emit('task-click', { id: 2, name: 'Locked', status: 'TODO' })
    await flushPromises()

    expect(wrapper.vm.drawerMode).toBe('view')
    expect(wrapper.find('[data-test="task-drawer-save"]').exists()).toBe(false)
  })

  it('emits save-task with valid data when the save action fires', async () => {
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    await wrapper.find('[data-test="add-task-button"]').trigger('click')
    await flushPromises()
    wrapper.vm.editingTask = { name: 'New' }
    await flushPromises()
    await wrapper.vm.handleSaveTask()

    const emitted = wrapper.emitted('save-task')
    expect(emitted).toBeTruthy()
    expect(emitted[0][0]).toEqual(expect.objectContaining({ mode: 'create', data: expect.objectContaining({ name: 'New' }) }))
    expect(wrapper.vm.drawerVisible).toBe(false)
  })

  it('forwards saved deliverable payload from TaskBoard without dropping the task id', async () => {
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [{ id: 31, name: 'T', status: 'TODO' }],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    wrapper.findComponent({ name: 'TaskBoard' }).vm.$emit('add-deliverable', 31, { id: 501, name: '附件.docx' })
    await flushPromises()

    expect(wrapper.emitted('add-deliverable')?.[0]).toEqual([31, { id: 501, name: '附件.docx' }])
  })

  it('opens saved task attachment preview from the drawer container', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [{ id: 31, name: 'T', status: 'TODO' }],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    wrapper.vm.openView({ id: 31, name: 'T', status: 'TODO' })
    await flushPromises()
    wrapper.findComponent({ name: 'TaskForm' }).vm.$emit('attachment-preview', {
      name: '任务附件.docx',
      url: '/api/projects/12/documents/801/download',
    })

    expect(openSpy).toHaveBeenCalledWith('/api/projects/12/documents/801/download', '_blank', 'noopener')
    openSpy.mockRestore()
  })
})

describe('CO-345: 任务看板顶部按钮按角色预过滤', () => {
  beforeEach(() => setActivePinia(createPinia()))

  function mountWithRole(role, props = {}) {
    const userStore = useUserStore()
    userStore.currentUser = { id: 1, role }
    return mount(ProjectTaskBoardCard, {
      props: {
        tasks: [],
        projectId: 12,
        canManageProjectTasks: true,
        ...props,
      },
      global: { stubs: baseStubs },
    })
  }

  it('admin 角色：3 个管理按钮都可见', () => {
    const w = mountWithRole('admin')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(true)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(true)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(true)
  })

  it('/bidAdmin 角色：3 个管理按钮都可见', () => {
    const w = mountWithRole('/bidAdmin')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(true)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(true)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(true)
  })

  it('bid-TeamLeader 角色：3 个管理按钮都可见', () => {
    const w = mountWithRole('bid-TeamLeader')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(true)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(true)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(true)
  })

  it('bid-projectLeader 角色：3 个管理按钮都可见', () => {
    const w = mountWithRole('bid-projectLeader')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(true)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(true)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(true)
  })

  it('bid-Team 角色：3 个管理按钮都可见', () => {
    const w = mountWithRole('bid-Team')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(true)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(true)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(true)
  })

  it('bid-otherDept 角色：3 个管理按钮都不可见', () => {
    const w = mountWithRole('bid-otherDept')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(false)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(false)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(false)
  })

  it('bid-administration 角色：3 个管理按钮都不可见', () => {
    const w = mountWithRole('bid-administration')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(false)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(false)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(false)
  })

  it('staff 角色：3 个管理按钮都不可见', () => {
    const w = mountWithRole('staff')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(false)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(false)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(false)
  })

  it('重置任务按钮已删除（任何角色下都不出现）', () => {
    const adminWrapper = mountWithRole('admin')
    // 重置任务按钮没有 data-test，通过文本匹配
    const resetButtons = adminWrapper.findAll('button').filter(b => b.text().includes('重置任务'))
    expect(resetButtons).toHaveLength(0)
  })
})

// CO-397: 任务详情抽屉增加提交审核/驳回/通过按钮
describe('CO-397: task drawer review buttons', () => {
  beforeEach(() => setActivePinia(createPinia()))

  function mountWithUser({ role, userId, project = null, task }) {
    const userStore = useUserStore()
    userStore.currentUser = { id: userId, role }
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        tasks: task ? [task] : [],
        projectId: 12,
        canManageProjectTasks: true,
      },
      global: { stubs: baseStubs },
    })
    if (project) {
      // 通过 store 注入 currentProject（CO-387 primaryLeadUserId/secondaryLeadUserId）
      const projectStore = useProjectStore()
      projectStore.currentProject = project
    }
    return wrapper
  }

  function openDrawer(wrapper, task) {
    wrapper.vm.openView(task)
  }

  // 测试要点 1: TODO 状态 + 执行人登录 → 显示「提交审核」按钮
  it('TODO task + assignee: shows 提交审核 button', async () => {
    const wrapper = mountWithUser({
      role: 'bid-Team', userId: 9,
      task: { id: 1, name: 'T1', status: 'TODO', assigneeId: 9 },
    })
    openDrawer(wrapper, { id: 1, name: 'T1', status: 'TODO', assigneeId: 9 })
    await flushPromises()
    expect(wrapper.find('[data-test="task-drawer-submit-review"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="task-drawer-reject"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="task-drawer-approve"]').exists()).toBe(false)
  })

  // 测试要点 2: TODO 状态 + 非执行人登录 → 不显示「提交审核」
  it('TODO task + non-assignee: hides 提交审核 button', async () => {
    const wrapper = mountWithUser({
      role: 'bid-Team', userId: 9,
      task: { id: 1, name: 'T1', status: 'TODO', assigneeId: 999 },
    })
    openDrawer(wrapper, { id: 1, name: 'T1', status: 'TODO', assigneeId: 999 })
    await flushPromises()
    expect(wrapper.find('[data-test="task-drawer-submit-review"]').exists()).toBe(false)
  })

  // 测试要点 3: REVIEW 状态 + 投标管理员 → 显示「驳回」「通过」
  it('REVIEW task + bidAdmin: shows 驳回 and 通过', async () => {
    const wrapper = mountWithUser({
      role: '/bidAdmin', userId: 100,
      task: { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 },
    })
    openDrawer(wrapper, { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 })
    await flushPromises()
    expect(wrapper.find('[data-test="task-drawer-reject"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="task-drawer-approve"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="task-drawer-submit-review"]').exists()).toBe(false)
  })

  // 测试要点 4: REVIEW 状态 + 投标组长 → 显示「驳回」「通过」
  it('REVIEW task + bid-TeamLeader: shows 驳回 and 通过', async () => {
    const wrapper = mountWithUser({
      role: 'bid-TeamLeader', userId: 101,
      task: { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 },
    })
    openDrawer(wrapper, { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 })
    await flushPromises()
    expect(wrapper.find('[data-test="task-drawer-reject"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="task-drawer-approve"]').exists()).toBe(true)
  })

  // 测试要点 5: REVIEW 状态 + 项目投标负责人 → 显示「驳回」「通过」
  it('REVIEW task + primary lead: shows 驳回 and 通过', async () => {
    const wrapper = mountWithUser({
      role: 'bid-projectLeader', userId: 50,
      project: { id: 12, primaryLeadUserId: 50, secondaryLeadUserId: null },
      task: { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 },
    })
    openDrawer(wrapper, { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 })
    await flushPromises()
    expect(wrapper.find('[data-test="task-drawer-reject"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="task-drawer-approve"]').exists()).toBe(true)
  })

  // 测试要点 6: REVIEW 状态 + 项目投标辅助人员 → 显示「驳回」「通过」
  it('REVIEW task + secondary lead: shows 驳回 and 通过', async () => {
    const wrapper = mountWithUser({
      role: 'bid-Team', userId: 51,
      project: { id: 12, primaryLeadUserId: null, secondaryLeadUserId: 51 },
      task: { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 },
    })
    openDrawer(wrapper, { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 })
    await flushPromises()
    expect(wrapper.find('[data-test="task-drawer-reject"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="task-drawer-approve"]').exists()).toBe(true)
  })

  // 测试要点 7: REVIEW 状态 + 其他角色 → 不显示「驳回」「通过」
  it('REVIEW task + non-reviewer: hides 驳回 and 通过', async () => {
    const wrapper = mountWithUser({
      role: 'bid-Team', userId: 80,
      project: { id: 12, primaryLeadUserId: 50, secondaryLeadUserId: 51 },
      task: { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 },
    })
    openDrawer(wrapper, { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 })
    await flushPromises()
    expect(wrapper.find('[data-test="task-drawer-reject"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="task-drawer-approve"]').exists()).toBe(false)
  })

  // 测试要点 8: 点击提交审核 → emit status-change with REVIEW + 关闭抽屉
  it('clicking 提交审核 emits status-change REVIEW and closes drawer', async () => {
    const wrapper = mountWithUser({
      role: 'bid-Team', userId: 9,
      task: { id: 1, name: 'T1', status: 'TODO', assigneeId: 9 },
    })
    openDrawer(wrapper, { id: 1, name: 'T1', status: 'TODO', assigneeId: 9 })
    await flushPromises()
    await wrapper.find('[data-test="task-drawer-submit-review"]').trigger('click')
    const emitted = wrapper.emitted('status-change')
    expect(emitted).toBeTruthy()
    expect(emitted[0]).toEqual([
      { id: 1, name: 'T1', status: 'TODO', assigneeId: 9 },
      'REVIEW',
    ])
    expect(wrapper.vm.drawerVisible).toBe(false)
  })

  // 测试要点 9: 点击驳回 → emit status-change with TODO + 关闭抽屉
  it('clicking 驳回 emits status-change TODO and closes drawer', async () => {
    const wrapper = mountWithUser({
      role: '/bidAdmin', userId: 100,
      task: { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 },
    })
    openDrawer(wrapper, { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 })
    await flushPromises()
    await wrapper.find('[data-test="task-drawer-reject"]').trigger('click')
    const emitted = wrapper.emitted('status-change')
    expect(emitted).toBeTruthy()
    expect(emitted[0]).toEqual([
      { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 },
      'TODO',
    ])
    expect(wrapper.vm.drawerVisible).toBe(false)
  })

  // 测试要点 10: 点击通过 → emit status-change with COMPLETED + 关闭抽屉
  it('clicking 通过 emits status-change COMPLETED and closes drawer', async () => {
    const wrapper = mountWithUser({
      role: '/bidAdmin', userId: 100,
      task: { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 },
    })
    openDrawer(wrapper, { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 })
    await flushPromises()
    await wrapper.find('[data-test="task-drawer-approve"]').trigger('click')
    const emitted = wrapper.emitted('status-change')
    expect(emitted).toBeTruthy()
    expect(emitted[0]).toEqual([
      { id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 },
      'COMPLETED',
    ])
    expect(wrapper.vm.drawerVisible).toBe(false)
  })

  // 补充：view 模式下不显示提交审核/驳回/通过（非 TODO/REVIEW 状态）
  it('COMPLETED task: hides all review buttons', async () => {
    const wrapper = mountWithUser({
      role: '/bidAdmin', userId: 100,
      task: { id: 3, name: 'T3', status: 'COMPLETED', assigneeId: 999 },
    })
    openDrawer(wrapper, { id: 3, name: 'T3', status: 'COMPLETED', assigneeId: 999 })
    await flushPromises()
    expect(wrapper.find('[data-test="task-drawer-submit-review"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="task-drawer-reject"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="task-drawer-approve"]').exists()).toBe(false)
  })

  // 补充：create 模式下不显示审核按钮
  it('create mode: hides all review buttons', async () => {
    const wrapper = mountWithUser({
      role: '/bidAdmin', userId: 100, task: null,
    })
    wrapper.vm.openCreate()
    await flushPromises()
    expect(wrapper.find('[data-test="task-drawer-submit-review"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="task-drawer-reject"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="task-drawer-approve"]').exists()).toBe(false)
  })
})
