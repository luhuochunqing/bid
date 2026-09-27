import { describe, expect, it } from 'vitest'
import { extractionReason, extractionTaskLabel, scoreNumber } from './scoreExtractionPresentation.js'

describe('score extraction presentation', () => {
  it('renders pending and numeric scores consistently', () => {
    expect(scoreNumber(null)).toBe('待确认')
    expect(scoreNumber(28)).toBe('28')
  })

  it('maps known and unknown extraction states to safe labels', () => {
    expect(extractionTaskLabel('COMPLETED')).toBe('提取完成')
    expect(extractionTaskLabel('UNKNOWN')).toBe('状态待确认')
    expect(extractionReason('FULL_RULE_UNIT_UNRESOLVED')).toBe('本项完整原文边界待核对')
    expect(extractionReason('UNKNOWN')).toContain('待核对')
  })
})
