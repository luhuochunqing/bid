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
        { id: 3, title: '【待立项】另一标讯', status: 'TODO' },
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
    expect(project.value.tasks[0].name).toBe('正常任务')
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

  it('loadProjectWorkflowData 在 getDocuments reject 时仍渲染 tasks（allSettled 容错）', async () => {
    // CO-361 根因场景：bid-projectLeader 非主负责人时 getDocuments 返回 403，
    // 原 Promise.all fail-fast 会丢弃已成功的 getTasks 数据，看板整页空白。
    const project = ref({ id: 14, name: '项目106', tasks: [] })
    const getTasks = vi.fn().mockResolvedValue({
      success: true,
      data: [{ id: 301, title: '编制投标书', status: 'TODO' }],
    })
    const getDocuments = vi.fn().mockRejectedValue(new Error('Request failed with status code 403'))

    const { loadProjectWorkflowData } = useProjectDetailDocumentActions({
      route: { params: { id: '14' } },
      project,
      projectExpenses: ref([]),
      userStore: {},
      projectsApi: { getTasks, getDocuments },
      isApiProject: ref(true),
      message: { success: vi.fn(), error: vi.fn() },
      state: {},
    })

    await loadProjectWorkflowData('14')

    expect(project.value.tasks).toHaveLength(1)
    expect(project.value.tasks[0].status).toBe('TODO')
    expect(project.value.documents).toEqual([])
  })
})


describe('real API mode — document/archive/reminder actions reject non-API calls', () => {
  it('handleArchiveDocuments errors when not an API project', async () => {
    const error = vi.fn()
    const project = ref({ id: 'demo-1', name: 'Demo项目', documents: [] })
    const state = { project, activities: ref([]) }

    const { handleArchiveDocuments } = useProjectDetailDocumentActions({
      route: { params: { id: 'demo-1' } },
      project,
      projectExpenses: ref([]),
      userStore: { userName: '测试用户' },
      projectsApi: {},
      isApiProject: ref(false),
      message: { success: vi.fn(), error },
      state,
    })

    await handleArchiveDocuments()
    expect(error).toHaveBeenCalledWith('当前项目仅支持通过 API 归档资料')
  })

  it('handleSetReminder errors when not an API project', async () => {
    const error = vi.fn()
    const project = ref({ id: 'demo-1', name: 'Demo项目' })
    const state = { project, activities: ref([]) }

    const { handleSetReminder } = useProjectDetailDocumentActions({
      route: { params: { id: 'demo-1' } },
      project,
      projectExpenses: ref([]),
      userStore: { userName: '测试用户' },
      projectsApi: {},
      isApiProject: ref(false),
      message: { success: vi.fn(), error },
      state,
    })

    await handleSetReminder()
    expect(error).toHaveBeenCalledWith('当前项目仅支持通过 API 设置提醒')
  })

  it('handleAddDocument errors when not an API project', async () => {
    const error = vi.fn()
    const project = ref({ id: 'demo-1', name: 'Demo项目', documents: [] })
    const state = { project, activities: ref([]) }

    const { handleAddDocument } = useProjectDetailDocumentActions({
      route: { params: { id: 'demo-1' } },
      project,
      projectExpenses: ref([]),
      userStore: { userName: '测试用户' },
      projectsApi: {},
      isApiProject: ref(false),
      message: { success: vi.fn(), error },
      state,
    })

    await handleAddDocument()
    expect(error).toHaveBeenCalledWith('当前项目仅支持通过 API 添加文档')
  })

  it('handleUpload errors when not an API project', async () => {
    const error = vi.fn()
    const project = ref({ id: 'demo-1', name: 'Demo项目', documents: [] })
    const state = { project, activities: ref([]) }
    const file = new File(['test'], 'test.docx')

    const { handleUpload } = useProjectDetailDocumentActions({
      route: { params: { id: 'demo-1' } },
      project,
      projectExpenses: ref([]),
      userStore: { userName: '测试用户' },
      projectsApi: {},
      isApiProject: ref(false),
      message: { success: vi.fn(), error },
      state,
    })

    await handleUpload(file)
    expect(error).toHaveBeenCalledWith('当前项目仅支持通过 API 上传文档')
  })

  it('handleExport errors when not an API project', async () => {
    const error = vi.fn()
    const project = ref({ id: 'demo-1', name: 'Demo项目' })
    const state = { project, activities: ref([]) }

    const { handleExport } = useProjectDetailDocumentActions({
      route: { params: { id: 'demo-1' } },
      project,
      projectExpenses: ref([]),
      userStore: { userName: '测试用户' },
      projectsApi: {},
      isApiProject: ref(false),
      message: { success: vi.fn(), error },
      state,
    })

    handleExport()
    expect(error).toHaveBeenCalledWith('当前项目仅支持通过 API 导出资料')
  })

  it('handleShare errors when not an API project', async () => {
    const error = vi.fn()
    const project = ref({ id: 'demo-1', name: 'Demo项目' })
    const state = { project, activities: ref([]) }

    const { handleShare } = useProjectDetailDocumentActions({
      route: { params: { id: 'demo-1' } },
      project,
      projectExpenses: ref([]),
      userStore: { userName: '测试用户' },
      projectsApi: {},
      isApiProject: ref(false),
      message: { success: vi.fn(), error },
      state,
    })

    await handleShare()
    expect(error).toHaveBeenCalledWith('当前项目仅支持通过 API 生成分享链接')
  })
})
