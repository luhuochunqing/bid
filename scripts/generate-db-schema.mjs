#!/usr/bin/env node
// Input: Flyway SQL migrations (V*.sql, B*.sql) under backend/src/main/resources/db/migration-mysql/
// Output: docs/generated/db-schema.md — auto-generated database schema reference
// Pos: scripts/ — Flyway 迁移驱动的数据库结构文档生成器
// 维护声明: 新增或修改 Flyway migration 后，必须重新运行本脚本（npm run db:generate-schema）刷新 docs/generated/db-schema.md。

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptDir, '..')
const migrationDir = path.join(repoRoot, 'backend/src/main/resources/db/migration-mysql')
const outputFile = path.join(repoRoot, 'docs/generated/db-schema.md')

// ── Main ──

function main() {
  if (!fs.existsSync(migrationDir)) {
    console.error(`Migration directory not found: ${migrationDir}`)
    process.exit(1)
  }

  const files = forwardMigrationFiles(migrationDir)
  console.log(`Scanning ${files.length} forward migration(s)...`)

  const tables = new Map() // tableName → { columns: Map<name, {type, nullable, defaultValue, comment, since}>, indexes: [], since, comment }
  const alterLog = []      // ALTER TABLE entries that couldn't be fully parsed

  for (const fileName of files) {
    const sql = fs.readFileSync(path.join(migrationDir, fileName), 'utf8')
    const version = fileName.replace(/^(V|B)/, '').split('__')[0]
    parseMigrations(sql, version, fileName, tables, alterLog)
  }

  const output = renderOutput(tables, alterLog, files.length)
  fs.mkdirSync(path.dirname(outputFile), { recursive: true })
  fs.writeFileSync(outputFile, output, 'utf8')
  console.log(`Generated ${outputFile} (${tables.size} tables, ${files.length} migrations)`)
}

// ── File enumeration ──

function forwardMigrationFiles(directory) {
  return fs.readdirSync(directory)
    .filter((f) => f.endsWith('.sql') && /^(V|B)\d+/.test(f))
    .sort(compareMigrationFileNames)
}

function compareMigrationFileNames(left, right) {
  return left.localeCompare(right, 'en', { numeric: true })
}

// ── SQL parsing ──

function parseMigrations(sql, version, fileName, tables, alterLog) {
  const cleaned = stripBlockComments(sql)
  const statements = splitStatements(cleaned)

  for (const raw of statements) {
    const stmt = stripLineComments(raw).trim()
    if (!stmt) continue

    parseCreateTable(stmt, version, fileName, tables)
      || parseCreateIndex(stmt, version, fileName, tables)
      || parseAlterTable(stmt, version, fileName, tables, alterLog)
    // DML / other statements are silently skipped
  }
}

