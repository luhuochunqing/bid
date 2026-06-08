// Input: projectStore / projectsApi.getTasks response / task status normalize
// Output: project.value.tasks normalized for kanban rendering
// Pos: src/composables/projectDetail/ - Regression for IJSVX7 问题二
//   真实场景：投标管理员创建任务后，切到其他页面再回来，看板"待办"列任务消失。
//   控制台 DIAGNOSTIC 输出：[TaskBoard] props.tasks.length: 20 statuses: ['268:doing', '269:doing', ...]
//   根因：useProjectDetailInit.loadProjectWorkflowData 整体替换 project.value.tasks，
//        跳过 taskBackendToCard + normalizeTaskStatusFromApi，
//        导致后端 mapStatus 输出的小写字符串原样塞进前端，
//        TaskBoard.columns.filter((s) => s.code !== 'IN_PROGRESS') 过滤后
//        把 'doing' (upcase 后变 IN_PROGRESS) 任务全部丢掉。

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

const mountInit = async (getTasksResponse) => {
  setActivePinia(createPinia())
  const { useProjectStore } = await import('@/stores/project.js')
  const projectStore = useProjectStore()
  projectStore.taskStatusesLoaded = true
  projectStore.taskStatuses = [
    { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false },
    { code: 'IN_PROGRESS', name: '进行中', category: 'IN_PROGRESS', color: '#409eff', sortOrder: 20, initial: false, terminal: false },
    { code: 'COMPLETED', name: '已完成', category: 'CLOSED', color: '#67c23a', sortOrder: 40, initial: false, terminal: true },
  ]
  // Mock getProjectById 让它直接给 currentProject 赋目标对象 + 返回
  projectStore.getProjectById = vi.fn().mockImplementation((id) => {
    projectStore.currentProject = { id, name: 'P', tasks: [] }
    return Promise.resolve(projectStore.currentProject)
  })
  projectStore.loadTaskStatuses = vi.fn().mockResolvedValue(undefined)
  const projectsApi = {
    getTasks: vi.fn().mockResolvedValue({ success: true, data: getTasksResponse }),
    getDocuments: vi.fn().mockResolvedValue({ success: true, data: [] }),
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
    userStore: { userName: 'admin' },
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
  for (let i = 0; i < 10; i += 1) {
    await flushPromises()
  }
  return { wrapper, project, projectsApi }
}

describe('useProjectDetailInit (IJSVX7 问题二 regression)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loadProjectWorkflowData normalizes backend lowercase status to canonical uppercase', async () => {
    // 后端 mapStatus 输出小写字符串：'todo' / 'doing' / 'done' / 'review'
    // 复现控制台 DIAGNOSTIC 输出 ['268:doing', '269:doing', ...]
    const tasks = [
      buildTask(1, 'todo'),
      buildTask(2, 'doing'),
      buildTask(3, 'done'),
      buildTask(4, 'review'),
    ]
    const { project } = await mountInit(tasks)
    // **关键 assertion**：每个 task.status 必须是大写规范码
    // 这是修复的合同：loadProjectWorkflowData 必须走 normalizeTaskStatusFromApi
    expect(project.value.tasks.map((t) => t.status)).toEqual(['TODO', 'IN_PROGRESS', 'COMPLETED', 'REVIEW'])
  })

  it('loadProjectWorkflowData 后的 tasks 在 TaskBoard 按 status 归类时不丢失', async () => {
    // 复现：所有 task status='doing'，TaskBoard.columns 过滤 IN_PROGRESS，
    // 修复前 → 19 个 task 都不在 TODO/REVIEW/COMPLETED 列（"消失"）
    // 修复后 → status='IN_PROGRESS' 的 task 也得在 IN_PROGRESS 列显示
    // (或被 availableStatuses 兜底映射)
    const tasks = [
      buildTask(101, 'doing'),
      buildTask(102, 'doing'),
      buildTask(103, 'todo'),
    ]
    const { project } = await mountInit(tasks)
    // 模拟 TaskBoard.getTasksByStatus('TODO')：找 status='TODO' 的
    const normalize = (s) => String(s || '').toUpperCase()
    const todoTasks = project.value.tasks.filter((t) => normalize(t.status) === 'TODO')
    expect(todoTasks).toHaveLength(1)
    expect(todoTasks[0].id).toBe(103)
  })
})
