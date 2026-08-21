import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AiRecommendDualPaneDialog from './AiRecommendDualPaneDialog.vue'

const mockCase = {
  scoringTitle: '高可用架构与容灾方案',
  requirementRaw: '投标人必须具备高可用容灾架构能力',
  responseText: '我方采用同城双活部署，支持动态容灾切换',
  sourceProjectName: '2025省级电网项目',
  bidEntity: '某省电力公司',
  createdAt: '2026-03-12',
  projectStatus: 'WON',
  customerType: 'CENTRAL_SOE',
  projectType: '综合',
}

const stubs = {
  'el-dialog': {
    template: '<div class="el-dialog-stub"><slot /><slot name="footer"/></div>'
  },
  'el-button': {
    template: '<button @click="$emit(\'click\')"><slot /></button>'
  },
  'el-tag': {
    template: '<span><slot /></span>'
  },
}

describe('AiRecommendDualPaneDialog.vue', () => {
  it('does not display reuse count', () => {
    const wrapper = mount(AiRecommendDualPaneDialog, {
      props: { modelValue: true, selectedCase: { ...mockCase, reuseCount: 8 } },
      global: { stubs }
    })
    expect(wrapper.text()).not.toMatch(/复用次数/)
    expect(wrapper.text()).not.toMatch(/复用\s*\d+\s*次/)
  })

  it('renders scoring item on top, three middle layers, and reuse button', () => {
    const wrapper = mount(AiRecommendDualPaneDialog, {
      props: { modelValue: true, selectedCase: mockCase },
      global: { stubs }
    })
    const text = wrapper.text()
    expect(text).toContain('高可用架构与容灾方案')
    expect(text).toContain('评分项详细要素原文')
    expect(text).toContain('投标人必须具备高可用容灾架构能力')
    expect(text).toContain('应答片段原文')
    expect(text).toContain('我方采用同城双活部署，支持动态容灾切换')
    expect(text).toContain('项目名称')
    expect(text).toContain('2025省级电网项目')
    expect(text).toContain('招标主体')
    expect(text).toContain('某省电力公司')
    expect(text).toContain('创建时间')
    expect(text).toContain('2026-03-12')
    expect(text).toContain('项目状态')
    expect(text).toContain('客户类型')
    expect(text).toContain('项目类型')
    expect(text).toContain('综合')
    expect(wrapper.findAll('button').some(b => b.text().trim() === '复用')).toBe(true)
  })

  it('shows dash for empty customer type and project type', () => {
    const wrapper = mount(AiRecommendDualPaneDialog, {
      props: {
        modelValue: true,
        selectedCase: { ...mockCase, customerType: '', projectType: '' }
      },
      global: { stubs }
    })
    expect(wrapper.text()).not.toContain('通用客户')
    expect(wrapper.text()).not.toContain('常规项目')
    expect(wrapper.text()).toMatch(/客户类型\s*-/)
    expect(wrapper.text()).toMatch(/项目类型\s*-/)
  })

  it('emits reuse when the reuse button is clicked', async () => {
    const wrapper = mount(AiRecommendDualPaneDialog, {
      props: { modelValue: true, selectedCase: mockCase },
      global: { stubs }
    })
    const reuseBtn = wrapper.findAll('button').find(b => b.text().trim() === '复用')
    await reuseBtn.trigger('click')
    expect(wrapper.emitted('reuse')).toBeTruthy()
    expect(wrapper.emitted('reuse')[0][0]).toEqual(mockCase)
  })
})
