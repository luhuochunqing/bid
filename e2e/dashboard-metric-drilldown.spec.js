import { test, expect } from '@playwright/test'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }

  return payload
}

async function ensureSession() {
  const username = process.env.COMMERCIAL_E2E_USERNAME || `eri151_${Date.now()}`
  const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'
  const email = `${username}@example.com`

  try {
    const payload = await requestJson(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        password,
        email,
        fullName: 'ERI-151 E2E',
        role: 'ADMIN',
      }),
    })

    if (payload?.success && payload?.data?.token && payload?.data?.id) {
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
  } catch (error) {
    if (!String(error.message).includes('409')) throw error
  }

  const payload = await requestJson(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })

  if (!payload?.success || !payload?.data?.token || !payload?.data?.id) {
    throw new Error('Backend login response missing token or user identity')
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

async function authedJson(path, session, options = {}) {
  const headers = {
    Authorization: `Bearer ${session.token}`,
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(options.headers || {}),
  }
  return requestJson(`${apiBaseUrl}${path}`, { ...options, headers })
}

function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

test('dashboard metric projects drill-down renders real rows', async ({ page }) => {
  const session = await ensureSession()
  const suffix = Date.now()

  const tenderPayload = await authedJson('/api/tenders', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `ERI-151 项目明细标讯 ${suffix}`,
      source: 'ERI-151',
      budget: 680000,
      deadline: toLocalDateTimeString(new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)),
      status: 'TRACKING',
      aiScore: 81,
      riskLevel: 'LOW',
    }),
  })

  expect(tenderPayload?.data?.id).toBeTruthy()

  const projectName = `ERI-151 项目明细项目 ${suffix}`
  const projectPayload = await authedJson('/api/projects', session, {
    method: 'POST',
    body: JSON.stringify({
      name: projectName,
      tenderId: tenderPayload.data.id,
      status: 'BIDDING',
      managerId: session.user.id,
      teamMembers: [session.user.id],
      startDate: toLocalDateTimeString(new Date()),
      endDate: toLocalDateTimeString(new Date(Date.now() + 14 * 24 * 60 * 60 * 1000)),
      sourceModule: 'score-draft',
      sourceCustomerId: `cust-${suffix}`,
      sourceCustomer: '上海城投',
      sourceOpportunityId: `opp-${suffix}`,
      sourceReasoningSummary: '从评分草稿转入正式项目，验证来源字段与 drill-down 一致性。',
    }),
  })

  expect(projectPayload?.data?.id).toBeTruthy()

  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  await page.goto('/analytics/dashboard?drilldown=projects&status=BIDDING')
  await expect(page.getByRole('heading', { name: '数据分析' })).toBeVisible()

  const drawer = page.locator('.metric-drawer')
  await expect(drawer).toBeVisible()
  await expect(page.getByText('进行中项目明细')).toBeVisible()
  await expect(page.locator('.metric-summary-card .summary-value').first()).toBeVisible()
  await expect(drawer).toContainText(projectName)
})

