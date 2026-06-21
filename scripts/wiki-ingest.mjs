// Input: files under .wiki/sources (raw) AND docs/ (curated markdown), plus previous source catalog snapshot
// Output: extracted markdown artifacts and refreshed source catalog with pending changes
// Pos: scripts/ - Wiki source ingestion pipeline for mixed raw-file onboarding
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import {
  catalogRoot,
  decodeXmlEntities,
  docsRoot,
  ensureWikiDirs,
  extractsRoot,
  fileSha256,
  formatOutputName,
  isDocsIgnored,
  isIgnoredFile,
  isoDate,
  readJsonOrDefault,
  relToRepo,
  relToSources,
  runCommand,
  sourceTypeFromPath,
  sourcesRoot,
  stripXml,
  topicFromSourceRel,
  walkFiles,
  writeJson,
} from './wiki-common.mjs'

const sourceCatalogJson = path.join(catalogRoot, 'source-catalog.json')
const pendingJson = path.join(catalogRoot, 'pending-build.json')

function writeExtract(record, content) {
  fs.mkdirSync(path.dirname(record.extractPathAbs), { recursive: true })
  fs.writeFileSync(record.extractPathAbs, content, 'utf8')
}

function extractMarkdown(absPath) {
  return fs.readFileSync(absPath, 'utf8')
}

function extractDocx(absPath) {
  const xml = runCommand('unzip', ['-p', absPath, 'word/document.xml'])
  const text = stripXml(xml)
  return text || ''
}

function extractXlsx(absPath) {
  const entries = runCommand('unzip', ['-Z1', absPath])
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
  const sheets = entries.filter((item) => /^xl\/worksheets\/sheet\d+\.xml$/.test(item))
  const sharedStringsPath = entries.find((item) => item === 'xl/sharedStrings.xml')

  let sharedStrings = []
  if (sharedStringsPath) {
    const raw = runCommand('unzip', ['-p', absPath, sharedStringsPath])
    sharedStrings = Array.from(raw.matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)).map((m) =>
      decodeXmlEntities(m[1]).replace(/\s+/g, ' ').trim()
    )
  }

  const sections = []
  for (const sheetPath of sheets) {
    const xml = runCommand('unzip', ['-p', absPath, sheetPath])
    const lines = []
    const rowMatches = Array.from(xml.matchAll(/<row[^>]*>([\s\S]*?)<\/row>/g))
    for (const rowMatch of rowMatches) {
      const rowXml = rowMatch[1]
      const cells = Array.from(rowXml.matchAll(/<c([^>]*)>([\s\S]*?)<\/c>/g))
      const values = []
      for (const cell of cells) {
        const attrs = cell[1]
        const inner = cell[2]
        const isShared = /t="s"/.test(attrs)
        const vMatch = inner.match(/<v>([\s\S]*?)<\/v>/)
        const rawValue = vMatch ? decodeXmlEntities(vMatch[1]).trim() : ''
        if (!rawValue) {
          continue
        }
        if (isShared) {
          const idx = Number(rawValue)
          values.push(sharedStrings[idx] || '')
        } else {
          values.push(rawValue)
        }
      }
      if (values.length > 0) {
        lines.push(values.join(' | '))
      }
    }

    if (lines.length > 0) {
      sections.push(`## ${path.basename(sheetPath, '.xml')}\n\n${lines.join('\n')}`)
    }
  }

  return sections.join('\n\n').trim()
}

function extractPdf(absPath) {
  try {
    const output = runCommand('pdftotext', [absPath, '-'])
    return output.trim()
  } catch {
    return ''
  }
}

function extractImagePlaceholder(sourceRel) {
  return `# Image Placeholder\n\n- source: ${sourceRel}\n- note: OCR not enabled in v1. Please add manual markdown notes if needed.\n`
}

function buildExtractRecord(absPath) {
  const sourceRel = relToSources(absPath)
  const sourcePath = relToRepo(absPath)
  const fileType = sourceTypeFromPath(sourceRel)
  const topic = topicFromSourceRel(sourceRel)
  const hash = fileSha256(absPath)
  const id = hash.slice(0, 16)
  const extractFileName = `${formatOutputName(sourceRel)}.md`
  const extractPathAbs = path.join(extractsRoot, extractFileName)
  const extractPath = relToRepo(extractPathAbs)

  return {
    id,
    path: sourcePath,
    type: fileType,
    topic,
    hash,
    status: 'pending',
    extract_path: extractPath,
    confidence: 0,
    ingested_at: new Date().toISOString(),
    extractPathAbs,
  }
}

