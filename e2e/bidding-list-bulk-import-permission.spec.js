import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * Verifies the batch import button visibility rules per §4.2.1 blueprint:
 * - bid_admin/bid_lead/bid_specialist: can see batch import button
 * - sales (项目负责人): batch import button is hidden
 *
 * Relevant UI change: BiddingPageHeader.vue switched batch import button gate
 * from `canCreateTender` to `canBulkImport`, where
 *   canBulkImport = canCreateTender && userRole !== 'sales'
 */

async function loginAsRoleProfile(page, roleProfile) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `bulk_${roleProfile}_${suffix}`,
    role: 'ADMIN',
    fullName: `Bulk ${roleProfile}`
  })

  await injectSession(page, session)
  return session
}

test.describe('bidding list bulk import button visibility', () => {
  test('bid_admin sees the batch import button', async ({ page }) => {
    await loginAsRoleProfile(page, 'bid_admin')

    await page.goto('/bidding')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // bid_admin has canCreateTender && role !== 'sales', so button is visible  // @ui-cover:bidding
    const bulkImportButton = page.getByRole('button', { name: '批量导入' })
    await expect(bulkImportButton).toBeVisible()
  })

  test('bid_lead sees the batch import button', async ({ page }) => {
    await loginAsRoleProfile(page, 'bid_lead')

    await page.goto('/bidding')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // bid_lead has canCreateTender && role !== 'sales', so button is visible
    const bulkImportButton = page.getByRole('button', { name: '批量导入' })
    await expect(bulkImportButton).toBeVisible()
  })

  test('bid_specialist sees the batch import button', async ({ page }) => {
    await loginAsRoleProfile(page, 'bid_specialist')

    await page.goto('/bidding')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // bid_specialist has canCreateTender && role !== 'sales', so button is visible
    const bulkImportButton = page.getByRole('button', { name: '批量导入' })
    await expect(bulkImportButton).toBeVisible()
  })

  test('sales (项目负责人) does NOT see the batch import button', async ({ page }) => {
    await loginAsRoleProfile(page, 'sales')

    await page.goto('/bidding')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // sales has canCreateTender but role === 'sales', so canBulkImport = false
    const bulkImportButton = page.getByRole('button', { name: '批量导入' })
    await expect(bulkImportButton).not.toBeVisible()
  })
})
