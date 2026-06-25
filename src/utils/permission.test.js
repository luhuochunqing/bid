import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { hasAnyPermission, hasAllPermissions, isAdminRole, matchesCurrentUser, isTaskAssignee, isBidReviewer } from './permission'

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    currentUser: { id: 1 }
  })
}))

beforeEach(() => {
  setActivePinia(createPinia())
})

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

describe('hasAllPermissions', () => {
  it('returns true when user has every required permission', () => {
    expect(hasAllPermissions(
      ['knowledge', 'knowledge-qualification'],
      ['knowledge', 'knowledge-qualification']
    )).toBe(true)
  })

  it('does not infer parent permission from child permission', () => {
    expect(hasAllPermissions(
      ['knowledge-qualification'],
      ['knowledge', 'knowledge-qualification']
    )).toBe(false)
  })

  it('returns false when one required child permission is missing', () => {
    expect(hasAllPermissions(
      ['knowledge', 'knowledge-qualification'],
      ['knowledge', 'knowledge-case']
    )).toBe(false)
  })

  it('returns true when userPermissions includes "all"', () => {
    expect(hasAllPermissions(['all'], ['knowledge', 'knowledge-qualification'])).toBe(true)
  })

  it('returns true when requiredPermissions is empty', () => {
    expect(hasAllPermissions([], [])).toBe(true)
    expect(hasAllPermissions(undefined, undefined)).toBe(true)
  })
})

describe('isAdminRole', () => {
  it('returns true for admin', () => {
    expect(isAdminRole('admin')).toBe(true)
  })

  it('returns false for non-admin roles', () => {
    expect(isAdminRole('manager')).toBe(false)
    expect(isAdminRole('bid-Team')).toBe(false)
    expect(isAdminRole('/bidAdmin')).toBe(false)
    expect(isAdminRole('')).toBe(false)
    expect(isAdminRole(undefined)).toBe(false)
  })
})

describe('matchesCurrentUser', () => {
  it('returns true when the id matches the mocked global currentUser.id', () => {
    // Default mock: currentUser.id = 1
    expect(matchesCurrentUser(1)).toBe(true)
    expect(matchesCurrentUser('1')).toBe(true)
  })

  it('returns false when the id does not match', () => {
    expect(matchesCurrentUser(99)).toBe(false)
    expect(matchesCurrentUser('99')).toBe(false)
  })

  it('returns false for null/undefined id', () => {
    expect(matchesCurrentUser(null)).toBe(false)
    expect(matchesCurrentUser(undefined)).toBe(false)
  })
})

describe('isTaskAssignee', () => {
  it('returns true when task.assigneeId matches currentUser.id', () => {
    expect(isTaskAssignee({ assigneeId: 1 })).toBe(true)
    expect(isTaskAssignee({ assigneeId: '1' })).toBe(true)
  })

  it('returns false when task.assigneeId does not match', () => {
    expect(isTaskAssignee({ assigneeId: 99 })).toBe(false)
  })

  it('returns false when task.assigneeId is null/undefined', () => {
    expect(isTaskAssignee({ assigneeId: null })).toBe(false)
    expect(isTaskAssignee({ assigneeId: undefined })).toBe(false)
  })

  it('returns false when task is null/undefined', () => {
    expect(isTaskAssignee(null)).toBe(false)
    expect(isTaskAssignee(undefined)).toBe(false)
  })
})

describe('isBidReviewer', () => {
  it('returns true when item.reviewerId matches currentUser.id', () => {
    expect(isBidReviewer({ reviewerId: 1 })).toBe(true)
    expect(isBidReviewer({ reviewerId: '1' })).toBe(true)
  })

  it('returns false when item.reviewerId does not match', () => {
    expect(isBidReviewer({ reviewerId: 99 })).toBe(false)
  })

  it('returns false when item.reviewerId is null/undefined', () => {
    expect(isBidReviewer({ reviewerId: null })).toBe(false)
    expect(isBidReviewer({ reviewerId: undefined })).toBe(false)
  })

  it('returns false when item is null/undefined', () => {
    expect(isBidReviewer(null)).toBe(false)
    expect(isBidReviewer(undefined)).toBe(false)
  })
})
