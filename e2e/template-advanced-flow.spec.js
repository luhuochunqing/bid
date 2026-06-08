// @ui-cover:knowledge
import { test, expect } from '@playwright/test'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'
const username = process.env.COMMERCIAL_E2E_USERNAME || `eri97_${Date.now()}`
const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'
const email = `${username}@example.com`

async function ensureUser() {
  const response = await fetch(`${apiBaseUrl}/api/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      username,
      password,
      email,
      fullName: 'ERI-97 自动化用户',
      role: 'bid_admin',
    }),
  })

  if (response.status !== 201) {
    throw new Error(`Register failed with status ${response.status}`)
  }
}

async function apiLogin() {
  const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username, password }),
  })

  if (!response.ok) {
    throw new Error(`Login failed with status ${response.status}`)
  }

  const payload = await response.json()
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

  if (!response.ok) {
    throw new Error(`API request failed: ${path} -> ${response.status}`)
  }

  return response.json()
}

test('template advanced actions use real API contracts', async ({ page }) => {
  await ensureUser()
  const session = await apiLogin()
  const templateName = `ERI-97 模板 ${Date.now()}`
  const visibleMenuItem = (label) =>
    page.locator('li[role="menuitem"]:visible').filter({ hasText: label }).last()

  const templatePayload = await apiRequest('/api/knowledge/templates', session, {
    method: 'POST',
    body: JSON.stringify({
      name: templateName,
      category: 'TECHNICAL',
      productType: '智慧城市',
      industry: '政府',
      documentType: '商务应答',
      description: 'ERI-97 模板高级能力验证',
      fileSize: '1.8MB',
      tags: ['ERI-97', '模板'],
      createdBy: session.user.id,
    }),
  })

  const templateId = templatePayload?.data?.id
  expect(templateId).toBeTruthy()

  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  await page.goto('/knowledge/template')
  const findTemplateRow = (name) => page.locator('.el-table__row').filter({ hasText: name }).first()
  await page.getByLabel('模板名称').fill(templateName)
  await page.getByRole('button', { name: '搜索' }).click()

  let row = findTemplateRow(templateName)
  await expect(row).toBeVisible()

  await row.getByRole('button', { name: '更多' }).click()
  await visibleMenuItem('复制模板').click()
  const copiedTemplateName = `${templateName}（副本）`
  await expect(page.getByText(copiedTemplateName)).toBeVisible()

  row = findTemplateRow(templateName)
  await row.getByRole('button', { name: '更多' }).click()
  await visibleMenuItem('版本历史').evaluate((element) => element.click())
  const versionDialog = page.getByRole('dialog', { name: '版本历史' })
  await expect(versionDialog).toBeVisible()
  await expect(versionDialog.getByText('v1.0')).toBeVisible()
  await page.keyboard.press('Escape')

  const downloadPromise = page.waitForEvent('download')
  row = findTemplateRow(templateName)
  await row.getByRole('button', { name: '更多' }).click()
  await visibleMenuItem('下载').evaluate((element) => element.click())
  const download = await downloadPromise
  expect(download.suggestedFilename()).toContain(templateName)
})
