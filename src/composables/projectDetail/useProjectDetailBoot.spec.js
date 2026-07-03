import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProjectLoadError, PROJECT_LOAD_ERROR_TYPE } from '@/utils/projectErrors.js'

const onMountedCallbacks = []
const apiMocks = vi.hoisted(() => ({
  getProjectApprovals: vi.fn(),
  getTemplateList: vi.fn(),
  getProjectActivityLogs: vi.fn(),
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

vi.mock('@/api/modules/audit.js', () => ({
  auditApi: {
    getProjectActivityLogs: apiMocks.getProjectActivityLogs,
  },
}))

import { useProjectDetailBoot } from './useProjectDetailBoot.js'

function createContext(overrides = {}) {
  const result = {
    route: { params: { id: '12' } },
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
      loadError: ref(null),
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
    apiMocks.getProjectActivityLogs.mockReset()
    apiMocks.getProjectActivityLogs.mockResolvedValue({ data: [] })
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
        user: '系统',
        action: '创建了项目',
        time: '',
      },
    ])
  })
})

describe('useProjectDetailBoot ProjectLoadError 错误处理', () => {
  beforeEach(() => {
    onMountedCallbacks.length = 0
    apiMocks.getProjectApprovals.mockReset()
    apiMocks.getProjectApprovals.mockResolvedValue({ data: [] })
    apiMocks.getTemplateList.mockReset()
    apiMocks.getTemplateList.mockResolvedValue({ success: true, data: [] })
    apiMocks.getProjectActivityLogs.mockReset()
    apiMocks.getProjectActivityLogs.mockResolvedValue({ data: [] })
    context = createContext()
  })

  it('getProjectById 抛出 no-permission 时，state.loadError 被设为 no-permission，loading 为 false', async () => {
    context.projectStore.getProjectById = vi.fn().mockRejectedValue(
      new ProjectLoadError(PROJECT_LOAD_ERROR_TYPE.NO_PERMISSION, '无权限访问该项目', null)
    )

    useProjectDetailBoot(context)
    expect(onMountedCallbacks).toHaveLength(1)

    await onMountedCallbacks[0]()
    await flushPromises()

    expect(context.projectStore.getProjectById).toHaveBeenCalledWith('12')
    expect(context.state.loadError.value).toBe(PROJECT_LOAD_ERROR_TYPE.NO_PERMISSION)
    expect(context.state.loading.value).toBe(false)
    // initializeProjectActivities 不应被调用（activities 保持空数组）
    expect(context.state.activities.value).toEqual([])
    // loadTaskStatuses 不应被调用
    expect(context.projectStore.loadTaskStatuses).not.toHaveBeenCalled()
  })

  it('getProjectById 抛出 not-found 时，state.loadError 被设为 not-found', async () => {
    context.projectStore.getProjectById = vi.fn().mockRejectedValue(
      new ProjectLoadError(PROJECT_LOAD_ERROR_TYPE.NOT_FOUND, '项目不存在', null)
    )

    useProjectDetailBoot(context)
    await onMountedCallbacks[0]()
    await flushPromises()

    expect(context.state.loadError.value).toBe(PROJECT_LOAD_ERROR_TYPE.NOT_FOUND)
    expect(context.state.loading.value).toBe(false)
  })

  it('getProjectById 抛出 network-error 时，state.loadError 被设为 network-error', async () => {
    context.projectStore.getProjectById = vi.fn().mockRejectedValue(
      new ProjectLoadError(PROJECT_LOAD_ERROR_TYPE.NETWORK_ERROR, '加载失败', null)
    )

    useProjectDetailBoot(context)
    await onMountedCallbacks[0]()
    await flushPromises()

    expect(context.state.loadError.value).toBe(PROJECT_LOAD_ERROR_TYPE.NETWORK_ERROR)
    expect(context.state.loading.value).toBe(false)
  })

  it('getProjectById 成功时，state.loadError 保持 null，正常执行后续流程', async () => {
    useProjectDetailBoot(context)
    await onMountedCallbacks[0]()
    await flushPromises()

    expect(context.state.loadError.value).toBeNull()
    expect(context.state.loading.value).toBe(false)
    expect(context.projectStore.loadTaskStatuses).toHaveBeenCalled()
  })
})
