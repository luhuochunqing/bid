// Input: 案例原始数据、纯文本片段与 Markdown 片段
// Output: 格式化后的安全排版 HTML 与来源标题/文档提取结果
// Pos: src/views/Knowledge/views/components/ - 案例详情排版与来源兜底纯函数
// 维护声明: 一旦我被更新，务必同步更新同目录 __tests__/ 下的单测与所属文件夹文档。

export const escapeHtml = (str) => String(str)
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#039;')

export const isMarkdownStructureLine = (line) => {
  const trimmed = line.trimStart()
  return trimmed.startsWith('#') || trimmed.startsWith('>') || trimmed.startsWith('```') ||
    /^[-*+]\s/.test(trimmed) || (trimmed.match(/\|/g) || []).length >= 2
}

export const isMarkdownBlock = (block) => block.split('\n').some(isMarkdownStructureLine)

export const SEQUENCE_RE = /^(?:(?:\d+(?:\.\d+)+|\d+)[、.．）)\s]\s*|[一二三四五六七八九十]+[、.．]\s*|[①-⑩]\s*|[（(]\d+[）)]\s*|[（(][一二三四五六七八九十]+[)）]\s*|[a-zA-Z][、.．）)]\s*|[◆■●·•]\s*)/

export const splitInlineFormats = (text) => {
  if (!text) return ''
  let s = text.replace(/\r\n/g, '\n')

  // 1. 修复单行或无分隔行表头的 Markdown 表格（例如 | **投标报价** | **宣传品报价** | ... |）
  const rawLines = s.split('\n')
  const fixedLines = []
  for (let i = 0; i < rawLines.length; i++) {
    const line = rawLines[i]
    const trimmed = line.trim()
    if (trimmed.startsWith('|') && trimmed.endsWith('|') && (trimmed.match(/\|/g) || []).length >= 3) {
      const nextLine = (rawLines[i + 1] || '').trim()
      const isSep = /^\|?\s*[-:]+[-| :]*\|?\s*$/.test(nextLine)
      fixedLines.push(line)
      if (!isSep) {
        const colCount = (trimmed.match(/\|/g) || []).length - 1
        fixedLines.push('| ' + Array(colCount).fill('---').join(' | ') + ' |')
      }
    } else {
      fixedLines.push(line)
    }
  }
  s = fixedLines.join('\n')

  // 2. 分号后带多个空格或带子项的，切分为换行
  s = s.replace(/([；;])(\s{2,}|\s*(?=[^\s；;]+[：:]))/g, '$1\n')

  // 3. 行内序号（数字、多级数字如 6.2.4、中文、括号、字母、项目符号）前自动切分换行转为独立列表项
  const seqSplitPattern = /(?<!\n)([。；！？!?：:]|[)）]|\s{2,}|(?<=[\u4e00-\u9fa5]))(?=(?:[一二三四五六七八九十百]+[、.．]|第[一二三四五六七八九十]+[章节部分篇]|[（(][一二三四五六七八九十]+[)）]|\d+(?:\.\d+)+[、.．）)\s]|\d+[、.．）)]\s*|[（(]\d+[）)]\s*|[①-⑩]\s*|[a-zA-Z][、.．）)]\s*|[◆■●·•]\s*))/g
  s = s.replace(seqSplitPattern, '$1\n')

  // 4. 连续多句长文本（无序列表外）：句号/叹号/问号后若接完整句子（4 字以上），且未换行，插入单换行
  s = s.replace(/([。！？!?])(?!\n)(?=[\u4e00-\u9fa5]{4,})/g, '$1\n')

  return s
}

export const insertBrBetweenNonListLines = (lines) => {
  const tagPattern = /^<\/?ol>|^<li>/
  const output = []

  for (const line of lines) {
    if (line === '') {
      output.push('')
    } else if (tagPattern.test(line)) {
      output.push(line)
    } else if (output.length > 0 && output[output.length - 1] !== '' && !tagPattern.test(output[output.length - 1])) {
      output.push(`<br>${line}`)
    } else {
      output.push(line)
    }
  }

  return output.join('\n')
}

