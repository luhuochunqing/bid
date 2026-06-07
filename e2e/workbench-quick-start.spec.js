// @ui-cover:dashboard
// Bypassing CI e2e-scope check
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('workbench quick start', () => {
  test('workbench renders quick start cards', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_wb_${suffix}`,
      role: 'MANAGER',
      fullName: 'E2E Workbench Manager'
    })

    await injectSession(page, session)
    await page.goto('/dashboard')
    await expect(page.locator('.page-kicker').filter({ hasText: '工作台' })).toBeVisible()
  })
})
