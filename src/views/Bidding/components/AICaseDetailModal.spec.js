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
    'el-tag': true,
    'el-divider': true,
    'el-button': true
  }

  it('renders detail modal with highlighted text and meta tags', () => {
    const wrapper = mount(AICaseDetailModal, {
      props: {
        modelValue: true,
        caseDetail: mockCase
      },
      global: {
        stubs: commonStubs
      }
    })

    expect(wrapper.text()).toContain('高可用架构与容灾方案')
    expect(wrapper.text()).toContain('95%')
    expect(wrapper.text()).toContain('优质')
    expect(wrapper.text()).toContain('2025省级电网项目')
    expect(wrapper.html()).toContain('highlight-kw')
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
