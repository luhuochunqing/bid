// Input: wiki pages, source/page catalogs, and extracted artifacts
// Output: validation report with fix instructions for link integrity, schema completeness, and wiki freshness gates
// Pos: scripts/ - Wiki health check gate for pre-commit and manual quality verification
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import {
  catalogRoot,
  ensureWikiDirs,
  parseFrontmatter,
  readJsonOrDefault,
  relToRepo,
  resolvePagePathFromLink,
  slugFromPagePath,
  walkFiles,
  wikiRoot,
} from './wiki-common.mjs'

const sourceCatalogJson = path.join(catalogRoot, 'source-catalog.json')
const pageCatalogJson = path.join(catalogRoot, 'page-catalog.json')

const requiredFrontmatterFields = ['title', 'space', 'category', 'sources', 'updated', 'health_checked']
const allowedSpaces = new Set(['engineering', 'implementation'])

function dateDaysDiff(dateText) {
  const date = new Date(dateText)
  if (Number.isNaN(date.getTime())) {
    return Number.POSITIVE_INFINITY
  }
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  return Math.floor(diffMs / (24 * 60 * 60 * 1000))
}

function existsInRepo(repoRelativePath) {
  if (!repoRelativePath) {
    return false
  }
  return fs.existsSync(path.join(process.cwd(), repoRelativePath))
}

function todayStr() {
  return new Date().toISOString().slice(0, 10)
}

function checkPageFrontmatter(pageAbsPath, frontmatter, violations) {
  const relPath = relToRepo(pageAbsPath)
  for (const field of requiredFrontmatterFields) {
    const value = frontmatter[field]
    if (value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0 && field !== 'sources')) {
      violations.push({
        file: relPath,
        issue: `missing frontmatter field: ${field}`,
        fix: field === 'health_checked'
          ? `Add "health_checked: ${todayStr()}" to frontmatter`
          : field === 'updated'
            ? `Add "updated: ${todayStr()}" to frontmatter`
            : `Add "${field}: <value>" to frontmatter`
      })
    }
  }

  if (frontmatter.space && !allowedSpaces.has(frontmatter.space)) {
    violations.push({
      file: relPath,
      issue: `invalid space: ${frontmatter.space}`,
      fix: `Change space to one of: ${[...allowedSpaces].join(', ')}`
    })
  }

  // archive: true 的页面（历史档案）跳过 updated 检查，但仍需 health_checked
  // 工程背景(2026-07-23): SOW/合同/里程碑等历史档案内容不会变，updated >30 天是正常的
  // 详见 .wiki/WIKI.md §3 AI-First 编写标准 - 历史档案豁免
  // 注意：parseFrontmatter 不做类型转换，archive: true 被解析为字符串 "true"
  const isArchive = frontmatter.archive === 'true' || frontmatter.archive === true

  // updated 检查：仅当 health_checked 也过期时才报违规
  // 工程背景(2026-07-23): 活跃文档 updated >30 天但 health_checked 7 天内 → 已 review 过，不报违规
  // 这样 updated 检查的作用变成"提醒 review"，而不是"强制改内容"
  const healthCheckedFresh = frontmatter.health_checked && dateDaysDiff(frontmatter.health_checked) <= 7

  if (!isArchive && !healthCheckedFresh && frontmatter.updated && dateDaysDiff(frontmatter.updated) > 30) {
    violations.push({
      file: relPath,
      issue: `stale updated date (>30 days): ${frontmatter.updated}`,
      fix: `Review content & update "updated" to ${todayStr()} (or just update "health_checked" to ${todayStr()} if content still accurate)`
    })
  }

  if (frontmatter.health_checked && dateDaysDiff(frontmatter.health_checked) > 7) {
    violations.push({
      file: relPath,
      issue: `stale health_checked date (>7 days): ${frontmatter.health_checked}`,
      fix: `Update "health_checked" to ${todayStr()} in frontmatter`
    })
  }

  if (Array.isArray(frontmatter.sources)) {
    for (const source of frontmatter.sources) {
      if (!existsInRepo(source)) {
        violations.push({
          file: relPath,
          issue: `references missing source: ${source}`,
          fix: `Create the file or remove "${source}" from sources list`
        })
      }
    }
  }
}

function checkWikiLinks(pageAbsPath, body, knownSlugs, violations) {
  const relPath = relToRepo(pageAbsPath)
  const links = Array.from(body.matchAll(/\[\[([^\]]+)\]\]/g)).map((m) => m[1].trim())
  for (const link of links) {
    const resolved = resolvePagePathFromLink(link)
    if (!resolved) {
      violations.push({
        file: relPath,
        issue: `broken wiki link: [[${link}]]`,
        fix: `Create the linked page or fix the link target`
      })
      continue
    }

    const slug = slugFromPagePath(resolved)
    if (!knownSlugs.has(slug)) {
      violations.push({
        file: relPath,
        issue: `link resolved outside page catalog: [[${link}]]`,
        fix: `Add the target page to .wiki/pages/ or update the link`
      })
    }
  }
}