function stripBlockComments(sql) {
  return sql.replace(/\/\*[\s\S]*?\*\//g, ' ')
}

function stripLineComments(sql) {
  return sql
    .split('\n')
    .filter((line) => !/^\s*--/.test(line))
    .join('\n')
}

function splitStatements(sql) {
  return sql.split(';')
}

// ── CREATE TABLE ──

function parseCreateTable(stmt, version, fileName, tables) {
  const match = stmt.match(/^CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?\s*\(/is)
  if (!match) return false

  const tableName = match[1]
  const bodyStart = stmt.indexOf('(') + 1
  const bodyEnd = findMatchingParen(stmt, bodyStart - 1)
  if (bodyEnd === -1) return false

  const body = stmt.slice(bodyStart, bodyEnd)
  const tableComment = extractTableComment(stmt.slice(bodyEnd))

  const columns = new Map()
  const indexes = []
  const parts = splitColumnDefs(body)

  for (const part of parts) {
    const trimmed = part.trim()
    if (!trimmed) continue

    // Index/constraint definitions inside CREATE TABLE
    const idx = parseInlineIndex(trimmed, version)
    if (idx) { indexes.push(idx); continue }

    // Column definition
    const col = parseColumnDef(trimmed, version)
    if (col) { columns.set(col.name, col); continue }

    // primary key / unique constraint inside body
    const pk = parseInlinePrimaryKey(trimmed)
    if (pk) continue
  }

  tables.set(tableName, { columns, indexes, since: version, comment: tableComment || '' })
  return true
}

function findMatchingParen(sql, openPos) {
  let depth = 0
  let inString = false
  let stringChar = ''
  for (let i = openPos; i < sql.length; i++) {
    const ch = sql[i]
    if (inString) {
      if (ch === stringChar && sql[i - 1] !== '\\') inString = false
      continue
    }
    if (ch === "'" || ch === '"') { inString = true; stringChar = ch; continue }
    if (ch === '(') depth++
    if (ch === ')') { depth--; if (depth === 0) return i }
  }
  return -1
}

function splitColumnDefs(body) {
  // Split on commas that are NOT inside parentheses (for types like enum(...), decimal(...))
  const parts = []
  let current = ''
  let depth = 0
  let inString = false
  let stringChar = ''

  for (let i = 0; i < body.length; i++) {
    const ch = body[i]
    if (inString) {
      current += ch
      if (ch === stringChar && body[i - 1] !== '\\') inString = false
      continue
    }
    if (ch === "'" || ch === '"') { inString = true; stringChar = ch; current += ch; continue }
    if (ch === '(') { depth++; current += ch; continue }
    if (ch === ')') { depth--; current += ch; continue }
    if (ch === ',' && depth === 0) { parts.push(current); current = ''; continue }
    current += ch
  }
  if (current.trim()) parts.push(current)
  return parts
}

function extractTableComment(afterParen) {
  const match = afterParen.match(/COMMENT\s*=?\s*'([^']*)'/i)
  return match ? match[1] : ''
}

function parseColumnDef(raw, version) {
  // Match: column_name TYPE [(size)] [NOT NULL | NULL] [DEFAULT ...] [AUTO_INCREMENT] [COMMENT '...'] [AFTER ...]
  const match = raw.match(
    /^\s*`?(\w+)`?\s+((?:tiny|small|medium|big)?int(?:\s*\(\d+\))?(?:\s+unsigned)?|(?:var)?char\s*\(\d+\)|text(?:\(\d+\))?|datetime(?:\(\d+\))?|date|time|timestamp(?:\(\d+\))?(?:\s+ON\s+UPDATE\s+CURRENT_TIMESTAMP)?|decimal\s*\(\d+,\s*\d+\)|float|double|enum\s*\([^)]+\)|bit|boolean|blob|json|longtext|mediumtext|char\s*\(\d+\))/i
  )
  if (!match) return null

  const name = match[1]
  const rest = raw.slice(match.index + match[0].length)
  const fullType = match[2].trim()

  const nullable = /\bNOT\s+NULL\b/i.test(raw) ? false : true
  const autoInc = /\bAUTO_INCREMENT\b/i.test(raw)

  const defaultMatch = rest.match(/\bDEFAULT\s+('(?:[^'\\]|\\.)*'|NULL|\d+\.?\d*|\w+\(\))/i)
  const defaultValue = defaultMatch ? defaultMatch[1] : null

  const commentMatch = rest.match(/\bCOMMENT\s+'((?:[^'\\]|\\.)*)'/i)
  const comment = commentMatch ? commentMatch[1] : ''

  return {
    name,
    type: fullType,
    nullable: autoInc ? false : nullable,
    defaultValue,
    comment,
    since: version,
  }
}

function parseInlineIndex(raw, version) {
  const idxMatch = raw.match(
    /^(?:UNIQUE\s+)?(?:KEY|INDEX)\s+`?(\w+)`?\s*\(([^)]+)\)/i
  )
  if (!idxMatch) return null
  return { name: idxMatch[1], columns: idxMatch[2].trim(), since: version, unique: /^UNIQUE/i.test(raw) }
}

