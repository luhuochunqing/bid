// Input: projectQualityApi normalizer fixtures
// Output: vitest coverage for canonical quality result mapping
// Pos: src/api/modules/ai/ - Frontend quality API tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it } from 'vitest'
import { normalizeQualityResult } from './quality.js'

describe('normalizeQualityResult', () => {
  it('keeps backend status as the canonical state', () => {
    expect(normalizeQualityResult({
      id: 7,
      status: 'EMPTY',
      empty: true,
      issues: [
        { id: 1, originalText: '原文', suggestionText: '建议', locationLabel: '摘要' },
      ],
    })).toMatchObject({
      id: 7,
      status: 'EMPTY',
      empty: true,
      errors: [
        {
          id: 1,
          original: '原文',
          suggestion: '建议',
          location: '摘要',
        },
      ],
    })
  })

  it('falls back to empty when backend returns no issues and no explicit status', () => {
    expect(normalizeQualityResult({
      empty: true,
    })).toMatchObject({
      status: 'EMPTY',
      empty: true,
      issues: [],
      errors: [],
      suggestions: [],
    })
  })
})
