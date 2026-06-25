/**
 * Minimal spec entrypoint — full wiring tests live in ProjectDetailTaskStatusEvents.spec.js
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import ProjectDetailMainColumn from './ProjectDetailMainColumn.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getResult: vi.fn(),
  },
}))

vi.mock('@/composables/projectDetail/context.js', async () => {
  const actual = await vi.importActual('@/composables/projectDetail/context.js')
  return { ...actual }
})

describe('ProjectDetailMainColumn', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    projectLifecycleApi.getResult.mockResolvedValue({ data: {} })
  })

  it('renders loading state when activeStageTab is empty', () => {
    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        stubs: {
          ProjectBasicInfoCard: true,
          ProjectStageTimeline: true,
          ProjectApprovalStatusCard: true,
          ElEmpty: false,
        },
        provide: {
          [projectDetailKey]: {
            project: ref(null),
          },
        },
      },
    })
    expect(wrapper.find('.main-content').exists()).toBe(true)
  })

  it('opens the backend default stage from timeline snapshot', async () => {
    const timelineStub = {
      name: 'ProjectStageTimeline',
      emits: ['snapshot'],
      template: '<button class="timeline-stub" @click="$emit(\'snapshot\', { currentStage: \'INITIATED\', defaultOpenStage: \'DRAFTING\' })" />',
    }
    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        stubs: {
          ProjectBasicInfoCard: true,
          ProjectStageTimeline: timelineStub,
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
          ElEmpty: true,
        },
        provide: {
          [projectDetailKey]: {
            project: { id: 42, tasks: [] },
            approvalHistory: [],
            canApproveCurrent: false,
            canManageProjectTasks: false,
            isDemoMode: false,
            userStore: { currentUser: { id: 88 } },
          },
        },
      },
    })

    await wrapper.find('.timeline-stub').trigger('click')
    await flushPromises()

    expect(wrapper.findComponent({ name: 'DraftingStage' }).exists()).toBe(true)
    expect(projectLifecycleApi.getResult).toHaveBeenCalledWith(42)
  })
})
