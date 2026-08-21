import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AICaseDetailModal from './AICaseDetailModal.vue'

describe('AICaseDetailModal.vue', () => {
  const mockCase = {
    id: 101,
    scoringPointTitle: '高可用架构与容灾方案',
    scoringCategory: '技术方案',
    matchScore: 95,
    matchLabel: '优质',
    matchReasons: ['评分项条款高度契合', '历史中标案例'],
    matchedKeywords: ['高可用', '容灾'],
    requirementRaw: '投标人必须具备高可用容灾架构能力',
    responseText: '我方采用高可用架构，支持同城双活与动态容灾',
    sourceProjectName: '2025省级电网项目',
    bidResult: 'WON'
  }

  const commonStubs = {
    'el-dialog': {
      template: '<div class="el-dialog-stub"><slot name="header"/><slot/><slot name="footer"/></div>'
    },
    'el-descriptions': true,
    'el-descriptions-item': true,
    'el-tag': {
      template: '<span><slot /></span>'
    },
    'el-divider': true,
    'el-button': true
  }

  it('renders three-module detail without reuse count', () => {
    const wrapper = mount(AICaseDetailModal, {
      props: {
        modelValue: true,
        caseDetail: {
          ...mockCase,
          reuseCount: 8,
          bidEntity: '某省电力公司',
          createdAt: '2026-03-12',
          projectStatus: 'WON',
          customerType: 'CENTRAL_SOE',
          projectType: '综合'
        }
      },
      global: {
        stubs: commonStubs
      }
    })

    const text = wrapper.text()
    expect(text).toContain('高可用架构与容灾方案')
    expect(text).toContain('95%')
    expect(text).toContain('优质')
    expect(text).toContain('评分项详细要素原文')
    expect(text).toContain('应答片段原文')
    expect(text).toContain('项目名称')
    expect(text).toContain('2025省级电网项目')
    expect(text).toContain('招标主体')
    expect(text).toContain('创建时间')
    expect(text).toContain('项目状态')
    expect(text).toContain('客户类型')
    expect(text).toContain('项目类型')
    expect(text).not.toMatch(/复用次数/)
    expect(text).not.toMatch(/复用\s*\d+\s*次/)
  })

  it('emits reuse event when reuse button is clicked', async () => {
    const wrapper = mount(AICaseDetailModal, {
      props: {
        modelValue: true,
        caseDetail: mockCase
      },
      global: {
        stubs: commonStubs
      }
    })

    await wrapper.vm.handleReuse()
    expect(wrapper.emitted('reuse')).toBeTruthy()
    expect(wrapper.emitted('reuse')[0][0]).toEqual(mockCase)
  })
})
