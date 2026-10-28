import { describe, expect, it } from 'vitest'
import { isManualScoreCandidate } from './manualScoreCandidate.js'

describe('isManualScoreCandidate', () => {
  it('allows unverified scores when the current scheme is unambiguous', () => {
    expect(isManualScoreCandidate({ candidateEligible: false, scopeStatus: 'SOURCE_UNVERIFIED' }, null, 0)).toBe(true)
    expect(isManualScoreCandidate({ candidateEligible: false, scopeStatus: 'SOURCE_UNVERIFIED' }, 'a', 2)).toBe(true)
  })
  it('does not automatically include scheme-external or manually removed rows', () => {
    expect(isManualScoreCandidate({ candidateEligible: false, scopeStatus: 'OUT_OF_SCHEME' }, null, 0)).toBe(false)
    expect(isManualScoreCandidate({ candidateEligible: true, manualExcluded: true }, null, 0)).toBe(false)
  })
  it('does not count an unselected alternative extraction proposal', () => {
    const fact = { candidateEligible: true, scopeStatus: 'IN_SCHEME', schemeId: 'a',
      evidences: [{ kind: 'ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED' }] }
    expect(isManualScoreCandidate(fact, 'a', 1)).toBe(false)
  })
  it('allows a reviewer to explicitly include an alternative proposal', () => {
    const fact = { candidateEligible: true, scopeStatus: 'IN_SCHEME', schemeId: 'a', manualIncluded: true,
      evidences: [{ kind: 'ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED' }] }
    expect(isManualScoreCandidate(fact, 'a', 1)).toBe(true)
  })
  it('requires a selected scheme and does not mix a different scheme', () => {
    const fact = { candidateEligible: true, schemeId: 'a' }
    expect(isManualScoreCandidate(fact, null, 2)).toBe(false)
    expect(isManualScoreCandidate(fact, 'b', 2)).toBe(false)
    expect(isManualScoreCandidate(fact, 'a', 2)).toBe(true)
  })
})
