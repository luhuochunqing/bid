/**
 * HTTP 客户端封装
 * 基于 axios 实现，支持拦截器、自动刷新和会话状态同步
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { API_CONFIG } from './config'
import { clearSessionState } from './session.js'
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
  // H13 根治 (2026-06-14): refresh 成功后浏览器自动更新 access cookie (Set-Cookie),
  // 前端无需持有 token
  const response = await httpClient.post('/api/auth/refresh', null, {
    skipAuthRefresh: true,
    silentAuthError: true
  })

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
// H13 根治 (2026-06-14): access token 走 HttpOnly cookie (httpClient withCredentials 自动带),
// 不再手动注入 Authorization header.
httpClient.interceptors.request.use(
  (config) => {
    if (!config.headers['X-Trace-Id']) {
      // 兼容不支持 crypto.randomUUID 的环境（老版本浏览器）
      const generateUUID = () => {
        if (typeof crypto !== 'undefined' && crypto.randomUUID) {
          return crypto.randomUUID().replace(/-/g, '')
        }
        return 'xxxxxxxxxxxx4xxxyxxxxxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
          const r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8)
          return v.toString(16)
        })
      }
      config.headers['X-Trace-Id'] = generateUUID()
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
httpClient.interceptors.response.use(
  (response) => {
    // 4.1.1.3：blob 响应不解包 response.data（让调用方 .data 拿到原始 Blob 对象）
    if (response.config?.responseType === 'blob') {
      return response
    }
    // 后端返回的统一格式: { success: true, data: ..., message: ... }
    return response.data
  },
  async (error) => {
    const { response, config } = error
    const shouldStaySilent = Boolean(config?.silentAuthError)

    // blob 响应的错误需要特殊处理：将错误 blob 转换为文本并尝试解析
    if (config?.responseType === 'blob' && response?.data instanceof Blob) {
      try {
        const text = await response.data.text()
        const json = JSON.parse(text)
        response.data = json
      } catch {
        // 非 JSON 响应，保持原样
      }
    }

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
        // H13 根治: refresh 成功后浏览器已更新 access cookie, 直接重试 (cookie 自动带)
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
      const serverMsg = response.data?.msg
      switch (response.status) {
        case 401:
          await handleAuthFailure()
          break
        case 403:
          ElMessage.error(serverMsg || '没有操作权限，请联系管理员')
          break
        case 400:
          ElMessage.error(serverMsg || '请求参数有误，请检查输入')
          break
        case 404:
          ElMessage.error(serverMsg || '请求的资源不存在')
          break
        case 407:
          ElMessage.error(serverMsg || '身份验证失败，请重新登录')
          break
        case 409:
          ElMessage.error(serverMsg || '操作冲突，请刷新后重试')
          break
        case 429:
          ElMessage.warning(serverMsg || '操作过于频繁，请稍后再试')
          break
        case 500:
          ElMessage.error(serverMsg || '服务器出现问题，请稍后重试')
          break
        case 502:
        case 503:
        case 504:
          ElMessage.error(serverMsg || '服务暂时不可用，请稍后重试')
          break
        default:
          ElMessage.error(serverMsg || '请求失败，请稍后重试')
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
