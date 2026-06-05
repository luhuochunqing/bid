// Input: authApi responses, persisted session snapshot, runtime settings
// Output: useUserStore - Pinia store for auth, session restore, and user scope state
// Pos: src/stores/ - State management layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { defineStore } from 'pinia'
import { authApi } from '@/api'
import { registerAuthStoreBridge } from '@/api/authStoreBridge.js'
import { clearAuthState, hasPersistentSession } from '@/api/modules/auth.js'
import { persistRuntimeSettings } from '@/api/modules/settings.js'
import {
  bootstrapLegacyAccessToken,
  getStoredUser,
  persistUserHint
} from '@/api/session.js'
import { navigateToLogin } from '@/router/sessionNavigation.js'
import { resolveLoginFailureMessage } from './loginFailureMessage.js'

export const useUserStore = defineStore('user', {
  state: () => {
    const savedUser = getStoredUser()

    return {
      currentUser: savedUser,
      token: bootstrapLegacyAccessToken(),
      users: [],
      isRestoringSession: false,
      hasRestoredSession: false
    }
  },

  getters: {
    isLoggedIn: (state) => !!state.currentUser && !!state.token,
    userRole: (state) => state.currentUser?.role || 'staff',
    userName: (state) => state.currentUser?.name || '用户',
    allowedProjectIds: (state) => state.currentUser?.allowedProjectIds || [],
    allowedDepts: (state) => state.currentUser?.allowedDepts || [],
    menuPermissions: (state) => Array.isArray(state.currentUser?.menuPermissions) ? state.currentUser.menuPermissions : [],
    hasPermission: (state) => (permissionKey) => {
      const perms = Array.isArray(state.currentUser?.menuPermissions) ? state.currentUser.menuPermissions : []
      if (perms.includes('all')) return true
      return perms.includes(permissionKey)
    }
  },

  actions: {
    applyAuthSession(authData, remember = hasPersistentSession()) {
      const nextUser = authData?.user || authData
      const nextToken = authData?.token

      if (!nextUser) {
        return null
      }

      this.currentUser = nextUser
      if (nextToken) {
        this.token = nextToken
      }
      persistRuntimeSettings({
        roles: [{
          code: nextUser?.roleCode || nextUser?.role,
          name: nextUser?.roleName || '',
          menuPermissions: Array.isArray(nextUser?.menuPermissions) ? nextUser.menuPermissions : []
        }]
      })

      this.persistSession(remember)
      return this.currentUser
    },

    async login(username, password, remember = true) {
      let result

      try {
        result = await authApi.login(username, password, remember)
      } catch (error) {
        throw new Error(resolveLoginFailureMessage(error), { cause: error })
      }

      if (!result?.success || !result?.data?.user || !result?.data?.token) {
        throw new Error(resolveLoginFailureMessage(result))
      }

      this.applyAuthSession(result.data, remember)
      this.hasRestoredSession = true
      return this.currentUser
    },

    async loginByWeCom(code, state) {
      const result = await authApi.loginByWeCom(code, state)

      if (!result?.success || !result?.data?.user || !result?.data?.token) {
        throw new Error(result?.msg || '企业微信登录失败')
      }

      this.applyAuthSession(result.data, true)
      this.hasRestoredSession = true
      return this.currentUser
    },

    async restoreSession() {
      if (this.isRestoringSession) {
        return this.currentUser
      }

      if (this.hasRestoredSession) {
        return this.currentUser
      }

      this.isRestoringSession = true

      try {
        const result = this.token
          ? await authApi.getCurrentUser({ silentAuthError: true })
          : await authApi.refreshToken()

        if (!result?.success || !result?.data) {
          throw new Error(result?.msg || 'Session restore failed')
        }

        this.applyAuthSession(result.data, hasPersistentSession())
        return this.currentUser
      } catch (error) {
        this.resetSession()
        await navigateToLogin()
        return null
      } finally {
        this.isRestoringSession = false
        this.hasRestoredSession = true
      }
    },

    async logout() {
      try {
        await authApi.logout()
      } catch (error) {
        console.warn('Logout request failed, clearing local session anyway:', error)
      } finally {
        this.resetSession()
        await navigateToLogin()
      }
    },

    resetSession() {
      this.currentUser = null
      this.token = null
      this.isRestoringSession = false
      this.hasRestoredSession = true
      clearAuthState()
    },

    // 持久化用户状态到 storage
    persistSession(remember = true) {
      if (!this.currentUser) return
      persistUserHint(this.currentUser, remember)
    }
  }
})

registerAuthStoreBridge({
  applySession: (authData) => useUserStore().applyAuthSession(authData),
  resetSession: () => useUserStore().resetSession()
})
