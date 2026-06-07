// Input: wiki directories, filesystem contents, and script runtime environment
// Output: shared wiki parsing, catalog, and filesystem helper functions
// Pos: scripts/ - Shared utilities for wiki ingest/build/check workflows
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { execFileSync } from 'node:child_process'

export const repoRoot = process.cwd()
export const wikiRoot = path.join(repoRoot, '.wiki')
export const sourcesRoot = path.join(wikiRoot, 'sources')
export const extractsRoot = path.join(wikiRoot, 'extracts')
export const pagesRoot = path.join(wikiRoot, 'pages')
export const catalogRoot = path.join(wikiRoot, 'catalog')
export const outputsRoot = path.join(wikiRoot, 'outputs')

export function ensureWikiDirs() {
  for (const dir of [wikiRoot, sourcesRoot, extractsRoot, pagesRoot, catalogRoot, outputsRoot]) {
    fs.mkdirSync(dir, { recursive: true })
  }
}

export function isoDate() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function escapeRegExp(input) {
  return input.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function isIgnoredFile(fileName) {
  return fileName === '.DS_Store' || fileName === '.gitkeep' || fileName === 'README.md'
}

export function walkFiles(rootDir) {
  const files = []

  function walk(currentDir) {
    if (!fs.existsSync(currentDir)) {
      return
    }
    for (const entry of fs.readdirSync(currentDir, { withFileTypes: true })) {
      const fullPath = path.join(currentDir, entry.name)
      if (entry.isDirectory()) {
        walk(fullPath)
        continue
      }
      files.push(fullPath)
    }
  }

  walk(rootDir)
  return files
}

export function relToRepo(absPath) {
  return path.relative(repoRoot, absPath).replace(/\\/g, '/')
}

export function relToSources(absPath) {
  return path.relative(sourcesRoot, absPath).replace(/\\/g, '/')
}

export function fileSha256(absPath) {
  const content = fs.readFileSync(absPath)
  return crypto.createHash('sha256').update(content).digest('hex')
}

export function readJsonOrDefault(absPath, fallback) {
  if (!fs.existsSync(absPath)) {
    return fallback
  }
  try {
    return JSON.parse(fs.readFileSync(absPath, 'utf8'))
  } catch {
    return fallback
  }
}

export function writeJson(absPath, value) {
  fs.mkdirSync(path.dirname(absPath), { recursive: true })
  fs.writeFileSync(absPath, `${JSON.stringify(value, null, 2)}\n`, 'utf8')
}

export function markdownEscapeCell(text) {
  return String(text ?? '').replace(/\|/g, '\\|').replace(/\n/g, ' ')
}

export function slugFromPagePath(absPath) {
  const rel = path.relative(pagesRoot, absPath).replace(/\\/g, '/')
  return rel.replace(/\.md$/, '')
}

export function decodeXmlEntities(text) {
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
}

export function stripXml(xml) {
  return decodeXmlEntities(
    xml
      .replace(/<w:p[^>]*>/g, '\n')
      .replace(/<w:tr[^>]*>/g, '\n')
      .replace(/<\/w:tr>/g, '\n')
      .replace(/<[^>]+>/g, ' ')
      .replace(/\s+/g, ' ')
      .replace(/\n\s+/g, '\n')
      .trim()
  )
}

export function runCommand(command, args) {
  return execFileSync(command, args, {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  })
}

export function parseFrontmatter(content) {
  if (!content.startsWith('---\n')) {
    return { frontmatter: {}, body: content }
  }
  const end = content.indexOf('\n---\n', 4)
  if (end === -1) {
    return { frontmatter: {}, body: content }
  }

  const raw = content.slice(4, end)
  const body = content.slice(end + 5)
  const lines = raw.split('\n')
  const frontmatter = {}

  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    const kv = line.match(/^([A-Za-z0-9_-]+):\s*(.*)$/)
    if (!kv) {
      i += 1
      continue
    }
    const [, key, value] = kv

    if (value === '') {
      const next = lines[i + 1] || ''
      if (next.trim().startsWith('- ')) {
        const arr = []
        i += 1
        while (i < lines.length && lines[i].trim().startsWith('- ')) {
          arr.push(lines[i].trim().slice(2).trim())
          i += 1
        }
        frontmatter[key] = arr
        continue
      }
      frontmatter[key] = ''
      i += 1
      continue
    }

    if (value.startsWith('[') && value.endsWith(']')) {
      const bodyText = value.slice(1, -1).trim()
      if (!bodyText) {
        frontmatter[key] = []
      } else {
        frontmatter[key] = bodyText
          .split(',')
          .map((item) => item.trim().replace(/^['"]|['"]$/g, ''))
      }
      i += 1
      continue
    }

    frontmatter[key] = value.replace(/^['"]|['"]$/g, '')
    i += 1
  }

  return { frontmatter, body }
}

export function toFrontmatterValue(value) {
  if (Array.isArray(value)) {
    return `[${value.map((item) => String(item)).join(', ')}]`
  }
  return String(value)
}

export function buildFrontmatter(frontmatter, body) {
  const lines = ['---']
  const orderedKeys = [
    'title',
    'space',
    'category',
    'tags',
    'sources',
    'backlinks',
    'created',
    'updated',
    'health_checked',
  ]

  const remaining = Object.keys(frontmatter).filter((key) => !orderedKeys.includes(key))
  const keys = [...orderedKeys.filter((key) => key in frontmatter), ...remaining]

  for (const key of keys) {
    const value = frontmatter[key]
    if (Array.isArray(value)) {
      if (key === 'sources' || key === 'backlinks') {
        lines.push(`${key}:`)
        for (const item of value) {
          lines.push(`  - ${item}`)
        }
      } else {
        lines.push(`${key}: ${toFrontmatterValue(value)}`)
      }
    } else {
      lines.push(`${key}: ${toFrontmatterValue(value)}`)
    }
  }

  lines.push('---')
  lines.push('')
  return `${lines.join('\n')}${body.startsWith('\n') ? body.slice(1) : body}`
}

export function topicFromSourceRel(sourceRel) {
  const [topic = 'uncategorized'] = sourceRel.split('/')
  return topic || 'uncategorized'
}

export function sourceTypeFromPath(sourceRel) {
  const ext = path.extname(sourceRel).toLowerCase()
  if (ext === '.md' || ext === '.markdown') return 'markdown'
  if (ext === '.txt') return 'text'
  if (ext === '.doc' || ext === '.docx') return 'docx'
  if (ext === '.xls' || ext === '.xlsx') return 'xlsx'
  if (ext === '.pdf') return 'pdf'
  if (['.png', '.jpg', '.jpeg', '.webp', '.gif', '.bmp', '.tiff'].includes(ext)) return 'image'
  return ext.replace('.', '') || 'unknown'
}

export function formatOutputName(sourceRel) {
  return sourceRel.replace(/[\\/]/g, '__').replace(/\s+/g, '_')
}

export function resolvePagePathFromLink(linkText) {
  const normalized = linkText.trim().replace(/^\/+/, '').replace(/\.md$/, '')
  if (!normalized) {
    return null
  }

  const directPath = path.join(pagesRoot, `${normalized}.md`)
  if (fs.existsSync(directPath)) {
    return directPath
  }

  const directNestedPath = path.join(pagesRoot, normalized)
  if (fs.existsSync(directNestedPath) && directNestedPath.endsWith('.md')) {
    return directNestedPath
  }

  const nameOnly = path.basename(normalized)
  const candidate = walkFiles(pagesRoot).find((absPath) => slugFromPagePath(absPath).endsWith(nameOnly))
  return candidate || null
}

export function nowTimestamp() {
  return new Date().toISOString()
}