function materializeExtraction(record, sourceAbsPath) {
  try {
    let extracted = ''
    if (record.type === 'markdown' || record.type === 'text') {
      extracted = extractMarkdown(sourceAbsPath)
      record.status = 'extracted'
      record.confidence = 1
    } else if (record.type === 'docx') {
      extracted = extractDocx(sourceAbsPath)
      record.status = extracted ? 'extracted' : 'manual_review'
      record.confidence = extracted ? 0.85 : 0.3
    } else if (record.type === 'xlsx') {
      extracted = extractXlsx(sourceAbsPath)
      record.status = extracted ? 'extracted' : 'manual_review'
      record.confidence = extracted ? 0.75 : 0.3
    } else if (record.type === 'pdf') {
      extracted = extractPdf(sourceAbsPath)
      record.status = extracted ? 'extracted' : 'manual_review'
      record.confidence = extracted ? 0.6 : 0.2
    } else if (record.type === 'image') {
      extracted = extractImagePlaceholder(relToSources(sourceAbsPath))
      record.status = 'manual_review'
      record.confidence = 0.2
    } else {
      extracted = `# Unsupported Source\n\n- source: ${record.path}\n- detected_type: ${record.type}\n- action: please provide manual markdown summary for this source.\n`
      record.status = 'manual_review'
      record.confidence = 0.1
    }

    const header = [
      `# Extract: ${path.basename(record.path)}`,
      '',
      `- source_path: ${record.path}`,
      `- source_type: ${record.type}`,
      `- topic: ${record.topic}`,
      `- generated_at: ${new Date().toISOString()}`,
      `- status: ${record.status}`,
      `- confidence: ${record.confidence}`,
      '',
    ].join('\n')

    writeExtract(record, `${header}${extracted}\n`)
  } catch (error) {
    record.status = 'error'
    record.confidence = 0
    const message = error instanceof Error ? error.message : String(error)
    writeExtract(
      record,
      `# Extract Error\n\n- source_path: ${record.path}\n- error: ${message}\n- action: please add manual markdown summary and rerun ingest.\n`
    )
  } finally {
    delete record.extractPathAbs
  }
}

function toSourceCatalogMarkdown(records) {
  const lines = [
    '# Source Catalog / 源文档编目',
    '',
    `> Updated: ${isoDate()} · Generated by \`npm run wiki:ingest\``,
    '',
    '## Raw Sources (.wiki/sources/)',
    '',
    '原始资料文件（docx/xlsx/pdf/md 等），通过 extract 抽取到 `.wiki/extracts/`。',
    '',
    '| id | path | type | topic | status | confidence | extract_path | ingested_at |',
    '|---|---|---|---|---|---:|---|---|',
  ]

  const rawRecords = records.filter((r) => r.status !== 'docs_tracked')
  const docsRecords = records.filter((r) => r.status === 'docs_tracked')

  for (const record of rawRecords) {
    lines.push(
      `| ${record.id} | ${record.path} | ${record.type} | ${record.topic} | ${record.status} | ${record.confidence} | ${record.extract_path} | ${record.ingested_at.slice(0, 10)} |`
    )
  }

  lines.push('')
  lines.push('## Docs Tracked (docs/)')
  lines.push('')
  lines.push('项目事实源 markdown 文件，不抽取，仅按 hash 追踪变更用于触发 wiki 页面 `updated` 刷新。')
  lines.push('')
  lines.push('| id | path | topic | hash (前8位) | ingested_at |')
  lines.push('|---|---|---|---|---|')

  for (const record of docsRecords) {
    lines.push(
      `| ${record.id} | ${record.path} | ${record.topic} | ${record.hash.slice(0, 8)} | ${record.ingested_at.slice(0, 10)} |`
    )
  }

  lines.push('')
  lines.push('## Topic Summary')
  lines.push('')

  const byTopic = new Map()
  for (const record of records) {
    byTopic.set(record.topic, (byTopic.get(record.topic) || 0) + 1)
  }

  lines.push('| topic | count |')
  lines.push('|---|---:|')
  for (const [topic, count] of Array.from(byTopic.entries()).sort(([a], [b]) => a.localeCompare(b))) {
    lines.push(`| ${topic} | ${count} |`)
  }

  lines.push('')
  return `${lines.join('\n')}\n`
}

