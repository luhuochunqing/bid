// @ui-cover:knowledge
import { test, expect } from '@playwright/test'
import { ensureApiSession } from './auth-helpers.js'
const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1323'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'
const username = process.env.COMMERCIAL_E2E_USERNAME || `eri99_${Date.now()}`
const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function ensureSession() {
  // H13 后 access token 走 HttpOnly Set-Cookie（body.token=null），统一复用 auth-helpers
  const session = await ensureApiSession({
    username,
    role: '/bidAdmin',
    fullName: 'ERI-99 E2E',
    password,
  })
  return {
    token: session.token,
    user: {
      id: session.user.id,
      name: session.user.name,
      username: session.user.username,
      role: String(session.user.role || '').toLowerCase(),
    },
  }
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }
  return payload
}

async function authedJson(path, session, options = {}) {
  return requestJson(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
    },
  })
}

test('case detail advanced actions use real backend contracts', async ({ page }) => {
  const session = await ensureSession()
  const suffix = Date.now()
  const excerptOnlyKeyword = `星河联动中台-${suffix}`
  const casePayload = await authedJson('/api/knowledge/cases', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `ERI-99 案例 ${suffix}`,
      industry: 'INFRASTRUCTURE',
      outcome: 'WON',
      amount: 520,
      projectDate: '2025-04-01',
      description: '案例初始摘要',
      customerName: '测试客户',
      locationName: '杭州',
      projectPeriod: '2025-01-01 - 2025-12-31',
      documentSnapshotText: excerptOnlyKeyword,
      tags: ['智慧园区', '政务'],
      highlights: ['统一门户', '一网统管'],
      technologies: ['Vue', 'Spring Boot'],
      viewCount: 10,
      useCount: 2,
    }),
  })

  const caseId = casePayload?.data?.id
  expect(caseId).toBeTruthy()

  await page.context().addCookies([{ name: "access_token", value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: "Lax" }, { name: "access_token", value: session.token, url: frontendBaseUrl, httpOnly: true, sameSite: "Lax" }])
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  // 独立案例详情页已下线（/knowledge/case/detail 路由不存在）；
  // 编辑/引用的真实后端契约改在 API 层验证，UI 出口为 AI 方案复用页
  const updatedTitle = `ERI-99 案例 ${suffix} 已更新`
  const updatePayload = await authedJson(`/api/knowledge/cases/${caseId}`, session, {
    method: 'PUT',
    body: JSON.stringify({
      title: updatedTitle,
      industry: 'INFRASTRUCTURE',
      outcome: 'WON',
      amount: 520,
      projectDate: '2025-04-01',
      description: '案例内容已更新',
      customerName: '测试客户',
    }),
  })
  expect(updatePayload?.data?.title).toBe(updatedTitle)
  expect(updatePayload?.data?.description).toBe('案例内容已更新')

  await authedJson(`/api/knowledge/cases/${caseId}/references`, session, {
    method: 'POST',
    body: JSON.stringify({
      referencedBy: session.user.id,
      referencedByName: 'ERI-99 E2E',
      referenceTarget: 'E2E 引用目标项目',
      referenceContext: 'E2E 复用引用',
    }),
  })

  const referencePayload = await authedJson(`/api/cases/${caseId}/references`, session)
  // GET /api/cases/{id}/references 返回裸数组（非 ApiResponse 包装）
  expect(Array.isArray(referencePayload)).toBeTruthy()

  // UI 出口：方案复用页搜索可见更新后的案例
  await page.goto('/ai-center/solution-reuse')
  const keywordInput = page.getByPlaceholder('输入方案名称、行业、关键词...')
  await keywordInput.fill(updatedTitle)
  await page.getByRole('button', { name: /搜索|查询/ }).first().click()
  await expect(page.getByText(updatedTitle).first()).toBeVisible({ timeout: 10000 })
})
