export const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'

export async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }

  return payload
}

export async function createAuthenticatedSession() {
  const username = process.env.COMMERCIAL_E2E_USERNAME || `eri92_${Date.now()}`
  const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

  const doLogin = async () => {
    const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    const loginPayload = await response.json().catch(() => null)
    return { response, loginPayload }
  }

  let { response, loginPayload } = await doLogin()

  if (!response.ok) {
    // 用户不存在 → 注册 → 再 login (H13: register 不签 token, 必须走 login 拿 cookie)
    await requestJson(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        password,
        email: `${username}@example.com`,
        fullName: 'ERI-92 E2E',
        role: 'bid_admin',
      }),
    }).catch(() => {})
    ;({ response, loginPayload } = await doLogin())
  }

  if (!response.ok || !loginPayload?.success || !loginPayload?.data?.id) {
    throw new Error(`Backend login failed: ${response.status} ${JSON.stringify(loginPayload)}`)
  }

  // H13 根治 (2026-06-14): access token 从 Set-Cookie 提取 (body.token 已为 null)
  const setCookie = response.headers.get('set-cookie') || ''
  const tokenMatch = setCookie.match(/access_token=([^;]+)/)
  if (!tokenMatch) {
    throw new Error('Login response missing access_token cookie (H13)')
  }

  return {
    token: tokenMatch[1],
    user: {
      id: loginPayload.data.id,
      name: loginPayload.data.fullName || loginPayload.data.username,
      username: loginPayload.data.username,
      email: loginPayload.data.email,
      role: String(loginPayload.data.role || '').toLowerCase(),
    },
  }
}

export async function authedJson(path, token, options = {}) {
  const headers = {
    Authorization: `Bearer ${token}`,
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(options.headers || {}),
  }

  return requestJson(`${apiBaseUrl}${path}`, {
    ...options,
    headers,
  })
}

// H13 根治 (2026-06-14): 浏览器内 page 认证靠 access_token cookie (前端走 cookie, 不读 storage token).
// spec 调此 helper 注入 cookie + user hint, 替代旧的 sessionStorage.setItem('token').
export async function attachSessionToPage(page, session) {
  const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1314'
  await page.context().addCookies([
    { name: 'access_token', value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: 'Lax' },
    { name: 'access_token', value: session.token, url: frontendBaseUrl, httpOnly: true, sameSite: 'Lax' },
  ])
  await page.addInitScript((user) => {
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session.user)
}

export function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

export async function createProjectFixture(session, label = '项目详情流程') {
  const suffix = Date.now()
  const tenderPayload = await authedJson('/api/tenders', session.token, {
    method: 'POST',
    body: JSON.stringify({
      title: `E2E ${label}标讯 ${suffix}`,
      source: 'Playwright',
      budget: 880000,
      deadline: toLocalDateTimeString(new Date(Date.now() + 14 * 24 * 60 * 60 * 1000)),
      status: 'TRACKING',
      aiScore: 86,
      riskLevel: 'LOW',
    }),
  })

  const tenderId = tenderPayload?.data?.id
  if (!tenderId) {
    throw new Error('Unable to create tender fixture for project detail workflow E2E')
  }

  const projectPayload = await authedJson('/api/projects', session.token, {
    method: 'POST',
    body: JSON.stringify({
      name: `E2E ${label}项目 ${suffix}`,
      tenderId,
      status: 'BIDDING',
      managerId: session.user.id,
      teamMembers: [session.user.id],
      startDate: toLocalDateTimeString(new Date()),
      endDate: toLocalDateTimeString(new Date(Date.now() + 10 * 24 * 60 * 60 * 1000)),
    }),
  })

  const project = projectPayload?.data
  if (!project?.id) {
    throw new Error('Unable to create project fixture for project detail workflow E2E')
  }

  return project
}
