export const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'

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
        role: '/bidAdmin',
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
  const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1323'
  await page.context().addCookies([
    { name: 'access_token', value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: 'Lax' },
    { name: 'access_token', value: session.token, url: frontendBaseUrl, httpOnly: true, sameSite: 'Lax' },
  ])
  await page.addInitScript((user) => {
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session.user)
}

/**
 * 将项目推进到 DRAFTING 阶段——走真实生命周期 API：
 * 提交立项（最小合法载荷）→ 立项审批通过（INITIATED→DRAFTING，分配主负责人）。
 * 不直接改库/不走旁路，保证与真实用户路径一致。
 */
export async function ensureRoleUser(role) {
  const suffix = Date.now() + Math.floor(Math.random() * 1000)
  const username = `e2e_${role.replace(/-/g, '_')}_${suffix}`
  const password = 'XiyuDemo!2026'
  await requestJson(`${apiBaseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username, password,
      email: `${username}@example.com`,
      fullName: `E2E ${role}`,
      role,
    }),
  })
  const loginRes = await fetch(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!loginRes.ok) throw new Error(`role user login failed ${loginRes.status}`)
  const setCookies = loginRes.headers.getSetCookie?.() || []
  const at = setCookies.find(c => c.startsWith('access_token='))
  const token = at ? at.split(';')[0].split('=')[1] : null
  const body = await loginRes.json()
  return { id: body.data.id, username: body.data.username, role: body.data.roleCode || role, token }
}

/**
 * 将项目推进到 DRAFTING 阶段——走真实生命周期 API：
 *   投标项目负责人(PL)上传招标文件 → PL 提交立项 → bid-admin 审批通过（INITIATED→DRAFTING）
 * 前置：projectId 的 teamMembers 必须包含 PL 用户 id（服务层做项目访问断言）。
 */
export async function advanceProjectToDrafting(session, projectId, pl) {
  // 1. PL 上传招标文件（multipart，documentCategory=TENDER）
  const form = new FormData()
  form.append('file', new Blob(['%PDF-1.4 e2e-fixture'], { type: 'application/pdf' }), '招标文件.pdf')
  form.append('name', '招标文件.pdf')
  form.append('size', '1MB')
  form.append('fileType', 'application/pdf')
  form.append('documentCategory', 'TENDER')
  form.append('linkedEntityType', 'PROJECT_INITIATION')
  form.append('linkedEntityId', String(projectId))
  const docRes = await fetch(`${apiBaseUrl}/api/projects/${projectId}/documents`, {
    method: 'POST',
    headers: { Cookie: `access_token=${pl.token}` },
    body: form,
  })
  const docJson = await docRes.json().catch(() => null)
  if (!docRes.ok || !docJson?.data?.id) {
    throw new Error(`tender doc upload failed ${docRes.status}: ${JSON.stringify(docJson).slice(0, 200)}`)
  }

  // 2. PL 提交立项
  await requestJson(`${apiBaseUrl}/api/projects/${projectId}/initiation`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Cookie: `access_token=${pl.token}` },
    body: JSON.stringify({
      ownerUnit: 'E2E Owner Unit',
      needDeposit: 'NO',
      expectedBidders: 3,
      bidOpenTime: new Date(Date.now() + 7 * 86400_000).toISOString().slice(0, 19),
      tenderDocumentId: docJson.data.id,
    }),
  })

  // 3. 审批通过（INITIATED→DRAFTING）。
  //    审批人固定用 E2eDemoDataInitializer 种子的 lizong（密码 123456）：
  //    实证其权限确定可用；动态注册用户的授权差异会偶发方法安全 403。
  const adminLogin = await fetch(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'lizong', password: '123456' }),
  })
  if (!adminLogin.ok) throw new Error(`approver login failed ${adminLogin.status}`)
  const adminCookies = adminLogin.headers.getSetCookie?.() || []
  const adminAt = adminCookies.find(c => c.startsWith('access_token='))
  await requestJson(`${apiBaseUrl}/api/projects/${projectId}/initiation/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Cookie: `access_token=${adminAt.split(';')[0].split('=')[1]}` },
    body: JSON.stringify({ primaryLeadUserId: pl.id }),
  })
  return projectId
}

export function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

export async function createProjectFixture(session, label = '项目详情流程', overrides = {}) {
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
      // 需要任务看板/标书编制阶段的用例传 { stage: 'DRAFTING' }
      ...overrides,
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
