import { describe, it, expect } from 'vitest'
import { hasAnyPermission, isAdminRole } from './permission'

describe('hasAnyPermission', () => {
  it('returns true when requiredPermissions is empty (no restriction needed)', () => {
    expect(hasAnyPermission(['dashboard'], [])).toBe(true)
    expect(hasAnyPermission([], [])).toBe(true)
  })

  it('returns false when userPermissions is empty (deny by default)', () => {
    // 空权限用户必须被拒绝，不能隐式放行
    expect(hasAnyPermission([], ['dashboard'])).toBe(false)
  })

  it('returns true when userPermissions includes "all"', () => {
    expect(hasAnyPermission(['all'], ['dashboard', 'settings'])).toBe(true)
  })

  it('returns true when user has at least one required permission', () => {
    expect(hasAnyPermission(['dashboard', 'bidding'], ['bidding'])).toBe(true)
    expect(hasAnyPermission(['dashboard', 'bidding'], ['bidding', 'settings'])).toBe(true)
  })

  it('returns false when user has none of the required permissions', () => {
    expect(hasAnyPermission(['dashboard'], ['bidding'])).toBe(false)
    expect(hasAnyPermission(['dashboard', 'project'], ['bidding', 'settings'])).toBe(false)
  })

  it('handles undefined/null inputs gracefully with deny-by-default', () => {
    // undefined userPermissions treated as empty → deny
    expect(hasAnyPermission(undefined, undefined)).toBe(true) // empty required → allowed
    expect(hasAnyPermission(undefined, ['dashboard'])).toBe(false) // no permissions → denied
    expect(hasAnyPermission(['all'], undefined)).toBe(true) // empty required → allowed
  })
})

describe('isAdminRole', () => {
  it('returns true for admin', () => {
    expect(isAdminRole('admin')).toBe(true)
  })

  it('returns false for non-admin roles', () => {
    expect(isAdminRole('manager')).toBe(false)
    expect(isAdminRole('staff')).toBe(false)
    expect(isAdminRole('bid_admin')).toBe(false)
    expect(isAdminRole('')).toBe(false)
    expect(isAdminRole(undefined)).toBe(false)
  })
})