function parseInlinePrimaryKey(raw) {
  return /^\s*(?:PRIMARY\s+KEY|CONSTRAINT\s+`?\w+`?\s+PRIMARY\s+KEY)/i.test(raw)
}

// ── CREATE INDEX ──

function parseCreateIndex(stmt, version, fileName, tables) {
  const match = stmt.match(
    /^CREATE\s+(UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?\s+ON\s+`?(\w+)`?\s*\(([^)]+)\)/i
  )
  if (!match) return false

  const unique = !!match[1]
  const indexName = match[2]
  const tableName = match[3]
  const columns = match[4].trim()

  const table = tables.get(tableName)
  if (table) {
    table.indexes.push({ name: indexName, columns, since: version, unique })
  } else {
    // Index on table we haven't seen yet (shouldn't happen in sorted order, but be safe)
    const newTable = { columns: new Map(), indexes: [{ name: indexName, columns, since: version, unique }], since: version, comment: '' }
    tables.set(tableName, newTable)
  }
  return true
}

// ── ALTER TABLE ──

function parseAlterTable(stmt, version, fileName, tables, alterLog) {
  const match = stmt.match(/^ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?`?(\w+)`?/i)
  if (!match) return false

  const tableName = match[1]
  const rest = stmt.slice(match[0].length)

  // ADD COLUMN
  const addCol = rest.match(/ADD\s+COLUMN\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?\s+((?:tiny|small|medium|big)?int(?:\s*\(\d+\))?(?:\s+unsigned)?|(?:var)?char\s*\(\d+\)|text(?:\(\d+\))?|datetime(?:\(\d+\))?|date|time|timestamp(?:\(\d+\))?|decimal\s*\(\d+,\s*\d+\)|float|double|enum\s*\([^)]+\)|bit|boolean|blob|json|longtext|mediumtext|char\s*\(\d+\))/i)
  if (addCol) {
    const table = getOrCreateTable(tables, tableName, version)
    const colRest = rest.slice(addCol.index + addCol[0].length)
    const nullable = /\bNOT\s+NULL\b/i.test(rest) ? false : true
    const defaultMatch = colRest.match(/\bDEFAULT\s+('(?:[^'\\]|\\.)*'|NULL|\d+\.?\d*|\w+\(\))/i)
    const commentMatch = colRest.match(/\bCOMMENT\s+'((?:[^'\\]|\\.)*)'/i)
    table.columns.set(addCol[1], {
      name: addCol[1],
      type: addCol[2].trim(),
      nullable,
      defaultValue: defaultMatch ? defaultMatch[1] : null,
      comment: commentMatch ? commentMatch[1] : '',
      since: version,
    })
    return true
  }

  // ADD CONSTRAINT (unique/foreign key)
  const addConstr = rest.match(/ADD\s+CONSTRAINT\s+`?(\w+)`?\s+(UNIQUE|FOREIGN\s+KEY|PRIMARY\s+KEY)\s*\(([^)]+)\)/i)
  if (addConstr) {
    const table = getOrCreateTable(tables, tableName, version)
    table.indexes.push({
      name: addConstr[1],
      columns: addConstr[3].trim(),
      since: version,
      unique: /^UNIQUE/i.test(addConstr[2]) || /^PRIMARY/i.test(addConstr[2]),
    })
    return true
  }

  // ADD INDEX
  const addIdx = rest.match(/ADD\s+(?:UNIQUE\s+)?(?:INDEX|KEY)\s+`?(\w+)`?\s*\(([^)]+)\)/i)
  if (addIdx) {
    const table = getOrCreateTable(tables, tableName, version)
    table.indexes.push({
      name: addIdx[1],
      columns: addIdx[2].trim(),
      since: version,
      unique: /ADD\s+UNIQUE/i.test(rest),
    })
    return true
  }

  // Other ALTER (drop column, modify column, etc.) — log for reference
  alterLog.push({ table: tableName, version, file: fileName, snippet: rest.trim().slice(0, 120) })
  return true
}

