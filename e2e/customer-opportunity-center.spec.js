// @ui-cover:bidding,dashboard
import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

async function loginAsApiUser(page) {
  const session = await ensureApiSession({
    username: `customer_center_${Date.now()}`,
    role: 'MANAGER',
    fullName: 'Customer Opportunity Manager'
  })
  await injectSession(page, session)
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/dashboard$/)
}

test.describe('customer opportunity center', () => {
  test('api delivery mode hides customer opportunity center entry points', async ({ page }) => {
    await loginAsApiUser(page)

    await page.goto('/bidding')
    await expect(page.getByRole('button', { name: '客户商机中心' })).toHaveCount(0)

    await page.goto('/bidding/customer-opportunities')

    await expect(page).toHaveURL(/\/bidding$/)
    await expect(page.getByRole('heading', { name: '标讯中心' })).toBeVisible()
  })
})
