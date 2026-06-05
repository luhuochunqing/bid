// Input: repository source tree, guarded roots, forbidden dependency patterns
// Output: violations when frontend business layers cross mock-data boundaries
// Pos: scripts/ - Frontend architecture guardrail
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const repoRoot = process.cwd()
const sourceRoot = path.join(repoRoot, 'src')

const guardedRoots = ['views', 'components', 'stores']
const extensions = new Set(['.js', '.ts', '.vue'])

const violations = []

function walk(dirPath) {
  for (const entry of fs.readdirSync(dirPath, { withFileTypes: true })) {
    const fullPath = path.join(dirPath, entry.name)
    if (entry.isDirectory()) {
      walk(fullPath)
      continue
    }
    if (extensions.has(path.extname(entry.name))) {
      inspectFile(fullPath)
    }
  }
}

function inspectFile(filePath) {
  const relativePath = path.relative(sourceRoot, filePath)
  const topLevel = relativePath.split(path.sep)[0]
  if (!guardedRoots.includes(topLevel)) {
    return
  }

  const content = fs.readFileSync(filePath, 'utf8')
  const checks = [
    {
      pattern: /from\s+['"]@\/api\/mock['"]/g,
      message: 'must not import @/api/mock directly',
    },
    {
      pattern: /from\s+['"]@\/utils\/demoPersistence['"]/g,
      message: 'must not import demoPersistence directly',
    },
    {
      pattern: /import\s*\{[^}]*mockData[^}]*\}\s*from\s*['"]@\/api['"]/g,
      message: 'must not import mockData from @/api',
    },
    {
      pattern: /\b(?:const|let|var)\s+mockData\s*=/g,
      message: 'must not define local mockData fallback',
    },
  ]

  for (const check of checks) {
    let match
    while ((match = check.pattern.exec(content)) !== null) {
      const line = content.slice(0, match.index).split('\n').length
      violations.push(`${relativePath}:${line} ${check.message}`)
    }
  }
}

for (const root of guardedRoots) {
  walk(path.join(sourceRoot, root))
}

const apiIndexPath = path.join(sourceRoot, 'api', 'index.js')
const apiIndexContent = fs.readFileSync(apiIndexPath, 'utf8')
if (/export\s*\{\s*mockData\b/.test(apiIndexContent)) {
  violations.push('api/index.js must not re-export mockData')
}

if (violations.length > 0) {
  console.error('Front data boundary check failed:')
  for (const violation of violations) {
    console.error(`- ${violation}`)
  }
  process.exit(1)
}

console.log('Front data boundary check passed.')
