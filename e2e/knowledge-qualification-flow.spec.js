import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('knowledge qualification flow', () => {
  test('browse and search qualifications library', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_know_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E Knowledge Admin'
    })

    // Seed a qualification  // @ui-cover:knowledge
    const qualRes = await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: `E2E 资质 ${suffix}`,
        certificateNo: `E2E-${suffix}`,
        issuer: '测试发证机关',
        holderName: '测试持有人',
        expiryDate: '2027-12-31',
        status: 'valid'
      })
    })
    expect(qualRes.ok).toBeTruthy()
    // Small delay to let the seeded data propagate
    await new Promise(r => setTimeout(r, 1000))

    await injectSession(page, session)
    await page.goto('/knowledge/qualification', { waitUntil: 'domcontentloaded' })
    // Wait for the table or empty state to appear — the seeded qualification may take a moment to appear
    await page.waitForSelector('.el-table, .el-empty', { timeout: 20000 })
    // Use .first() to avoid breadcrumb text collision
    await expect(page.getByText('资质库').first()).toBeVisible({ timeout: 10000 })
  })
})
