// Input: browser storage and access token lifecycle events
// Output: single-source auth session helpers for token memory and user hints
// Pos: src/api/ - Shared auth session state
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const ACCESS_TOKEN_KEY = 'token'
const LEGACY_REFRESH_TOKEN_KEY = 'refreshToken'
const USER_KEY = 'user'

let accessToken = null

const getBrowserStorages = () => {
  if (typeof window === 'undefined') {
    return []
  }

  return [window.localStorage, window.sessionStorage].filter(Boolean)
}

// SECURITY (H13 根治 2026-06-14): access token 改由后端 HttpOnly; Secure; SameSite=Lax cookie 投递,
// 前端 JS 不再持有/持久化 token (XSS 不可达). setAccessToken 降级为清理历史残留的 no-op,
// 保留导出签名避免调用方 import 报错.
export const setAccessToken = (_token, _remember = false) => {
  accessToken = null
  getBrowserStorages().forEach((storage) => storage.removeItem(ACCESS_TOKEN_KEY))
  return accessToken
}

// token 走 HttpOnly cookie, JS 永远拿不到. 始终返回 null.
export const getAccessToken = () => null

export const clearAccessToken = () => {
  accessToken = null
  // 同时清除存储中的 token
  getBrowserStorages().forEach((storage) => storage.removeItem(ACCESS_TOKEN_KEY))
}

// H13 根治 (2026-06-14): 不再从 storage 恢复 token (走 HttpOnly cookie). 仅清理历史残留.
export const bootstrapLegacyAccessToken = () => {
  getBrowserStorages().forEach((storage) => storage.removeItem(ACCESS_TOKEN_KEY))
  return null
}

export const getStoredUser = () => {
  const raw = getBrowserStorages()
    .map((storage) => storage.getItem(USER_KEY))
    .find(Boolean)

  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export const hasStoredUserHint = () => Boolean(getStoredUser())

export const hasPersistentUserHint = () => {
  if (typeof window === 'undefined') {
    return false
  }

  return Boolean(window.localStorage.getItem(USER_KEY))
}

export const persistUserHint = (user, remember = true) => {
  if (typeof window === 'undefined' || !user) {
    return
  }

  const storage = remember ? window.localStorage : window.sessionStorage
  const otherStorage = remember ? window.sessionStorage : window.localStorage

  otherStorage.removeItem(USER_KEY)
  storage.setItem(USER_KEY, JSON.stringify(user))
  // 不再删除 token，token 由 setAccessToken 单独管理
}

export const clearAuthHints = () => {
  getBrowserStorages().forEach((storage) => {
    storage.removeItem(USER_KEY)
    storage.removeItem(ACCESS_TOKEN_KEY)
    storage.removeItem(LEGACY_REFRESH_TOKEN_KEY)
  })
}

export const clearSessionState = () => {
  clearAccessToken()
  clearAuthHints()
}