function getOrCreateTable(tables, tableName, version) {
  if (!tables.has(tableName)) {
    tables.set(tableName, { columns: new Map(), indexes: [], since: version, comment: '' })
  }
  return tables.get(tableName)
}

// ── Rendering ──

function renderOutput(tables, alterLog, migrationCount) {
  const now = new Date().toISOString().replace('T', ' ').slice(0, 19)
  const lines = []

  lines.push(`<!-- AUTO-GENERATED by scripts/generate-db-schema.mjs at ${now} -->`)
  lines.push('<!-- ⚠️ 本文件由脚本自动生成，禁止手动编辑。运行 npm run db:generate-schema 刷新。 -->')
  lines.push('')
  lines.push('# 数据库结构参考 (db-schema)')
  lines.push('')
  lines.push(`> 自动从 ${migrationCount} 个 Flyway 正向迁移解析生成。源目录：backend/src/main/resources/db/migration-mysql/`)
  lines.push('')
  lines.push('## 表清单')
  lines.push('')
  lines.push('| # | 表名 | 首次出现版本 | 列数 | 索引数 | 注释 |')
  lines.push('|---:|---|---|---:|---:|---|')

  const sortedNames = [...tables.keys()].sort()
  let idx = 0
  for (const name of sortedNames) {
    idx++
    const t = tables.get(name)
    lines.push(`| ${idx} | ${name} | ${t.since} | ${t.columns.size} | ${t.indexes.length} | ${t.comment || '—'} |`)
  }

  lines.push('')
  lines.push('---')
  lines.push('')

  // Per-table detail
  for (const name of sortedNames) {
    const t = tables.get(name)
    lines.push(`## ${name}`)
    if (t.comment) lines.push(`> ${t.comment}`)
    lines.push('')
    lines.push(`首次出现: V${t.since}`)
    lines.push('')

    // Columns
    lines.push('### 列')
    lines.push('')
    lines.push('| 列名 | 类型 | 可空 | 默认值 | 注释 | Since |')
    lines.push('|---|---|---|---|---|---|')
    for (const [colName, col] of t.columns) {
      const nullable = col.nullable ? '✓' : '✗'
      const def = col.defaultValue !== null ? col.defaultValue : '—'
      const comment = col.comment || '—'
      lines.push(`| ${colName} | ${col.type} | ${nullable} | ${def} | ${comment} | V${col.since} |`)
    }

    // Indexes
    if (t.indexes.length > 0) {
      lines.push('')
      lines.push('### 索引')
      lines.push('')
      lines.push('| 索引名 | 列 | 类型 | Since |')
      lines.push('|---|---|---|---|')
      for (const ix of t.indexes) {
        const type = ix.unique ? 'UNIQUE' : 'INDEX'
        lines.push(`| ${ix.name} | ${ix.columns} | ${type} | V${ix.since} |`)
      }
    }

    lines.push('')
    lines.push('---')
    lines.push('')
  }

  // ALTER TABLE log (unparsed operations)
  if (alterLog.length > 0) {
    lines.push('## 未完整解析的 ALTER 操作')
    lines.push('')
    lines.push('> 以下 ALTER TABLE 操作未被完整解析（如 DROP COLUMN、MODIFY COLUMN 等），供人工复查。')
    lines.push('')
    lines.push('| 表名 | 版本 | 文件 | 操作摘要 |')
    lines.push('|---|---|---|---|')
    for (const entry of alterLog) {
      lines.push(`| ${entry.table} | V${entry.version} | ${entry.file} | ${entry.snippet} |`)
    }
    lines.push('')
  }

  lines.push('---')
  lines.push('')
  lines.push(`_Generated at ${now} from ${migrationCount} forward migrations._`)

  return lines.join('\n')
}

main()
