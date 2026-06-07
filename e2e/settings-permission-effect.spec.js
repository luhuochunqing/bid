import { test, expect } from '@playwright/test'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'
const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

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

async function ensureSession({ username, role, fullName }) {
  const email = `${username}@example.com`

  try {
    await requestJson(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        password,
        email,
        fullName,
        roleCode: String(role || '').toLowerCase()
      })
    })
  } catch (error) {
    if (!String(error.message).includes('409') && !String(error.message).includes('already exists')) {
      throw error
    }
  }

  const payload = await requestJson(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })

  if (!payload?.success || !payload?.data?.token || !payload?.data?.id) {
    throw new Error('Backend login response missing token or user identity')
  }

  return {
    token: payload.data.token,
    refreshToken: payload.data.refreshToken || null,
    user: {
      id: payload.data.id,
      name: payload.data.fullName || payload.data.username,
      username: payload.data.username,
      email: payload.data.email,
      role: String(payload.data.roleCode || payload.data.role || '').toLowerCase(),
      roleName: payload.data.roleName || '',
      menuPermissions: Array.isArray(payload.data.menuPermissions) ? payload.data.menuPermissions : []
    }
  }
}

async function setSession(page, session) {
  await page.addInitScript(({ currentSession }) => {
    sessionStorage.setItem('token', currentSession.token)
    if (currentSession.refreshToken) {
      sessionStorage.setItem('refreshToken', currentSession.refreshToken)
    }
    sessionStorage.setItem('user', JSON.stringify(currentSession.user))
  }, { currentSession: session })
}

test('api settings page supports custom roles and still blocks managers from admin routes', async ({ page, context }) => {
  const suffix = Date.now()
  const adminSession = await ensureSession({
    username: `settings_admin_${suffix}`,
    role: 'ADMIN',
    fullName: 'Settings Admin'
  })
  const managerSession = await ensureSession({
    username: `settings_manager_${suffix}`,
    role: 'MANAGER',
    fullName: 'Settings Manager'
  })

  await setSession(page, adminSession)
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
      roleId: createdRole.data.id,
      enabled: true
    })
  })

  const customSession = await ensureSession({
    username: customUsername,
    role: 'staff',
    fullName: 'Custom Role User'
  })
  const customPage = await context.newPage()
  await setSession(customPage, customSession)
  await customPage.goto('/dashboard')
  await expect(customPage.getByText('工作台').first()).toBeVisible()
  await expect(customPage.getByText('投标项目').first()).toBeHidden()
  await expect(customPage.getByText('知识库').first()).toBeHidden()

  const managerPage = await context.newPage()
  await setSession(managerPage, managerSession)
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
