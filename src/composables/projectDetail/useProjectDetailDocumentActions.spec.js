import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useProjectDetailDocumentActions } from './useProjectDetailDocumentActions.js'

describe('useProjectDetailDocumentActions', () => {
  it('loadProjectWorkflowData filters out 【待立项】tasks from task board', async () => {
    const project = ref({
      id: 12,
      name: '测试项目',
      tasks: [],
    })

    const getTasks = vi.fn().mockResolvedValue({
      success: true,
      data: [
        { id: 1, title: '【待立项】某标讯', status: 'TODO' },
        { id: 2, title: '正常任务', status: 'TODO' },
        { id: 3, title: '【待立项】另一标讯', status: 'IN_PROGRESS' },
      ],
    })
    const getDocuments = vi.fn().mockResolvedValue({ success: true, data: [] })

    const { loadProjectWorkflowData } = useProjectDetailDocumentActions({
      route: { params: { id: '12' } },
      project,
      projectExpenses: ref([]),
      userStore: {},
      projectsApi: { getTasks, getDocuments },
      isApiProject: ref(true),
      message: { success: vi.fn(), error: vi.fn() },
      state: {},
    })

    await loadProjectWorkflowData('12')

    expect(project.value.tasks).toHaveLength(1)
    expect(project.value.tasks[0].title).toBe('正常任务')
  })

  it('loadProjectWorkflowData keeps all tasks when none start with 【待立项】', async () => {
    const project = ref({
      id: 13,
      name: '另一个项目',
      tasks: [],
    })

    const getTasks = vi.fn().mockResolvedValue({
      success: true,
      data: [
        { id: 10, title: '任务A', status: 'TODO' },
        { id: 11, title: '任务B', status: 'COMPLETED' },
      ],
    })
    const getDocuments = vi.fn().mockResolvedValue({ success: true, data: [] })

    const { loadProjectWorkflowData } = useProjectDetailDocumentActions({
      route: { params: { id: '13' } },
      project,
      projectExpenses: ref([]),
      userStore: {},
      projectsApi: { getTasks, getDocuments },
      isApiProject: ref(true),
      message: { success: vi.fn(), error: vi.fn() },
      state: {},
    })

    await loadProjectWorkflowData('13')

    expect(project.value.tasks).toHaveLength(2)
  })

  it('loadProjectWorkflowData does nothing when not an API project', async () => {
    const project = ref({
      id: 'demo-1',
      name: 'Demo项目',
      tasks: [],
    })

    const getTasks = vi.fn()
    const getDocuments = vi.fn()

    const { loadProjectWorkflowData } = useProjectDetailDocumentActions({
      route: { params: { id: 'demo-1' } },
      project,
      projectExpenses: ref([]),
      userStore: {},
      projectsApi: { getTasks, getDocuments },
      isApiProject: ref(false),
      message: { success: vi.fn(), error: vi.fn() },
      state: {},
    })

    await loadProjectWorkflowData('demo-1')

    expect(getTasks).not.toHaveBeenCalled()
    expect(getDocuments).not.toHaveBeenCalled()
    expect(project.value.tasks).toEqual([])
  })
})
