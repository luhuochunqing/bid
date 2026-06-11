// Input: Playwright E2E env vars and backend auth endpoints
// Output: shared helpers for authenticated API-backed Playwright sessions
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'
const frontendUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1314'
const defaultPassword = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }

  return payload
}

function extractSession(data, overrides = {}) {
  if (!data?.token || !data?.id) {
    throw new Error('Backend response missing token or user identity')
  }
  // 防止后端返回空 menuPermissions 覆盖我们显式注入的 menuPermissions: ['all']
  const finalMenuPermissions = (Array.isArray(overrides.menuPermissions) && overrides.menuPermissions.length > 0)
    ? overrides.menuPermissions
    : (Array.isArray(data.menuPermissions) && data.menuPermissions.length > 0 ? data.menuPermissions : [])
  return {
    token: data.token,
    refreshToken: data.refreshToken || null,
    user: {
      id: data.id,
      name: data.fullName || data.username,
      username: data.username,
      email: data.email,
      role: String(data.role || '').toLowerCase(),
      menuPermissions: finalMenuPermissions,
      ...(overrides.userFields || {}),
    }
  }
}

export async function ensureApiSession({ username, role = 'ADMIN', fullName, password = defaultPassword, userFields = {} }) {
  const email = `${username}@example.com`

  // 尝试注册新用户（注册成功时后端直接返回 token，无需再调登录）
  try {
    const registerPayload = await requestJson(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        password,
        email,
        fullName: fullName || username,
        role
      })
    })
    if (registerPayload?.success && registerPayload?.data?.token && registerPayload?.data?.id) {
      return extractSession(registerPayload.data, { userFields })
    }
  } catch (error) {
    const message = String(error.message)
    // 409 表示用户已存在，需要走登录流程
    if (!message.includes('409') && !message.includes('already exists')) {
      throw error
    }
  }

  // 登录已有用户
  const payload = await requestJson(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  return extractSession(payload?.data, { userFields })
}

export async function injectSession(page, session) {
  try {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])
  } catch (permErr) { /* 忽略 */ }

  await page.addInitScript(({ apiBaseUrl: browserApiBaseUrl }) => {
    const existingProcess = globalThis.process || { env: {} }
    existingProcess.env = existingProcess.env || {}
    existingProcess.env.VITE_API_BASE_URL = existingProcess.env.VITE_API_BASE_URL || browserApiBaseUrl
    globalThis.process = existingProcess
  }, { apiBaseUrl })

  // 拦截 /api/auth/me 请求，返回注入的 user。
  // axios 用相对 URL（如 '/api/auth/me'），浏览器解析为当前页面 origin。
  // 同时注册两个 route：基于 frontendUrl 和 apiBaseUrl，以防请求走不同端口。
  for (const base of [frontendUrl, apiBaseUrl]) {
    await page.route(base + '/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: session.user }),
      })
    })
  }

  // 写 localStorage：getStoredUser() 在 Pinia 初始化时读
  await page.addInitScript(({ currentSession }) => {
    const USER_KEY = 'user'
    const TOKEN_KEY = 'token'
    window.localStorage.removeItem(USER_KEY)
    window.localStorage.removeItem(TOKEN_KEY)
    window.sessionStorage.removeItem(USER_KEY)
    window.sessionStorage.removeItem(TOKEN_KEY)
    window.localStorage.setItem(USER_KEY, JSON.stringify(currentSession.user))
    window.localStorage.setItem(TOKEN_KEY, currentSession.token)
    window.sessionStorage.setItem(USER_KEY, JSON.stringify(currentSession.user))
    window.sessionStorage.setItem(TOKEN_KEY, currentSession.token)
    window.__xiyuAccessToken = currentSession.token
  }, { currentSession: session })
}

export { apiBaseUrl, defaultPassword }

export async function injectAuthToken(page, { role = 'ADMIN', fullName = role, password = defaultPassword }) {
  const username = role.toLowerCase() + '_e2e_' + Date.now()
  const session = await ensureApiSession({ username, role, fullName, password })
  await injectSession(page, session)
}
