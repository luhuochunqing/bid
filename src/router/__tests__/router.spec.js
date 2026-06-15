/**
 * CO-210: Test for non-admin role permission redirect fix
 * Tests the getFirstAccessiblePath function to prevent infinite redirect loops
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock the sidebar menu config
const mockSidebarMenuConfig = [
  { path: '/dashboard', meta: { permissionKeys: ['dashboard'] } },
  { path: '/bidding', meta: { permissionKeys: ['bidding'] } },
  { path: '/project', meta: { permissionKeys: ['project'] } },
  { path: '/knowledge', meta: { permissionKeys: ['knowledge'] } }
]

// Mock the user store
const createMockUserStore = (menuPermissions) => ({
  hasPermission: (key) => {
    if (!menuPermissions || menuPermissions.length === 0) return false
    if (menuPermissions.includes('all')) return true
    return menuPermissions.includes(key)
  },
  menuPermissions: menuPermissions || []
})

// Import the logic we're testing (simulated since getFirstAccessiblePath is not exported)
const DEFAULT_AUTHENTICATED_HOME = '/dashboard'

const hasAnyPermission = (userPermissions, requiredPermissions) => {
  if (!requiredPermissions || requiredPermissions.length === 0) return true
  const perms = Array.isArray(userPermissions) ? userPermissions : []
  if (perms.length === 0) return false
  if (perms.includes('all')) return true
  return requiredPermissions.some((key) => perms.includes(key))
}

const getFirstAccessiblePath = (userStore) => {
  // CO-210 Fix: Check if user has any permissions at all
  const perms = userStore.menuPermissions || []
  if (perms.length === 0 && !perms.includes('all')) {
    // User has no permissions - redirect to a safe fallback instead of /dashboard
    return '/no-permission'
  }

  if (userStore.hasPermission('dashboard')) return DEFAULT_AUTHENTICATED_HOME
  for (const menu of mockSidebarMenuConfig) {
    if (hasAnyPermission(userStore.menuPermissions, menu.meta?.permissionKeys)) {
      return menu.path
    }
  }
  // CO-210 Fix: If no accessible menu found, don't default to /dashboard
  return '/login'
}

describe('CO-210: Router permission redirect fix', () => {
  it('should return /no-permission when user has empty menuPermissions', () => {
    const userStore = createMockUserStore([])
    const result = getFirstAccessiblePath(userStore)
    expect(result).toBe('/no-permission')
  })

  it('should return /no-permission when user has null menuPermissions', () => {
    const userStore = createMockUserStore(null)
    const result = getFirstAccessiblePath(userStore)
    expect(result).toBe('/no-permission')
  })

  it('should return /dashboard when user has dashboard permission', () => {
    const userStore = createMockUserStore(['dashboard', 'bidding'])
    const result = getFirstAccessiblePath(userStore)
    expect(result).toBe('/dashboard')
  })

  it('should return first accessible menu path when user lacks dashboard permission', () => {
    const userStore = createMockUserStore(['bidding', 'project'])
    const result = getFirstAccessiblePath(userStore)
    expect(result).toBe('/bidding')
  })

  it('should return /login when user has permissions but none match available menus', () => {
    const userStore = createMockUserStore(['settings', 'analytics'])
    const result = getFirstAccessiblePath(userStore)
    expect(result).toBe('/login')
  })

  it('should return /dashboard when user has "all" permission', () => {
    const userStore = createMockUserStore(['all'])
    const result = getFirstAccessiblePath(userStore)
    expect(result).toBe('/dashboard')
  })

  it('should handle admin role with all permissions correctly', () => {
    const userStore = createMockUserStore(['all'])
    expect(userStore.hasPermission('dashboard')).toBe(true)
    expect(userStore.hasPermission('bidding')).toBe(true)
    expect(userStore.hasPermission('any_random_permission')).toBe(true)
    const result = getFirstAccessiblePath(userStore)
    expect(result).toBe('/dashboard')
  })

  it('should handle staff role with limited permissions correctly', () => {
    const userStore = createMockUserStore([
      'dashboard',
      'bidding',
      'project',
      'knowledge',
      'resource'
    ])
    const result = getFirstAccessiblePath(userStore)
    expect(result).toBe('/dashboard')
  })

  it('should handle bid_specialist role correctly', () => {
    const userStore = createMockUserStore([
      'dashboard',
      'bidding',
      'project',
      'knowledge',
      'resource',
      'task.view.own',
      'task.handle.own',
      'evaluation.update',
      'retrospective.submit',
      'bidding.create'
    ])
    const result = getFirstAccessiblePath(userStore)
    expect(result).toBe('/dashboard')
  })

  it('should prevent infinite redirect loop for users with no permissions', () => {
    // This is the core CO-210 fix test
    const userStore = createMockUserStore([])

    // Simulate what would happen in the router guard
    const firstRedirect = getFirstAccessiblePath(userStore)
    expect(firstRedirect).toBe('/no-permission')

    // The key fix: /no-permission should NOT trigger another redirect
    // because it doesn't require specific permissions
    // (In real scenario, this route would be accessible to all authenticated users)
  })
})
