// Input: user store instance, mocked auth dependencies, simulated OSS/local user payloads
// Output: vitest specs verifying hasPermission getter behavior for OSS vs local users (specs/032)
// Pos: src/stores/__tests__/ - User store unit test layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock 外部依赖以隔离 user store 单元测试
vi.mock('@/api', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    getCurrentUser: vi.fn(),
    refreshToken: vi.fn(),
    loginByWeCom: vi.fn(),
    homeSso: vi.fn(),
  },
}))

vi.mock('@/api/authStoreBridge.js', () => ({
  registerAuthStoreBridge: vi.fn(),
}))

vi.mock('@/api/modules/auth.js', () => ({
  clearAuthState: vi.fn(),
  hasPersistentSession: vi.fn(() => false),
}))

vi.mock('@/api/modules/settings.js', () => ({
  persistRuntimeSettings: vi.fn(),
}))

vi.mock('@/api/session.js', () => ({
  getStoredUser: vi.fn(() => null),
  persistUserHint: vi.fn(),
}))

vi.mock('@/router/sessionNavigation.js', () => ({
  navigateToLogin: vi.fn(),
}))

vi.mock('./loginFailureMessage.js', () => ({
  resolveLoginFailureMessage: vi.fn(() => '登录失败'),
}))

vi.mock('@/utils/formatDisplayName.js', () => ({
  formatDisplayName: vi.fn((name) => name || ''),
}))

import { useUserStore } from '../user.js'

describe('useUserStore - hasPermission (specs/032 OSS 权限扩散修复)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('OSS 用户 menuPermissions 含 all 时不应短路放行（specs/032 US3）', () => {
    // 模拟 OSS admin 用户：后端已过滤 all，但前端必须防御性兜底
    // 即使后端漏过 all，前端 hasPermission 也不应短路
    const store = useUserStore()
    store.currentUser = {
      id: 1,
      username: '03063',
      roleCode: 'admin',
      role: 'admin',
      menuPermissions: ['all', 'dashboard'],
      isOssUser: true,
    }

    // OSS 用户即使携带 all，也不应短路放行
    // 期望：只有 dashboard 权限能通过，其他权限键被拒绝
    expect(store.hasPermission('dashboard')).toBe(true)
    expect(store.hasPermission('bidding.manage')).toBe(false)
    expect(store.hasPermission('system.admin')).toBe(false)
    expect(store.hasPermission('warehouse.manage')).toBe(false)
  })

  it('OSS 用户 menuPermissions 不含 all 时按精确匹配（specs/032 回归保护）', () => {
    const store = useUserStore()
    store.currentUser = {
      id: 1,
      username: '06234',
      roleCode: 'admin',
      role: 'admin',
      menuPermissions: ['dashboard', 'bidding.view'],
      isOssUser: true,
    }

    expect(store.hasPermission('dashboard')).toBe(true)
    expect(store.hasPermission('bidding.view')).toBe(true)
    expect(store.hasPermission('bidding.manage')).toBe(false)
  })

  it('本地 admin 用户 menuPermissions 含 all 时应短路放行（specs/032 回归）', () => {
    // 本地 admin 保留 all 短路逻辑 — 这是本地 admin 的预期行为
    const store = useUserStore()
    store.currentUser = {
      id: 999,
      username: 'admin',
      roleCode: 'admin',
      role: 'admin',
      menuPermissions: ['all'],
      isOssUser: false,
    }

    // 本地 admin 携带 all → 短路放行所有权限
    expect(store.hasPermission('dashboard')).toBe(true)
    expect(store.hasPermission('bidding.manage')).toBe(true)
    expect(store.hasPermission('system.admin')).toBe(true)
    expect(store.hasPermission('warehouse.manage')).toBe(true)
    expect(store.hasPermission('any.unknown.permission')).toBe(true)
  })

  it('本地 admin 用户 isOssUser 字段缺失时也短路放行（向后兼容）', () => {
    // 历史代码不传 isOssUser 字段，应视为本地用户（短路放行）
    const store = useUserStore()
    store.currentUser = {
      id: 999,
      username: 'admin',
      roleCode: 'admin',
      role: 'admin',
      menuPermissions: ['all'],
      // isOssUser 字段缺失
    }

    expect(store.hasPermission('dashboard')).toBe(true)
    expect(store.hasPermission('bidding.manage')).toBe(true)
    expect(store.hasPermission('any.unknown.permission')).toBe(true)
  })

  it('普通本地用户 menuPermissions 不含 all 时按精确匹配（回归）', () => {
    const store = useUserStore()
    store.currentUser = {
      id: 2,
      username: 'xiaowang',
      roleCode: 'bid-Team',
      role: 'manager',
      menuPermissions: ['dashboard', 'bidding.view'],
      isOssUser: false,
    }

    expect(store.hasPermission('dashboard')).toBe(true)
    expect(store.hasPermission('bidding.view')).toBe(true)
    expect(store.hasPermission('bidding.manage')).toBe(false)
  })

  it('currentUser 为空时 hasPermission 返回 false（边界保护）', () => {
    const store = useUserStore()
    store.currentUser = null

    expect(store.hasPermission('dashboard')).toBe(false)
    expect(store.hasPermission('all')).toBe(false)
  })
})
