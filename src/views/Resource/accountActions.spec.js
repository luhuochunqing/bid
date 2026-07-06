import { describe, expect, it } from 'vitest'
import { resolveAccountActions, isCurrentUserContactPerson, canRevealPassword, isBorrowerWithinWindow } from './accountActions.js'

describe('resolveAccountActions', () => {
  it('returns edit/takeDown for manager (return收敛到我的审批Tab)', () => {
    expect(resolveAccountActions({ isManager: true, isBidTeam: false, isContactPerson: false, isApplicant: false }))
      .toEqual({ edit: true, return: false, takeDown: true })
  })

  it('returns edit/takeDown for bid-Team contact person (return收敛到我的审批Tab)', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: true, isContactPerson: true, isApplicant: false }))
      .toEqual({ edit: true, return: false, takeDown: true })
  })

  it('returns borrow for bid-Team when they are not the contact person', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: true, isContactPerson: false, isApplicant: false }))
      .toEqual({ borrow: true })
  })

  it('returns apply for applicant', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: false, isContactPerson: false, isApplicant: true, status: 'AVAILABLE' }))
      .toEqual({ apply: true })
  })

  it('returns empty object for unknown roles', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: false, isContactPerson: false, isApplicant: false }))
      .toEqual({})
  })
})

describe('isCurrentUserContactPerson', () => {
  it('matches by user id (CO-390口径)', () => {
    expect(isCurrentUserContactPerson({ contactPerson: 42 }, { id: 42, name: '张三' })).toBe(true)
    expect(isCurrentUserContactPerson({ contactPerson: '42' }, { id: 42, name: '张三' })).toBe(true)
  })

  it('returns false when id does not match', () => {
    expect(isCurrentUserContactPerson({ contactPerson: 99 }, { id: 42, name: '张三' })).toBe(false)
  })

  it('returns false for missing data', () => {
    expect(isCurrentUserContactPerson({}, { id: 1, name: '张三' })).toBe(false)
    expect(isCurrentUserContactPerson({ contactPerson: 1 }, null)).toBe(false)
  })
})

describe('canRevealPassword', () => {
  it('returns true for manager regardless of contact person status', () => {
    expect(canRevealPassword({ isManager: true, isBidTeam: false, isContactPerson: false })).toBe(true)
    expect(canRevealPassword({ isManager: true, isBidTeam: true, isContactPerson: false })).toBe(true)
  })

  it('returns true for bid-Team when they are the contact person', () => {
    expect(canRevealPassword({ isManager: false, isBidTeam: true, isContactPerson: true })).toBe(true)
  })

  it('returns false for bid-Team when they are NOT the contact person', () => {
    expect(canRevealPassword({ isManager: false, isBidTeam: true, isContactPerson: false })).toBe(false)
  })

  it('returns false for non-manager non-bid-Team roles', () => {
    expect(canRevealPassword({ isManager: false, isBidTeam: false, isContactPerson: true })).toBe(false)
    expect(canRevealPassword({ isManager: false, isBidTeam: false, isContactPerson: false })).toBe(false)
  })

  it('CO-524: returns true when isBorrowerWithinWindow is true, even for non-manager non-bid-Team', () => {
    expect(canRevealPassword({ isManager: false, isBidTeam: false, isContactPerson: false, isBorrowerWithinWindow: true })).toBe(true)
  })

  it('CO-524: returns false when isBorrowerWithinWindow is false and other conditions all false', () => {
    expect(canRevealPassword({ isManager: false, isBidTeam: false, isContactPerson: false, isBorrowerWithinWindow: false })).toBe(false)
  })
})

describe('isBorrowerWithinWindow (CO-524)', () => {
  const futureDate = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString()
  const pastDate = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()

  it('returns true when borrower within window (status=IN_USE, borrowedBy==user.id, dueAt future)', () => {
    expect(isBorrowerWithinWindow({ status: 'IN_USE', borrowedBy: 42, dueAt: futureDate }, { id: 42 })).toBe(true)
    expect(isBorrowerWithinWindow({ status: 'IN_USE', borrowedBy: '42', dueAt: futureDate }, { id: 42 })).toBe(true)
  })

  it('returns false when dueAt is in the past (expired, not returned)', () => {
    expect(isBorrowerWithinWindow({ status: 'IN_USE', borrowedBy: 42, dueAt: pastDate }, { id: 42 })).toBe(false)
  })

  it('returns false when status is not IN_USE', () => {
    expect(isBorrowerWithinWindow({ status: 'AVAILABLE', borrowedBy: 42, dueAt: futureDate }, { id: 42 })).toBe(false)
    expect(isBorrowerWithinWindow({ status: 'PENDING_APPROVAL', borrowedBy: 42, dueAt: futureDate }, { id: 42 })).toBe(false)
  })

  it('returns false when borrowedBy does not match user.id', () => {
    expect(isBorrowerWithinWindow({ status: 'IN_USE', borrowedBy: 99, dueAt: futureDate }, { id: 42 })).toBe(false)
  })

  it('returns false when dueAt is missing', () => {
    expect(isBorrowerWithinWindow({ status: 'IN_USE', borrowedBy: 42, dueAt: null }, { id: 42 })).toBe(false)
    expect(isBorrowerWithinWindow({ status: 'IN_USE', borrowedBy: 42 }, { id: 42 })).toBe(false)
  })

  it('returns false when borrowedBy is missing (already returned)', () => {
    expect(isBorrowerWithinWindow({ status: 'AVAILABLE', borrowedBy: null, dueAt: null }, { id: 42 })).toBe(false)
  })

  it('returns false for missing row or user', () => {
    expect(isBorrowerWithinWindow(null, { id: 42 })).toBe(false)
    expect(isBorrowerWithinWindow({ status: 'IN_USE', borrowedBy: 42, dueAt: futureDate }, null)).toBe(false)
  })
})
