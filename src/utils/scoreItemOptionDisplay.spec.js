import { describe, expect, it } from 'vitest'
import {
  scoreItemMatchesQuery,
  scoreItemOptionTitle,
  scoreItemOptionTooltip
} from './scoreItemOptionDisplay.js'

const item = {
  id: 1,
  code: '3.1',
  dim: '技术评分',
  detail: '技术方案实施方案完整可行，包含架构设计、部署方案与应急预案等关键要素'
}

describe('scoreItemOptionTitle', () => {
  it('keeps only serial number and score item name', () => {
    expect(scoreItemOptionTitle(item)).toBe('3.1 技术评分')
  })

  it('falls back to dim when code is missing', () => {
    expect(scoreItemOptionTitle({ dim: '商务', detail: '很长的原文' })).toBe('商务')
  })

  it('falls back to code when dim is missing', () => {
    expect(scoreItemOptionTitle({ code: 'A1', detail: '很长的原文' })).toBe('A1')
  })

  it('never includes original bid text', () => {
    expect(scoreItemOptionTitle(item)).not.toContain(item.detail)
  })
})

describe('scoreItemOptionTooltip', () => {
  it('shows full scoring element original text only', () => {
    expect(scoreItemOptionTooltip(item)).toBe(item.detail)
  })

  it('does not prefix code or dim', () => {
    expect(scoreItemOptionTooltip(item)).not.toMatch(/^3\.1/)
    expect(scoreItemOptionTooltip(item)).not.toContain('技术评分')
  })

  it('returns empty string when detail is blank', () => {
    expect(scoreItemOptionTooltip({ code: 'A1', dim: 'x', detail: '  ' })).toBe('')
  })
})

describe('scoreItemMatchesQuery', () => {
  it('matches code, dim, and original detail', () => {
    expect(scoreItemMatchesQuery(item, '3.1')).toBe(true)
    expect(scoreItemMatchesQuery(item, '技术')).toBe(true)
    expect(scoreItemMatchesQuery(item, '应急预案')).toBe(true)
    expect(scoreItemMatchesQuery(item, '不存在')).toBe(false)
  })

  it('treats blank query as match-all', () => {
    expect(scoreItemMatchesQuery(item, '  ')).toBe(true)
  })
})
