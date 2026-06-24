import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('project create full flow', () => {
  test('create project from tender and verify detail page', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_proj_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Project Admin'
    })

    // Seed a tender  // @ui-cover:project,bidding
    const tenderRes = await fetch(`${apiBaseUrl}/api/tenders`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        title: `E2E 项目标讯 ${suffix}`,
        source: 'E2E',
        budget: 500000,
        deadline: new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 19),
        status: 'TRACKING'
      })
    })
    const tenderData = await tenderRes.json()
    expect(tenderData?.data?.id).toBeTruthy()

    await injectSession(page, session)

    // 在首次页面导航前固定当前用户响应，避免路由守卫恢复会话时跳转到 /login。
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
            menuPermissions: ['all']
          }
        })
      })
    })

    await page.goto('/project')
    await page.waitForLoadState('networkidle')

    // Wait for the page to fully render
    await expect(page.locator('.title').filter({ hasText: '投标项目列表' })).toBeVisible({ timeout: 20000 })

    // Navigate to bidding page
    await page.goto('/bidding')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.card-title').filter({ hasText: '标讯列表' })).toBeVisible({ timeout: 20000 })
  })
})