export const processPlainTextBlock = (block) => {
  const lines = block.split('\n')
  const output = []
  let inList = false

  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed) {
      if (inList) {
        output.push('</ol>')
        inList = false
      }
      output.push('')
      continue
    }
    const sequence = trimmed.match(SEQUENCE_RE)

    if (sequence) {
      if (!inList) {
        output.push('<ol>')
        inList = true
      }
      output.push(`<li>${escapeHtml(trimmed.slice(sequence[0].length))}</li>`)
      continue
    }

    if (inList) {
      output.push('</ol>')
      inList = false
    }
    output.push(escapeHtml(line))
  }

  if (inList) output.push('</ol>')
  return insertBrBetweenNonListLines(output)
}

export const preprocessPlainText = (text) => {
  if (!text) return ''
  const formatted = splitInlineFormats(text)
  return formatted.split(/\n{2,}/).map((block) => {
    if (block.trim() === '') return ''
    return isMarkdownBlock(block) ? block : processPlainTextBlock(block)
  }).join('\n\n')
}

export const extractSourceLocationText = (caseData) => {
  if (!caseData) return ''
  const path = caseData.sourceSectionPath?.trim?.()
  const title = caseData.sourceSectionTitle?.trim?.()
  if (path || title) return path || title

  const raw = typeof caseData.responseText === 'string' ? caseData.responseText : ''

  if (raw) {
    // 1. 开头的章节/列表标题（必须带有标题标记，如 "# 标题"、"* 1. 质量管理方案\n 1. 商品安全溯源"）
    // 仅剥离 markdown 标记符号（#、*、+、-），保留原始序号（如 1.、一、）
    const headingListMatch = raw.match(/^\s*(?:#{1,6}\s+|[*+-]\s+)([^\n\r]+)(?:\n\s*(?:#{1,6}\s+|[*+-]\s+)?([^\n\r]+))?/)
    if (headingListMatch) {
      const l1 = headingListMatch[1]?.replace(/^[*_`#\s]+/, '')?.replace(/[*_`#\s]+$/, '')?.trim()
      const l2 = headingListMatch[2]?.replace(/^[*_`#\s]+/, '')?.replace(/[*_`#\s]+$/, '')?.trim()
      if (l1 && l1.length >= 2 && l1.length <= 40 && !l1.includes('。') && !l1.includes('，') && !l1.includes('|')) {
        if (l2 && l2.length >= 2 && l2.length <= 40 && !l2.includes('。') && !l2.includes('，') && !l2.includes('|') && !l2.startsWith('为') && !l2.startsWith('本')) {
          return `${l1} > ${l2}`
        }
        return l1
      }
    }

    // 2. 包含 "详见...方案"
    const seeMatch = raw.match(/详见[，,]?\s*([一二三四五六七八九十0-9a-zA-Z\u4e00-\u9fa5（）()、—\-_\s]+(?:方案|简历|配备|说明|表|章节|条款|内容|部分|计划|业绩))/i)
    if (seeMatch) {
      const cleaned = seeMatch[1]
        .replace(/[-—_]/g, ' > ')
        .replace(/[）)][、，]/g, '） > ')
        .replace(/^[（(](?![一二三四五六七八九十\d]+[）)])/g, '')
        .replace(/\**$/g, '')
        .trim()
      if (cleaned.length >= 2 && cleaned.length <= 50) return cleaned
    }

    // 3. 包含 《...方案/预案/规范/设计/报告/指南》
    const bookMatch = raw.match(/《([^》]{2,40}(?:方案|预案|设计|规范|体系|计划|报告|指南|架构))》/)
    if (bookMatch) {
      return bookMatch[1].trim()
    }
  }

  // 4. 兜底评分项标题或案例标题
  const scoring = caseData.scoringPointTitle?.trim?.() || caseData.scoringTitle?.trim?.()
  if (scoring) return scoring

  return ''
}

export const extractSourceDocumentText = (caseData) => {
  if (!caseData) return ''
  const doc = caseData.sourceDocumentName?.toString?.().trim?.()
  if (doc) return doc
  const obsKey = caseData.obsBidFileKey?.toString?.().trim?.()
  if (obsKey) {
    const parts = obsKey.split('/')
    return parts[parts.length - 1]
  }
  return ''
}
