/**
 * Minimal spec entrypoint — full wiring tests live in ProjectDetailTaskStatusEvents.spec.js
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import ProjectDetailMainColumn from './ProjectDetailMainColumn.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

vi.mock('@/composables/projectDetail/context.js', async () => {
  const actual = await vi.importActual('@/composables/projectDetail/context.js')
  return { ...actual }
})

describe('ProjectDetailMainColumn', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders loading state when activeStageTab is empty', () => {
    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        stubs: {
          ProjectBasicInfoCard: true,
          ProjectStageTimeline: true,
          ProjectApprovalStatusCard: true,
          ElMain: { template: '<main><slot /></main>' },
          ElEmpty: false,
        },
        provide: {
          [projectDetailKey]: {
            project: ref(null),
          },
        },
      },
    })
    expect(wrapper.find('main').exists()).toBe(true)
  })
})
