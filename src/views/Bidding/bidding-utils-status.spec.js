import { describe, expect, it } from 'vitest'
import {
  getTenderStatusBadgeClass,
  getTenderStatusTagType,
  getTenderStatusText,
  matchesTenderStatus,
  normalizeTenderCollection,
  normalizeTenderStatusCode,
  TENDER_STATUSES,
  toBackendTenderStatus,
} from './bidding-utils-status.js'

describe('bidding-utils-status', () => {
  it.each([
    ['new', TENDER_STATUSES.PENDING_ASSIGNMENT],
    ['contacted', TENDER_STATUSES.TRACKING],
    ['following', TENDER_STATUSES.TRACKING],
    ['quoting', TENDER_STATUSES.TRACKING],
    ['bidding', TENDER_STATUSES.BIDDING],
    ['abandoned', TENDER_STATUSES.ABANDONED],
    ['已投标', TENDER_STATUSES.BIDDING],
    ['TRACKING', TENDER_STATUSES.TRACKING],
  ])('normalizes %s to %s', (input, expected) => {
    expect(normalizeTenderStatusCode(input)).toBe(expected)
  })

  it('normalizes tender collections in one place', () => {
    expect(normalizeTenderCollection([
      { id: 1, status: 'new' },
      { id: 2, status: 'quoting' },
    ])).toEqual([
      { id: 1, status: 'PENDING_ASSIGNMENT' },
      { id: 2, status: 'TRACKING' },
    ])
  })

  it('matches canonical and legacy status filters', () => {
    expect(matchesTenderStatus('TRACKING', 'following')).toBe(true)
    expect(matchesTenderStatus('bidding', 'BIDDING')).toBe(true)
    expect(matchesTenderStatus('PENDING_ASSIGNMENT', 'ABANDONED')).toBe(false)
  })

  it('exposes display helpers from one source of truth', () => {
    expect(getTenderStatusText('quoting')).toBe('跟踪中')
    expect(getTenderStatusTagType('TRACKING')).toBe('primary')
    expect(getTenderStatusBadgeClass('bidding')).toBe('bidding')
  })

  it('re-exports backend status conversion through canonical mapping', () => {
    expect(toBackendTenderStatus('contacted')).toBe('TRACKING')
  })
})
