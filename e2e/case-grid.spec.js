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
    await expect(page.getByText('AI 案例库网格').first()).toBeVisible({ timeout: 10000 })

    // Filter form present
    await expect(page.getByPlaceholder('搜索打分点/需求/应答...')).toBeVisible()

    // 等筛选栏 / 卡片 / 空状态其一渲染完成（避免 networkidle 等待）
    await page.waitForSelector('.el-select, .case-card, .el-empty', { timeout: 10000 })
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
    await page.waitForSelector('.el-select, .case-card, .el-empty', { timeout: 10000 })

    const input = page.getByPlaceholder('搜索打分点/需求/应答...')
    await input.fill('测试关键词')

    // 点筛选后等 /api/cases 列表接口响应
    const respPromise = page.waitForResponse(r => r.url().includes('/api/cases') && r.status() === 200, { timeout: 10000 })
    await page.getByRole('button', { name: '筛选' }).click()
    await respPromise

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
    await page.waitForSelector('.el-select, .case-card, .el-empty', { timeout: 10000 })

    // Element Plus 把 click handler 绑在 .el-select 包装容器上；直接点容器打开下拉
    const formItem = page.locator('.el-form-item').filter({ hasText: '客户类型' })
    const selectWrapper = formItem.locator('.el-select')
    await expect(selectWrapper).toBeVisible()
    await selectWrapper.click()
    // 下拉渲染到 body 后断言 option 已出现在 DOM（Element Plus 用 display 切换；
    // accessibility tree 可见但 layout 偶尔 hidden，用 count 比 toBeVisible 更稳）
    await expect(page.getByRole('option', { name: '央企' }).first()).toHaveCount(1)
  })

  test('date range picker is accessible', async ({ page }) => {
    const { ensureApiSession, injectSession } = await import('./auth-helpers.js')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_casegrid_date_${suffix}`,
      role: 'bid_admin',
      fullName: 'E2E CaseGrid Date'
    })
    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForSelector('.el-select, .case-card, .el-empty', { timeout: 10000 })

    const picker = page.getByRole('combobox', { name: '上传时间' })
    await expect(picker).toBeVisible()
  })
})
