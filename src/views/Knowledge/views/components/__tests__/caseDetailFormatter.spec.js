import { describe, expect, it } from 'vitest'
import {
  escapeHtml,
  isMarkdownStructureLine,
  isMarkdownBlock,
  splitInlineFormats,
  splitFlattenedNumberedRows,
  isSentenceLikeTitle,
  isVerifiedChapterTitle,
  isNumberOnlyTitle,
  isValidSectionTitle,
  stripTrailingPageNumber,
  insertBrBetweenNonListLines,
  processPlainTextBlock,
  preprocessPlainText,
  extractSourceLocationText,
  extractSourceDocumentText
} from '../caseDetailFormatter.js'
// 与后端共享的契约夹具：Java 侧由 SentenceLikeTitleContractTest 读同一文件，避免两侧规则漂移
import sentenceLikeTitleCases from '../../../../../../backend/src/test/resources/casework/sentence-like-title-cases.json'
// 与后端共享的章节标题契约夹具：Java 侧由 ChapterTitleContractTest 读同一文件
import chapterTitleCases from '../../../../../../backend/src/test/resources/casework/chapter-title-contract-cases.json'

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

  describe('splitFlattenedNumberedRows（打平表格/编号列表行切分）', () => {
    it('递增行号前插入换行：打平的对接案例表格按行拆开', () => {
      const input = '因投标文件篇幅有限，如下仅展示部分对接案例。序号客户名称所属集团名称平台名称对接时间网址对接方案企业类型1启明信息技术股份有限公司启明集团向阳花商城2023/1/30www.flowersun.comAPI大型企业2河北省公共资源交易中心内部网2023/1/5内网API大型企业3中盐常州化工股份有限公司内部网API大型企业'
      const output = splitFlattenedNumberedRows(input)
      expect(output).toContain('\n1启明信息技术股份有限公司')
      expect(output).toContain('\n2河北省公共资源交易中心')
      expect(output).toContain('\n3中盐常州化工股份有限公司')
    })

    it('行号后紧跟日期斜杠不破坏递增序列识别（2023/1/5内网 不吞行号）', () => {
      const input = '序号单位名称备注说明文字补充1第一公司说明备注2023/1/5内网归档存放2第二公司说明备注2024/3/12内网归档存放3第三公司说明备注2025/6/20内网归档存放完毕'
      const output = splitFlattenedNumberedRows(input)
      expect(output).toContain('\n1第一公司')
      expect(output).toContain('\n2第二公司')
      expect(output).toContain('\n3第三公司')
    })

    it('日期与数量数字不构成递增序列，长句不误切', () => {
      const input = '自2023年1月1日至本次投标截止日部分西域成功对接案例188个以及部分系统对接情况说明文字补齐到足够长以触发行长度阈值检查逻辑分支。'
      expect(splitFlattenedNumberedRows(input)).toBe(input)
    })

    it('两行表格同样还原换行（≥2 个连续行号即认定行结构）', () => {
      const input = '序号客户名称所属集团名称平台名称对接时间网址对接方案企业类型1启明信息技术股份有限公司启明集团向阳花商城2023/1/30www.flowersun.comAPI大型企业2河北省公共资源交易中心内部网2023/1/5内网API大型企业'
      const output = splitFlattenedNumberedRows(input)
      expect(output).toContain('\n1启明信息技术股份有限公司')
      expect(output).toContain('\n2河北省公共资源交易中心内部网')
      expect(output).not.toContain('API大型企业2河北省')
    })

    it('两行编号列表同样还原换行', () => {
      const input = '实施要点如下：1质量管理方案包含全过程质量控制与验收标准2商品安全溯源方案覆盖采购到交付的每个环节。'
      const output = splitFlattenedNumberedRows(input)
      expect(output).toContain('\n1质量管理方案')
      expect(output).toContain('\n2商品安全溯源方案')
    })

    it('短的两行表格同样还原换行（无整行长度先验门槛）', () => {
      // 49 字：结构证据（递增 1/2 + 每行行内容）齐备，不得因"整行不够长"被提前拒绝
      const input = '序号客户名称企业类型1启明信息技术股份有限公司API大型企业2河北建设集团有限责任公司建筑大型企业'
      expect(input.length).toBeLessThan(50)
      const output = splitFlattenedNumberedRows(input)
      expect(output).toContain('\n1启明信息技术股份有限公司')
      expect(output).toContain('\n2河北建设集团有限责任公司')
      expect(output).not.toContain('API大型企业2河北')
    })

    it('无表头的短两行列表同样还原换行', () => {
      const input = '1质量管理方案包含全过程质量控制与验收标准2商品安全溯源方案覆盖采购到交付的每个环节。'
      const output = splitFlattenedNumberedRows(input)
      expect(output).toContain('\n1质量管理方案')
      expect(output).toContain('\n2商品安全溯源方案')
    })

    it('末行也必须有行内容（行尾只剩行号加一个字不算行结构）', () => {
      const input = '序号客户名称说明文字补充1启明信息技术股份有限公司2甲'
      expect(splitFlattenedNumberedRows(input)).toBe(input)
    })

    it('数字相邻但中间没有行内容的不算行结构（第1章第2节 不误切）', () => {
      const input = '根据招标文件第三章的说明，本项目详见第1章第2节的相关要求，投标人须逐条响应并作出承诺，否则视为无效投标文件。'
      expect(splitFlattenedNumberedRows(input)).toBe(input)
    })

    it('「第N章/节」正文引用不构成行结构（第 引导的数字不是候选行号）', () => {
      // 阈值降到 2 行后，『第1章…第2章…』这类引用会凑成递增序列，必须在候选阶段就排除
      const twoRefs = '请参照第1章内容说明并结合第2章内容要求完成投标文件编制，同时按照项目实际情况补充相关实施方案和质量保障措施。'
      expect(splitFlattenedNumberedRows(twoRefs)).toBe(twoRefs)
      const threeRefs = '见第1章总则与第2章技术要求以及第3章商务条款的规定，投标人须逐条响应并按要求提供完整的技术方案与报价明细清单'
      expect(splitFlattenedNumberedRows(threeRefs)).toBe(threeRefs)
    })

    it('量词数字（1个月/2个月）不构成行结构，不误切', () => {
      const input = '我方承诺在1个月内完成部署，随后进入2个月的试运行期，期间每周提交进展报告并接受甲方监督检查，同时配合完成阶段性验收与材料归档。'
      expect(splitFlattenedNumberedRows(input)).toBe(input)
    })

    it('短行不处理', () => {
      expect(splitFlattenedNumberedRows('任务一完成任务二完成')).toBe('任务一完成任务二完成')
    })

    it('括号内周期与量词数字不误切（（2周）（3周）（4周） 保持原文）', () => {
      const input = '我司将按照以下阶段实施：1.需求调研（2周）2.方案设计（3周）3.系统部署（4周）4.测试验收（2周）5.培训上线（1周），总计12周完成。'
      expect(splitFlattenedNumberedRows(input)).toBe(input)
    })

    it('空值安全：原样返回输入', () => {
      expect(splitFlattenedNumberedRows('')).toBe('')
      expect(splitFlattenedNumberedRows(null)).toBe(null)
    })
  })

  describe('isSentenceLikeTitle（句子型伪标题识别）', () => {
    it('含句读的正文句识别为伪标题', () => {
      expect(isSentenceLikeTitle('因投标文件篇幅有限，如下仅展示自 2023 年 1 月 1 日至本次投标截止日部分西域成功对接案例188个以及部分系统对接。')).toBe(true)
      expect(isSentenceLikeTitle('双方需严格按期交付！')).toBe(true)
    })

    it('超长标题（>80 字）识别为伪标题', () => {
      expect(isSentenceLikeTitle('本项目实施团队将严格按照招标文件要求的工期节点组织施工并在各个阶段安排专职人员对接客户需求确保交付质量与进度可控且全过程接受甲方监督检查并定期提交阶段性成果报告以便于双方及时核对实施效果与偏差情况')).toBe(true)
    })

    it('真实章节标题不误判', () => {
      expect(isSentenceLikeTitle('13.2.1. 对接成功案例清单')).toBe(false)
      expect(isSentenceLikeTitle('第一章 项目背景')).toBe(false)
      expect(isSentenceLikeTitle('1.2 建设目标，思路与路径')).toBe(false)
    })

    it('空值识别为伪标题（不可用作标题）', () => {
      expect(isSentenceLikeTitle(null)).toBe(true)
      expect(isSentenceLikeTitle('')).toBe(true)
      expect(isSentenceLikeTitle('   ')).toBe(true)
    })

    it('与后端共享契约夹具逐例同判（含 80/81、30/31 边界）', () => {
      const { cases } = sentenceLikeTitleCases
      expect(cases.length).toBeGreaterThanOrEqual(12)

      const mismatches = cases
        .filter(({ title, sentenceLike }) => isSentenceLikeTitle(title) !== sentenceLike)
        .map(({ title, sentenceLike }) => `${JSON.stringify(title)} 期望=${sentenceLike}`)
      expect(mismatches).toEqual([])

      // 夹具必须同时覆盖真/假两类，避免规则失效时空跑通过
      expect(cases.some(({ sentenceLike }) => sentenceLike === true)).toBe(true)
      expect(cases.some(({ sentenceLike }) => sentenceLike === false)).toBe(true)
    })
  })

  describe('isVerifiedChapterTitle（章节号 + 标题文字 契约）', () => {
    it('编号在串首的真实章节放行', () => {
      expect(isVerifiedChapterTitle('13.2.1. 对接成功案例清单')).toBe(true)
      expect(isVerifiedChapterTitle('第一章 项目背景')).toBe(true)
      expect(isVerifiedChapterTitle('一、总体要求')).toBe(true)
      expect(isVerifiedChapterTitle('（1）项目概述')).toBe(true)
    })

    it('无编号小标题不是章节（PDF 转换器产出的加粗排版层）', () => {
      expect(isVerifiedChapterTitle('项目实施')).toBe(false)
      expect(isVerifiedChapterTitle('应答片段摘要')).toBe(false)
    })

    it('只有编号没有标题文字不算章节（「章节号 + 标题文字」缺后半段）', () => {
      expect(isVerifiedChapterTitle('1.')).toBe(false)
      expect(isVerifiedChapterTitle('3、')).toBe(false)
      expect(isVerifiedChapterTitle('13.2.1.')).toBe(false)
      expect(isVerifiedChapterTitle('第一章')).toBe(false)
      expect(isVerifiedChapterTitle('第一节')).toBe(false)
      expect(isVerifiedChapterTitle('一、')).toBe(false)
      expect(isVerifiedChapterTitle('（1）')).toBe(false)
      expect(isVerifiedChapterTitle('①')).toBe(false)
      expect(isVerifiedChapterTitle('A.')).toBe(false)
      // 全角空格（U+3000）：JS 的 trim/\s 覆盖它，后端不覆盖——两侧必须都显式处理，否则判据分叉
      expect(isVerifiedChapterTitle('第一章　')).toBe(false)
      expect(isVerifiedChapterTitle('　第一章 项目背景')).toBe(true)
    })

    it('字母列表标记不是章节号（A. 附录清单 / B、服务方案 / a. 投标函）', () => {
      // 字母枚举与正文里的字母列表从字面上无从区分，不足以自证是数值章节序号
      expect(isVerifiedChapterTitle('A. 附录清单')).toBe(false)
      expect(isVerifiedChapterTitle('B、服务方案')).toBe(false)
      expect(isVerifiedChapterTitle('a. 投标函')).toBe(false)
    })

    it('编号不在串首不算章节；句子门优先于编号门', () => {
      expect(isVerifiedChapterTitle('技术方案 1.2 先进性')).toBe(false)
      expect(isVerifiedChapterTitle('13.2 建设计划，分三期推进；同时包含后续说明')).toBe(false)
      expect(isVerifiedChapterTitle('因投标文件篇幅有限，如下仅展示部分对接案例。')).toBe(false)
    })

    it('空值不是章节', () => {
      expect(isVerifiedChapterTitle(null)).toBe(false)
      expect(isVerifiedChapterTitle('')).toBe(false)
      expect(isVerifiedChapterTitle('   ')).toBe(false)
    })

    it('与后端共享契约夹具逐例同判', () => {
      const { cases } = chapterTitleCases
      expect(cases.length).toBeGreaterThanOrEqual(15)

      const mismatches = cases
        .filter(({ title, chapter }) => isVerifiedChapterTitle(title) !== chapter)
        .map(({ title, chapter }) => `${JSON.stringify(title)} 期望=${chapter}`)
      expect(mismatches).toEqual([])

      // 夹具必须同时覆盖正反例与关键反例（无编号小标题），避免规则失效时空跑通过
      expect(cases.some(({ chapter }) => chapter === true)).toBe(true)
      const rejected = cases.filter(({ chapter }) => chapter === false).map(({ title }) => title)
      expect(rejected).toContain('项目实施')
      // 「章节号 + 标题文字」中的"标题文字"一侧与全角空格边界，夹具丢掉任一条两侧都会重新放行
      expect(rejected).toEqual(expect.arrayContaining(['1.', '第一章', '（1）', '①', 'A.', '第一章　']))
      // 字母列表标记反例：任一侧重新接纳字母枚举，这里就会失败
      expect(rejected).toEqual(expect.arrayContaining(['A. 附录清单', 'B、服务方案', 'a. 投标函']))
      expect(cases.filter(({ chapter }) => chapter === true).map(({ title }) => title)).toContain('　第一章 项目背景')
    })
  })

  describe('isNumberOnlyTitle & isValidSectionTitle（合法章节小标题与纯编号识别）', () => {
    it('纯编号伪标题被正确识别', () => {
      expect(isNumberOnlyTitle('1.')).toBe(true)
      expect(isNumberOnlyTitle('3、')).toBe(true)
      expect(isNumberOnlyTitle('13.2.1.')).toBe(true)
      expect(isNumberOnlyTitle('第一章')).toBe(true)
      expect(isNumberOnlyTitle('第一节')).toBe(true)
      expect(isNumberOnlyTitle('一、')).toBe(true)
      expect(isNumberOnlyTitle('（1）')).toBe(true)
      expect(isNumberOnlyTitle('①')).toBe(true)
      expect(isNumberOnlyTitle('A.')).toBe(true)
      expect(isNumberOnlyTitle('第一章　')).toBe(true)
      expect(isNumberOnlyTitle('技术团队人员安排')).toBe(false)
      expect(isNumberOnlyTitle('第一章 项目背景')).toBe(false)
      expect(isNumberOnlyTitle('A. 附录清单')).toBe(false)
    })

    it('合法小标题（含无编号小标题与字母列表标题）放行', () => {
      expect(isValidSectionTitle('技术团队人员安排')).toBe(true)
      expect(isValidSectionTitle('项目实施')).toBe(true)
      expect(isValidSectionTitle('第一章 项目背景')).toBe(true)
      expect(isValidSectionTitle('13.2.1. 对接成功案例清单')).toBe(true)
      expect(isValidSectionTitle('A. 附录清单')).toBe(true)
      expect(isValidSectionTitle('六、 技术方案')).toBe(true)
      expect(isValidSectionTitle('3. 技术团队配置')).toBe(true)
    })

    it('正文句子伪标题和纯编号被拒绝', () => {
      expect(isValidSectionTitle('因投标文件篇幅有限，如下仅展示自 2023 年 1 月 1 日至本次投标截止日部分西域成功对接案例188个以及部分系统对接。')).toBe(false)
      expect(isValidSectionTitle('双方需严格按期交付！')).toBe(false)
      expect(isValidSectionTitle('1.')).toBe(false)
      expect(isValidSectionTitle('第一章')).toBe(false)
      expect(isValidSectionTitle('（1）')).toBe(false)
      expect(isValidSectionTitle('A.')).toBe(false)
      expect(isValidSectionTitle(null)).toBe(false)
      expect(isValidSectionTitle('')).toBe(false)
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

    it('打平的表格文本按行渲染（每行独立成行，不再塌成一大段）', () => {
      const text = '因投标文件篇幅有限，如下仅展示部分对接案例。序号客户名称企业类型1启明信息技术股份有限公司API大型企业2河北省公共资源交易中心内网API大型企业3中盐常州化工股份有限公司内网API大型企业'
      const res = preprocessPlainText(text)
      expect(res).toContain('<br>1启明信息技术股份有限公司')
      expect(res).toContain('<br>2河北省公共资源交易中心')
      expect(res).toContain('<br>3中盐常州化工股份有限公司')
      expect(res).not.toContain('API大型企业2河北省')
    })

    it('两行打平表格渲染后行间确有 <br>（最小合法表格）', () => {
      const text = '序号客户名称所属集团名称平台名称对接时间网址对接方案企业类型1启明信息技术股份有限公司启明集团向阳花商城2023/1/30www.flowersun.comAPI大型企业2河北省公共资源交易中心内部网2023/1/5内网API大型企业'
      const res = preprocessPlainText(text)
      expect(res).toContain('<br>1启明信息技术股份有限公司')
      expect(res).toContain('<br>2河北省公共资源交易中心内部网')
      expect(res).not.toContain('API大型企业2河北省')
    })

    it('空文本安全返回空字符串', () => {
      expect(preprocessPlainText('')).toBe('')
      expect(preprocessPlainText(null)).toBe('')
    })
  })

  describe('extractSourceLocationText（只展示已验证章节）', () => {
    it('整条路径都是合法章节标题时展示完整路径，否则退回命中章节自身的标题', () => {
      expect(extractSourceLocationText({ sourceSectionPath: '第一章 项目背景 > 第一节 服务范围', sourceSectionTitle: '第一节 服务范围' })).toBe('第一章 项目背景 > 第一节 服务范围')
      expect(extractSourceLocationText({ sourceSectionTitle: '第二节 服务范围' })).toBe('第二节 服务范围')
    })

    it('路径含纯编号祖先时退回章节自身标题，不把纯编号展示出去', () => {
      // 祖先只是"只有编号没有标题文字"的层级节点时退回章节自身标题
      expect(extractSourceLocationText({
        sourceSectionPath: '第一章 > 13.2.1. 对接成功案例清单',
        sourceSectionTitle: '13.2.1. 对接成功案例清单'
      })).toBe('13.2.1. 对接成功案例清单')
    })

    it('只有编号没有标题文字的标题不给来源，返回空串由 UI 显示「暂无来源标题」', () => {
      expect(extractSourceLocationText({ sourceSectionTitle: '第一章', responseText: '表格正文' })).toBe('')
      expect(extractSourceLocationText({ sourceSectionTitle: '1.' })).toBe('')
      expect(extractSourceLocationText({
        sourceSectionPath: '第一章 > （1）',
        sourceSectionTitle: '（1）'
      })).toBe('')
    })

    it('字母列表标记标题作为合法章节小标题展示来源', () => {
      expect(extractSourceLocationText({ sourceSectionTitle: 'A. 附录清单' })).toBe('A. 附录清单')
      expect(extractSourceLocationText({ sourceSectionPath: 'A. 附录清单 > B、服务方案' })).toBe('A. 附录清单 > B、服务方案')
    })

    it('无编号标题（加粗小标题如技术团队人员安排/项目实施）正常展示来源', () => {
      expect(extractSourceLocationText({ sourceSectionTitle: '技术团队人员安排', responseText: '表格正文' })).toBe('技术团队人员安排')
      expect(extractSourceLocationText({ sourceSectionTitle: '项目实施', responseText: '表格正文' })).toBe('项目实施')
      expect(extractSourceLocationText({
        sourceSectionPath: '六、 技术方案 > 3. 技术团队配置 > 技术团队人员安排',
        sourceSectionTitle: '技术团队人员安排',
        responseText: '表格正文'
      })).toBe('六、 技术方案 > 3. 技术团队配置 > 技术团队人员安排')
      expect(extractSourceLocationText({
        sourceSectionPath: '13.2. 对接平台对接成功案例及证明材料 > 项目实施',
        sourceSectionTitle: '项目实施',
        responseText: '表格正文'
      })).toBe('13.2. 对接平台对接成功案例及证明材料 > 项目实施')
    })

    it('存量伪标题（正文句子）被拒绝，不再回退评分项标题或正文推断', () => {
      const caseData = {
        sourceSectionTitle: '因投标文件篇幅有限，如下仅展示自 2023 年 1 月 1 日至本次投标截止日部分西域成功对接案例188个以及部分系统对接。',
        sourceSectionPath: null,
        responseText: '序号客户名称所属集团名称平台名称对接时间网址对接方案企业类型1启明信息技术股份有限公司',
        scoringPointTitle: '对接成功案例'
      }
      expect(extractSourceLocationText(caseData)).toBe('')
    })

    it('不再从 responseText 启发式推断来源（章节列表/详见/书名号一律不给）', () => {
      expect(extractSourceLocationText({
        responseText: '* 1. 质量管理方案\n     1. 商品安全溯源\n\n为全面保障质量安全...'
      })).toBe('')
      expect(extractSourceLocationText({
        responseText: '本公司承诺按期交货，详见七、服务方案-（二）、供货方案'
      })).toBe('')
      expect(extractSourceLocationText({
        responseText: '已编制《突发事件防汛防灾及双活容灾应急预案》并报批。'
      })).toBe('')
    })

    it('评分项标题不再兜底为「原文所在标题」（它在独立字段展示）', () => {
      expect(extractSourceLocationText({ scoringPointTitle: '售后服务承诺', responseText: '普通文本' })).toBe('')
      expect(extractSourceLocationText({ scoringTitle: '备件供应保障', responseText: '普通文本' })).toBe('')
    })

    it('存量伪路径（含正文句子）被拒绝，正常标题仍可用', () => {
      const caseData = {
        sourceSectionTitle: '13.2.1. 对接成功案例清单',
        sourceSectionPath: '因投标文件篇幅有限，如下仅展示自 2023 年 1 月 1 日至本次投标截止日部分西域成功对接案例。',
        responseText: '正文内容',
        scoringPointTitle: '对接成功案例'
      }
      expect(extractSourceLocationText(caseData)).toBe('13.2.1. 对接成功案例清单')
    })

    it('回填后的真实章节路径正常返回（序号+标题文字）', () => {
      const caseData = {
        sourceSectionTitle: '13.2.1. 对接成功案例清单',
        sourceSectionPath: '13.2. 对接平台对接成功案例及证明材料 > 13.2.1. 对接成功案例清单',
        responseText: '表格正文',
        scoringPointTitle: '对接成功案例'
      }
      expect(extractSourceLocationText(caseData)).toBe('13.2. 对接平台对接成功案例及证明材料 > 13.2.1. 对接成功案例清单')
    })

    it('没有任何可验证来源时返回空字符串', () => {
      expect(extractSourceLocationText({})).toBe('')
      expect(extractSourceLocationText(null)).toBe('')
    })

    it('案例 1254：来源路径中内嵌页码和制表符被有效清洗，展示干净的章节路径', () => {
      const caseData = {
        sourceSectionTitle: '7. 响应性评审标准承诺函\t2003',
        sourceSectionPath: '一、 投标函\t1 > 九、 本项目相关其他内容\t1806 > （一） 近年完成的项目情况表\t1806 > 1. 中国海洋石油集团有限公司\t1808 > 2. 国家电网有限公司\t1820 > 7. 响应性评审标准承诺函\t2003',
        responseText: '表格正文',
        scoringPointTitle: '投标报价'
      }
      expect(extractSourceLocationText(caseData)).toBe(
        '一、 投标函 > 九、 本项目相关其他内容 > （一） 近年完成的项目情况表 > 1. 中国海洋石油集团有限公司 > 2. 国家电网有限公司 > 7. 响应性评审标准承诺函'
      )
    })

    it('案例 1261：售后服务方案回填后正常展示来源路径与小标题', () => {
      const caseData = {
        sourceSectionTitle: '1. 售前咨询',
        sourceSectionPath: '七、 服务方案 > （五） 售后服务方案 > 1. 售前咨询',
        responseText: '（五） 售后服务方案\t1705\n\n---\n\n1. 售前咨询\t1705',
        scoringPointTitle: '售后服务方 案'
      }
      expect(extractSourceLocationText(caseData)).toBe(
        '七、 服务方案 > （五） 售后服务方案 > 1. 售前咨询'
      )
    })
  })

  describe('stripTrailingPageNumber', () => {
    it('清除包含制表符或多空格的末尾页码', () => {
      expect(stripTrailingPageNumber('一、 投标函\t1')).toBe('一、 投标函')
      expect(stripTrailingPageNumber('一、 投标函 1')).toBe('一、 投标函')
      expect(stripTrailingPageNumber('九、 本项目相关其他内容\t1806')).toBe('九、 本项目相关其他内容')
      expect(stripTrailingPageNumber('九、 本项目相关其他内容 1806')).toBe('九、 本项目相关其他内容')
      expect(stripTrailingPageNumber('7. 响应性评审标准承诺函\t2003')).toBe('7. 响应性评审标准承诺函')
      expect(stripTrailingPageNumber('7. 响应性评审标准承诺函 2003')).toBe('7. 响应性评审标准承诺函')
      expect(stripTrailingPageNumber('技术团队人员安排......12')).toBe('技术团队人员安排')
      expect(stripTrailingPageNumber('七、 服务方案······134')).toBe('七、 服务方案')
    })

    it('保护合法实体编号标题不被误裁', () => {
      expect(stripTrailingPageNumber('附录 1')).toBe('附录 1')
      expect(stripTrailingPageNumber('附件 2')).toBe('附件 2')
      expect(stripTrailingPageNumber('表 3')).toBe('表 3')
      expect(stripTrailingPageNumber('图 4')).toBe('图 4')
      expect(stripTrailingPageNumber('标段 1')).toBe('标段 1')
      expect(stripTrailingPageNumber('分包 2')).toBe('分包 2')
      expect(stripTrailingPageNumber('阶段 1')).toBe('阶段 1')
      expect(stripTrailingPageNumber('包 1')).toBe('包 1')
      expect(stripTrailingPageNumber('技术团队人员安排')).toBe('技术团队人员安排')
      expect(stripTrailingPageNumber('13.2.1. 对接成功案例清单')).toBe('13.2.1. 对接成功案例清单')
    })

    it('空值与边界处理', () => {
      expect(stripTrailingPageNumber(null)).toBe('')
      expect(stripTrailingPageNumber(undefined)).toBe('')
      expect(stripTrailingPageNumber('')).toBe('')
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
