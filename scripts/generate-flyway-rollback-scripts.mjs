#!/usr/bin/env node
// Input: Flyway SQL migrations under backend/src/main/resources/db/migration*
// Output: generated rollback scripts under backend/src/main/resources/db/rollback*
// Pos: Flyway 历史迁移 down 脚本补齐工具
// 维护声明: 新增或修改 Flyway migration 时，必须同步运行本脚本（npm run db:generate-rollback）并复核生成的 rollback 注释。
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptDir, '..')
const root = path.join(repoRoot, 'backend/src/main/resources/db')

const migrationSets = [
  { source: 'migration-mysql', target: 'rollback/migration-mysql', dialect: 'mysql' },
]

function main() {
  for (const set of migrationSets) {
    generateSet(set)
  }
}

function generateSet({ source, target, dialect }) {
  const sourceRoot = path.join(root, source)
  const targetRoot = path.join(root, target)
  fs.mkdirSync(targetRoot, { recursive: true })

  for (const fileName of migrationFiles(sourceRoot)) {
    const sourceSql = fs.readFileSync(path.join(sourceRoot, fileName), 'utf8')
    const rollbackSql = buildRollbackSql({ source, fileName, sourceSql, dialect })
    fs.writeFileSync(path.join(targetRoot, rollbackFileName(fileName)), rollbackSql, 'utf8')
  }
}

function migrationFiles(directory) {
  return fs.readdirSync(directory)
    .filter((fileName) => fileName.endsWith('.sql'))
    .sort(compareMigrationFileNames)
}

function compareMigrationFileNames(left, right) {
  return left.localeCompare(right, 'en', { numeric: true })
}

function rollbackFileName(fileName) {
  // Versioned migrations (V*) become U*; baseline migrations (B*) become UB*
  // so a Vn / Bn pair never collides on the same Un prefix in the rollback dir.
  if (fileName.startsWith('B')) return `U${fileName}`
  return fileName.replace(/^V/, 'U')
}

function buildRollbackSql({ source, fileName, sourceSql, dialect }) {
  const statements = splitStatements(sourceSql)
  const rollbackStatements = statements
    .map((statement) => reverseStatement(statement, dialect))
    .reverse()

  return [
    `-- Input: ${source}/${fileName}`,
    `-- Output: rollback script for ${dialect} environments; review data-loss comments before production use.`,
    '-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.',
    '-- 维护声明: source migration changes must update this rollback script in the same branch.',
    '',
    ...(rollbackStatements.length > 0 ? rollbackStatements : ['-- No-op rollback: source migration contains no executable SQL statements.']),
    '',
  ].join('\n')
}

