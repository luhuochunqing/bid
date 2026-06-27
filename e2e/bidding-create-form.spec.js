import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('标讯创建表单', () => {
  let session

  test.beforeAll(async () => {
    session = await ensureApiSession({
      username: `e2e_bid_create_${Date.now()}`,
      role: 'bid_admin',
      fullName: 'E2E Bid Create'
    })
  })

  test.beforeEach(async ({ page }) => {
    await injectSession(page, session)
  })

  test('表单字段与蓝图对齐', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForLoadState('networkidle')

    // 标题应为"项目名称"而非"标讯标题"
    const label = page.locator('.el-form-item__label:has-text("项目名称")')
    await expect(label.first()).toBeVisible()

    // 不应有预算金额、标签、来源平台字段
    await expect(page.locator('.el-form-item__label:has-text("预算金额")')).toHaveCount(0)
    await expect(page.locator('.el-form-item__label:has-text("标签")')).toHaveCount(0)
    await expect(page.locator('.el-form-item__label:has-text("来源平台")')).toHaveCount(0)
  })
})