function checkCatalogConsistency(sourceCatalog, pageCatalog, violations) {
  if (!Array.isArray(sourceCatalog) || sourceCatalog.length === 0) {
    violations.push({
      file: relToRepo(sourceCatalogJson),
      issue: 'empty or invalid',
      fix: 'Run wiki sync to regenerate source-catalog.json'
    })
  }

  if (!Array.isArray(pageCatalog) || pageCatalog.length === 0) {
    violations.push({
      file: relToRepo(pageCatalogJson),
      issue: 'empty or invalid',
      fix: 'Run wiki sync to regenerate page-catalog.json'
    })
  }

  for (const source of sourceCatalog) {
    if (source.status === "missing") continue;
    if (!source.path || !source.hash || !source.status || !source.extract_path) {
      violations.push({
        file: relToRepo(sourceCatalogJson),
        issue: `source catalog item missing required fields: ${JSON.stringify(source)}`,
        fix: 'Run wiki sync to rebuild source catalog'
      })
      continue
    }

    if (!existsInRepo(source.path)) {
      violations.push({
        file: relToRepo(sourceCatalogJson),
        issue: `source catalog path not found: ${source.path}`,
        fix: `Create the file or remove this entry from source-catalog.json`
      })
    }

    if (!existsInRepo(source.extract_path)) {
      violations.push({
        file: relToRepo(sourceCatalogJson),
        issue: `source extract not found: ${source.extract_path}`,
        fix: `Run wiki sync to regenerate extract, or create the file`
      })
    }
  }

  for (const page of pageCatalog) {
    if (!page.slug || !page.path || !page.space || !page.updated) {
      violations.push({
        file: relToRepo(pageCatalogJson),
        issue: `page catalog item missing required fields: ${JSON.stringify(page)}`,
        fix: 'Run wiki sync to rebuild page catalog'
      })
      continue
    }

    if (!existsInRepo(page.path)) {
      violations.push({
        file: relToRepo(pageCatalogJson),
        issue: `page catalog path not found: ${page.path}`,
        fix: `Create the page file or remove this entry from page-catalog.json`
      })
    }

    if (!allowedSpaces.has(page.space)) {
      violations.push({
        file: relToRepo(pageCatalogJson),
        issue: `page catalog invalid space for ${page.slug}: ${page.space}`,
        fix: `Change space to one of: ${[...allowedSpaces].join(', ')}`
      })
    }
  }
}

function main() {
  ensureWikiDirs()

  const violations = []

  const requiredTopLevelFiles = ['INDEX.md', 'PAGE_INDEX.md']
  for (const file of requiredTopLevelFiles) {
    const absPath = path.join(wikiRoot, file)
    if (!fs.existsSync(absPath)) {
      violations.push({
        file: relToRepo(absPath),
        issue: 'missing required wiki index file',
        fix: `Create ${file} in .wiki/ directory`
      })
    }
  }

  const sourceCatalog = readJsonOrDefault(sourceCatalogJson, [])
  const pageCatalog = readJsonOrDefault(pageCatalogJson, [])
  checkCatalogConsistency(sourceCatalog, pageCatalog, violations)

  const pageAbsPaths = walkFiles(path.join(wikiRoot, 'pages')).filter((absPath) => absPath.endsWith('.md'))
  const knownSlugs = new Set(pageAbsPaths.map((absPath) => slugFromPagePath(absPath)))

  for (const pageAbsPath of pageAbsPaths) {
    const content = fs.readFileSync(pageAbsPath, 'utf8')
    const { frontmatter, body } = parseFrontmatter(content)
    checkPageFrontmatter(pageAbsPath, frontmatter, violations)
    checkWikiLinks(pageAbsPath, body, knownSlugs, violations)
  }

  if (violations.length > 0) {
    console.error('wiki:check failed with violations:\n')
    for (const v of violations) {
      console.error(`  ✗ ${v.file}`)
      console.error(`    ${v.issue}`)
      console.error(`    💡 Fix: ${v.fix}`)
      console.error('')
    }
    console.error(`Total: ${violations.length} violation(s). Run "npm run wiki:fix" for auto-fix suggestions.`)
    process.exit(1)
  }

  console.log(`wiki:check passed. pages=${pageAbsPaths.length}`)
}

main()
