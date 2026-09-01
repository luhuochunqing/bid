import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CaseDetailDialog from '../CaseDetailDialog.vue'

const stubs = {
  ElDialog: { template: '<div v-if="modelValue" class="dialog-stub"><slot /></div>', props: ['modelValue'] },
  ElButton: { template: '<button type="button" @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  ElIcon: { template: '<i><slot /></i>' },
  ElDescriptions: { template: '<div class="desc-stub"><slot /></div>' },
  ElDescriptionsItem: { template: '<div class="desc-item-stub"><slot /></div>' },
  ElTag: { template: '<span class="tag-stub"><slot /></span>' },
  DateTimeDisplay: { template: '<span class="dtd-stub" />' }
}

const caseData = {
  scoringPointTitle: '现场服务能力',
  requirementRaw: '能提供现场服务方案，得3分',
  responseText: '我方能提供现场服务方案，24小时内能到现场',
  sourceProjectName: '2026-测试竞品表格',
  ownerUnit: '上海锦电新能源科技有限公司',
  createdAt: '2026-08-27 15:57:04',
  bidResult: 'WON',
  customerType: 'PRIVATE',
  projectType: 'COMPREHENSIVE'
}

const mountDialog = (props = {}) => mount(CaseDetailDialog, {
  props: { modelValue: true, caseData, loading: false, ...props },
  global: { stubs }
})

const findButtonByText = (wrapper, text) =>
  wrapper.findAll('button').find((b) => b.text().includes(text))

describe('CaseDetailDialog - 案例详情弹窗布局契约', () => {
  it('应答片段标题为「应答片段摘要」，查看源项目/标书原文按钮在标题行右侧', () => {
    const wrapper = mountDialog()
    const titleRow = wrapper.find('.response-title')
    expect(titleRow.text()).toContain('应答片段摘要')
    expect(titleRow.text()).not.toContain('应答片段全文')

    const titleActions = titleRow.find('.title-actions')
    expect(titleActions.exists()).toBe(true)
    expect(findButtonByText(titleActions, '查看源项目')).toBeTruthy()
    expect(findButtonByText(titleActions, '标书原文')).toBeTruthy()
  })

  it('字数提示与应答内容正常渲染', () => {
    const wrapper = mountDialog()
    expect(wrapper.find('.word-count-hint').text()).toBe(`共 ${caseData.responseText.length} 字`)
    expect(wrapper.find('.response-box').text()).toContain(caseData.responseText)
  })

  it('底部操作栏仅保留「复用」按钮', () => {
    const wrapper = mountDialog()
    const buttons = wrapper.find('.dialog-actions').findAll('button')
    expect(buttons).toHaveLength(1)
    expect(buttons[0].text()).toContain('复用')
  })

  it('点击操作按钮触发对应事件并携带案例数据', async () => {
    const wrapper = mountDialog()
    await findButtonByText(wrapper, '查看源项目').trigger('click')
    await findButtonByText(wrapper, '标书原文').trigger('click')
    await findButtonByText(wrapper, '复用').trigger('click')
    expect(wrapper.emitted('go-source')[0][0]).toMatchObject({ sourceProjectName: caseData.sourceProjectName })
    expect(wrapper.emitted('open-bid')).toHaveLength(1)
    expect(wrapper.emitted('reuse')).toHaveLength(1)
  })

  it('当提供匹配度数据时渲染匹配分析提示条与命中依据', () => {
    const wrapper = mountDialog({
      caseData: {
        ...caseData,
        matchScore: 88,
        matchLabel: '优质',
        matchReasons: ['评分项条款高度契合', '类别一致', '历史中标案例'],
        matchedKeywords: ['现场服务', '应急响应']
      }
    })

    const banner = wrapper.find('.match-detail-banner')
    expect(banner.exists()).toBe(true)
    expect(banner.text()).toContain('88%')
    expect(banner.text()).toContain('优质')
    expect(banner.text()).toContain('评分项条款高度契合')
    expect(banner.text()).toContain('类别一致')
    expect(banner.text()).toContain('现场服务')
  })

  it('项目状态标签三态映射：WON=已中标 / LOST=未中标 / FAILED=已流标，缺省显示未知', () => {
    const labelOf = (bidResult) => mountDialog({ caseData: { ...caseData, bidResult } }).text()
    expect(labelOf('WON')).toContain('已中标')
    expect(labelOf('LOST')).toContain('未中标')
    expect(labelOf('FAILED')).toContain('已流标')
    expect(labelOf(null)).toContain('未知')
  })

  it('未提供匹配度数据时不渲染匹配分析提示条', () => {
    const wrapper = mountDialog({
      caseData: { ...caseData, matchScore: null, score: null }
    })
    expect(wrapper.find('.match-detail-banner').exists()).toBe(false)
  })
})

