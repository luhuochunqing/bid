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

// 打平表格/编号列表的行边界检测：候选行号 = 1~3 位数字且后紧跟中文（4 位年份天然排除），
// 并排除四类伪行号：前面是数字 / : -（"2023/1/5内网"、"12内"）、前面是左括号（"（2周）"）、
// 前面是「第」（"第1章…第2章…"、"第1节…第2节…" 是章节引用而非表格行号——阈值降到 2 行后
// 这类引用会凑成递增序列，故在候选阶段就排除）、后面是量词（"共3个月"、"188个"）。
// 仅当候选行号构成 ≥2 个连续递增序列（1,2 / 2,3 …）才认定是打平的行结构。
// **不对整行长度设先验门槛**：短的两行表格同样是真实表格，是否切分只由结构证据（候选行号 +
// 递增序列 + 每行行内容长度）决定，不用"整行不够长"提前拒绝。
const FLATTENED_ROW_NUM_RE = /(?<![\d/:\-（(【第[])(\d{1,3})(?![周天月年日个次人份项套台件页条家种元万亿])(?=[一-龥])/g
const FLATTENED_SPLIT_MIN_RUN = 2
// 结构强度判据（判定行结构的唯一依据，不配合任何长度门槛）：每个行号后面（到下一个行号之前，
// 末行则到行尾）至少要跟着 FLATTENED_ROW_MIN_SEGMENT 个字符的行内容。"第1章第2节"、"1号楼2单元"
// 这类数字相邻但中间没有行内容的串不是行结构。末行同样要求有行内容——表格/列表的最后一行也有
// 内容，否则只是句子里两个相邻数字。
const FLATTENED_ROW_MIN_SEGMENT = 4

export const splitFlattenedNumberedRows = (text) => {
  if (!text) return text
  return text.split('\n').map((line) => {
    const candidates = []
    FLATTENED_ROW_NUM_RE.lastIndex = 0
    let match
    while ((match = FLATTENED_ROW_NUM_RE.exec(line)) !== null) {
      candidates.push({ num: Number(match[1]), start: match.index })
    }
    let bestRun = []
    let currentRun = []
    for (let i = 0; i < candidates.length; i++) {
      if (i > 0 && candidates[i].num === candidates[i - 1].num + 1) {
        currentRun.push(candidates[i])
      } else {
        currentRun = [candidates[i]]
      }
      if (currentRun.length > bestRun.length) bestRun = currentRun
    }
    if (bestRun.length < FLATTENED_SPLIT_MIN_RUN) return line
    const rowsHaveContent = bestRun.every((row, i) => {
      const rowEnd = i + 1 < bestRun.length ? bestRun[i + 1].start : line.length
      return rowEnd - row.start >= FLATTENED_ROW_MIN_SEGMENT
    })
    if (!rowsHaveContent) return line
    let out = line
    for (let i = bestRun.length - 1; i >= 0; i--) {
      out = out.slice(0, bestRun[i].start) + '\n' + out.slice(bestRun[i].start)
    }
    return out
  }).join('\n')
}

// 句子型伪标题识别：与后端 MarkdownTitleRules.isSentenceLikeTitle 保持同规则。
// PDF→markdown 转换器会把加粗正文句输出为标题，存量 sourceSectionTitle 里已写入这类伪标题。
export const isSentenceLikeTitle = (value) => {
  if (value === null || value === undefined) return true
  const t = String(value).trim()
  if (!t) return true
  if (/[。！？!?；;]/.test(t)) return true
  if (t.length > 80) return true
  if (/[，,]/.test(t) && t.length > 30) return true
  return false
}

// 章节号前缀：与后端 MarkdownTitleRules.isChapterTitle 同一契约，由共享夹具
// backend/src/test/resources/casework/chapter-title-contract-cases.json 双向锚定。
const CHAPTER_NUMBER_RE = /^(?:第[一二三四五六七八九十百千万0-9]+[章节篇卷部分条]|\d+(?:\.\d+)+[、.．]?|\d+[、.．]|[一二三四五六七八九十百]+[、.．]|[（(](?:\d+|[一二三四五六七八九十百]+)[）)]|[①-⑩])/

// 编号两侧的空白与分隔符：必须与后端 EDGE_SEPARATOR_PATTERN / LEADING_SEPARATOR_PATTERN 逐字一致。
// Java 的 \s 与 JS 的 \s Unicode 范围不同，故全角空格（U+3000）与不换行空格（U+00A0）两侧都显式列出，
// 由夹具中的 U+3000 边界用例锚定，避免"后端拒绝、前端展示"式的判据漂移。
const TITLE_SEPARATOR_CLASS = '[\\s　 、.．,，:：]'
// g 标志让首尾两段分别被剥掉，与后端 replaceAll 的全量扫描语义一致（replace 会把 lastIndex 归零，无状态残留）
const TITLE_EDGE_SEPARATOR_RE = new RegExp(`^${TITLE_SEPARATOR_CLASS}+|${TITLE_SEPARATOR_CLASS}+$`, 'g')
const TITLE_TEXT_SEPARATOR_RE = new RegExp(`^${TITLE_SEPARATOR_CLASS}+`)

// 已验证章节：只有「章节号 + 标题文字」形式（且不是正文句）的标题才算标书真实章节。
// 无编号的小标题（如"项目实施"，PDF 转换器产出的加粗排版层）无序号可核对；
// 只有编号没有文字的串（如 "1."、"第一章"、"（1）"、"①"）指不到具体内容——两者都不足以自证是原文章节。
// 字母列表标记（"A. 附录清单"、"B、服务方案"）同样不算：字母枚举与正文里的字母列表从字面上无从区分。
export const isVerifiedChapterTitle = (value) => {
  if (isSentenceLikeTitle(value)) return false
  const normalized = String(value).replace(TITLE_EDGE_SEPARATOR_RE, '')
  const matched = normalized.match(CHAPTER_NUMBER_RE)
  if (!matched) return false
  return normalized.slice(matched[0].length).replace(TITLE_TEXT_SEPARATOR_RE, '').length > 0
}

const SEQUENCE_RE = /^(?:(?:\d+(?:\.\d+)+|\d+)[、.．）)\s]\s*|[一二三四五六七八九十]+[、.．]\s*|[①-⑩]\s*|[（(]\d+[）)]\s*|[（(][一二三四五六七八九十]+[)）]\s*|[a-zA-Z][、.．）)]\s*|[◆■●·•]\s*)/

