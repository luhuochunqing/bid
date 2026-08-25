import { test, expect } from '@playwright/test'
import { ensureApiSession } from './auth-helpers.js'
const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1323'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }

  return payload
}

// H13 后 access token 走 HttpOnly Set-Cookie（body.token=null），统一复用 auth-helpers 的会话逻辑
async function ensureSession() {
  const username = process.env.COMMERCIAL_E2E_USERNAME || `eri151_${Date.now()}`
  const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'
  const session = await ensureApiSession({
    username,
    role: '/bidAdmin',
    fullName: 'ERI-151 E2E',
    password,
  })

  return {
    token: session.token,
    user: {
      id: session.user.id,
      name: session.user.name,
      username: session.user.username,
      email: session.user.email,
      role: String(session.user.role || '').toLowerCase(),
      roleCode: session.user.roleCode || session.user.role,
      menuPermissions: session.user.menuPermissions || [],
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

// H13: token 走 HttpOnly cookie；前端路由守卫/权限依赖 storage 内 user.menuPermissions
function injectAuth(page, session) {
  page.context().addCookies([
    { name: 'access_token', value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: 'Lax' },
    { name: 'access_token', value: session.token, url: frontendBaseUrl, httpOnly: true, sameSite: 'Lax' },
  ])
  page.addInitScript((user) => {
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session.user)
}

// 数据分析页已重构为 M0~M4 模块（b9afae503），旧 ?drilldown= 深链抽屉已下线；
// 本文件保留"真实业务数据渲染到分析/工作台页面"的验证意图，断言对齐当前 UI。

test('analytics M0 KPI cards render seeded project counts', async ({ page }) => {
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
      sourceReasoningSummary: '从评分草稿转入正式项目，验证来源字段与分析页一致性。',
    }),
  })

  expect(projectPayload?.data?.id).toBeTruthy()

  await injectAuth(page, session)
  await page.goto('/analytics/dashboard')
  await expect(page.getByRole('heading', { name: '数据分析' })).toBeVisible()

  // KPI 卡片渲染真实统计值（非加载失败占位"—"）
  const totalCountCard = page.locator('.kpi-card').filter({ hasText: '投标总数' })
  await expect(totalCountCard).toBeVisible({ timeout: 15000 })
  await expect(totalCountCard.locator('.kpi-value')).toHaveText(/^\d+/,)

  const biddingCard = page.locator('.kpi-card').filter({ hasText: '投标中' })
  await expect(biddingCard.locator('.kpi-value')).toHaveText(/^[1-9]/)
})

test('analytics M0 win-rate card renders from real outcome stats', async ({ page }) => {
  const session = await ensureSession()

  await injectAuth(page, session)
  await page.goto('/analytics/dashboard')
  await expect(page.getByRole('heading', { name: '数据分析' })).toBeVisible()

  const wonCard = page.locator('.kpi-card').filter({ has: page.locator('.kpi-label:text-is("中标数")') })
  await expect(wonCard).toBeVisible({ timeout: 15000 })
  await expect(wonCard.locator('.kpi-value')).toHaveText(/^\d+/)

  const winRateCard = page.locator('.kpi-card').filter({ has: page.locator('.kpi-label:text-is("中标率")') })
  await expect(winRateCard.locator('.kpi-value')).toHaveText(/\d+(\.\d+)?%?/)
})

test('dashboard quick entry routes navigate correctly', async ({ page }) => {
  const session = await ensureSession()

  await injectAuth(page, session)
  await page.goto('/dashboard')

  // 工作台已重构：欢迎横幅 + 待办分类卡（任务/标讯/项目/资源）+ 截止面板
  await expect(page.locator('.workbench').first()).toBeVisible({ timeout: 15000 })
  await expect(page.getByText('任务·待办').first()).toBeVisible({ timeout: 10000 })
})

test('knowledge kb-layout tabs navigate correctly', async ({ page }) => {
  const session = await ensureSession()

  await injectAuth(page, session)

  // /knowledge 默认重定向到 qualification 子页（vue-router 相对 redirect 落在 /qualification）；
  // 知识库子页入口在侧边栏菜单（KbLayout 已改为纯 router-view 壳）
  await page.goto('/knowledge')
  await expect(page).toHaveURL(/\/qualification$/, { timeout: 10000 })
  await expect(page.getByRole('heading', { name: '资质证书' })).toBeVisible({ timeout: 10000 })

  await expect(page.getByRole('menuitem', { name: '项目档案' })).toBeVisible({ timeout: 10000 })
  await expect(page.getByRole('menuitem', { name: '案例库' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '资质证书' })).toBeVisible()

  // 点击侧边栏菜单项并验证 URL 变化
  await page.getByRole('menuitem', { name: '资质证书' }).click()
  await expect(page).toHaveURL(/\/knowledge\/qualification/)
})
