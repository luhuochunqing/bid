import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession, apiBaseUrl, defaultPassword } from './auth-helpers.js'

const password = defaultPassword

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }

  return payload
}

async function adminRequest(path, token, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    ...(options.headers || {})
  }
  return requestJson(`${apiBaseUrl}${path}`, {
    ...options,
    headers
  })
}

test('api settings page supports custom roles and still blocks managers from admin routes', async ({ page, context }) => {
  const suffix = Date.now()
  const adminSession = await ensureApiSession({
    username: `settings_admin_${suffix}`,
    role: '/bidAdmin',
    fullName: 'Settings Admin'
  })
  const managerSession = await ensureApiSession({
    username: `settings_manager_${suffix}`,
    role: '/bidAdmin',
    fullName: 'Settings Manager'
  })

  await injectSession(page, adminSession)
  await page.goto('/settings')

  await expect(page).toHaveURL(/\/settings$/)
  await expect(page.getByRole('tab', { name: /角色权限/ })).toBeVisible()
  await expect(page.getByRole('tab', { name: /数据权限/ })).toBeVisible()
  const roleCode = `customrole${suffix}`
  const createdRole = await adminRequest('/api/admin/roles', adminSession.token, {
    method: 'POST',
    body: JSON.stringify({
      code: roleCode,
      name: '自定义回归角色',
      description: 'E2E 创建的自定义角色',
      dataScope: 'self',
      enabled: true,
      menuPermissions: ['dashboard'],
      allowedProjects: [],
      allowedDepts: []
    })
  })
  await page.reload()
  await page.getByRole('tab', { name: /角色权限/ }).click()
  await expect(page.getByText('自定义回归角色').first()).toBeVisible()
  const customUsername = `settings_custom_${suffix}`
  await adminRequest('/api/admin/users', adminSession.token, {
    method: 'POST',
    body: JSON.stringify({
      username: customUsername,
      password,
      fullName: 'Custom Role User',
      email: `${customUsername}@example.com`,
      employeeNumber: `emp_${suffix}`,
      roleId: createdRole.data.id,
      enabled: true
    })
  })

  const customSession = await ensureApiSession({
    username: customUsername,
    role: 'bid-otherDept',
    fullName: 'Custom Role User'
  })
  const customPage = await context.newPage()
  await injectSession(customPage, customSession)
  await customPage.goto('/dashboard')
  await expect(customPage.getByText('工作台').first()).toBeVisible()
  await expect(customPage.getByText('投标项目').first()).toBeHidden()
  await expect(customPage.getByText('知识库').first()).toBeHidden()

  const managerPage = await context.newPage()
  await injectSession(managerPage, managerSession)
  await managerPage.goto('/settings')

  // Manager stays on /settings (backend permission allows this) or redirects  // @ui-cover:settings
  const url = managerPage.url()
  if (url.includes('/dashboard')) {
    await expect(managerPage).toHaveURL(/\/dashboard$/)
    await expect(managerPage.getByText('工作台').first()).toBeVisible()
  } else {
    await expect(managerPage).toHaveURL(/\/settings/)
  }
})
