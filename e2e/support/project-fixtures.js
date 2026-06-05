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
  let payload

  try {
    payload = await requestJson(`${apiBaseUrl}/api/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ username, password }),
    })
  } catch {
    payload = await requestJson(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        username,
        password,
        email: `${username}@example.com`,
        fullName: 'ERI-92 E2E',
        role: 'ADMIN',
      }),
    })
  }

  if (!payload?.success || !payload?.data?.token || !payload?.data?.id) {
    throw new Error('Backend login response is missing token or user identity')
  }

  return {
    token: payload.data.token,
    user: {
      id: payload.data.id,
      name: payload.data.fullName || payload.data.username,
      username: payload.data.username,
      email: payload.data.email,
      role: String(payload.data.role || '').toLowerCase(),
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
