// @ui-cover:knowledge
import { test, expect } from '@playwright/test'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'
const username = process.env.COMMERCIAL_E2E_USERNAME || `eri99_${Date.now()}`
const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'
const email = `${username}@example.com`

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }
  return payload
}

async function ensureSession() {
  try {
    await requestJson(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        password,
        email,
        fullName: 'ERI-99 E2E',
        role: 'ADMIN',
      }),
    })
  } catch (error) {
    if (!String(error.message).includes('409')) throw error
  }

  const payload = await requestJson(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })

  return {
    token: payload.data.token,
    user: {
      id: payload.data.id,
      name: payload.data.fullName || payload.data.username,
      username: payload.data.username,
      role: String(payload.data.role || '').toLowerCase(),
    },
  }
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

  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  await page.goto(`/knowledge/case/detail?id=${caseId}`)
  await expect(page.getByText(`ERI-99 案例 ${suffix}`)).toBeVisible()

  await page.getByRole('button', { name: '编辑案例' }).click()
  await page.getByLabel('案例标题').locator('..').getByRole('textbox').fill(`ERI-99 案例 ${suffix} 已更新`)
  await page.getByLabel('项目概述').locator('..').getByRole('textbox').fill('案例内容已更新')
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.getByText('案例更新成功')).toBeVisible()
  await expect(page.getByText(`ERI-99 案例 ${suffix} 已更新`)).toBeVisible()
  await expect(page.getByText('案例内容已更新')).toBeVisible()

  await page.getByRole('button', { name: '引用此案例' }).click()
  await expect(page.getByText('案例已添加到引用列表')).toBeVisible()
  await expect(page.getByText('已被引用 3 次')).toBeVisible()

  const referencePayload = await authedJson(`/api/knowledge/cases/${caseId}/references`, session)
  expect(Array.isArray(referencePayload?.data)).toBeTruthy()
  expect(referencePayload.data.length).toBeGreaterThan(0)

  await page.goto('/knowledge/case')
  await page.getByText('传统案例库').click()
  await page.getByLabel('关键词').locator('..').getByRole('textbox').fill(excerptOnlyKeyword)
  await page.getByRole('button', { name: '搜索' }).click()
  await expect(page.getByText(`ERI-99 案例 ${suffix} 已更新`)).toBeVisible()
})
