import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import ProjectDetailWorkflowCard from './ProjectDetailWorkflowCard.vue'

function makeContext(overrides = {}) {
  return {
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
    handleDraftSubmit: vi.fn(),
    handleReview: vi.fn(),
    handleSealApply: vi.fn(),
    handleSubmit: vi.fn(),
    handleDownloadDeliverable: vi.fn(),
    canOperateStep: vi.fn(() => true),
    getStepStatusText: vi.fn((step) => `${step}-pending`),
    getCurrentPhaseType: vi.fn(() => 'primary'),
    getCurrentPhaseText: vi.fn(() => '初稿编制'),
    getProcessProgress: vi.fn(() => 25),
    ...overrides,
  }
}

function mountCard(context = makeContext()) {
  return mount(ProjectDetailWorkflowCard, {
    global: {
      provide: { [projectDetailKey]: context },
      stubs: {
        ElButton: {
          emits: ['click'],
          template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>',
        },
        ElCard: {
          template: '<section class="el-card"><header><slot name="header" /></header><slot /></section>',
        },
        ElEmpty: {
          props: ['description'],
          template: '<div class="el-empty" :description="description"><slot /></div>',
        },
        ElIcon: { template: '<i><slot /></i>' },
      },
    },
  })
}

describe('ProjectDetailWorkflowCard regression coverage', () => {
  // Regression: ISSUE-001 — the workflow card crashed when bidProcess was a plain injected object.
  // Found by /qa on 2026-04-22.
  // Report: .gstack/qa-reports/qa-report-127-0-0-1-2026-04-22.md
  it('renders the empty workflow when bidProcess is provided as an unwrapped object', async () => {
    const context = makeContext()
    const wrapper = mountCard(context)

    const empty = wrapper.find('.el-empty')
    const initiateButton = wrapper.findAll('el-button, button').find((button) => button.text().includes('立即发起'))

    expect(wrapper.find('.workflow-header-actions').text()).toContain('AI 生成初稿')
    expect(wrapper.find('.workflow-header-actions').text()).toContain('发起流程')
    expect(empty.attributes('description')).toBe('暂未发起标书编制流程')
    expect(initiateButton.exists()).toBe(true)

    await initiateButton.trigger('click')

    expect(context.handleInitiateProcess).toHaveBeenCalledTimes(1)
  })

  it('renders an initiated workflow without ref-only template access', () => {
    const context = makeContext({
      bidProcess: {
        initiated: true,
        currentStep: 1,
        initiator: '系统管理员',
        initiateTime: '2026-04-22 17:16:00',
        steps: {
          draft: { completed: true, time: '2026-04-22 17:16:00' },
          review: { completed: false, time: '' },
          seal: { completed: false, time: '' },
          submit: { completed: false, time: '' },
        },
        deliverables: [],
      },
    })
    const wrapper = mountCard(context)

    expect(wrapper.text()).toContain('初稿已提交')
    expect(wrapper.text()).toContain('发起评审')
    expect(wrapper.text()).toContain('系统管理员')
  })
})
