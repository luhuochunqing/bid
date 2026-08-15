import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreParseDrawer from './ScoreParseDrawer.vue'
import { bidAgentApi } from '@/api/modules/bidAgent.js'

vi.mock('@/api/modules/bidAgent.js', () => ({
  bidAgentApi: {
    getFullAnalysis: vi.fn(),
    getQualificationMatch: vi.fn(),
    getScoringCriteria: vi.fn(),
  },
}))

describe('ScoreParseDrawer.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    bidAgentApi.getFullAnalysis.mockResolvedValue({
      data: {
        sourceFileName: '测试招标文件.pdf',
        scoringCriteria: {
          items: [
            { itemNumber: 'A1', dimension: '技术方案', indicator: '总体架构设计', weight: 10, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '专家人工评审' },
            { itemNumber: 'D1', dimension: '资质业绩', indicator: '系统集成一级', weight: 6, status: 'ok', statusText: '✓ 满足', scoreType: '客观项', estScore: 6, estBasis: '资质库匹配' },
          ],
        },
      },
    })
    bidAgentApi.getQualificationMatch.mockResolvedValue({ data: {} })
    bidAgentApi.getScoringCriteria.mockResolvedValue({
      data: {
        sourceFileName: '测试招标文件.pdf',
        structuredItems: [
          { itemNumber: 'A1', dimension: '技术方案', indicator: '总体架构设计', weight: 10, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '专家人工评审' },
          { itemNumber: 'D1', dimension: '资质业绩', indicator: '系统集成一级', weight: 6, status: 'ok', statusText: '✓ 满足', scoreType: '客观项', estScore: 6, estBasis: '资质库匹配' },
        ],
      },
    })
  })

  it('mounts and opens drawer in stage 2', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: {
        projectId: 123,
      },
      global: {
        stubs: {
          'el-drawer': {
            template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>',
            props: ['modelValue'],
          },
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ stage: 2, autoScore: false })

    expect(wrapper.vm.visible).toBe(true)
    expect(wrapper.vm.currentStage).toBe(2)
    expect(wrapper.text()).toContain('招标文件解析')
    expect(wrapper.text()).toContain('投标文件评分')
    expect(wrapper.text()).toContain('总体架构设计')
    expect(wrapper.text()).toContain('系统集成一级')
  })

  it('supports stage 1 mode without actual score calculation', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: {
        projectId: 123,
      },
      global: {
        stubs: {
          'el-drawer': {
            template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>',
            props: ['modelValue'],
          },
          'el-button': true,
        },
      },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false })

    expect(wrapper.vm.currentStage).toBe(1)
    expect(wrapper.text()).toContain('尚未上传投标文件')
  })
})
