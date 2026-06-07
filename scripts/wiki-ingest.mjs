// Input: files under .wiki/sources, including bidding/contract/etc., plus previous source catalog snapshot
// Output: extracted markdown artifacts and refreshed source catalog with pending changes
// Pos: scripts/ - Wiki source ingestion pipeline for mixed raw-file onboarding
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import {
  catalogRoot,
  decodeXmlEntities,
  ensureWikiDirs,
  extractsRoot,
  fileSha256,
  formatOutputName,
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
    '| id | path | type | topic | status | confidence | extract_path | ingested_at |',
    '|---|---|---|---|---|---:|---|---|',
  ]

  for (const record of records) {
    lines.push(
      `| ${record.id} | ${record.path} | ${record.type} | ${record.topic} | ${record.status} | ${record.confidence} | ${record.extract_path} | ${record.ingested_at.slice(0, 10)} |`
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

function main() {
  ensureWikiDirs()

  const previous = readJsonOrDefault(sourceCatalogJson, [])
  const previousByPath = new Map(previous.map((item) => [item.path, item]))

  const sourceAbsPaths = walkFiles(sourcesRoot).filter((absPath) => !isIgnoredFile(path.basename(absPath)))
  const records = []
  const pending = []

  for (const sourceAbsPath of sourceAbsPaths) {
    const record = buildExtractRecord(sourceAbsPath)
    materializeExtraction(record, sourceAbsPath)
    records.push(record)

    const old = previousByPath.get(record.path)
    if (!old || old.hash !== record.hash || old.status !== record.status) {
      pending.push(record.path)
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

  console.log(`wiki:ingest completed. sources=${records.length}, changed=${pending.length}`)
}

main()
