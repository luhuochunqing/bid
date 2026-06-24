// @ui-cover:document
import { test, expect } from '@playwright/test'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'

async function apiLogin() {
  const username = process.env.COMMERCIAL_E2E_USERNAME || `eri100_${Date.now()}`
  const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'
  let payload

  try {
    const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })

    if (!response.ok) throw new Error(`Login failed with status ${response.status}`)
    payload = await response.json()
  } catch {
    const response = await fetch(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        password,
        email: `${username}@example.com`,
        fullName: 'ERI-100 E2E',
        role: '/bidAdmin',
      }),
    })

    if (!response.ok) throw new Error(`Register failed with status ${response.status}`)
    payload = await response.json()
  }

  const auth = payload?.data
  if (!payload?.success || !auth?.token || !auth?.id) {
    throw new Error('Login payload missing token or user identity')
  }

  return {
    token: auth.token,
    user: {
      id: auth.id,
      name: auth.fullName || auth.username,
      username: auth.username,
      email: auth.email,
      role: String(auth.role || '').toLowerCase(),
    },
  }
}

async function apiRequest(path, session, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
      ...(options.headers || {}),
    },
  })

  if (!response.ok) throw new Error(`API request failed: ${path} -> ${response.status}`)
  return response.json()
}

function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

test('document editor export and archive use real API workflows', async ({ page }) => {
  const session = await apiLogin()
  const suffix = Date.now()

  const tenderPayload = await apiRequest('/api/tenders', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `ERI-100 文档归档标讯 ${suffix}`,
      source: 'Playwright',
      budget: 860000,
      deadline: toLocalDateTimeString(new Date(Date.now() + 14 * 24 * 60 * 60 * 1000)),
      status: 'TRACKING',
      aiScore: 88,
      riskLevel: 'LOW',
    }),
  })

  const tenderId = tenderPayload?.data?.id
  expect(tenderId).toBeTruthy()

  const projectPayload = await apiRequest('/api/projects', session, {
    method: 'POST',
    body: JSON.stringify({
      name: `ERI-100 文档归档项目 ${suffix}`,
      tenderId,
      status: 'BIDDING',
      managerId: session.user.id,
      teamMembers: [session.user.id],
      startDate: toLocalDateTimeString(new Date()),
      endDate: toLocalDateTimeString(new Date(Date.now() + 10 * 24 * 60 * 60 * 1000)),
    }),
  })

  const project = projectPayload?.data
  expect(project?.id).toBeTruthy()
  const projectId = project.id

  const structurePayload = await apiRequest(`/api/documents/${projectId}/editor/structure`, session, {
    method: 'POST',
    body: JSON.stringify({ projectId, name: `ERI-100 文档结构 ${projectId}` }),
  })

  const structureId = structurePayload?.data?.id
  expect(structureId).toBeTruthy()

  const sectionPayload = await apiRequest(`/api/documents/${projectId}/editor/sections`, session, {
    method: 'POST',
    body: JSON.stringify({
      structureId,
      sectionType: 'SECTION',
      title: '导出归档章节',
      content: '用于验证真实导出与归档',
      orderIndex: 1,
    }),
  })

  expect(sectionPayload?.data?.id).toBeTruthy()

  await page.context().addCookies([{ name: "access_token", value: session.token, url: "http://127.0.0.1:18080", httpOnly: true, sameSite: "Lax" }, { name: "access_token", value: session.token, url: "http://127.0.0.1:1314", httpOnly: true, sameSite: "Lax" }])
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  await page.goto(`/document/editor/${projectId}`)
  await expect(page.getByText('章节目录')).toBeVisible()
  await expect(page.getByText(project.name).first()).toBeVisible()
  await expect(page.locator('.section-tree-card').getByText('导出归档章节', { exact: true })).toBeVisible()

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出' }).click()
  const download = await downloadPromise
  await expect(page.getByText('导出成功')).toBeVisible()
  expect(download.suggestedFilename()).toContain('document_export')
  await expect(page.getByText('导出历史')).toBeVisible()
  await expect(page.getByText('document_export.json')).toBeVisible()

  await page.getByRole('button', { name: '归档' }).click()
  await expect(page.getByText('归档成功')).toBeVisible()
  await expect(page.getByText('归档记录')).toBeVisible()

  const archivePayload = await apiRequest(`/api/documents/${projectId}/archive-records`, session)
  expect(Array.isArray(archivePayload?.data)).toBeTruthy()
  expect(archivePayload.data.length).toBeGreaterThan(0)

  const exportPayload = await apiRequest(`/api/documents/${projectId}/exports`, session)
  expect(Array.isArray(exportPayload?.data)).toBeTruthy()
  expect(exportPayload.data.length).toBeGreaterThan(0)
})