function buildDocsRecord(absPath) {
  const repoRel = relToRepo(absPath)
  const hash = fileSha256(absPath)
  const id = hash.slice(0, 16)
  // docs/ 下文件 topic 取 docs/ 后第一级目录（如 docs/integration、docs/specs）
  // docs/ 根目录下的散落文件归到 docs/root
  const relToDocs = repoRel.slice(5) // 去掉 'docs/'
  const firstSegment = relToDocs.split('/')[0]
  const topic = relToDocs.includes('/') ? `docs/${firstSegment}` : 'docs/root'
  return {
    id,
    path: repoRel,
    type: 'markdown',
    topic,
    hash,
    status: 'docs_tracked', // docs/ 文件不抽取，仅追踪变更
    extract_path: repoRel,  // docs/ 文件本身就是 markdown，extract_path 指向自身
    confidence: 1,
    ingested_at: new Date().toISOString(),
  }
}

// 同步 buildDocsRecord，但跳过无法读取的文件（如断符号链接）
// 返回 null 表示应跳过该文件
function buildDocsRecordSafe(absPath) {
  try {
    return buildDocsRecord(absPath)
  } catch {
    // 文件无法读取（可能是断符号链接），跳过
    return null
  }
}

function main() {
  ensureWikiDirs()

  const previous = readJsonOrDefault(sourceCatalogJson, [])
  const previousByPath = new Map(previous.map((item) => [item.path, item]))

  const sourceAbsPaths = walkFiles(sourcesRoot).filter((absPath) => !isIgnoredFile(path.basename(absPath)))
  const records = []
  const pending = []

  // 1. 摄入 .wiki/sources/ 下的原始文件（需要 extract）
  for (const sourceAbsPath of sourceAbsPaths) {
    const record = buildExtractRecord(sourceAbsPath)
    materializeExtraction(record, sourceAbsPath)
    records.push(record)

    const old = previousByPath.get(record.path)
    if (!old || old.hash !== record.hash || old.status !== record.status) {
      pending.push(record.path)
    }
  }

  // 2. 追踪 docs/ 下的 markdown 文件（不 extract，仅 hash 比对检测变更）
  //    docs/ 是项目的事实源文档，wiki 页面的 sources 字段 90% 指向这里
  //    通过 hash 变更写入 pending，让 wiki:build 自动刷新相关页面的 updated 字段
  if (fs.existsSync(docsRoot)) {
    const docsAbsPaths = walkFiles(docsRoot).filter((absPath) => {
      const repoRel = relToRepo(absPath)
      return !isDocsIgnored(repoRel)
    })

    for (const docsAbsPath of docsAbsPaths) {
      const record = buildDocsRecordSafe(docsAbsPath)
      if (!record) {
        continue // 跳过无法读取的文件（如断符号链接）
      }
      records.push(record)

      const old = previousByPath.get(record.path)
      if (!old || old.hash !== record.hash) {
        pending.push(record.path)
      }
    }
  }

  records.sort((a, b) => a.path.localeCompare(b.path))

  writeJson(sourceCatalogJson, records)
  writeJson(pendingJson, {
    generated_at: new Date().toISOString(),
    pending_paths: pending,
  })

  fs.writeFileSync(path.join(sourcesRoot, 'README.md'), `# 原始资料 / Raw Sources\n\n> 本目录存放项目原始资料，遵循“源文件不可变、仅追加版本”的原则。\n\n## 混合摄入模式（默认）\n\n1. 优先直接放入原始文件（docx/xlsx/pdf/图片/md）\n2. 运行 \`npm run wiki:ingest\` 自动抽取到 \`.wiki/extracts/\`\n3. 抽取状态为 \`manual_review\` 时，补充人工 Markdown 再次执行 ingest\n\n## 分类目录\n\n- \`bidding/\`\n- \`contract/\`\n- \`industry/\`\n- \`competitor/\`\n- \`customer/\`\n- \`technical/\`\n- \`internal/\`\n- \`implementation/\`\n\n`, 'utf8')

  fs.writeFileSync(path.join(path.dirname(sourcesRoot), 'INDEX.md'), toSourceCatalogMarkdown(records), 'utf8')

  const docsCount = records.filter((r) => r.status === 'docs_tracked').length
  console.log(`wiki:ingest completed. sources=${records.length}, docs_tracked=${docsCount}, changed=${pending.length}`)
}

main()
