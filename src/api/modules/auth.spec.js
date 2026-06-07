// Input: auth.js API module
// Output: unit tests for authApi request options
// Pos: src/api/modules/ - Auth API module tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../client.js', () => ({
  default: {
    post: vi.fn(),
  },
}))

vi.mock('./settings.js', () => ({
  persistRuntimeSettings: vi.fn(),
}))

vi.mock('../authNormalizer.js', () => ({
  normalizeAuthSessionResponse: vi.fn((response) => response),
  normalizeUser: vi.fn(() => ({ role: 'admin', menuPermissions: ['all'] })),
}))

vi.mock('../session.js', () => ({
  clearSessionState: vi.fn(),
  getStoredUser: vi.fn(),
  hasPersistentUserHint: vi.fn(),
  setAccessToken: vi.fn(),
}))

import httpClient from '../client.js'
import { authApi } from './auth.js'

describe('authApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('lets the login page own credential failure messages', async () => {
    httpClient.post.mockResolvedValue({
      success: true,
      data: {
        token: 'token',
        username: 'admin',
        role: 'admin',
      },
    })

    await authApi.login('admin', 'bad-password', false)

    expect(httpClient.post).toHaveBeenCalledWith(
      '/api/auth/login',
      { username: 'admin', password: 'bad-password', rememberMe: false },
      { skipAuthRefresh: true, skipGlobalErrorMessage: true }
    )
  })
})
