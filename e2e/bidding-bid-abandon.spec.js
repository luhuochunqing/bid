import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
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

test.describe('§4.2.6 bid/abandon buttons', () => {
  test('bid and abandon buttons visible on EVALUATED tender detail page', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `bid_abandon_${suffix}`,
      role: 'bid_admin',
      fullName: 'Bid Abandon E2E',
    })

    const tenderPayload = await apiRequest('/api/tenders', session, {
      method: 'POST',
      body: JSON.stringify({
        title: `§4.2.6 测试标讯 ${suffix}`,
        source: 'E2E',
        budget: 500000,
        deadline: toLocalDateTimeString(new Date(Date.now() + 30 * 86400000)),
        status: 'TRACKING',
      }),
    })
    const tender = tenderPayload?.data
    expect(tender?.id).toBeTruthy()

    // 直接更新标讯状态为 EVALUATED（绕过复杂的评估表提交 API）  // @ui-cover:bidding
    const updatePayload = await apiRequest(`/api/tenders/${tender.id}`, session, {
      method: 'PUT',
      body: JSON.stringify({
        title: `§4.2.6 测试标讯 ${suffix}`,
        source: 'E2E',
        budget: 500000,
        deadline: toLocalDateTimeString(new Date(Date.now() + 30 * 86400000)),
        status: 'EVALUATED',
      }),
    })
    expect(updatePayload?.data?.status, 'tender should be EVALUATED').toBe('EVALUATED')

    await injectSession(page, session)

    // Mock /api/auth/me 避免路由守卫恢复会话时跳转到 /login
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            id: session.user.id,
            username: session.user.username,
            fullName: session.user.name,
            email: session.user.email,
            role: session.user.role,
            roleCode: session.user.role,
            menuPermissions: ['all'],
          },
        }),
      })
    })

    await page.goto(`/bidding/${tender.id}`)
    await expect(page).toHaveURL(new RegExp(`/bidding/${tender.id}$`))

    await expect(page.getByRole('button', { name: '立即投标' })).toBeVisible()
    await expect(page.getByRole('button', { name: '放弃投标' })).toBeVisible()
  })

  test('no bid/abandon buttons on TRACKING tender detail page', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `bid_tracking_${suffix}`,
      role: 'bid_admin',
      fullName: 'Bid Tracking E2E',
    })

    const tenderPayload = await apiRequest('/api/tenders', session, {
      method: 'POST',
      body: JSON.stringify({
        title: `§4.2.6 追踪态测试 ${suffix}`,
        source: 'E2E',
        budget: 500000,
        deadline: toLocalDateTimeString(new Date(Date.now() + 30 * 86400000)),
        status: 'TRACKING',
      }),
    })
    const tender = tenderPayload?.data
    expect(tender?.id).toBeTruthy()

    await injectSession(page, session)

    // Mock /api/auth/me 避免路由守卫恢复会话时跳转到 /login
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            id: session.user.id,
            username: session.user.username,
            fullName: session.user.name,
            email: session.user.email,
            role: session.user.role,
            roleCode: session.user.role,
            menuPermissions: ['all'],
          },
        }),
      })
    })

    await page.goto(`/bidding/${tender.id}`)
    await expect(page).toHaveURL(new RegExp(`/bidding/${tender.id}$`))

    await expect(page.getByRole('button', { name: '立即投标' })).not.toBeVisible()
    await expect(page.getByRole('button', { name: '放弃投标' })).not.toBeVisible()
  })
})
