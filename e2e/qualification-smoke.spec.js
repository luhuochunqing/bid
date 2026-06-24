import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('资质证书 §4.2 — smoke', () => {
  test('page loads with table', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_qual_smoke_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'E2E Qual Smoke'
    })
    await injectSession(page, session)
    await page.goto('/knowledge/qualifications')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.qualification-container')).toBeVisible()
  })
})
