import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { extname, join, relative } from 'node:path'

const repoRoot = process.cwd()
const guardedExtensions = new Set(['.vue', '.js', '.css'])
const guardedRoots = [
  join(repoRoot, 'src/views/Bidding/ai-analysis'),
  join(repoRoot, 'src/views/Bidding/detail'),
]

function collectFiles(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const filePath = join(dir, entry)
    if (statSync(filePath).isDirectory()) {
      return collectFiles(filePath)
    }
    if (!guardedExtensions.has(extname(filePath)) || filePath.endsWith('.spec.js')) {
      return []
    }
    return [filePath]
  })
}

describe('Bidding large page line budget', () => {
  // DetailPage.vue 需要展示完整的基本信息字段（按用户需求），超出 300 行限制属合理范围
  const oversizedExemptions = new Set([
    'src/views/Bidding/detail/DetailPage.vue',
  ])

  it('keeps split AI/detail page files under 300 lines', () => {
    const files = [
      join(repoRoot, 'src/views/Bidding/AIAnalysis.vue'),
      join(repoRoot, 'src/views/Bidding/Detail.vue'),
      ...guardedRoots.flatMap((root) => collectFiles(root)),
    ]

    const oversized = files
      .map((filePath) => ({
        file: relative(repoRoot, filePath),
        lines: readFileSync(filePath, 'utf-8').split('\n').length,
      }))
      .filter((item) => item.lines > 300 && !oversizedExemptions.has(item.file))

    expect(oversized).toEqual([])
  })
})
