import { describe, expect, it } from 'vitest'
import { resolveAccountActions, isCurrentUserContactPerson } from './accountActions.js'

describe('resolveAccountActions', () => {
  it('returns edit/return/takeDown for manager when account is in use', () => {
    expect(resolveAccountActions({ isManager: true, isBidTeam: false, isContactPerson: false, isApplicant: false, status: 'IN_USE' }))
      .toEqual({ edit: true, return: true, takeDown: true })
  })

  it('hides return for manager when account is not in use', () => {
    expect(resolveAccountActions({ isManager: true, isBidTeam: false, isContactPerson: false, isApplicant: false, status: 'AVAILABLE' }))
      .toEqual({ edit: true, return: false, takeDown: true })
  })

  it('returns edit/return/takeDown for bid-Team when they are the contact person and account is in use', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: true, isContactPerson: true, isApplicant: false, status: 'IN_USE' }))
      .toEqual({ edit: true, return: true, takeDown: true })
  })

  it('hides return for bid-Team contact person when account is not in use', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: true, isContactPerson: true, isApplicant: false, status: 'AVAILABLE' }))
      .toEqual({ edit: true, return: false, takeDown: true })
  })

  it('returns borrow for bid-Team when they are not the contact person', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: true, isContactPerson: false, isApplicant: false, status: 'AVAILABLE' }))
      .toEqual({ borrow: true })
  })

  it('returns apply for applicant', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: false, isContactPerson: false, isApplicant: true, status: 'AVAILABLE' }))
      .toEqual({ apply: true })
  })

  it('returns empty object for unknown roles', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: false, isContactPerson: false, isApplicant: false, status: 'AVAILABLE' }))
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
