// Input: projectStore / projectsApi.getTasks response / task status normalize
// Output: project.value.tasks normalized for kanban rendering
// Pos: src/composables/projectDetail/ - Regression for IJSVX7 问题二
//   真实场景：投标管理员创建任务后，切到其他页面再回来，看板"待办"列任务消失。
//   控制台 DIAGNOSTIC 输出：[TaskBoard] props.tasks.length: 20 statuses: ['268:todo', '269:review', ...]
//   根因：useProjectDetailInit.loadProjectWorkflowData 整体替换 project.value.tasks，
//        跳过 taskBackendToCard + normalizeTaskStatusFromApi，
//        导致后端 mapStatus 输出的小写字符串原样塞进前端。
//   CO-361 三态模型收口后后端只输出 'todo' / 'review' / 'done'，
//   normalize 层仍需把小写归一为大写规范码。

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { computed } from 'vue'

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0))

const buildTask = (id, status) => ({
  id,
  title: `T${id}`,
  name: `T${id}`,
  description: '',
  content: '',
  status,
  priority: 'medium',
  dueDate: '2026-12-31',
  assigneeId: 9,
  assigneeName: '被分配人',
  deliverables: [],
  hasDeliverable: false,
})

const mountInit = async (getTasksResponse, overrides = {}) => {
  setActivePinia(createPinia())
  const { useProjectStore } = await import('@/stores/project.js')
  const projectStore = useProjectStore()
  projectStore.taskStatusesLoaded = true
  projectStore.taskStatuses = [
    { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false },
    { code: 'REVIEW', name: '待审核', category: 'REVIEW', color: '#e6a23c', sortOrder: 30, initial: false, terminal: false },
    { code: 'COMPLETED', name: '已完成', category: 'CLOSED', color: '#67c23a', sortOrder: 40, initial: false, terminal: true },
  ]
  // Mock getProjectById 让它直接给 currentProject 赋目标对象 + 返回
  projectStore.getProjectById = vi.fn().mockImplementation((id) => {
    projectStore.currentProject = { id, name: 'P', tasks: [] }
    return Promise.resolve(projectStore.currentProject)
  })
  projectStore.loadTaskStatuses = vi.fn().mockResolvedValue(undefined)
  const projectsApi = {
    getTasks: overrides.getTasks ?? vi.fn().mockResolvedValue({ success: true, data: getTasksResponse }),
    getDocuments: overrides.getDocuments ?? vi.fn().mockResolvedValue({ success: true, data: [] }),
    getDetail: vi.fn().mockResolvedValue({ success: true, data: { id: 12, name: 'P' } }),
  }
  const knowledgeApi = { templates: { getList: vi.fn().mockResolvedValue({ success: true, data: [] }) } }
  const barStore = {
    getSites: vi.fn().mockResolvedValue([]),
    sites: [],
    checkSiteCapability: vi.fn().mockResolvedValue({ found: false }),
  }
  const approvalApi = { getProjectApprovals: vi.fn().mockResolvedValue({ success: true, data: [] }) }
  const projectDetailInit = await import('@/composables/projectDetail/useProjectDetailInit.js')
  const projectId = '12'
  const isApiProject = computed(() => /^\d+$/.test(String(projectId)))
  const project = computed(() => projectStore.currentProject)
  const context = {
    route: { params: { id: projectId } },
    projectStore,
    knowledgeApi,
    barStore,
    approvalApi,
    projectsApi,
    isApiProject,
    project,
    activities: computed(() => []),
    templates: computed(() => []),
    loading: computed(() => false),
    loadProjectExpenseAggregation: vi.fn().mockResolvedValue(undefined),
    approvalHistory: computed(() => []),
  }
  const Comp = {
    template: '<div />',
    setup() {
      return projectDetailInit.useProjectDetailInit(context)
    },
  }
  const wrapper = (await import('@vue/test-utils')).mount(Comp)
  // 等待所有异步操作完成
  for (let i = 0; i < 20; i += 1) {
    await flushPromises()
  }
  await vi.dynamicImportSettled()
  return { wrapper, project, projectsApi }
}

describe('useProjectDetailInit (IJSVX7 问题二 regression)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loadProjectWorkflowData normalizes backend lowercase status to canonical uppercase', async () => {
    // 后端 mapStatus 输出小写字符串：'todo' / 'review' / 'done'
    const tasks = [
      buildTask(1, 'todo'),
      buildTask(2, 'review'),
      buildTask(3, 'done'),
    ]
    const { project } = await mountInit(tasks)
    // **关键 assertion**：每个 task.status 必须是大写规范码
    // 这是修复的合同：loadProjectWorkflowData 必须走 normalizeTaskStatusFromApi
    expect(project.value.tasks.map((t) => t.status)).toEqual(['TODO', 'REVIEW', 'COMPLETED'])
  }, 10000)

  it('loadProjectWorkflowData 后的 tasks 在 TaskBoard 按 status 归类时不丢失', async () => {
    // 复现：task status 为小写，需经 normalize 后才能正确归类到看板列
    const tasks = [
      buildTask(101, 'todo'),
      buildTask(102, 'todo'),
      buildTask(103, 'review'),
    ]
    const { project } = await mountInit(tasks)
    // 模拟 TaskBoard.getTasksByStatus('TODO')：找 status='TODO' 的
    const normalize = (s) => String(s || '').toUpperCase()
    const todoTasks = project.value.tasks.filter((t) => normalize(t.status) === 'TODO')
    expect(todoTasks).toHaveLength(2)
    expect(todoTasks[0].id).toBe(101)
  }, 10000)

  it('getDocuments reject 时 tasks 仍正常渲染（allSettled 容错）', async () => {
    // CO-361 根因场景：bid-projectLeader 非主负责人时 getDocuments 返回 403，
    // 原 Promise.all fail-fast 会丢弃已成功的 getTasks 数据，看板整页空白。
    // 改用 allSettled 后，文档失败只让 documents 为空，任务不受影响。
    const tasks = [buildTask(201, 'todo'), buildTask(202, 'review')]
    const { project } = await mountInit(tasks, {
      getDocuments: vi.fn().mockRejectedValue(new Error('Request failed with status code 403')),
    })
    expect(project.value.tasks).toHaveLength(2)
    expect(project.value.tasks.map((t) => t.status)).toEqual(['TODO', 'REVIEW'])
    expect(project.value.documents).toEqual([])
  }, 10000)

  it('getTasks reject 时 documents 仍正常渲染（allSettled 容错）', async () => {
    // 对称场景：getTasks 失败不应拖垮 getDocuments 的渲染
    const docs = [{ id: 1, name: '投标文件.pdf' }]
    const { project } = await mountInit([], {
      getTasks: vi.fn().mockRejectedValue(new Error('Request failed with status code 500')),
      getDocuments: vi.fn().mockResolvedValue({ success: true, data: docs }),
    })
    expect(project.value.tasks).toEqual([])
    expect(project.value.documents).toEqual(docs)
  }, 10000)
})
