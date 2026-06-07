// Input: expense page shared helpers
// Output: bidding purchase fee remains mapped to backend-compatible OTHER category
// Pos: src/composables/ - expense shared helper tests

import { describe, expect, it } from 'vitest'
import { resolveExpenseCategory } from './expensePageShared.js'

describe('expensePageShared', () => {
  it('maps 标书购买费 to backend OTHER category while keeping legacy 标书费 compatible', () => {
    expect(resolveExpenseCategory('标书购买费')).toBe('OTHER')
    expect(resolveExpenseCategory('标书费')).toBe('OTHER')
  })
})
