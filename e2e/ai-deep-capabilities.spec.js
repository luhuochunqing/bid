// @ui-cover:bidding
import { test, expect } from '@playwright/test'
import { ensureApiSession } from './auth-helpers.js'
const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1323'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'
const username = process.env.COMMERCIAL_E2E_USERNAME || `eri98_${Date.now()}`
const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }

  return payload
}

// H13 后 access token 走 HttpOnly Set-Cookie（body.token=null），统一复用 auth-helpers 会话逻辑
async function ensureSession() {
  const session = await ensureApiSession({
    username,
    role: '/bidAdmin',
    fullName: 'ERI-98 E2E',
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

test('tender AI analysis page uses real backend analysis', async ({ page }) => {
  const session = await ensureSession()
  const suffix = Date.now()
  const tenderPayload = await authedJson('/api/tenders', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `ERI-98 智能分析标讯 ${suffix}`,
      source: 'Playwright',
      budget: 920000,
      deadline: toLocalDateTimeString(new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)),
      status: 'TRACKING',
      aiScore: 0,
      riskLevel: 'MEDIUM',
    }),
  })

  const tenderId = tenderPayload?.data?.id
  expect(tenderId).toBeTruthy()

  // AI 分析结果需先触发生成（GET 为只读，未生成时 404）
  await authedJson(`/api/tenders/${tenderId}/ai-analysis`, session, { method: 'POST' })

  await page.context().addCookies([{ name: "access_token", value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: "Lax" }, { name: "access_token", value: session.token, url: frontendBaseUrl, httpOnly: true, sameSite: "Lax" }])
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  await page.goto(`/bidding/ai-analysis/${tenderId}`)
  await expect(page.getByText('AI分析报告')).toBeVisible()
  await expect(page.getByText(`ERI-98 智能分析标讯 ${suffix}`)).toBeVisible()
  await expect(page.locator('.score-value')).not.toHaveText('0')
  await expect(page.locator('.risk-item').first()).toBeVisible()
  await expect(page.locator('.task-item').first()).toBeVisible()
})
