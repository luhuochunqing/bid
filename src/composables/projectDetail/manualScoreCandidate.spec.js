import { describe, expect, it } from 'vitest'
import { isManualScoreCandidate } from './manualScoreCandidate.js'

describe('isManualScoreCandidate', () => {
  it('allows unverified scores when the current scheme is unambiguous', () => {
    expect(isManualScoreCandidate({ weight: 10, candidateEligible: false, scopeStatus: 'SOURCE_UNVERIFIED' }, null, 0)).toBe(true)
    expect(isManualScoreCandidate({ weight: 10, candidateEligible: false, scopeStatus: 'SOURCE_UNVERIFIED' }, 'a', 2)).toBe(true)
  })
  it('does not automatically include scheme-external or manually removed rows', () => {
    expect(isManualScoreCandidate({ weight: 10, candidateEligible: false, scopeStatus: 'OUT_OF_SCHEME' }, null, 0)).toBe(false)
    expect(isManualScoreCandidate({ weight: 10, candidateEligible: true, manualExcluded: true }, null, 0)).toBe(false)
  })
  it('does not count an unselected alternative extraction proposal', () => {
    const fact = { weight: 10, candidateEligible: true, scopeStatus: 'IN_SCHEME', schemeId: 'a',
      evidences: [{ kind: 'ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED' }] }
    expect(isManualScoreCandidate(fact, 'a', 1)).toBe(false)
  })
  it('allows a reviewer to explicitly include an alternative proposal', () => {
    const fact = { weight: 10, candidateEligible: true, scopeStatus: 'IN_SCHEME', schemeId: 'a', manualIncluded: true,
      evidences: [{ kind: 'ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED' }] }
    expect(isManualScoreCandidate(fact, 'a', 1)).toBe(true)
  })
  it('requires a selected scheme and does not mix a different scheme', () => {
    const fact = { weight: 10, candidateEligible: true, schemeId: 'a' }
    expect(isManualScoreCandidate(fact, null, 2)).toBe(false)
    expect(isManualScoreCandidate(fact, 'b', 2)).toBe(false)
    expect(isManualScoreCandidate(fact, 'a', 2)).toBe(true)
  })
})

// A source/legacy eligibility flag is not evidence of a positive scoring maximum.
it('keeps zero and unknown maxima in audit until explicitly included for editing', () => {
  for (const weight of [0, null, undefined, '', ' ', 'bad', -1]) {
    const fact = { weight, candidateEligible: true, scopeStatus: 'IN_SCHEME' }
    expect(isManualScoreCandidate(fact, null, 0)).toBe(false)
  }
  expect(isManualScoreCandidate({ weight: 0, manualIncluded: true }, null, 0)).toBe(true)
  expect(isManualScoreCandidate({ weight: 5, dimension: '满足资格条件的业绩', candidateEligible: true }, null, 0)).toBe(true)
})

it('retains only source-verified zero deductions as automatic candidates', () => {
  const fact = { weight: 0, candidateEligible: true, scopeStatus: 'IN_SCHEME', sourceText: '每发生一次扣2分',
    evidences: [{ kind: 'SOURCE_VERIFIED_ZERO_DEDUCTION', quotedText: '每发生一次扣2分' }] }
  expect(isManualScoreCandidate(fact, null, 0)).toBe(true)
  expect(isManualScoreCandidate({ ...fact, sourceText: '改写后的规则' }, null, 0)).toBe(false)
  expect(isManualScoreCandidate({ ...fact, candidateEligible: false }, null, 0)).toBe(false)
})
