import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import ProjectDetailWorkflowCard from './ProjectDetailWorkflowCard.vue'

describe('ProjectDetailWorkflowCard', () => {
  it('does not expose the retired bid-agent draft button', () => {
    const wrapper = mount(ProjectDetailWorkflowCard, {
      global: {
        provide: {
          [projectDetailKey]: {
            bidAgent: { openDrawer: vi.fn() },
            bidProcess: {
              initiated: false,
              currentStep: 0,
              initiator: '',
              initiateTime: '',
              steps: {
                draft: { completed: false, time: '' },
                review: { completed: false, time: '' },
                seal: { completed: false, time: '' },
                submit: { completed: false, time: '' },
              },
              deliverables: [],
            },
            handleInitiateProcess: vi.fn(),
            canOperateStep: vi.fn(() => true),
            getStepStatusText: vi.fn(() => ''),
            getCurrentPhaseType: vi.fn(() => 'primary'),
            getCurrentPhaseText: vi.fn(() => ''),
            getProcessProgress: vi.fn(() => 0),
          },
        },
        stubs: {
          ElButton: { template: '<button class="el-button"><slot /></button>' },
          ElCard: { template: '<section><header><slot name="header" /></header><slot /></section>' },
          ElEmpty: { template: '<div class="el-empty"><slot /></div>' },
          ElIcon: { template: '<i><slot /></i>' },
        },
      },
    })

    expect(wrapper.text()).not.toContain('AI 生成初稿')
    expect(wrapper.text()).toContain('发起流程')
  })
})
