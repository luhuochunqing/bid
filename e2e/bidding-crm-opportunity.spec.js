// @ui-cover:bidding
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('bidding CRM opportunity', () => {
  test('opportunity center is enabled in bidding page', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_crm_${suffix}`,
      role: 'MANAGER',
      fullName: 'E2E CRM Manager'
    })

    await injectSession(page, session)
    await page.goto('/bidding')
    await expect(page.getByText('标讯列表')).toBeVisible()
  })
})
