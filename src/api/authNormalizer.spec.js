import { describe, it, expect } from 'vitest'
import { normalizeUser } from './authNormalizer'

describe('normalizeUser roleCode', () => {
  it('preserves OSS role code with leading slash (/bidAdmin)', () => {
    const user = normalizeUser({ roleCode: '/bidAdmin' })
    expect(user.roleCode).toBe('/bidAdmin')
    expect(user.role).toBe('/bidAdmin')
  })

  it('preserves OSS role code case (bid-TeamLeader)', () => {
    const user = normalizeUser({ roleCode: 'bid-TeamLeader' })
    expect(user.roleCode).toBe('bid-TeamLeader')
  })

  it('preserves OSS role code case (bid-Team)', () => {
    const user = normalizeUser({ roleCode: 'bid-Team' })
    expect(user.roleCode).toBe('bid-Team')
  })

  it('falls back to role field when roleCode is missing', () => {
    const user = normalizeUser({ role: 'admin' })
    expect(user.roleCode).toBe('admin')
  })

  it('returns empty string when both roleCode and role are missing', () => {
    const user = normalizeUser({})
    expect(user.roleCode).toBe('')
  })
})

describe('normalizeUser menuPermissions', () => {
  it('adds knowledge parent permission when knowledge qualification child exists', () => {
    const user = normalizeUser({ menuPermissions: ['dashboard', 'knowledge-qualification'] })

    expect(user.menuPermissions).toContain('dashboard')
    expect(user.menuPermissions).toContain('knowledge-qualification')
    expect(user.menuPermissions).toContain('knowledge')
  })

  it('does not add knowledge parent permission for unrelated permissions', () => {
    const user = normalizeUser({ menuPermissions: ['dashboard'] })

    expect(user.menuPermissions).toEqual(['dashboard'])
  })

  it('adds knowledge parent permission for other knowledge child permissions', () => {
    const user = normalizeUser({ menuPermissions: ['knowledge-case'] })

    expect(user.menuPermissions).toContain('knowledge-case')
    expect(user.menuPermissions).toContain('knowledge')
  })

  it('preserves all permission without adding knowledge parent permission', () => {
    const user = normalizeUser({ menuPermissions: ['all'] })

    expect(user.menuPermissions).toEqual(['all'])
  })

  // CO-393 修复：OSS 同步用户只返回子权限码（resource-account/resource-ca/resource-margin），
  // 路由守卫要求 ['resource', 'resource-account'] 父子权限同时存在（AND 逻辑），
  // 因此对 resource-* 子权限需自动补齐 resource 父权限（与 knowledge-* 逻辑对称）。
  it('adds resource parent permission when resource-account child exists', () => {
    const user = normalizeUser({ menuPermissions: ['dashboard', 'resource-account'] })

    expect(user.menuPermissions).toContain('dashboard')
    expect(user.menuPermissions).toContain('resource-account')
    expect(user.menuPermissions).toContain('resource')
  })

  it('adds resource parent permission when resource-ca child exists', () => {
    const user = normalizeUser({ menuPermissions: ['resource-ca'] })

    expect(user.menuPermissions).toContain('resource-ca')
    expect(user.menuPermissions).toContain('resource')
  })

  it('adds resource parent permission when resource-margin child exists', () => {
    const user = normalizeUser({ menuPermissions: ['resource-margin'] })

    expect(user.menuPermissions).toContain('resource-margin')
    expect(user.menuPermissions).toContain('resource')
  })

  it('does not add resource parent permission for unrelated permissions', () => {
    const user = normalizeUser({ menuPermissions: ['dashboard'] })

    expect(user.menuPermissions).toEqual(['dashboard'])
  })

  it('does not duplicate resource parent permission when already present', () => {
    const user = normalizeUser({ menuPermissions: ['resource', 'resource-account'] })

    expect(user.menuPermissions.filter((p) => p === 'resource')).toHaveLength(1)
  })

  it('preserves all permission without adding resource parent permission', () => {
    const user = normalizeUser({ menuPermissions: ['all'] })

    expect(user.menuPermissions).toEqual(['all'])
  })
})
