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

describe('CaseDetailDialog - 应答片段 markdown 安全渲染', () => {
  const tableMarkdown = [
    '| 指标 | 数值 |',
    '| --- | --- |',
    '| 可用性 | 99.9% |'
  ].join('\n')

  it('preprocessPlainText 将纯文本分段、换行和序号转换为安全 HTML', () => {
    const wrapper = mountDialog()
    const preprocess = wrapper.vm.preprocessPlainText

    expect(preprocess('第一段\n第二行\n\n第二段')).toBe('第一段\n<br>第二行\n\n第二段')
    expect(preprocess('1. 第一项\n2. 第二项')).toContain('<ol>\n<li>第一项</li>\n<li>第二项</li>\n</ol>')
    for (const input of ['一、中文序号', '①带圈序号', '（1）括号序号', '(2)半角括号']) {
      expect(preprocess(input)).toContain('<li>')
      expect(preprocess(input)).not.toContain(input.slice(0, -4))
    }
    expect(preprocess('')).toBe('')
    expect(preprocess('<script> & >')).toBe('&lt;script&gt; &amp; &gt;')
  })

  it.each([
    ['S1 表格', tableMarkdown, '<table>'],
    ['S2 标题+段落', '## 标题\n段落内容', '<h2>'],
    ['S3 表格+段落', `${tableMarkdown}\n\n普通段落`, '<table>'],
    ['S4 引用', '> 引用内容\n> 第二行', '<blockquote>'],
    ['S5 围栏代码块', '```java\ncode here\n```', '<pre><code']
  ])('%s 保持 markdown 结构并正常渲染', (_name, responseText, expectedTag) => {
    const wrapper = mountDialog({ caseData: { ...caseData, responseText } })
    expect(wrapper.find('.response-box').html()).toContain(expectedTag)
  })

  it('组件渲染纯文本换行和序号列表，li 内不重复显示编号', () => {
    const responseText = '说明第一行\n说明第二行\n\n1. 第一项\n2. 第二项'
    const wrapper = mountDialog({ caseData: { ...caseData, responseText } })
    const box = wrapper.find('.response-box')

    expect(box.html()).toContain('<br>')
    expect(box.find('ol li').exists()).toBe(true)
    expect(box.findAll('ol li')[0].text()).toBe('第一项')
    expect(box.findAll('ol li')[1].text()).toBe('第二项')
  })

  it('GFM 表格渲染为 HTML table，字数仍按原文长度', () => {
    const wrapper = mountDialog({
      caseData: { ...caseData, responseText: tableMarkdown }
    })
    const box = wrapper.find('.response-box')
    const table = box.find('table')
    expect(table.exists()).toBe(true)
    expect(table.find('th').exists()).toBe(true)
    expect(table.find('td').exists()).toBe(true)
    expect(table.text()).toContain('指标')
    expect(table.text()).toContain('99.9%')
    expect(wrapper.find('.word-count-hint').text()).toBe(`共 ${tableMarkdown.length} 字`)
  })

  it('应答片段中的 script / onerror 不以可执行标签落地', () => {
    const wrapper = mountDialog({
      caseData: {
        ...caseData,
        responseText: '<script>alert(1)</script>\n<img src=x onerror=alert(1)>'
      }
    })
    const box = wrapper.find('.response-box')
    expect(box.find('script').exists()).toBe(false)
    const withOnerror = box.findAll('*').filter((el) => el.attributes('onerror') !== undefined)
    expect(withOnerror).toHaveLength(0)
  })

  it('应答片段为空、空白或非字符串时展示「暂无应答内容」', () => {
    for (const responseText of ['', '   ', null, undefined, 12]) {
      const wrapper = mountDialog({
        caseData: { ...caseData, responseText }
      })
      expect(wrapper.find('.response-box').text()).toContain('暂无应答内容')
      expect(wrapper.find('.response-box').find('p').exists()).toBe(false)
    }
  })

  it('markdown javascript: 链接不保留可执行 href', () => {
    const wrapper = mountDialog({
      caseData: { ...caseData, responseText: '[点击](javascript:alert(1))' }
    })
    const html = wrapper.find('.response-box').html().toLowerCase()
    expect(html).not.toContain('javascript:')
    const link = wrapper.find('.response-box a')
    if (link.exists()) {
      expect(link.attributes('href') || '').not.toMatch(/javascript:/i)
    }
  })

  it('markdown 图片与裸 img 均不渲染为 img 标签', () => {
    const wrapper = mountDialog({
      caseData: {
        ...caseData,
        responseText: '架构见图：![logo](https://evil.example/track.png)\n<img src="https://evil.example/x.png">'
      }
    })
    expect(wrapper.find('.response-box').find('img').exists()).toBe(false)
  })

  it('无表格的多行应答在 HTML 中保留换行，不折叠成一行', () => {
    const prose = '联系人：张三\n电话：138xxxx\n地址：上海市'
    const wrapper = mountDialog({
      caseData: { ...caseData, responseText: prose }
    })
    const paragraph = wrapper.find('.response-box p')
    expect(paragraph.exists()).toBe(true)
    expect(paragraph.element.textContent).toMatch(/张三/)
    expect(paragraph.element.textContent).toMatch(/电话/)
    expect(paragraph.element.textContent).toContain('\n')
  })

  it('复用事件携带原始 responseText，不是渲染后的 HTML', async () => {
    const wrapper = mountDialog({
      caseData: { ...caseData, responseText: tableMarkdown }
    })
    await findButtonByText(wrapper, '复用').trigger('click')
    expect(wrapper.emitted('reuse')[0][0].responseText).toBe(tableMarkdown)
    expect(wrapper.emitted('reuse')[0][0].responseText).not.toContain('<table')
  })
})
