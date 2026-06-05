import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
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
    emits: ['update:modelValue', 'submit'],
    template: '<div class="task-form-stub" />',
    methods: {
      submit() {
        return { valid: true, data: { ...this.modelValue } }
      },
    },
  },
}

describe('ProjectTaskBoardCard', () => {
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

  it('opens drawer in edit mode when TaskBoard emits task-click', async () => {
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
    expect(wrapper.vm.drawerMode).toBe('edit')
    expect(wrapper.vm.editingTask.id).toBe(1)
    expect(wrapper.vm.editingTask.name).toBe('T')
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
})
