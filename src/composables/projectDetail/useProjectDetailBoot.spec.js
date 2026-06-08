import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const onMountedCallbacks = []
const apiMocks = vi.hoisted(() => ({
  getProjectApprovals: vi.fn(),
  getTemplateList: vi.fn(),
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual('vue')
  return {
    ...actual,
    onMounted: (callback) => {
      onMountedCallbacks.push(callback)
    },
  }
})

vi.mock('@/api', () => ({
  approvalApi: {
    getProjectApprovals: apiMocks.getProjectApprovals,
  },
  knowledgeApi: {
    templates: {
      getList: apiMocks.getTemplateList,
    },
  },
}))

import { useProjectDetailBoot } from './useProjectDetailBoot.js'

function createContext(overrides = {}) {
  const result = {
    route: { params: { id: '12' } },
    userStore: { userName: '测试用户' },
    projectStore: {
      currentProject: null,
      getProjectById: vi.fn().mockImplementation(async () => {
        result.projectStore.currentProject = { id: 12, name: '测试项目' }
        return result.projectStore.currentProject
      }),
      loadTaskStatuses: vi.fn().mockResolvedValue([]),
    },
    barStore: {
      getSites: vi.fn().mockResolvedValue([]),
      sites: [],
      checkSiteCapability: vi.fn().mockResolvedValue(null),
    },
    state: {
      loading: ref(false),
      approvalHistory: ref([]),
      activities: ref([]),
      assetCheckResult: ref(null),
    },
    workflow: {
      templates: ref([]),
    },
    expenseAggregation: {
      loadProjectExpenseAggregation: vi.fn().mockResolvedValue([]),
    },
    loadProjectWorkflowData: vi.fn().mockResolvedValue([]),
    ...overrides,
  }
  return result
}

let context

async function flushPromises() {
  await Promise.resolve()
  await Promise.resolve()
  await new Promise((resolve) => setTimeout(resolve, 0))
}

describe('useProjectDetailBoot', () => {
  beforeEach(() => {
    onMountedCallbacks.length = 0
    apiMocks.getProjectApprovals.mockReset()
    apiMocks.getProjectApprovals.mockResolvedValue({ data: [] })
    apiMocks.getTemplateList.mockReset()
    apiMocks.getTemplateList.mockResolvedValue({ success: true, data: [] })
    context = createContext()
  })

  it('clears loading after project detail dependencies fail', async () => {
    context.expenseAggregation.loadProjectExpenseAggregation = vi.fn().mockRejectedValue(new Error('expense boom'))
    context.loadProjectWorkflowData = vi.fn().mockRejectedValue(new Error('workflow boom'))

    useProjectDetailBoot(context)
    expect(onMountedCallbacks).toHaveLength(1)

    await onMountedCallbacks[0]()
    await flushPromises()

    expect(context.projectStore.getProjectById).toHaveBeenCalledWith('12')
    expect(context.state.loading.value).toBe(false)
    expect(context.state.activities.value).toEqual([
      {
        id: 'project-created-12',
        user: '测试用户',
        action: '创建了项目',
        time: '',
      },
    ])
  })
})
