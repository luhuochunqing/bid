import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('case grid', () => {
  test('page loads and renders case list', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_casegrid_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E CaseGrid Admin'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')

    // Page title visible (tab header)  // @ui-cover:knowledge
    await expect(page.getByText('AI 案例库网格')).toBeVisible({ timeout: 10000 })

    // Filter form present
    await expect(page.getByPlaceholder('搜索打分点/需求/应答...')).toBeVisible()

    // Case grid renders (at least empty state or loading)
    await page.waitForLoadState('networkidle')
  })

  test('keyword filter triggers search', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_casegrid_filter_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E CaseGrid Filter'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('networkidle')

    const input = page.getByPlaceholder('搜索打分点/需求/应答...')
    await input.fill('测试关键词')
    await page.getByRole('button', { name: '搜索' }).click()
    await page.waitForLoadState('networkidle')

    // No error toast should appear (basic smoke test)
    await expect(page.locator('.el-message--error')).toHaveCount(0, { timeout: 3000 })
  })

  test('customer type filter is accessible', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_casegrid_ctype_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E CaseGrid CType'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('networkidle')

    const select = page.locator('.el-select').filter({ hasText: '选择客户类型' }).first()
    await expect(select).toBeVisible()
    await select.click()
    await expect(page.locator('.el-select-dropdown__item')).toHaveCount.greaterThan(0)
    await page.keyboard.press('Escape')
  })

  test('date range picker is accessible', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_casegrid_date_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E CaseGrid Date'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('networkidle')

    const picker = page.locator('.el-date-editor').filter({ hasText: '创建时间' }).first()
    await expect(picker).toBeVisible()
  })
})
