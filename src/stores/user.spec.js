// Input: user.js Pinia store
// Output: unit tests for useUserStore getters
// Pos: src/stores/ - State management test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock dependencies to avoid loading real modules during store bootstrap
vi.mock('@/api', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    refreshToken: vi.fn(),
    getCurrentUser: vi.fn(),
    loginByWeCom: vi.fn()
  }
}))
vi.mock('@/api/authStoreBridge.js', () => ({
  registerAuthStoreBridge: vi.fn()
}))
vi.mock('@/api/modules/auth.js', () => ({
  clearAuthState: vi.fn(),
  hasPersistentSession: vi.fn()
}))
vi.mock('@/api/modules/settings.js', () => ({
  persistRuntimeSettings: vi.fn()
}))
vi.mock('@/api/session.js', () => ({
  bootstrapLegacyAccessToken: vi.fn(() => 'mock-token'),
  getStoredUser: vi.fn(() => null),
  persistUserHint: vi.fn()
}))
vi.mock('@/router/sessionNavigation.js', () => ({
  navigateToLogin: vi.fn()
}))

import { useUserStore } from './user.js'

describe('useUserStore getters', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('correctly determines isBidAdmin', () => {
    const store = useUserStore()

    store.currentUser = { role: '/bidAdmin' }
    expect(store.isBidAdmin).toBe(true)

    store.currentUser = { roleCode: 'admin' }
    expect(store.isBidAdmin).toBe(true)

    store.currentUser = { role: 'bid-TeamLeader' }
    expect(store.isBidAdmin).toBe(false)
  })

  it('correctly determines isBidLead', () => {
    const store = useUserStore()

    store.currentUser = { role: 'bid-TeamLeader' }
    expect(store.isBidLead).toBe(true)

    store.currentUser = { role: '/bidAdmin' }
    expect(store.isBidLead).toBe(false)
  })

  it('correctly determines isBidSenior', () => {
    const store = useUserStore()

    // bid_senior 已删除，映射到 bid-TeamLeader
    store.currentUser = { role: 'bid-TeamLeader' }
    expect(store.isBidSenior).toBe(true)

    store.currentUser = { role: '/bidAdmin' }
    expect(store.isBidSenior).toBe(false)
  })

  it('correctly determines isBidManager', () => {
    const store = useUserStore()

    // bid_senior 已删除，映射到 bid-TeamLeader
    store.currentUser = { role: 'bid-TeamLeader' }
    expect(store.isBidManager).toBe(true)

    store.currentUser = { role: 'bid-TeamLeader' }
    expect(store.isBidManager).toBe(true)

    store.currentUser = { role: '/bidAdmin' }
    expect(store.isBidManager).toBe(true)

    store.currentUser = { roleCode: 'admin' }
    expect(store.isBidManager).toBe(true)

    store.currentUser = { role: 'bid-projectLeader' }
    expect(store.isBidManager).toBe(false)
  })
})