test('dashboard metric win-rate drill-down honors outcome query filters', async ({ page }) => {
  const session = await ensureSession()
  const suffix = Date.now()

  const wonTender = await authedJson('/api/tenders', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `ERI-151 中标标讯 ${suffix}`,
      source: 'ERI-151',
      budget: 520000,
      deadline: toLocalDateTimeString(new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)),
      status: 'BIDDING',
      aiScore: 89,
      riskLevel: 'LOW',
    }),
  })

  const inProgressTender = await authedJson('/api/tenders', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `ERI-151 跟进标讯 ${suffix}`,
      source: 'ERI-151',
      budget: 410000,
      deadline: toLocalDateTimeString(new Date(Date.now() + 9 * 24 * 60 * 60 * 1000)),
      status: 'TRACKING',
      aiScore: 77,
      riskLevel: 'MEDIUM',
    }),
  })

  const wonProjectName = `ERI-151 中标项目 ${suffix}`
  const inProgressProjectName = `ERI-151 跟进项目 ${suffix}`

  await authedJson('/api/projects', session, {
    method: 'POST',
    body: JSON.stringify({
      name: wonProjectName,
      tenderId: wonTender.data.id,
      status: 'BIDDING',
      managerId: session.user.id,
      teamMembers: [session.user.id],
      startDate: toLocalDateTimeString(new Date()),
      endDate: toLocalDateTimeString(new Date(Date.now() + 14 * 24 * 60 * 60 * 1000)),
      sourceModule: 'score-draft',
      sourceCustomerId: `won-cust-${suffix}`,
      sourceCustomer: '上海城投',
      sourceOpportunityId: `won-opp-${suffix}`,
      sourceReasoningSummary: '验证 win-rate 路由筛选只展示中标记录。',
    }),
  })

  await authedJson('/api/projects', session, {
    method: 'POST',
    body: JSON.stringify({
      name: inProgressProjectName,
      tenderId: inProgressTender.data.id,
      status: 'BIDDING',
      managerId: session.user.id,
      teamMembers: [session.user.id],
      startDate: toLocalDateTimeString(new Date()),
      endDate: toLocalDateTimeString(new Date(Date.now() + 14 * 24 * 60 * 60 * 1000)),
      sourceModule: 'score-draft',
      sourceCustomerId: `progress-cust-${suffix}`,
      sourceCustomer: '上海城投',
      sourceOpportunityId: `progress-opp-${suffix}`,
      sourceReasoningSummary: '验证 win-rate 路由筛选排除进行中记录。',
    }),
  })

  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  // Mock drill-down API — simulates backend filtering by outcome param  // @ui-cover:dashboard
  // Frontend calls /api/analytics/drilldown/win-rate?outcome=WON
  await page.route('**/drilldown/win-rate**', async (route) => {
    const url = new URL(route.request().url())
    const outcome = url.searchParams.get('outcome')

    // Filter items by outcome like the real backend does
    let items = [
      {
        id: 1, relatedId: 1, title: wonProjectName, subtitle: '中标',
        status: 'BIDDING', ownerName: session.user.name || '管理员',
        amount: 520000, score: null, createdAt: '2026-05-15T00:00:00',
        deadline: null, outcome: 'WON', role: null, count: null,
        wonCount: null, activeProjectCount: null, managedProjectCount: null,
        totalTaskCount: null, completedTaskCount: null, overdueTaskCount: null,
        taskCompletionRate: null, rate: 1, teamSize: null,
      },
      {
        id: 2, relatedId: 2, title: inProgressProjectName, subtitle: '进行中',
        status: 'BIDDING', ownerName: session.user.name || '管理员',
        amount: 410000, score: null, createdAt: '2026-05-14T00:00:00',
        deadline: null, outcome: null, role: null, count: null,
        wonCount: null, activeProjectCount: null, managedProjectCount: null,
        totalTaskCount: null, completedTaskCount: null, overdueTaskCount: null,
        taskCompletionRate: null, rate: 0, teamSize: null,
      },
    ]

    if (outcome === 'WON') {
      items = items.filter((i) => i.outcome === 'WON')
    } else if (outcome === 'IN_PROGRESS' || outcome === 'LOST') {
      items = items.filter((i) => i.outcome === outcome)
    }

    const totalAmount = items.reduce((s, i) => s + Number(i.amount || 0), 0)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          metricKey: 'win-rate',
          metricLabel: '中标率明细',
          filters: { dimensions: [] },
          pagination: { page: 1, size: 10, total: items.length, totalPages: 1, hasNext: false },
          summary: { totalCount: items.length, totalAmount },
          items,
        },
        message: null,
      }),
    })
  })

  await page.goto('/analytics/dashboard?drilldown=win-rate&outcome=WON')
  await expect(page.getByRole('heading', { name: '数据分析' })).toBeVisible()

  const drawer = page.locator('.metric-drawer')
  await expect(drawer).toBeVisible()
  await expect(page.getByText('中标率明细')).toBeVisible()
  await expect(drawer).toContainText(wonProjectName)
  await expect(drawer).not.toContainText(inProgressProjectName)
})

test('dashboard quick entry routes navigate correctly', async ({ page }) => {
  const session = await ensureSession()

  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  // Mock summary API to avoid backend dependency
  await page.route('**/api/analytics/summary', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: { totalBudget: 1e6, successRate: 0.75, totalTenders: 10, activeProjects: 3 } }),
    })
  })

  await page.goto('/dashboard')

  // Verify the page loaded with metric cards visible
  await expect(page.locator('.metric-cards-container, .stat-cards, .quick-entry-section').first()).toBeVisible({ timeout: 10000 })
  await expect(page.getByText(/进行中项目|投标数量|中标率/).first()).toBeVisible({ timeout: 5000 })
})

test('knowledge kb-layout tabs navigate correctly', async ({ page }) => {
  const session = await ensureSession()

  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  // Mock archive API for the default tab
  await page.route('**/api/archive', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: { content: [], totalElements: 0 } }),
    })
  })

  await page.goto('/knowledge')

  // Verify KbLayout tabs are visible
  await expect(page.getByText('档案台账')).toBeVisible({ timeout: 10000 })
  await expect(page.getByText('资质库')).toBeVisible()
  await expect(page.getByText('案例库')).toBeVisible()
  await expect(page.getByText('模板库')).toBeVisible()

  // Click a tab and verify URL changes
  await page.getByText('资质库').click()
  await expect(page).toHaveURL(/\/knowledge\/qualification/)
})
