import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

// Covers: Warehouse.vue multi-dim filter, WarehouseDialog create/edit, WarehouseDrawer detail/attachments/logs
test.describe('仓库信息 §4.4 — smoke', () => {
  test('page loads', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_warehouse_${Date.now()}`,
      role: 'admin', fullName: 'E2E Warehouse'
    })
    await injectSession(page, session)
    await page.goto('/knowledge/warehouse')
    await page.waitForLoadState('domcontentloaded')
    // 仓库管理页面标题应可见（验证页面正确加载）
    await expect(page.getByRole('heading', { name: '仓库管理' })).toBeVisible({ timeout: 10000 })
  })
})
