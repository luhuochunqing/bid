import { describe, it, expect } from 'vitest'
import { scoringQualityPresentation } from './scoringQualityPresentation.js'

describe('actual scoring quality presentation', () => {
  it('keeps absent legacy diagnostics unknown instead of claiming complete or zero failures', () => {
    const view = scoringQualityPresentation({ summary: { okCount: 1 } })
    expect(view.qualityStatus).toBe('UNKNOWN')
    expect(view.text).toContain('诊断未知')
    expect(view.text).toContain('覆盖未知')
    expect(view.text).not.toContain('失败0项')
    expect(view.success).toBe(false)
  })

  it('separates objective numeric scores, pending, subjective and technical failures', () => {
    const view = scoringQualityPresentation({ qualityStatus: 'PARTIAL', coverage: { coverageStatus: 'PARTIAL' },
      objectiveScored: 2, objectivePending: 1, subjectivePending: 1, failed: 1, diagnosticsKnown: true })
    expect(view.text).toContain('客观已评分2项、待确认1项、主观待评1项、失败1项')
    expect(view.text).toContain('部分覆盖')
    expect(view.success).toBe(false)
  })

  it('never presents COMPLETED plus NEEDS_REVIEW as successful scoring', () => {
    const view = scoringQualityPresentation({ qualityStatus: 'NEEDS_REVIEW', coverage: { coverageStatus: 'COMPLETE' },
      objectiveScored: 0, objectivePending: 3, subjectivePending: 1, failed: 0, diagnosticsKnown: true })
    expect(view.success).toBe(false)
    expect(view.text).toContain('待复核')
  })

  it('permits complete scores including zero and partial score without treating satisfaction as quality', () => {
    const view = scoringQualityPresentation({ qualityStatus: 'COMPLETE', coverage: { coverageStatus: 'COMPLETE' },
      objectiveScored: 2, objectivePending: 0, subjectivePending: 1, failed: 0, diagnosticsKnown: true,
      summary: { okCount: 0, dangerCount: 1, pendingCount: 2 } })
    expect(view.success).toBe(true)
    expect(view.text).toContain('客观已评分2项')
  })
})
