// Input: authApi responses, persisted session snapshot, runtime settings, and login navigation bridge
// Output: useUserStore - Pinia store for auth, resilient logout, session restore, and user scope state
// Pos: src/stores/ - State management layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { defineStore } from 'pinia'
import { authApi } from '@/api'
import { registerAuthStoreBridge } from '@/api/authStoreBridge.js'
import { clearAuthState, hasPersistentSession } from '@/api/modules/auth.js'
import { persistRuntimeSettings } from '@/api/modules/settings.js'
import {
  getStoredUser,
  persistUserHint
} from '@/api/session.js'
import { navigateToLogin } from '@/router/sessionNavigation.js'
import { resolveLoginFailureMessage } from './loginFailureMessage.js'
import { formatDisplayName } from '@/utils/formatDisplayName.js'

export const useUserStore = defineStore('user', {
  state: () => {
    const savedUser = getStoredUser()

    return {
      currentUser: savedUser,
      // H13 根治 (2026-06-14): token 字段退役 (走 HttpOnly cookie), 登录态由 currentUser 判定
      isRestoringSession: false,
      hasRestoredSession: false
    }
  },

  getters: {
    isLoggedIn: (state) => !!state.currentUser,
    userRole: (state) => state.currentUser?.role || 'bid-Team',
      userName: (state) => formatDisplayName(state.currentUser?.name, state.currentUser?.employeeNumber) || '用户',
      allowedProjectIds: (state) => state.currentUser?.allowedProjectIds || [],
      allowedDepts: (state) => state.currentUser?.allowedDepts || [],
      menuPermissions: (state) => Array.isArray(state.currentUser?.menuPermissions) ? state.currentUser.menuPermissions : [],
      hasPermission: (state) => (permissionKey) => {
        const perms = Array.isArray(state.currentUser?.menuPermissions) ? state.currentUser.menuPermissions : []
        if (perms.includes('all')) return true
        return perms.includes(permissionKey)
      },
      isBidAdmin: (state) => {
        const r = state.currentUser?.roleCode || state.currentUser?.role || ''
        return r === 'admin' || r === '/bidAdmin'
      },
      isBidLead: (state) => {
        const r = state.currentUser?.roleCode || state.currentUser?.role || ''
        return r === 'bid-TeamLeader'
      },
      isBidSenior: (state) => {
        // bid_senior 已删除，映射到 bid-TeamLeader
        const r = state.currentUser?.roleCode || state.currentUser?.role || ''
        return r === 'bid-TeamLeader'
      },
      isBidManager: (state) => {
        const r = state.currentUser?.roleCode || state.currentUser?.role || ''
        return ['admin', '/bidAdmin', 'bid-TeamLeader'].includes(r)
      }
  },

  actions: {
    applyAuthSession(authData, remember = hasPersistentSession()) {
      const nextUser = authData?.user || authData

      if (!nextUser) {
        return null
      }

      this.currentUser = nextUser
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

      if (!result?.success || !result?.data?.user) {
        throw new Error(resolveLoginFailureMessage(result))
      }

      this.applyAuthSession(result.data, remember)
      this.hasRestoredSession = true
      return this.currentUser
    },

    async loginByWeCom(code, state) {
      const result = await authApi.loginByWeCom(code, state)

      if (!result?.success || !result?.data?.user) {
        throw new Error(result?.msg || '企业微信登录失败')
      }

      this.applyAuthSession(result.data, true)
      this.hasRestoredSession = true
      return this.currentUser
    },

    async loginByHomeSso(ssoToken) {
      const result = await authApi.homeSso(ssoToken)

      if (!result?.success || !result?.data?.user) {
        throw new Error(result?.msg || 'Home SSO 登录失败')
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
        // H13 根治 (2026-06-14): 不再用内存 token 选分支. 先试 /me (access cookie,
        // client.js 401 会自动 refresh+重试), 失败再显式 refresh (refresh cookie) 兜底.
        let result = await authApi.getCurrentUser({ silentAuthError: true })
        if (!result?.success || !result?.data) {
          result = await authApi.refreshToken()
        }

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
      }

      this.resetSession()
      await navigateToLogin()
    },

    resetSession() {
      this.currentUser = null
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
