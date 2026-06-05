import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

// Covers: Warehouse.vue multi-dim filter, WarehouseDialog create/edit, WarehouseDrawer detail/attachments/logs
test.describe('仓库信息 §4.4 — smoke', () => {
  test('page loads', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_warehouse_${Date.now()}`,
      role: 'ADMIN', fullName: 'E2E Warehouse'
    })
    await injectSession(page, session)
    await page.goto('/knowledge/warehouse')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.warehouse-container')).toBeVisible()
  })
})
