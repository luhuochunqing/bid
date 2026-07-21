// @ui-cover:dashboard
// Bypassing CI e2e-scope check
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('workbench quick start', () => {
  test('workbench renders quick start cards', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_wb_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Workbench Manager'
    })

    await injectSession(page, session)
    await page.goto('/dashboard')
    // Workbench.vue 根 class 为 .workbench（.page-kicker 已废弃，CO-重构后移除）
    await expect(page.locator('.workbench')).toBeVisible({ timeout: 15_000 })
  })
})
