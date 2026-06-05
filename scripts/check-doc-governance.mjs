// Input: repository filesystem, governed directory list, governed file patterns
// Output: documentation governance violations for README coverage and file headers
// Pos: scripts/ - Repository maintenance guardrail
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const repoRoot = process.cwd()
const readmeDeclaration = '一旦我所属的文件夹有所变化，请更新我。'
const fileMaintenanceLine = '一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。'

const governedDirectories = [
  '.',
  'docs',
  'scripts',
  'src/api',
  'src/api/modules',
  'src/stores',
  'src/views',
  'src/components',
  'src/router',
  'src/config',
  'src/styles',
  'src/utils',
  'backend',
  'src/views/Bidding',
  'src/views/Knowledge',
  'src/views/Resource',
  'src/views/Resource/BAR',
  'src/views/AI',
  'src/components/ai',
  'src/components/charts',
  'src/components/common',
  'src/components/layout',
]

const backendModuleDirectories = []
const backendModuleRoot = path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'xiyu', 'bid')
if (fs.existsSync(backendModuleRoot)) {
  for (const entry of fs.readdirSync(backendModuleRoot, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      backendModuleDirectories.push(path.relative(repoRoot, path.join(backendModuleRoot, entry.name)))
    }
  }
}

const governedFilePatterns = [
  /^src\/api\/index\.js$/,
  /^src\/api\/modules\/.+\.js$/,
  /^src\/stores\/.+\.js$/,
  /^scripts\/[^/]+\.mjs$/,
  /^scripts\/[^/]+\.sh$/,
  /^scripts\/release\/.+\.(mjs|sh)$/,
  /^backend\/src\/main\/java\/com\/xiyu\/bid\/(controller|service|config|auth|aspect|exception)\/.+\.java$/,
  /^backend\/src\/main\/java\/com\/xiyu\/bid\/.+\/(controller|service|config)\/.+\.java$/,
]

const violations = []

function isGovernedFile(relativePath) {
  return governedFilePatterns.some((pattern) => pattern.test(relativePath))
}

function readFileContent(filePath) {
  return fs.readFileSync(filePath, 'utf8')
}

function checkReadme(relativeDir, options = {}) {
  const { requireInventory = true } = options
  const readmePath = path.join(repoRoot, relativeDir, 'README.md')
  if (!fs.existsSync(readmePath)) {
    violations.push(`${relativeDir}/README.md missing`)
    return
  }

  const content = readFileContent(readmePath)
  const topLines = content.split('\n').slice(0, 5).join('\n')
  if (!topLines.includes(readmeDeclaration)) {
    violations.push(`${relativeDir}/README.md missing declaration`)
  }

  if (!requireInventory) {
    return
  }

  const hasFileTable = /\|\s*文件\s*\|[^\n]*功能\s*\|/.test(content)
  const hasStructureBlock = /##\s*(项目结构|目录结构)/.test(content) && /```/.test(content)
  if (!hasFileTable && !hasStructureBlock) {
    violations.push(`${relativeDir}/README.md missing file inventory table header`)
  }
}

function normalizeComment(line) {
  return line.replace(/^\s*(\/\/|#)\s?/, '').trim()
}

function checkGovernedFile(relativePath) {
  const filePath = path.join(repoRoot, relativePath)
  const content = readFileContent(filePath)
  const lines = content.split('\n').slice(0, 20)
  const commentLines = []
  const isJavaFile = relativePath.endsWith('.java')

  for (const line of lines) {
    if (line.startsWith('#!')) {
      continue
    }
    if (isJavaFile && /^package\s+.+;$/.test(line.trim())) {
      continue
    }
    if (/^\s*(\/\/|#)/.test(line)) {
      commentLines.push(normalizeComment(line))
      if (commentLines.length >= 4) {
        break
      }
      continue
    }
    if (line.trim() === '') {
      continue
    }
    break
  }

  const requiredPrefixes = ['Input:', 'Output:', 'Pos:']
  for (const prefix of requiredPrefixes) {
    if (!commentLines.some((line) => line.startsWith(prefix))) {
      violations.push(`${relativePath} missing header line "${prefix}"`)
    }
  }
  const hasMaintenanceLine =
    commentLines.includes(fileMaintenanceLine) ||
    commentLines.some((line) => line.startsWith('维护声明:'))
  if (!hasMaintenanceLine) {
    violations.push(`${relativePath} missing maintenance declaration`)
  }
}

function walk(dirPath) {
  for (const entry of fs.readdirSync(dirPath, { withFileTypes: true })) {
    const fullPath = path.join(dirPath, entry.name)
    if (entry.isDirectory()) {
      walk(fullPath)
      continue
    }
    const relativePath = path.relative(repoRoot, fullPath)
    if (isGovernedFile(relativePath)) {
      checkGovernedFile(relativePath)
    }
  }
}

for (const relativeDir of governedDirectories) {
  checkReadme(relativeDir)
}

for (const relativeDir of backendModuleDirectories) {
  checkReadme(relativeDir, { requireInventory: true })
}

for (const root of ['src', 'scripts']) {
  const fullRoot = path.join(repoRoot, root)
  if (fs.existsSync(fullRoot)) {
    walk(fullRoot)
  }
}

if (violations.length > 0) {
  console.error('Documentation governance check failed:')
  for (const violation of violations) {
    console.error(`- ${violation}`)
  }
  process.exit(1)
}

console.log(`Documentation governance check passed for ${governedDirectories.length + backendModuleDirectories.length} directories.`)
