// Input: src/api/session.js — token storage helpers (H13 HttpOnly cookie 根治后)
// Output: unit tests verifying token is NOT persisted to JS storage (XSS-unreachable)
// Pos: src/api/__tests__/ — session lifecycle regression

import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  clearSessionState,
  getAccessToken,
  setAccessToken
} from '../session.js'

const TOKEN_KEY = 'token'

describe('session.js — H13 HttpOnly cookie 根治', () => {
  beforeEach(() => {
    if (typeof window !== 'undefined') {
      window.localStorage.clear()
      window.sessionStorage.clear()
    }
    clearSessionState()
  })

  afterEach(() => {
    clearSessionState()
  })

  it('setAccessToken(remember=true) 不再持久化 token 到 localStorage (走 HttpOnly cookie)', () => {
    setAccessToken('token-local-1', true)

    expect(window.localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(window.sessionStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('setAccessToken(remember=false) 不再持久化到 sessionStorage', () => {
    setAccessToken('token-session-1', false)

    expect(window.sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(window.localStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('getAccessToken 始终返回 null (token 走 HttpOnly cookie, JS 不可达)', () => {
    setAccessToken('token-mem-1', true)

    expect(getAccessToken()).toBeNull()
  })

  it('setAccessToken 清理历史 storage 残留 token (迁移期友好)', () => {
    window.localStorage.setItem(TOKEN_KEY, 'legacy-token')
    window.sessionStorage.setItem(TOKEN_KEY, 'legacy-token')

    setAccessToken('token-new', true)

    expect(window.localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(window.sessionStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('clearSessionState 清理 storage 残留', () => {
    window.localStorage.setItem(TOKEN_KEY, 'stale')
    window.sessionStorage.setItem(TOKEN_KEY, 'stale')

    clearSessionState()

    expect(window.localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(window.sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(getAccessToken()).toBeNull()
  })
})
