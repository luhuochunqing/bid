// @ui-cover:ai-center
import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('AI market timing flow', () => {
  test('market timing page loads with title heading', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_mkt_${suffix}`,
      role: 'bid_admin',
      fullName: 'E2E Market Admin'
    })

    await injectSession(page, session)
    await page.goto('/ai-center/market-timing')
    await expect(page.locator('.market-timing-page')).toBeVisible({ timeout: 15000 })
    // Use heading role to avoid matching breadcrumb text
    await expect(page.getByRole('heading', { name: '商机时间预测' })).toBeVisible()
  })
})
