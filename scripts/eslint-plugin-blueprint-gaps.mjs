// Input: source files (via ESLint context) and docs/plans/ directory
// Output: ESLint lint violations for blueprint-driven development rules
// Pos: scripts/ - ESLint plugin for blueprint gap gates
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md.
//
// Rules exported:
//   blueprint-gaps/require-gap-table   — BLOCKER if blueprint-touched source has no gap table
//   blueprint-gaps/no-fake-success     — WARN if ElMessage.success() called without API call
//
// Usage: add to eslint.config.js → plugins + rules sections

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const BLUEPRINT_PATTERNS = [
  /4\.2\.\d+/,
  /4\.3\.\d+/,
  /4\.1\.\d+/,
]

function isBlueprintFile(filename) {
  return BLUEPRINT_PATTERNS.some(p => p.test(filename))
}

function findGapTable(repoRoot, filename) {
  const plansDir = path.join(repoRoot, 'docs', 'plans')
  if (!fs.existsSync(plansDir)) return null

  const match = filename.match(/4\.\d+\.\d+/)
  if (!match) return null

  const pattern = new RegExp(match[0].replace(/\./g, '\\.') + '.*plan\\.md', 'i')
  const files = fs.readdirSync(plansDir).filter(f => f.endsWith('.md'))
  const matched = files.filter(f => pattern.test(f))

  if (matched.length > 0) {
    const planPath = path.join(plansDir, matched[0])
    if (fs.existsSync(planPath)) {
      const content = fs.readFileSync(planPath, 'utf8')
      const hasGapSection = content.includes('## 1.') && content.includes('## 5.')
      return { path: planPath, valid: hasGapSection }
    }
  }
  return null
}

function hasApiCall(node, sourceCode) {
  const apiPatterns = [
    /await\s+/,
    /\.then\s*\(/,
    /\.catch\s*\(/,
    /axios\./,
    /tendersApi\./,
    /\$api\./,
    /fetch\s*\(/,
  ]

  const end = node.range?.[1] || 0
  const before = sourceCode.slice(Math.max(0, end - 300), end)
  const after = sourceCode.slice(end, end + 300)

  for (const pattern of apiPatterns) {
    if (pattern.test(before) || pattern.test(after)) {
      return true
    }
  }
  return false
}

export const rules = {
  'require-gap-table': {
    meta: {
      type: 'problem',
      docs: { description: 'Require a gap table for blueprint-touched source files' },
      messages: {
        missing: 'Blueprint-touched file "{{filename}}" has no gap table. Create docs/plans/*{{section}}*-plan.md with gap analysis.',
        invalid: 'Gap table for "{{filename}}" is incomplete. Must contain ## 1 (gap table), ## 5 (TODO), ## 6 (acceptance).',
      },
    },
    create(context) {
      const filename = context.filename || ''
      if (!isBlueprintFile(filename)) return {}

      const repoRoot = process.cwd()
      const result = findGapTable(repoRoot, filename)

      if (!result) {
        const section = filename.match(/4\.\d+\.\d+/)?.[0] || '?'
        context.report({
          loc: { line: 1, column: 0 },
          messageId: 'missing',
          data: { filename, section },
        })
      } else if (!result.valid) {
        context.report({
          loc: { line: 1, column: 0 },
          messageId: 'invalid',
          data: { filename },
        })
      }

      return {}
    },
  },

  'no-fake-success': {
    meta: {
      type: 'suggestion',
      docs: { description: 'Disallow ElMessage.success() without an API call' },
      messages: {
        fakeSuccess: 'Fake success: ElMessage.success() called without any API call. Either implement the API or remove the success message — users will be misled.',
      },
    },
    create(context) {
      return {
        CallExpression(node) {
          const callee = node.callee
          const isSuccess =
            (callee.type === 'MemberExpression' &&
              callee.object.name === 'ElMessage' &&
              callee.property.name === 'success') ||
            (callee.type === 'Identifier' && callee.name === 'ElMessage' &&
              node.arguments.length > 0 &&
              node.arguments[0]?.type === 'ObjectExpression' &&
              node.arguments[0].properties.some(p =>
                p.key.name === 'type' && p.value.value === 'success'
              ))

          if (!isSuccess) return

          const sourceCode = context.sourceCode?.getText?.() ||
            context.getSourceCode?.()?.text || ''

          if (!hasApiCall(node, sourceCode)) {
            context.report({
              node,
              messageId: 'fakeSuccess',
            })
          }
        },
      }
    },
  },
}
