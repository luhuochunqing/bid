// @ui-cover:settings
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('settings integration flow', () => {
  test('integration configuration page renders all cards', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_int_${suffix}`,
      role: 'bid_admin',
      fullName: 'E2E Integration Admin'
    })

    await injectSession(page, session)
    await page.goto('/settings?tab=integration')
    await expect(page.getByText('企业微信')).toBeVisible()
    await expect(page.locator('.integration-section').getByText('CRM').first()).toBeVisible()
  })
})
