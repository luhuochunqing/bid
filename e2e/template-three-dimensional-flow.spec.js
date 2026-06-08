// @ui-cover:knowledge
import { test, expect } from '@playwright/test'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'
const username = process.env.COMMERCIAL_E2E_USERNAME || `eri97_tpl_${Date.now()}`
const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'
const email = `${username}@example.com`

async function ensureUser() {
  const response = await fetch(`${apiBaseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username,
      password,
      email,
      fullName: 'ERI-97 模板测试用户',
      role: 'bid_admin'
    })
  })

  if (response.status !== 201) {
    throw new Error(`Register failed with status ${response.status}`)
  }
}

async function apiLogin() {
  const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
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
      role: String(auth.role || '').toLowerCase()
    }
  }
}

async function apiRequest(path, session, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
      ...(options.headers || {})
    }
  })

  if (!response.ok) {
    throw new Error(`API request failed: ${path} -> ${response.status}`)
  }

  return response.json()
}

function formSelect(container, label) {
  return container.locator('.el-form-item').filter({ hasText: label }).locator('.el-select').first()
}

test('template page supports three-dimensional filters and edit flow through real API', async ({ page }) => {
  await ensureUser()
  const session = await apiLogin()
  const templateName = `三维模板 ${Date.now()}`

  const createPayload = await apiRequest('/api/knowledge/templates', session, {
    method: 'POST',
    body: JSON.stringify({
      name: templateName,
      category: 'TECHNICAL',
      productType: '智慧交通',
      industry: '交通',
      documentType: '技术方案',
      description: '三维筛选验证模板',
      fileSize: '1.2 MB',
      tags: ['E2E', '三维分类'],
      createdBy: session.user.id
    })
  })

  const templateId = createPayload?.data?.id
  expect(templateId).toBeTruthy()

  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  await page.goto('/knowledge/template')
  const filterPanel = page.locator('.search-card')
  const nameFilter = page.getByLabel('模板名称')

  await page.getByRole('button', { name: '新建模板' }).click()
  const createDialog = page.getByRole('dialog', { name: '新建模板' })
  await expect(createDialog).toBeVisible()
  await createDialog.getByLabel('模板名称表单').fill(`缺少三维 ${Date.now()}`)
  await createDialog.getByRole('button', { name: '创建模板' }).click()
  await expect(page.getByText('请选择产品类型、行业和文档类型')).toBeVisible()
  await expect(createDialog).toBeVisible()
  await createDialog.getByRole('button', { name: '取消' }).click()

  await formSelect(filterPanel, '产品类型').click()
  await page.getByRole('option', { name: '智慧交通', exact: true }).click()
  await formSelect(filterPanel, '行业').click()
  await page.getByRole('option', { name: '交通', exact: true }).click()
  await formSelect(filterPanel, '文档类型').click()
  await page.getByRole('option', { name: '技术方案', exact: true }).click()
  await page.getByRole('button', { name: '搜索' }).click()

  const row = page.locator('.el-table__row').filter({ hasText: templateName }).first()
  await expect(row).toBeVisible()

  await nameFilter.fill(`不存在-${Date.now()}`)
  await page.getByRole('button', { name: '搜索' }).click()
  await expect(page.locator('.el-table__empty-block, .el-empty').first()).toBeVisible()

  await nameFilter.fill('')
  await page.getByRole('button', { name: '搜索' }).click()
  await expect(page.locator('.el-table__row').filter({ hasText: templateName }).first()).toBeVisible()

  const industrySelect = formSelect(filterPanel, '行业')
  await industrySelect.hover()
  await industrySelect.locator('.el-icon-circle-close').click()
  await page.getByRole('button', { name: '搜索' }).click()
  await expect(page.locator('.el-table__row').filter({ hasText: templateName }).first()).toBeVisible()

  await row.getByRole('button', { name: '更多' }).click()
  await page.locator('li[role="menuitem"]:visible').filter({ hasText: '编辑模板' }).last().click()

  const editDialog = page.getByRole('dialog', { name: '编辑模板' })
  await expect(editDialog).toBeVisible()
  await formSelect(editDialog, '行业').click()
  await page.getByRole('option', { name: '政府', exact: true }).click()
  await editDialog.getByRole('button', { name: '保存' }).click()

  await page.getByRole('button', { name: '重置' }).click()
  await formSelect(filterPanel, '行业').click()
  await page.getByRole('option', { name: '政府', exact: true }).click()
  await page.getByRole('button', { name: '搜索' }).click()

  await expect(page.locator('.el-table__row').filter({ hasText: templateName }).first()).toBeVisible()
})
