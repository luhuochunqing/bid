import { describe, it, expect } from 'vitest'
import { normalizeUser } from './authNormalizer'

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
})
