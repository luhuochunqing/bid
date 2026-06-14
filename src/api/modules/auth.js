// Input: httpClient, auth response normalizers, and runtime settings persistence
// Output: authApi - authentication and current-user accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 认证模块 API
 * 真实 API 为唯一数据源
 */
import httpClient from '../client.js'
import { persistRuntimeSettings } from './settings.js'
import { normalizeAuthSessionResponse, normalizeUser } from '../authNormalizer.js'
import {
  clearSessionState,
  getStoredUser,
  hasPersistentUserHint
} from '../session.js'

export const getSavedUser = () => getStoredUser()

export const hasPersistentSession = () => hasPersistentUserHint()

export const clearAuthState = () => {
  clearSessionState()
}

export const authApi = {
  async login(username, password, rememberMe = true) {
    const response = await httpClient.post('/api/auth/login', { username, password, rememberMe }, {
      skipAuthRefresh: true,
      skipGlobalErrorMessage: true
    })
    const authPayload = response?.data
    const normalizedUser = normalizeUser(authPayload)

    // H13 根治 (2026-06-14): access token 走 HttpOnly cookie, 前端不持有/不持久化
    persistRuntimeSettings({
      roles: [{
        code: normalizedUser.role,
        menuPermissions: normalizedUser.menuPermissions
      }]
    })

    return normalizeAuthSessionResponse(response)
  },

  async logout() {
    return httpClient.post('/api/auth/logout', null, {
      skipAuthRefresh: true,
      silentAuthError: true
    })
  },

  async getCurrentUser(options = {}) {
    const response = await httpClient.get('/api/auth/me', {
      silentAuthError: Boolean(options?.silentAuthError)
    })
    const authPayload = response?.data
    const normalizedUser = normalizeUser(authPayload)

    persistRuntimeSettings({
      roles: [{
        code: normalizedUser.role,
        menuPermissions: normalizedUser.menuPermissions
      }]
    })

    return {
      ...response,
      data: normalizedUser
    }
  },

  async refreshToken() {
    const response = await httpClient.post('/api/auth/refresh', null, {
      skipAuthRefresh: true,
      silentAuthError: true
    })
    const authPayload = response?.data
    const normalizedUser = normalizeUser(authPayload)

    // H13 根治 (2026-06-14): access token 走 HttpOnly cookie, 前端不持有/不持久化
    persistRuntimeSettings({
      roles: [{
        code: normalizedUser.role,
        menuPermissions: normalizedUser.menuPermissions
      }]
    })

    return normalizeAuthSessionResponse(response)
  },

  async getWeComAuthorizeParams() {
    return httpClient.get('/api/auth/wecom/authorize-params')
  },

  async loginByWeCom(code, state) {
    const response = await httpClient.get('/api/auth/wecom/callback', {
      params: { code, state },
      skipAuthRefresh: true,
      skipGlobalErrorMessage: true
    })
    const authPayload = response?.data
    const normalizedUser = normalizeUser(authPayload)

    // H13 根治 (2026-06-14): access token 走 HttpOnly cookie, 前端不持有/不持久化
    persistRuntimeSettings({
      roles: [{
        code: normalizedUser.role,
        menuPermissions: normalizedUser.menuPermissions
      }]
    })

    return normalizeAuthSessionResponse(response)
  }
}

export default authApi
