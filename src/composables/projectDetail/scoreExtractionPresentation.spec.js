import { describe, expect, it } from 'vitest'
import { extractionReason, extractionTaskLabel, scoreNumber } from './scoreExtractionPresentation.js'

describe('score extraction presentation', () => {
  it('renders pending and numeric scores consistently', () => {
    expect(scoreNumber(null)).toBe('待确认')
    expect(scoreNumber(28)).toBe('28')
  })

  it.each(['constructor', 'toString', '__proto__', 'constructor:detail', 'toString:detail', '__proto__:detail'])('keeps unknown diagnostic %s as a safe fallback label', code => {
    expect(extractionReason(code)).toBe('该项来源或规则仍有待核对的问题，请结合原文检查')
  })

  it('preserves known diagnostic labels with evidence suffixes', () => {
    expect(extractionReason('SOURCE_SCORE_ROW_UNCOVERED:page=3')).toBe('原文评分表仍有未完整覆盖的评分行，请对照招标文件复核。')
  })

  it('maps known and unknown extraction states to safe labels', () => {
    expect(extractionTaskLabel('COMPLETED')).toBe('提取完成')
    expect(extractionTaskLabel('UNKNOWN')).toBe('状态待确认')
    expect(extractionReason('FULL_RULE_UNIT_UNRESOLVED')).toBe('本项完整原文边界待核对')
    expect(extractionReason('UNKNOWN')).toContain('待核对')
  })

  it('distinguishes OCR credentials, table permission, and temporary failures', () => {
    expect(extractionReason('BAIDU_OCR_PAGE_FAILED:24:HTTP_401')).toContain('凭证')
    expect(extractionReason('BAIDU_OCR_PAGE_FAILED:24:PROVIDER_ERROR_6')).toContain('表格文字识别 V2')
    expect(extractionReason('BAIDU_OCR_PAGE_FAILED:24:PROVIDER_ERROR_6')).toContain('权限')
    expect(extractionReason('BAIDU_OCR_PAGE_FAILED:24:IOException')).toContain('第 24 页')
  })
})
