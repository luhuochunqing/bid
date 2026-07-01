// Input: ProjectStageTimeline mounted with mocked lifecycle API
// Output: linear stepper renders 6 stages, emits stage-click only for snapshot-accessible stages, and renders terminal CLOSED stage as 已完成 (CO-443)
// Pos: src/components/project/stage/ - 6-stage UI tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getStage: vi.fn(),
  },
}))
vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { ElMessage } from 'element-plus'
import ProjectStageTimeline from './ProjectStageTimeline.vue'

const stubs = {
  'el-steps': { template: '<div class="el-steps"><slot /></div>' },
  'el-step': {
    props: ['title', 'description', 'status'],
    inheritAttrs: false,
    template:
      '<div class="el-step" :data-title="title" :data-status="status" @click="$emit(\'click\')">{{ title }}/{{ description }}</div>',
  },
  'el-tag': { template: '<span><slot /></span>' },
}

describe('ProjectStageTimeline', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders 6 PRD stages in order', async () => {
    projectLifecycleApi.getStage.mockResolvedValue({
      data: { currentStage: 'EVALUATING', completedStages: ['INITIATED', 'DRAFTING'] },
    })
    const wrapper = mount(ProjectStageTimeline, {
      props: { projectId: 1 },
      global: { stubs },
    })
    await flushPromises()

    const steps = wrapper.findAll('.el-step')
    expect(steps).toHaveLength(6)
    const titles = steps.map((s) => s.attributes('data-title'))
    expect(titles).toEqual(['项目立项', '标书制作', '评标中', '结果确认', '项目复盘', '项目结项'])

    expect(steps[0].attributes('data-status')).toBe('success')
    expect(steps[1].attributes('data-status')).toBe('success')
    expect(steps[2].attributes('data-status')).toBe('process')
    expect(steps[3].attributes('data-status')).toBe('wait')
  })

  it('emits stage-click for unlocked stages and blocks locked ones', async () => {
    projectLifecycleApi.getStage.mockResolvedValue({
      data: { currentStage: 'DRAFTING', completedStages: ['INITIATED'] },
    })
    const wrapper = mount(ProjectStageTimeline, {
      props: { projectId: 1 },
      global: { stubs },
    })
    await flushPromises()

    const steps = wrapper.findAll('.el-step')
    await steps[0].trigger('click') // INITIATED (unlocked)
    await steps[3].trigger('click') // RESULT_PENDING (locked)

    const emitted = wrapper.emitted('stage-click') || []
    expect(emitted).toHaveLength(1)
    expect(emitted[0][0].code).toBe('INITIATED')
    expect(ElMessage.info).toHaveBeenCalledWith('该阶段尚未到达，无法进入')
  })

  it('emits stage-click for backend-accessible drafting stage', async () => {
    projectLifecycleApi.getStage.mockResolvedValue({
      data: { currentStage: 'INITIATED', completedStages: [], accessibleStages: ['INITIATED', 'DRAFTING'] },
    })
    const wrapper = mount(ProjectStageTimeline, {
      props: { projectId: 1 },
      global: { stubs },
    })
    await flushPromises()

    const steps = wrapper.findAll('.el-step')
    await steps[1].trigger('click') // DRAFTING (extra unlocked)

    const emitted = wrapper.emitted('stage-click') || []
    expect(emitted).toHaveLength(1)
    expect(emitted[0][0].code).toBe('DRAFTING')
    expect(ElMessage.info).not.toHaveBeenCalled()
  })

  // CO-443: 结项审核通过后进度导航栏仍显示进行中
  it('shows CLOSED stage as 已完成 when terminal=true', async () => {
    projectLifecycleApi.getStage.mockResolvedValue({
      data: {
        currentStage: 'CLOSED',
        completedStages: ['INITIATED', 'DRAFTING', 'EVALUATING', 'RESULT_PENDING', 'RETROSPECTIVE'],
        terminal: true,
      },
    })
    const wrapper = mount(ProjectStageTimeline, {
      props: { projectId: 1 },
      global: { stubs },
    })
    await flushPromises()

    const steps = wrapper.findAll('.el-step')
    const closedStep = steps[5] // 项目结项
    expect(closedStep.attributes('data-status')).toBe('success')
    expect(closedStep.text()).toContain('已完成')
    expect(closedStep.text()).not.toContain('进行中')
  })

  it('shows current non-terminal stage as 进行中 (regression)', async () => {
    projectLifecycleApi.getStage.mockResolvedValue({
      data: { currentStage: 'DRAFTING', completedStages: ['INITIATED'], terminal: false },
    })
    const wrapper = mount(ProjectStageTimeline, {
      props: { projectId: 1 },
      global: { stubs },
    })
    await flushPromises()

    const steps = wrapper.findAll('.el-step')
    expect(steps[1].attributes('data-status')).toBe('process')
    expect(steps[1].text()).toContain('进行中')
  })

  // CO-443: 结项申请审批中（terminal=false, currentStage=CLOSED）应显示"进行中"
  it('shows CLOSED stage as in-progress when terminal flag is false', async () => {
    projectLifecycleApi.getStage.mockResolvedValue({
      data: {
        currentStage: 'CLOSED',
        completedStages: ['INITIATED', 'DRAFTING', 'EVALUATING', 'RESULT_PENDING', 'RETROSPECTIVE'],
        accessibleStages: ['INITIATED', 'DRAFTING', 'EVALUATING', 'RESULT_PENDING', 'RETROSPECTIVE', 'CLOSED'],
        terminal: false,
      },
    })
    const wrapper = mount(ProjectStageTimeline, {
      props: { projectId: 1 },
      global: { stubs },
    })
    await flushPromises()

    const steps = wrapper.findAll('.el-step')
    const closedStep = steps[5]
    expect(closedStep.attributes('data-title')).toBe('项目结项')
    expect(closedStep.text()).toContain('进行中')
    expect(closedStep.text()).not.toContain('已完成')
    expect(closedStep.attributes('data-status')).toBe('process')
  })
})
