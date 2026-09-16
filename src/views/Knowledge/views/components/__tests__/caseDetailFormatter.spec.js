import { describe, expect, it } from 'vitest'
import {
  escapeHtml,
  isMarkdownStructureLine,
  isMarkdownBlock,
  splitInlineFormats,
  insertBrBetweenNonListLines,
  processPlainTextBlock,
  preprocessPlainText,
  extractSourceLocationText,
  extractSourceDocumentText
} from '../caseDetailFormatter.js'

describe('caseDetailFormatter 单元测试', () => {
  describe('escapeHtml', () => {
    it('转义 HTML 特殊字符', () => {
      expect(escapeHtml('<script>alert("xss") & \'foo\'</script>')).toBe(
        '&lt;script&gt;alert(&quot;xss&quot;) &amp; &#039;foo&#039;&lt;/script&gt;'
      )
    })
  })

  describe('isMarkdownStructureLine & isMarkdownBlock', () => {
    it('识别 markdown 结构行', () => {
      expect(isMarkdownStructureLine('# 一级标题')).toBe(true)
      expect(isMarkdownStructureLine('> 引用文本')).toBe(true)
      expect(isMarkdownStructureLine('```json')).toBe(true)
      expect(isMarkdownStructureLine('- 列表项')).toBe(true)
      expect(isMarkdownStructureLine('* 无序列表')).toBe(true)
      expect(isMarkdownStructureLine('| a | b |')).toBe(true)
      expect(isMarkdownStructureLine('普通段落文本')).toBe(false)
    })

    it('识别 markdown 块', () => {
      expect(isMarkdownBlock('普通文本\n# 标题\n普通文本')).toBe(true)
      expect(isMarkdownBlock('普通文本\n第二行')).toBe(false)
    })
  })

  describe('splitInlineFormats', () => {
    it('修复单行或无分隔行表头的 Markdown 表格', () => {
      const input = '| **指标** | **说明** | **备注** |'
      const output = splitInlineFormats(input)
      expect(output).toContain('| --- | --- | --- |')
    })

    it('分号后有多个空格或跟键值冒号时切分换行', () => {
      const input = '模块一：基础配置；  模块二：进阶配置'
      const output = splitInlineFormats(input)
      expect(output).toContain('模块一：基础配置；\n')
    })

    it('切分行内数字、多级数字、中文序号', () => {
      const input = '实施阶段如下：1.前期调研2.系统设计3.开发交付4.上线试运行。'
      const output = splitInlineFormats(input)
      expect(output).toMatch(/1\.前期调研\n2\.系统设计/)
    })

    it('切分多级数字序号（如 6.2.4、6.2.5）', () => {
      const input = '已完成。6.2.4）复盘任务生成6.2.5）差异分析'
      const output = splitInlineFormats(input)
      expect(output).toContain('\n6.2.4）')
      expect(output).toContain('\n6.2.5）')
    })

    it('多句长文本句号后自动换行切分段落', () => {
      const input = '我司具备完整的售后服务保障体系。全国设立了二十余个备品备件中心。工程师支持7x24小时全天候待命响应。'
      const output = splitInlineFormats(input)
      expect(output).toBe('我司具备完整的售后服务保障体系。\n全国设立了二十余个备品备件中心。\n工程师支持7x24小时全天候待命响应。')
    })
  })

  describe('insertBrBetweenNonListLines & processPlainTextBlock', () => {
    it('非列表连续行之间插入 <br>', () => {
      const lines = ['行一', '行二', '<ol>', '<li>项一</li>', '</ol>', '行三']
      const res = insertBrBetweenNonListLines(lines)
      expect(res).toBe('行一\n<br>行二\n<ol>\n<li>项一</li>\n</ol>\n行三')
    })

    it('处理带序号的纯文本块转为 <ol> 列表', () => {
      const block = '1. 需求分析\n2. 方案制定\n3. 现场实施'
      const res = processPlainTextBlock(block)
      expect(res).toContain('<ol>')
      expect(res).toContain('<li>需求分析</li>')
      expect(res).toContain('<li>方案制定</li>')
      expect(res).toContain('<li>现场实施</li>')
      expect(res).toContain('</ol>')
    })
  })

  describe('preprocessPlainText', () => {
    it('处理多段纯文本，保留空行分隔且转换为列表', () => {
      const text = '第一段说明\n\n1. 条目一\n2. 条目二\n\n第三段总结'
      const res = preprocessPlainText(text)
      expect(res).toContain('第一段说明')
      expect(res).toContain('<ol>')
      expect(res).toContain('第三段总结')
    })

    it('空文本安全返回空字符串', () => {
      expect(preprocessPlainText('')).toBe('')
      expect(preprocessPlainText(null)).toBe('')
    })
  })

  describe('extractSourceLocationText', () => {
    it('优先返回 sourceSectionPath 或 sourceSectionTitle', () => {
      expect(extractSourceLocationText({ sourceSectionPath: '第一章 > 第一节', sourceSectionTitle: '第一节' })).toBe('第一章 > 第一节')
      expect(extractSourceLocationText({ sourceSectionTitle: '第一节' })).toBe('第一节')
    })

    it('从 responseText 开头识别列表或章节标题并保留原始章节序号', () => {
      const caseData = { responseText: '* 1. 质量管理方案\n 1. 商品安全溯源\n为全面保障质量...' }
      expect(extractSourceLocationText(caseData)).toBe('1. 质量管理方案 > 1. 商品安全溯源')
    })

    it('从 responseText 提取 详见...方案 引用', () => {
      const caseData = { responseText: '本公司承诺按期交货，详见七、服务方案-（二）、供货方案' }
      expect(extractSourceLocationText(caseData)).toBe('七、服务方案 > （二） > 供货方案')
    })

    it('从 responseText 提取带括号序号的详见业绩引用并保留括号序号', () => {
      const caseData = { responseText: '我司提供满足上述业绩要求共11份业绩，**业绩证明材料，详见，（四） 附件四 2023 年以来类似项目业绩**' }
      expect(extractSourceLocationText(caseData)).toBe('（四） 附件四 2023 年以来类似项目业绩')
    })

    it('从 responseText 提取 《...方案/预案》 书名号', () => {
      const caseData = { responseText: '已编制《突发事件防汛防灾及双活容灾应急预案》并报批。' }
      expect(extractSourceLocationText(caseData)).toBe('突发事件防汛防灾及双活容灾应急预案')
    })

    it('兜底返回 scoringPointTitle 或 scoringTitle', () => {
      expect(extractSourceLocationText({ scoringPointTitle: '售后服务承诺', responseText: '普通文本' })).toBe('售后服务承诺')
      expect(extractSourceLocationText({ scoringTitle: '备件供应保障', responseText: '普通文本' })).toBe('备件供应保障')
    })

    it('没有任何匹配时返回空字符串', () => {
      expect(extractSourceLocationText({})).toBe('')
      expect(extractSourceLocationText(null)).toBe('')
    })
  })

  describe('extractSourceDocumentText', () => {
    it('优先返回 sourceDocumentName', () => {
      expect(extractSourceDocumentText({ sourceDocumentName: '投标文件.pdf' })).toBe('投标文件.pdf')
    })

    it('从 obsBidFileKey 提取文件名兜底', () => {
      expect(extractSourceDocumentText({ obsBidFileKey: 'bids/2026/09/商务及技术标书_v2.docx' })).toBe('商务及技术标书_v2.docx')
    })

    it('均为空时返回空字符串', () => {
      expect(extractSourceDocumentText({})).toBe('')
      expect(extractSourceDocumentText(null)).toBe('')
    })
  })
})
