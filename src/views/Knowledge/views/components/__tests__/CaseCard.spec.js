import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CaseCard from '../CaseCard.vue'

const stubs = {
  ElCard: { template: '<div class="card-stub" @click="$emit(\'click\', $event)"><slot /></div>' },
  ElButton: { template: '<button type="button" @click="$emit(\'click\', $event)"><slot /></button>', emits: ['click'] },
  ElIcon: { template: '<i><slot /></i>' },
  ElTag: { template: '<span class="tag-stub"><slot /></span>' },
  ElPopover: {
    props: ['modelValue'],
    template: '<div class="popover-stub"><slot name="reference" /><slot /></div>'
  },
  DateTimeDisplay: { template: '<span class="dtd-stub" />' }
}

const mockCaseData = {
  caseId: 101,
  scoringTitle: '应急预案与响应',
  responseTextSummary: '我司具备7*24小时应急响应机制与专业运维团队。',
  projectType: 'IT',
  customerType: 'GOV',
  createdAt: '2026-08-28 10:00:00',
  matchLabel: '优质',
  matchReasons: ['评分项条款高度契合', '类别一致', '历史中标案例'],
  matchedKeywords: ['应急预案', '运维']
}

describe('CaseCard.vue', () => {
  it('渲染普通案例卡片（无匹配度时）', () => {
    const wrapper = mount(CaseCard, {
      props: { caseData: mockCaseData, matchScore: null },
      global: { stubs }
    })

    expect(wrapper.text()).toContain('应急预案与响应')
    expect(wrapper.text()).not.toContain('匹配度')
    expect(wrapper.find('.match-popover-panel').exists()).toBe(false)
  })

  it('传入 matchScore 时渲染匹配度徽标和 Popover 命中依据面板', () => {
    const wrapper = mount(CaseCard, {
      props: { caseData: mockCaseData, matchScore: 85 },
      global: { stubs }
    })

    expect(wrapper.text()).toContain('匹配度')
    expect(wrapper.text()).toContain('85%')
    expect(wrapper.text()).toContain('优质')

    const popoverPanel = wrapper.find('.match-popover-panel')
    expect(popoverPanel.exists()).toBe(true)
    expect(popoverPanel.text()).toContain('AI 匹配度分析')
    expect(popoverPanel.text()).toContain('评分项条款高度契合')
    expect(popoverPanel.text()).toContain('类别一致')
    expect(popoverPanel.text()).toContain('应急预案')
    expect(popoverPanel.text()).toContain('运维')
  })

  it('支持 matchReasons 与 matchedKeywords 为字符串格式的容错解析', () => {
    const wrapper = mount(CaseCard, {
      props: {
        caseData: {
          ...mockCaseData,
          matchReasons: '评分项匹配、类别一致、客户类型一致',
          matchedKeywords: '架构设计, 高可用'
        },
        matchScore: 65
      },
      global: { stubs }
    })

    expect(wrapper.text()).toContain('65%')
    const popoverPanel = wrapper.find('.match-popover-panel')
    expect(popoverPanel.text()).toContain('评分项匹配')
    expect(popoverPanel.text()).toContain('客户类型一致')
    expect(popoverPanel.text()).toContain('架构设计')
    expect(popoverPanel.text()).toContain('高可用')
  })

  it('点击复用按钮触发 reuse 事件', async () => {
    const wrapper = mount(CaseCard, {
      props: { caseData: mockCaseData, matchScore: 90 },
      global: { stubs }
    })

    const button = wrapper.find('button')
    await button.trigger('click')
    expect(wrapper.emitted('reuse')).toHaveLength(1)
    expect(wrapper.emitted('reuse')[0][0]).toMatchObject({ caseId: 101 })
  })
})
