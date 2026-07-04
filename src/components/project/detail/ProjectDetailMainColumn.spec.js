/**
 * Minimal spec entrypoint — full wiring tests live in ProjectDetailTaskStatusEvents.spec.js
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ProjectDetailMainColumn from './ProjectDetailMainColumn.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getResult: vi.fn(),
    getDrafting: vi.fn(),
  },
}))

vi.mock('@/stores/project', () => ({
  useProjectStore: () => ({
    getProjectById: vi.fn().mockResolvedValue({ id: 42, tasks: [] }),
    currentProject: { id: 42, tasks: [] },
  }),
}))

vi.mock('@/composables/projectDetail/context.js', async () => {
  const actual = await vi.importActual('@/composables/projectDetail/context.js')
  return { ...actual }
})

// 构造一个最小 router，支持 /project/:id 和 /project/:id/:stage 两条路由
function createTestRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/project/:id', component: { template: '<div/>' } },
      { path: '/project/:id/:stage', component: { template: '<div/>' } },
    ],
  })
  return router
}

const stubs = {
  ProjectBasicInfoCard: true,
  ProjectStageTimeline: true,
  ProjectApprovalStatusCard: true,
  InitiationStage: true,
  DraftingStage: true,
  EvaluationStage: true,
  ResultConfirmStage: true,
  RetrospectiveStage: true,
  ClosureStage: true,
  ProjectTaskBoardCard: true,
  ScoreParseDrawer: true,
  TaskDecomposeDialog: true,
  ElCard: true,
  ElTimeline: true,
  ElTimelineItem: true,
  ElIcon: true,
  Clock: true,
  ElEmpty: true,
}

const baseProvide = {
  [projectDetailKey]: {
    project: { id: 42, tasks: [] },
    approvalHistory: [],
    canApproveCurrent: false,
    canManageProjectTasks: false,
    isDemoMode: false,
    userStore: { currentUser: { id: 88 } },
    activities: [],
  },
}

describe('ProjectDetailMainColumn', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    projectLifecycleApi.getResult.mockResolvedValue({ data: {} })
    projectLifecycleApi.getDrafting.mockResolvedValue({ data: {} })
  })

  it('renders loading state when activeStageTab is empty', () => {
    const router = createTestRouter()
    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        plugins: [router],
        stubs: {
          ProjectBasicInfoCard: true,
          ProjectStageTimeline: true,
          ProjectApprovalStatusCard: true,
          ElCard: true,
          ElTimeline: true,
          ElTimelineItem: true,
          ElIcon: true,
          Clock: true,
          ElEmpty: false,
        },
        provide: {
          [projectDetailKey]: {
            project: ref(null),
            activities: [],
          },
        },
      },
    })
    expect(wrapper.find('.main-content').exists()).toBe(true)
  })

  it('opens the backend default stage from timeline snapshot', async () => {
    const router = createTestRouter()
    const timelineStub = {
      name: 'ProjectStageTimeline',
      emits: ['snapshot'],
      template: '<button class="timeline-stub" @click="$emit(\'snapshot\', { currentStage: \'INITIATED\', defaultOpenStage: \'DRAFTING\' })" />',
    }
    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        plugins: [router],
        stubs: { ...stubs, ProjectStageTimeline: timelineStub },
        provide: baseProvide,
      },
    })

    await wrapper.find('.timeline-stub').trigger('click')
    await flushPromises()

    expect(wrapper.findComponent({ name: 'DraftingStage' }).exists()).toBe(true)
    expect(projectLifecycleApi.getResult).toHaveBeenCalledWith(42)
  })

  it('initializes activeStageTab from route.params.stage (notification jump target)', async () => {
    // 用户从通知点击跳转 /project/128/initiation
    // 组件挂载时 immediate watch 应立即把 activeStageTab 设为 'INITIATED'
    const router = createTestRouter()
    await router.push('/project/128/initiation')
    await router.isReady()

    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        plugins: [router],
        stubs,
        provide: {
          [projectDetailKey]: {
            ...baseProvide[projectDetailKey],
            project: { id: 128, tasks: [] },
          },
        },
      },
    })

    await flushPromises()

    expect(wrapper.findComponent({ name: 'InitiationStage' }).exists()).toBe(true)
  })

  it('URL stage parameter overrides timeline defaultOpenStage (user intent wins)', async () => {
    // URL 是 /project/128/drafting，但 timeline 推荐 INITIATED
    // 应优先 URL → 切到 DRAFTING
    const router = createTestRouter()
    await router.push('/project/128/drafting')
    await router.isReady()

    const timelineStub = {
      name: 'ProjectStageTimeline',
      emits: ['snapshot'],
      template: '<button class="timeline-stub" @click="$emit(\'snapshot\', { currentStage: \'DRAFTING\', defaultOpenStage: \'INITIATED\' })" />',
    }
    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        plugins: [router],
        stubs: { ...stubs, ProjectStageTimeline: timelineStub },
        provide: {
          [projectDetailKey]: {
            ...baseProvide[projectDetailKey],
            project: { id: 128, tasks: [] },
          },
        },
      },
    })

    // 触发 timeline snapshot（推荐 INITIATED）
    await wrapper.find('.timeline-stub').trigger('click')
    await flushPromises()

    // URL 参数优先，应切到 DRAFTING
    expect(wrapper.findComponent({ name: 'DraftingStage' }).exists()).toBe(true)
  })

  it('ignores invalid route.params.stage value (falls back to timeline snapshot)', async () => {
    const router = createTestRouter()
    await router.push('/project/128/unknown-stage')
    await router.isReady()

    const timelineStub = {
      name: 'ProjectStageTimeline',
      emits: ['snapshot'],
      template: '<button class="timeline-stub" @click="$emit(\'snapshot\', { currentStage: \'DRAFTING\' })" />',
    }
    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        plugins: [router],
        stubs: { ...stubs, ProjectStageTimeline: timelineStub },
        provide: {
          [projectDetailKey]: {
            ...baseProvide[projectDetailKey],
            project: { id: 128, tasks: [] },
          },
        },
      },
    })

    // timeline 推荐 DRAFTING，URL stage 'unknown-stage' 无对应 stage code → 忽略，回退 timeline
    await wrapper.find('.timeline-stub').trigger('click')
    await flushPromises()

    expect(wrapper.findComponent({ name: 'DraftingStage' }).exists()).toBe(true)
  })

  // CO-468 根因修复：阶段切换后必须重新拉取 tasks
  it('handleStageUpdated 调用 loadProjectWorkflowData 重新拉取 tasks', async () => {
    const router = createTestRouter()
    const loadProjectWorkflowData = vi.fn().mockResolvedValue()

    const timelineStub = {
      name: 'ProjectStageTimeline',
      emits: ['snapshot'],
      template: '<button class="timeline-stub" @click="$emit(\'snapshot\', { currentStage: \'INITIATED\', defaultOpenStage: \'INITIATED\' })" />',
    }

    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        plugins: [router],
        stubs: {
          ...stubs,
          ProjectStageTimeline: timelineStub,
          InitiationStage: { name: 'InitiationStage', template: '<div class="initiation-stub" />' },
        },
        provide: {
          [projectDetailKey]: {
            ...baseProvide[projectDetailKey],
            project: { id: 42, tasks: [] },
            loadProjectWorkflowData,
          },
        },
      },
    })

    // 触发 timeline snapshot → activeStageTab = 'INITIATED'
    await wrapper.find('.timeline-stub').trigger('click')
    await flushPromises()

    // 触发 InitiationStage 的 updated 事件 → handleStageUpdated
    const initiation = wrapper.findComponent({ name: 'InitiationStage' })
    expect(initiation.exists()).toBe(true)
    initiation.vm.$emit('updated')
    await flushPromises()

    // 核心断言：loadProjectWorkflowData 被调用，重新拉取了 tasks
    expect(loadProjectWorkflowData).toHaveBeenCalledWith(42)
  })

  // CO-497: 复盘提交后即使 handleStageUpdated 抛异常，也要确保 tab 切换到 CLOSED（结项阶段）
  it('onRetrospectiveSubmitted 即使 handleStageUpdated 抛异常也切换到 CLOSED tab', async () => {
    const router = createTestRouter()
    // mock loadProjectWorkflowData 抛异常 → handleStageUpdated 抛异常
    const loadProjectWorkflowData = vi.fn().mockRejectedValue(new Error('网络错误'))

    const timelineStub = {
      name: 'ProjectStageTimeline',
      emits: ['snapshot'],
      template: '<button class="timeline-stub" @click="$emit(\'snapshot\', { currentStage: \'RETROSPECTIVE\', defaultOpenStage: \'RETROSPECTIVE\' })" />',
    }

    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        plugins: [router],
        stubs: {
          ...stubs,
          ProjectStageTimeline: timelineStub,
          RetrospectiveStage: { name: 'RetrospectiveStage', template: '<div class="retro-stub" />' },
        },
        provide: {
          [projectDetailKey]: {
            ...baseProvide[projectDetailKey],
            project: { id: 42, tasks: [] },
            loadProjectWorkflowData,
          },
        },
      },
    })

    // 触发 timeline snapshot → activeStageTab = 'RETROSPECTIVE'
    await wrapper.find('.timeline-stub').trigger('click')
    await flushPromises()

    // 触发 RetrospectiveStage 的 submitted 事件 → onRetrospectiveSubmitted
    const retro = wrapper.findComponent({ name: 'RetrospectiveStage' })
    expect(retro.exists()).toBe(true)
    retro.vm.$emit('submitted')
    await flushPromises()

    // 核心断言：即使 loadProjectWorkflowData 抛异常，tab 也切换到 CLOSED（结项阶段）
    const closure = wrapper.findComponent({ name: 'ClosureStage' })
    expect(closure.exists()).toBe(true)
  })

  // CO-497 方案 A: 复盘提交后 timeline 异步回声 snapshot(RETROSPECTIVE) 不能拽回 tab
  // 时序：onRetrospectiveSubmitted → handleStageUpdated 内部 timeline.reload()
  //   → emit('snapshot', {currentStage:RETROSPECTIVE}) → handleSnapshot
  // 标志位 isRetrospectiveTransitioning 应堵住这次异步回声，tab 保持 CLOSED
  it('onRetrospectiveSubmitted 期间 timeline 异步 emit snapshot(RETROSPECTIVE) 不拽回 tab', async () => {
    const router = createTestRouter()
    const loadProjectWorkflowData = vi.fn().mockResolvedValue()

    const timelineStub = {
      name: 'ProjectStageTimeline',
      emits: ['snapshot'],
      template: `
        <div>
          <button class="timeline-stub" @click="$emit('snapshot', { currentStage: 'RETROSPECTIVE', defaultOpenStage: 'RETROSPECTIVE' })" />
          <button class="timeline-echo" @click="$emit('snapshot', { currentStage: 'RETROSPECTIVE', defaultOpenStage: 'RETROSPECTIVE' })" />
        </div>
      `,
    }

    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        plugins: [router],
        stubs: {
          ...stubs,
          ProjectStageTimeline: timelineStub,
          RetrospectiveStage: { name: 'RetrospectiveStage', template: '<div class="retro-stub" />' },
        },
        provide: {
          [projectDetailKey]: {
            ...baseProvide[projectDetailKey],
            project: { id: 42, tasks: [] },
            loadProjectWorkflowData,
          },
        },
      },
    })

    // 初始 timeline snapshot → activeStageTab = 'RETROSPECTIVE'
    await wrapper.find('.timeline-stub').trigger('click')
    await flushPromises()

    // 触发 RetrospectiveStage 的 submitted 事件 → onRetrospectiveSubmitted
    const retro = wrapper.findComponent({ name: 'RetrospectiveStage' })
    expect(retro.exists()).toBe(true)
    retro.vm.$emit('submitted')
    await flushPromises()

    // 此时 tab 应该是 CLOSED（标志位生效中）
    expect(wrapper.findComponent({ name: 'ClosureStage' }).exists()).toBe(true)

    // 模拟 timeline 异步回声：在跳转窗口期内再次 emit snapshot(RETROSPECTIVE)
    await wrapper.find('.timeline-echo').trigger('click')
    await flushPromises()

    // 核心断言：标志位堵住异步回声，tab 仍然是 CLOSED（不是 RETROSPECTIVE）
    expect(wrapper.findComponent({ name: 'ClosureStage' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'RetrospectiveStage' }).exists()).toBe(false)
  })
})
