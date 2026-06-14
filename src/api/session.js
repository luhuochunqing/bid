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

// SECURITY (H13 fix): the access token is no longer persisted to localStorage by
// default. localStorage is reachable from any XSS payload and any browser
// extension, so storing a long-lived JWT there turned every stored XSS into a
// session-takeover. The new defaults:
//   remember=false (default) → sessionStorage only. Token evaporates when the
//     tab closes, mirroring the historical "session cookie" mental model.
//   remember=true           → localStorage. Higher XSS blast radius but better
//     UX (user stays logged in across browser restarts). Opt-in only.
// TODO: when the backend is upgraded to issue the access token via an
// HttpOnly; Secure; SameSite=Strict cookie, this whole helper can be reduced
// to a no-op for the token slot (refresh handling stays in cookies).
export const setAccessToken = (token, remember = false) => {
  accessToken = token || null
  // 持久化 token 到存储
  if (token) {
    // SECURITY: clean up any stale copies in BOTH storages before writing the
    // new one, so toggling remember on/off never leaves a "ghost" token in
    // the wrong store. See LOW-49 in the 2026-06-13 audit.
    getBrowserStorages().forEach((storage) => {
      if (storage.getItem(ACCESS_TOKEN_KEY) !== null) {
        storage.removeItem(ACCESS_TOKEN_KEY)
      }
    })
    const storage = remember ? window.localStorage : window.sessionStorage
    storage.setItem(ACCESS_TOKEN_KEY, token)
  }
  return accessToken
}

export const getAccessToken = () => accessToken

export const clearAccessToken = () => {
  accessToken = null
  // 同时清除存储中的 token
  getBrowserStorages().forEach((storage) => storage.removeItem(ACCESS_TOKEN_KEY))
}

export const bootstrapLegacyAccessToken = () => {
  if (accessToken) {
    return accessToken
  }

  const legacyToken = getBrowserStorages()
    .map((storage) => storage.getItem(ACCESS_TOKEN_KEY))
    .find(Boolean)

  if (!legacyToken) {
    return null
  }

  accessToken = legacyToken
  // 保留存储中的 token，不再删除
  return accessToken
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
