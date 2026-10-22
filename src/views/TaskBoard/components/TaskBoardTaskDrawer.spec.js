import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { defineComponent, ref } from 'vue'
import { useProjectStore } from '@/stores/project.js'
import { useUserStore } from '@/stores/user.js'
import { tasksApi } from '@/api/modules/tasks.js'
import { projectsApi } from '@/api/modules/projects.js'
import TaskBoardTaskDrawer from './TaskBoardTaskDrawer.vue'

vi.mock('@/api/modules/tasks.js', () => ({
  tasksApi: {
    getTaskById: vi.fn(),
  },
}))

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: {
    updateTask: vi.fn(),
    updateTaskStatus: vi.fn(),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  },
}))

const TaskFormStub = defineComponent({
  name: 'TaskFormStub',
  template: '<div class="task-form-stub" />',
  props: ['modelValue', 'mode'],
  setup(props, { expose }) {
    function submit() {
      return {
        valid: true,
        data: {
          ...(props.modelValue || {}),
          assigneeId: 99,
          owner: '新执行人',
          assignee: '新执行人',
        },
      }
    }
    expose({ submit, canDeliver: false })
    return { submit, canDeliver: false }
  },
})

describe('TaskBoardTaskDrawer - 保证金缴纳任务执行人放权与响应式更新', () => {
  let pinia
  let projectStore
  let userStore

  beforeEach(() => {
    setActivePinia(createPinia())
    pinia = createPinia()
    setActivePinia(pinia)
    projectStore = useProjectStore()
    userStore = useUserStore()
    vi.clearAllMocks()
  })

  it('当当前用户是项目的 projectLeaderId 时，保证金缴纳任务 TODO 状态应允许编辑执行人（显示保存按钮）', async () => {
    userStore.currentUser = { id: 68, name: '项目负责人' }
    projectStore.currentProject = {
      id: 48,
      projectLeaderId: 68,
      primaryLeadUserId: null,
      secondaryLeadUserId: null,
    }

    const wrapper = mount(TaskBoardTaskDrawer, {
      props: {
        modelValue: true,
      },
      global: {
        plugins: [pinia],
        stubs: {
          TaskForm: TaskFormStub,
          ElDrawer: {
            template: '<div><slot /><slot name="footer" /></div>',
          },
          ElButton: {
            template: '<button><slot /></button>',
          },
        },
      },
    })

    // 设置选中的任务为保证金任务 + TODO
    const taskData = {
      id: 65,
      projectId: 48,
      title: '缴纳投标保证金',
      status: 'TODO',
      extendedFields: { _taskType: 'deposit-payment' },
      assigneeId: 10,
    }

    tasksApi.getTaskById.mockResolvedValueOnce({
      data: { data: taskData },
    })

    await wrapper.vm.loadTask(65)
    await flushPromises()

    // 应该渲染 保存 按钮
    const buttons = wrapper.findAll('button')
    const saveButton = buttons.find((b) => b.text().includes('保存'))
    expect(saveButton).toBeDefined()
    expect(saveButton.exists()).toBe(true)
  })

  it('loadTask 时若任务有 projectId 且 currentProject 为空或不同，应调用 projectStore.getProjectById 加载项目详情', async () => {
    userStore.currentUser = { id: 68, name: '项目负责人' }
    projectStore.currentProject = null
    const getProjectSpy = vi.spyOn(projectStore, 'getProjectById').mockImplementation(async (id) => {
      projectStore.currentProject = { id, projectLeaderId: 68 }
      return projectStore.currentProject
    })

    const wrapper = mount(TaskBoardTaskDrawer, {
      props: { modelValue: true },
      global: {
        plugins: [pinia],
        stubs: {
          TaskForm: TaskFormStub,
          ElDrawer: { template: '<div><slot /><slot name="footer" /></div>' },
          ElButton: { template: '<button><slot /></button>' },
        },
      },
    })

    tasksApi.getTaskById.mockResolvedValueOnce({
      data: {
        data: {
          id: 65,
          projectId: 48,
          title: '缴纳投标保证金',
          status: 'TODO',
          extendedFields: { _taskType: 'deposit-payment' },
        },
      },
    })

    await wrapper.vm.loadTask(65)
    await flushPromises()

    expect(getProjectSpy).toHaveBeenCalledWith(48)
    expect(projectStore.currentProject?.projectLeaderId).toBe(68)
  })

  it('handleSaveAssignee 保存执行人成功后，应当在 submitted 事件中携带更新后的执行人信息', async () => {
    userStore.currentUser = { id: 68, name: '项目负责人' }
    projectStore.currentProject = { id: 48, projectLeaderId: 68 }

    projectsApi.updateTask.mockResolvedValueOnce({
      data: { id: 65, assigneeId: 99, assigneeName: '新执行人' },
    })

    const wrapper = mount(TaskBoardTaskDrawer, {
      props: { modelValue: true },
      global: {
        plugins: [pinia],
        stubs: {
          TaskForm: TaskFormStub,
          ElDrawer: { template: '<div><slot /><slot name="footer" /></div>' },
          ElButton: { template: '<button><slot /></button>' },
        },
      },
    })

    tasksApi.getTaskById.mockResolvedValueOnce({
      data: {
        data: {
          id: 65,
          projectId: 48,
          title: '缴纳投标保证金',
          status: 'TODO',
          extendedFields: { _taskType: 'deposit-payment' },
        },
      },
    })

    await wrapper.vm.loadTask(65)
    await flushPromises()

    await wrapper.vm.handleSaveAssignee()
    await flushPromises()

    expect(projectsApi.updateTask).toHaveBeenCalledWith(65, expect.objectContaining({ assigneeId: 99 }))
    expect(wrapper.emitted('submitted')).toBeTruthy()
    const emittedData = wrapper.emitted('submitted')[0][0]
    expect(emittedData).toMatchObject({
      taskId: 65,
      updated: expect.objectContaining({
        assigneeId: 99,
        assigneeName: '新执行人',
      }),
    })
  })
})