export const splitInlineFormats = (text) => {
  if (!text) return ''
  let s = text.replace(/\r\n/g, '\n')

  // 0. 打平的表格/编号列表：按递增行号序列切分换行（日期、数量数字不构成递增序列，不误切）
  s = splitFlattenedNumberedRows(s)

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

// 来源路径的每一段都要过验证：只有整条路径都是已验证章节才展示完整路径。
// 祖先可能是无编号的排版小标题（后端 MarkdownSourceSectionIndex 的层级树接受它们），
// 此时退回命中章节自身的标题——后端 locate 只把已验证章节写进 sourceSectionTitle，
// 因此该字段内不会出现任何无章节号的文本，展示粒度与拒绝粒度保持一致。
const pathSegments = (path) => String(path).split('>').map((part) => part.trim()).filter(Boolean)

export const extractSourceLocationText = (caseData) => {
  if (!caseData) return ''
  // 只展示经章节索引证明、且满足「章节号 + 标题文字」契约的来源；其余一律留空，
  // 由 UI 显示「暂无来源标题」。评分项标题在「评分项标题」字段独立展示，
  // 正文句（存量伪标题）与 responseText 启发式推断都不得冒充「原文所在标题」。
  const path = caseData.sourceSectionPath?.toString?.().trim?.() || ''
  const segments = path ? pathSegments(path) : []
  if (segments.length > 0 && segments.every(isVerifiedChapterTitle)) return path
  const title = caseData.sourceSectionTitle?.toString?.().trim?.() || ''
  return isVerifiedChapterTitle(title) ? title : ''
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
