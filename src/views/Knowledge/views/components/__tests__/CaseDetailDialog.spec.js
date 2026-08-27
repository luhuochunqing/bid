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
})
