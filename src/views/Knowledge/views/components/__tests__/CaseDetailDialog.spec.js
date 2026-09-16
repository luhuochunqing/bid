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

const sourceLocation = (wrapper) => wrapper.find('.source-location')

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

  it('来源路径优先显示，并在同一行显示文档和页码', () => {
    const wrapper = mountDialog({
      caseData: {
        ...caseData,
        sourceSectionTitle: '仅标题',
        sourceSectionPath: '第一章 > 服务方案',
        sourceDocumentName: '技术标.docx',
        sourcePageNumber: 12
      }
    })
    expect(sourceLocation(wrapper).text()).toContain('原文所在标题：第一章 > 服务方案')
    expect(sourceLocation(wrapper).text()).toContain('来源文档：技术标.docx')
    expect(sourceLocation(wrapper).text()).toContain('页码：12')
    expect(sourceLocation(wrapper).text()).not.toContain('仅标题')
  })

  it('只有来源标题时显示标题且可点击', () => {
    const wrapper = mountDialog({ caseData: { ...caseData, sourceSectionTitle: '项目实施' } })
    expect(sourceLocation(wrapper).text()).toContain('原文所在标题：项目实施')
    expect(sourceLocation(wrapper).element.tagName).toBe('BUTTON')
  })

  it('来源路径和标题均为空时显示降级文案且信息行不可点击', () => {
    const wrapper = mountDialog({
      caseData: { ...caseData, scoringPointTitle: null, scoringTitle: null, sourceSectionTitle: null, sourceSectionPath: null, responseText: '纯普通文本无标题' }
    })
    expect(wrapper.find('.source-location').exists()).toBe(false)
    expect(wrapper.find('.source-location-fallback').text()).toBe('原文所在标题：暂无来源标题')
  })

  it('来源路径和标题为空但有评分项标题时智能兜底展示且信息行可点击', () => {
    const wrapper = mountDialog({
      caseData: { ...caseData, scoringPointTitle: '项目实施', sourceSectionTitle: null, sourceSectionPath: null, responseText: '纯正文内容' }
    })
    expect(sourceLocation(wrapper).text()).toContain('原文所在标题：项目实施')
    expect(sourceLocation(wrapper).element.tagName).toBe('BUTTON')
  })

  it('智能从应答正文提取章节标题、详见引用与方案书名', () => {
    // 1. Markdown 章节列表开头
    const w1 = mountDialog({
      caseData: { ...caseData, scoringPointTitle: null, sourceSectionTitle: null, sourceSectionPath: null, responseText: '* 1. 质量管理方案\n     1. 商品安全溯源\n\n为全面保障质量安全...' }
    })
    expect(sourceLocation(w1).text()).toContain('原文所在标题：质量管理方案 > 商品安全溯源')

    // 2. 详见引用
    const w2 = mountDialog({
      caseData: { ...caseData, scoringPointTitle: null, sourceSectionTitle: null, sourceSectionPath: null, responseText: '**西域承诺物流配送... 详见七、服务方案-（二）、供货方案**' }
    })
    expect(sourceLocation(w2).text()).toContain('原文所在标题：七、服务方案 > （二） > 供货方案')

    // 3. 书名号规范方案
    const w3 = mountDialog({
      caseData: { ...caseData, scoringPointTitle: null, sourceSectionTitle: null, sourceSectionPath: null, responseText: '我司针对三峡集团业务特性，制定了完备的《突发事件防汛防灾及双活容灾应急预案》。建立三级应急联动...' }
    })
    expect(sourceLocation(w3).text()).toContain('原文所在标题：突发事件防汛防灾及双活容灾应急预案')
  })

  it('来源标题与评分项标题同名时仍保留两个不同 label', () => {
    const wrapper = mountDialog({
      caseData: { ...caseData, sourceSectionTitle: caseData.scoringPointTitle }
    })
    expect(wrapper.text()).toContain(`评分项标题：${caseData.scoringPointTitle}`)
    expect(sourceLocation(wrapper).text()).toContain(`原文所在标题：${caseData.scoringPointTitle}`)
  })

  it('点击来源信息行触发 open-bid 并携带定位提示标记', async () => {
    const wrapper = mountDialog({ caseData: { ...caseData, sourceSectionPath: '第二章 > 售后服务' } })
    await sourceLocation(wrapper).trigger('click')
    expect(wrapper.emitted('open-bid')).toHaveLength(1)
    expect(wrapper.emitted('open-bid')[0][0]).toMatchObject({ sourceSectionPath: '第二章 > 售后服务' })
    expect(wrapper.emitted('open-bid')[0][1]).toEqual({ notifyLocation: true })
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

  describe('CaseDetailDialog - 真实库无换行大段文本排版与分段能力', () => {
    it('行内无换行阿拉伯数字序号自动换行并转为列表', () => {
      const raw = '我司将按照以下阶段实施：1.需求调研（2周）2.方案设计（3周）3.系统部署（4周）4.测试验收（2周）5.培训上线（1周），总计12周完成。'
      const wrapper = mountDialog({ caseData: { ...caseData, responseText: raw } })
      const box = wrapper.find('.response-box')

      expect(box.find('ol').exists()).toBe(true)
      const items = box.findAll('ol li')
      expect(items.length).toBeGreaterThanOrEqual(5)
      expect(items[0].text()).toContain('需求调研（2周）')
      expect(items[1].text()).toContain('方案设计（3周）')
    })

    it('多级序号与字母子序号在无换行文本中自动切分排版', () => {
      const raw = '完成。6.2.4）领取复盘任务初盘任务完成后，系统会把初盘有差异的盘点单生成对应的复盘任务。6.2.5）差异原因查找及差异数据的处理a.异常处理人员逐条核查b.复盘异常处理'
      const wrapper = mountDialog({ caseData: { ...caseData, responseText: raw } })
      const box = wrapper.find('.response-box')

      // 应包含断行或有序列表，将原本挤在一行的各子项拆分
      expect(box.html()).toMatch(/<ol>|<br>|<p>/)
      expect(box.text()).toContain('领取复盘任务')
      expect(box.text()).toContain('差异原因查找')
      expect(box.find('ol li').exists()).toBe(true)
    })

    it('单行无分隔行 Markdown 表格自动补全表头并渲染为 HTML table', () => {
      const raw = '| **投标报价** | **宣传品报价** | **商品池报价** | **/** |'
      const wrapper = mountDialog({ caseData: { ...caseData, responseText: raw } })
      const box = wrapper.find('.response-box')

      expect(box.find('table').exists()).toBe(true)
      expect(box.find('th').text()).toContain('投标报价')
    })

    it('多句长文本按句号智能断句插入换行，提升大段可读性', () => {
      const raw = '我司针对三峡集团业务特性，制定了完备的应急预案。建立三级应急联动响应指挥体系：一线运维工程师15分钟内到达现场。储备包括核心板卡在内的原厂应急备件库，保证系统重大故障在2小时内恢复服务。定期开展半年度实战攻防与应急演练。'
      const wrapper = mountDialog({ caseData: { ...caseData, responseText: raw } })
      const box = wrapper.find('.response-box')

      expect(box.html()).toContain('<br>')
    })
  })
})
