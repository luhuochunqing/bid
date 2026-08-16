/**
 * API 配置文件
 * 真实 API 为唯一数据源
 */

const viteEnv = typeof import.meta !== 'undefined' && import.meta.env ? import.meta.env : {}
const DEFAULT_API_HOST = '127.0.0.1'
const DEFAULT_API_PORT = 18080

const trimTrailingSlash = (value) => value.replace(/\/+$/, '')

const normalizeApiBaseUrl = (rawValue) => {
  const fallback = `http://${DEFAULT_API_HOST}:${DEFAULT_API_PORT}`
  const value = String(rawValue || '').trim()

  if (!value) {
    return ''
  }

  // 支持相对路径（如 /api），让浏览器通过当前 host + nginx 反向代理访问后端
  if (value.startsWith('/')) {
    return trimTrailingSlash(value)
  }

  if (/^https?:\/\//i.test(value)) {
    return trimTrailingSlash(value)
  }

  const missingHostMatch = value.match(/^\/{0,2}:([0-9]{2,5})$/)
  if (missingHostMatch) {
    return `http://${DEFAULT_API_HOST}:${missingHostMatch[1]}`
  }

  if (/^[a-z0-9.-]+:[0-9]{2,5}$/i.test(value)) {
    return `http://${value}`
  }

  console.warn(`[api/config] Invalid VITE_API_BASE_URL "${value}", fallback to ${fallback}`)
  return fallback
}

// 生产构建强制同源（baseURL 空）：前端+后端始终同入口部署（域名经 nginx 反代 / IP 一体），
// 同源是唯一"无论从哪个 origin 访问都不跨域"的方案。**忽略 VITE_API_BASE_URL**，从代码层
// 根治"构建配错绝对 URL base（IP/域名）→ 部署后跨域 403"——该 bug 已反复复发 3 次，
// 构建期 check 可被运维绕过（手动构建/旧产物/别的工具），本强制在代码里、不可绕过。
// dev 构建（vite serve，import.meta.env.PROD=false）仍用 VITE_API_BASE_URL 调本地后端。
export const API_BASE_URL = import.meta.env.PROD ? '' : normalizeApiBaseUrl(viteEnv.VITE_API_BASE_URL)

export const API_CONFIG = {
  mode: 'api',
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
}

export const isCommercialMode = () => true

export const getApiUrl = (path) => `${API_BASE_URL}${path}`

export default API_CONFIG
