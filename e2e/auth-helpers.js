// Input: Playwright E2E env vars and backend auth endpoints
// Output: shared helpers for authenticated API-backed Playwright sessions
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'
const defaultPassword = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }

  return payload
}

function extractSession(data) {
  if (!data?.token || !data?.id) {
    throw new Error('Backend response missing token or user identity')
  }
  return {
    token: data.token,
    refreshToken: data.refreshToken || null,
    user: {
      id: data.id,
      name: data.fullName || data.username,
      username: data.username,
      email: data.email,
      role: String(data.role || '').toLowerCase()
    }
  }
}

export async function ensureApiSession({ username, role = 'ADMIN', fullName, password = defaultPassword }) {
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
      return extractSession(registerPayload.data)
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
  return extractSession(payload?.data)
}

export async function injectSession(page, session) {
  // 给浏览器 context 授权剪贴板读写，避免 E2E 中 navigator.clipboard 抛 NotAllowedError
  // 这与真实用户手动授权浏览器剪贴板行为一致
  try {
    // 不传 origin：默认对所有 origin 授权，避免前端域名 (1315) 调 clipboard 时被拒
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])
  } catch (permErr) {
    // 某些浏览器实现不支持该 API；忽略即可
  }

  await page.addInitScript(({ apiBaseUrl: browserApiBaseUrl }) => {
    const existingProcess = globalThis.process || { env: {} }
    existingProcess.env = existingProcess.env || {}
    existingProcess.env.VITE_API_BASE_URL = existingProcess.env.VITE_API_BASE_URL || browserApiBaseUrl
    globalThis.process = existingProcess
  }, { apiBaseUrl })

  await page.addInitScript(({ currentSession }) => {
    sessionStorage.setItem('token', currentSession.token)
    if (currentSession.refreshToken) {
      sessionStorage.setItem('refreshToken', currentSession.refreshToken)
    }
    sessionStorage.setItem('user', JSON.stringify(currentSession.user))
  }, { currentSession: session })
}

export { apiBaseUrl, defaultPassword }

export async function injectAuthToken(page, { role = 'ADMIN', fullName = role, password = defaultPassword }) {
  const username = role.toLowerCase() + '_e2e_' + Date.now()
  const session = await ensureApiSession({ username, role, fullName, password })
  await injectSession(page, session)
}
