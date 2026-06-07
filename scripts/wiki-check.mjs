// Input: wiki pages, source/page catalogs, and extracted artifacts
// Output: validation report for link integrity, schema completeness, and wiki freshness gates
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

function checkPageFrontmatter(pageAbsPath, frontmatter, violations) {
  for (const field of requiredFrontmatterFields) {
    const value = frontmatter[field]
    if (value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)) {
      violations.push(`${relToRepo(pageAbsPath)} missing frontmatter field: ${field}`)
    }
  }

  if (frontmatter.space && !allowedSpaces.has(frontmatter.space)) {
    violations.push(`${relToRepo(pageAbsPath)} has invalid space: ${frontmatter.space}`)
  }

  if (frontmatter.updated && dateDaysDiff(frontmatter.updated) > 30) {
    violations.push(`${relToRepo(pageAbsPath)} has stale updated date (>30 days): ${frontmatter.updated}`)
  }

  if (frontmatter.health_checked && dateDaysDiff(frontmatter.health_checked) > 7) {
    violations.push(`${relToRepo(pageAbsPath)} has stale health_checked date (>7 days): ${frontmatter.health_checked}`)
  }

  if (Array.isArray(frontmatter.sources)) {
    for (const source of frontmatter.sources) {
      if (!existsInRepo(source)) {
        violations.push(`${relToRepo(pageAbsPath)} references missing source: ${source}`)
      }
    }
  }
}

function checkWikiLinks(pageAbsPath, body, knownSlugs, violations) {
  const links = Array.from(body.matchAll(/\[\[([^\]]+)\]\]/g)).map((m) => m[1].trim())
  for (const link of links) {
    const resolved = resolvePagePathFromLink(link)
    if (!resolved) {
      violations.push(`${relToRepo(pageAbsPath)} has broken wiki link: [[${link}]]`)
      continue
    }

    const slug = slugFromPagePath(resolved)
    if (!knownSlugs.has(slug)) {
      violations.push(`${relToRepo(pageAbsPath)} link resolved outside page catalog: [[${link}]]`)
    }
  }
}

function checkCatalogConsistency(sourceCatalog, pageCatalog, violations) {
  if (!Array.isArray(sourceCatalog) || sourceCatalog.length === 0) {
    violations.push(`${relToRepo(sourceCatalogJson)} is empty or invalid`)
  }

  if (!Array.isArray(pageCatalog) || pageCatalog.length === 0) {
    violations.push(`${relToRepo(pageCatalogJson)} is empty or invalid`)
  }

  for (const source of sourceCatalog) {
    if (!source.path || !source.hash || !source.status || !source.extract_path) {
      violations.push(`source catalog item missing required fields: ${JSON.stringify(source)}`)
      continue
    }

    if (!existsInRepo(source.path)) {
      violations.push(`source catalog path not found: ${source.path}`)
    }

    if (!existsInRepo(source.extract_path)) {
      violations.push(`source extract not found: ${source.extract_path}`)
    }
  }

  for (const page of pageCatalog) {
    if (!page.slug || !page.path || !page.space || !page.updated) {
      violations.push(`page catalog item missing required fields: ${JSON.stringify(page)}`)
      continue
    }

    if (!existsInRepo(page.path)) {
      violations.push(`page catalog path not found: ${page.path}`)
    }

    if (!allowedSpaces.has(page.space)) {
      violations.push(`page catalog invalid space for ${page.slug}: ${page.space}`)
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
      violations.push(`missing required wiki index file: ${relToRepo(absPath)}`)
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
    console.error('wiki:check failed with violations:')
    for (const violation of violations) {
      console.error(`- ${violation}`)
    }
    process.exit(1)
  }

  console.log(`wiki:check passed. pages=${pageAbsPaths.length}`)
}

main()
