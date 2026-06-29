import { describe, expect, it } from 'vitest'
import { resolveAccountActions } from './accountActions.js'

describe('resolveAccountActions', () => {
  it('returns edit/return/takeDown for manager', () => {
    expect(resolveAccountActions({ isManager: true, isBidTeam: false, isContactPerson: false, isApplicant: false }))
      .toEqual({ edit: true, return: true, takeDown: true })
  })

  it('returns edit/return/takeDown for bid-Team when they are the contact person', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: true, isContactPerson: true, isApplicant: false }))
      .toEqual({ edit: true, return: true, takeDown: true })
  })

  it('returns borrow for bid-Team when they are not the contact person', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: true, isContactPerson: false, isApplicant: false }))
      .toEqual({ borrow: true })
  })

  it('returns apply for applicant', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: false, isContactPerson: false, isApplicant: true }))
      .toEqual({ apply: true })
  })

  it('returns empty object for unknown roles', () => {
    expect(resolveAccountActions({ isManager: false, isBidTeam: false, isContactPerson: false, isApplicant: false }))
      .toEqual({})
  })
})
