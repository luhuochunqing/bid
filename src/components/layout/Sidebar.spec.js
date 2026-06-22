import { describe, it, expect } from 'vitest'
import { hasAllPermissions } from '@/utils/permission'

describe('Sidebar permission filtering', () => {
  it('hasAllPermissions requires ALL keys for hierarchical permissionKeys', () => {
    const userPerms = ['dashboard', 'knowledge', 'knowledge-qualification']

    // archive 路由 permissionKeys: ['knowledge', 'knowledge-archive']
    expect(hasAllPermissions(userPerms, ['knowledge', 'knowledge-archive'])).toBe(false)
    // qualification 路由 permissionKeys: ['knowledge', 'knowledge-qualification']
    expect(hasAllPermissions(userPerms, ['knowledge', 'knowledge-qualification'])).toBe(true)
  })

  it('hasAllPermissions with all perm grants access to everything', () => {
    expect(hasAllPermissions(['all'], ['knowledge', 'knowledge-archive'])).toBe(true)
  })

  it('hasAllPermissions with empty required returns true', () => {
    expect(hasAllPermissions([], [])).toBe(true)
  })
})