function splitStatements(sql) {
  return stripBlockComments(sql)
    .split(';')
    .map((statement) => stripLineComments(statement).trim())
    .filter(Boolean)
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

function reverseStatement(statement, dialect) {
  const normalized = statement.replace(/\s+/g, ' ').trim()

  return reverseCreateIndex(normalized, dialect)
    || reverseCreateTable(normalized)
    || reverseCreateSequence(normalized)
    || reverseAddColumn(normalized, dialect)
    || reverseAddForeignKey(normalized, dialect)
    || reverseAddConstraint(normalized, dialect)
    || reverseColumnAlteration(normalized)
    || reverseDroppedColumn(normalized)
    || dataRollbackComment(normalized)
    || manualRollbackComment(normalized)
}

function reverseCreateIndex(statement, dialect) {
  const match = statement.match(/^CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?([`"\w]+)\s+ON\s+([`"\w]+)/i)
  if (!match) return ''

  const indexName = cleanIdentifier(match[1])
  const tableName = cleanIdentifier(match[2])
  return dialect === 'mysql'
    ? `DROP INDEX ${indexName} ON ${tableName};`
    : `DROP INDEX IF EXISTS ${indexName};`
}

function reverseCreateTable(statement) {
  const match = statement.match(/^CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([`"\w]+)/i)
  return match ? `DROP TABLE IF EXISTS ${cleanIdentifier(match[1])};` : ''
}

function reverseCreateSequence(statement) {
  const match = statement.match(/^CREATE\s+SEQUENCE\s+(?:IF\s+NOT\s+EXISTS\s+)?([`"\w.]+)/i)
  return match ? `DROP SEQUENCE IF EXISTS ${cleanIdentifier(match[1])};` : ''
}

function reverseAddColumn(statement, dialect) {
  const match = statement.match(/^ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?([`"\w]+)\s+ADD\s+COLUMN\s+(?:IF\s+NOT\s+EXISTS\s+)?([`"\w]+)/i)
  if (!match) return ''

  const tableName = cleanIdentifier(match[1])
  const columnName = cleanIdentifier(match[2])
  return dialect === 'mysql'
    ? `ALTER TABLE ${tableName} DROP COLUMN ${columnName};`
    : `ALTER TABLE ${tableName} DROP COLUMN IF EXISTS ${columnName};`
}

function reverseAddForeignKey(statement, dialect) {
  const match = statement.match(/^ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?([`"\w]+)\s+ADD\s+CONSTRAINT\s+([`"\w]+)\s+FOREIGN\s+KEY/i)
  if (!match) return ''

  const tableName = cleanIdentifier(match[1])
  const constraintName = cleanIdentifier(match[2])
  return dialect === 'mysql'
    ? `ALTER TABLE ${tableName} DROP FOREIGN KEY ${constraintName};`
    : `ALTER TABLE ${tableName} DROP CONSTRAINT IF EXISTS ${constraintName};`
}

function reverseAddConstraint(statement, dialect) {
  const match = statement.match(/^ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?([`"\w]+)\s+ADD\s+CONSTRAINT\s+([`"\w]+)/i)
  if (!match) return ''

  const tableName = cleanIdentifier(match[1])
  const constraintName = cleanIdentifier(match[2])
  return dialect === 'mysql'
    ? `ALTER TABLE ${tableName} DROP INDEX ${constraintName};`
    : `ALTER TABLE ${tableName} DROP CONSTRAINT IF EXISTS ${constraintName};`
}

function reverseColumnAlteration(statement) {
  const match = statement.match(/^ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?([`"\w]+)\s+(?:ALTER|MODIFY)\s+COLUMN\s+([`"\w]+)/i)
  if (!match) return ''

  return `-- Manual rollback required for column alteration on ${cleanIdentifier(match[1])}.${cleanIdentifier(match[2])}.`
}

function reverseDroppedColumn(statement) {
  const match = statement.match(/^ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?([`"\w]+)\s+DROP\s+(?:COLUMN\s+)?([`"\w]+)/i)
  if (!match) return ''

  return `-- Manual rollback required: source migration dropped ${cleanIdentifier(match[1])}.${cleanIdentifier(match[2])}.`
}

function dataRollbackComment(statement) {
  const insertMatch = statement.match(/^INSERT\s+INTO\s+([`"\w]+)/i)
  if (insertMatch) {
    return `-- Data rollback required for INSERT INTO ${cleanIdentifier(insertMatch[1])}; verify seed rows before deleting.`
  }

  const updateMatch = statement.match(/^UPDATE\s+([`"\w]+)/i)
  if (updateMatch) {
    return `-- Data rollback required for UPDATE ${cleanIdentifier(updateMatch[1])}; original values are not stored in migration history.`
  }

  const deleteMatch = statement.match(/^DELETE\s+FROM\s+([`"\w]+)/i)
  if (deleteMatch) {
    return `-- Data rollback required for DELETE FROM ${cleanIdentifier(deleteMatch[1])}; deleted rows are not stored in migration history.`
  }

  return ''
}

function manualRollbackComment(statement) {
  return `-- Manual rollback required for statement: ${statement.slice(0, 180)}`
}

function cleanIdentifier(identifier) {
  return String(identifier || '').replace(/^[`"]|[`"]$/g, '')
}

main()
