// @ui-cover:ai-center
import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('AI solution reuse flow', () => {
  test('solution reuse page loads with title heading', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_sol_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E Solution Admin'
    })

    await injectSession(page, session)
    await page.goto('/ai-center/solution-reuse')
    await expect(page.locator('.solution-reuse-page')).toBeVisible({ timeout: 15000 })
    // Use heading role to avoid matching breadcrumb text (breadcrumb also shows the same title)
    await expect(page.getByRole('heading', { name: '历史方案提取与复用' })).toBeVisible()
  })
})
