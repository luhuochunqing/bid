/**
 * HTTP 客户端封装
 * 基于 axios 实现，支持拦截器、自动刷新和会话状态同步
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { API_CONFIG } from './config'
import {
  bootstrapLegacyAccessToken,
  clearSessionState,
  getAccessToken,
  setAccessToken
} from './session.js'
import { normalizeAuthSessionResponse } from './authNormalizer.js'
import { resetAuthStoreSession, syncAuthStoreSession } from './authStoreBridge.js'
import router from '@/router/index.js'

let refreshPromise = null

const syncRefreshedSession = async (refreshResult) => {
  if (!refreshResult?.success || !refreshResult?.data?.user) {
    return
  }

  try {
    await syncAuthStoreSession(refreshResult.data)
  } catch (syncError) {
    console.warn('Failed to sync refreshed auth session:', syncError)
  }
}

// 创建 axios 实例
const httpClient = axios.create({
  baseURL: API_CONFIG.baseURL,
  timeout: API_CONFIG.timeout,
  headers: API_CONFIG.headers,
  withCredentials: true
})

const refreshAuthSession = async () => {
  const response = await httpClient.post('/api/auth/refresh', null, {
    skipAuthRefresh: true,
    silentAuthError: true
  })

  const authPayload = response?.data
  setAccessToken(authPayload?.token, true)
  return normalizeAuthSessionResponse(response)
}

const shouldSkipAuthHeader = (config = {}) => {
  const url = String(config.url || '')
  return Boolean(
    config.skipAuthHeader ||
    url.includes('/api/auth/register') ||
    url.includes('/api/auth/login') ||
    url.includes('/api/auth/refresh')
  )
}

const shouldSkipRefresh = (config = {}) => {
  const url = String(config.url || '')
  return Boolean(
    config.skipAuthRefresh ||
    url.includes('/api/auth/register') ||
    url.includes('/api/auth/login') ||
    url.includes('/api/auth/refresh') ||
    url.includes('/api/auth/logout')
  )
}

const handleAuthFailure = async () => {
  // Use a simple time-based throttle instead of a boolean flag to avoid permanent lock
  if (handleAuthFailure._lastAlert && Date.now() - handleAuthFailure._lastAlert < 2000) {
    clearSessionState()
    return
  }
  
  handleAuthFailure._lastAlert = Date.now()

  // Use a fallback for ElMessage in tests if needed
  try {
    ElMessage.error('登录已过期，请重新登录')
  } catch (e) {
    console.warn('ElMessage failed:', e)
  }

  clearSessionState()

  try {
    await resetAuthStoreSession()
  } catch (syncError) {
    console.warn('Failed to reset auth store:', syncError)
  }

  // Always attempt redirect after session clear
  setTimeout(() => {
    const currentPath = router.currentRoute.value.path

    if (currentPath !== '/login') {
      router.push('/login').catch((navError) => {
        if (navError.name !== 'NavigationDuplicated' && 
            !navError.message?.includes('Redirected when going from')) {
          console.error('Navigation to login failed:', navError)
        }
      })
    }
  }, 100)
}

// 请求拦截器
httpClient.interceptors.request.use(
  (config) => {
    // Logout still needs the bearer token, but should never enter refresh replay.
    const token = getAccessToken() || bootstrapLegacyAccessToken()
    if (token && !shouldSkipAuthHeader(config)) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
httpClient.interceptors.response.use(
  (response) => {
    // 后端返回的统一格式: { success: true, data: ..., message: ... }
    return response.data
  },
  async (error) => {
    const { response, config } = error
    const shouldStaySilent = Boolean(config?.silentAuthError)

    if (response?.status === 401 && config && !config._retry && !shouldSkipRefresh(config)) {
      config._retry = true

      try {
        if (!refreshPromise) {
          refreshPromise = refreshAuthSession().finally(() => {
            refreshPromise = null
          })
        }

        const refreshResult = await refreshPromise
        await syncRefreshedSession(refreshResult)
        const token = refreshResult?.data?.token || getAccessToken()

        if (token) {
          config.headers = config.headers || {}
          config.headers.Authorization = `Bearer ${token}`
        }

        return httpClient(config)
      } catch (refreshError) {
        await handleAuthFailure()
        throw refreshError
      }
    }

    if (shouldStaySilent && (response?.status === 401 || response?.status === 403)) {
      return Promise.reject(error)
    }

    if (config?.silentError || config?.skipGlobalErrorMessage) {
      return Promise.reject(error)
    }

    if (response) {
      // 服务器返回错误状态码
      switch (response.status) {
        case 401:
          await handleAuthFailure()
          break
        case 403:
          ElMessage.error(response.data?.msg || '没有权限访问该资源')
          break
        case 404:
          ElMessage.error(response.data?.msg || '请求的资源不存在')
          break
        case 500:
          ElMessage.error(response.data?.msg || '服务器内部错误，请稍后重试')
          break
        default:
          ElMessage.error(response.data?.msg || '请求失败')
      }
    } else if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      ElMessage.error('请求超时，请检查网络后重试')
    } else {
      ElMessage.error('网络异常，请检查网络连接')
    }

    return Promise.reject(error)
  }
)

export default httpClient
