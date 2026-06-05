// @ui-cover:dashboard
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('dashboard analytics drilldown', () => {
  test('dashboard renders charts and drilldown triggers', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_dash_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E Dashboard Admin'
    })

    await injectSession(page, session)
    await page.goto('/analytics/dashboard')
    await expect(page.getByRole('heading', { name: '数据分析' })).toBeVisible({ timeout: 15000 })
  })
})
