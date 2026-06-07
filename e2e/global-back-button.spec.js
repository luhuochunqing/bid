import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('全局返回按钮', () => {
  let session

  test.beforeAll(async () => {
    const suffix = Date.now()
    session = await ensureApiSession({
      username: `e2e_back_btn_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E Back Button Admin'
    })
  })

  test.beforeEach(async ({ page }) => {
    await injectSession(page, session)
  })

  test('顶级页面不显示返回按钮', async ({ page }) => {
    await page.goto('/bidding')
    await page.waitForLoadState('networkidle')

    const backBtn = page.locator('.back-btn')
    await expect(backBtn).toHaveCount(0)
  })

  test('子页面显示返回按钮并可点击返回', async ({ page }) => {
    await page.goto('/bidding/favorites')
    await page.waitForLoadState('networkidle')

    const backBtn = page.locator('.back-btn')
    await expect(backBtn).toBeVisible()

    await backBtn.click()
    await page.waitForURL('**/bidding')

    // 确认返回后回到了列表页且返回按钮消失
    await expect(page.locator('.back-btn')).toHaveCount(0)
  })

  test('直接访问子页面时返回按钮仍可用', async ({ page }) => {
    // 模拟直接 URL 输入（无浏览器历史记录）
    await page.goto('/bidding/favorites')
    await page.waitForLoadState('networkidle')

    const backBtn = page.locator('.back-btn')
    await expect(backBtn).toBeVisible()

    await backBtn.click()
    await page.waitForURL('**/bidding')
    await expect(page.locator('.back-btn')).toHaveCount(0)
  })

  test('收藏页无多余面包屑行', async ({ page }) => {
    // /bidding/favorites 是 showBack: true 的单层路由
    // 返回按钮在 Header 中（不再占用内容区），面包屑仅在多层路由时显示
    await page.goto('/bidding')
    await page.waitForLoadState('networkidle')

    // 顶级页面：无返回按钮，无面包屑
    await expect(page.locator('.back-btn')).toHaveCount(0)
    await expect(page.locator('.layout-breadcrumb')).toHaveCount(0)

    // 进入子页面：有返回按钮
    await page.goto('/bidding/favorites')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.back-btn')).toBeVisible()

    // 返回按钮点击 → 回到顶部页面
    await page.locator('.back-btn').click()
    await page.waitForURL('**/bidding')
    await expect(page.locator('.back-btn')).toHaveCount(0)
  })
})
