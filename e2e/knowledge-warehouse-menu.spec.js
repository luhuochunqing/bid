import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * 仓库信息 §4.4 — KbLayout 入口验证
 *
 * 覆盖范围：
 * - KbLayout Tab 栏中存在"仓库信息" Tab
 * - 直接访问 /knowledge/warehouse 时 Tab 正确高亮
 * - 从其他 Tab 点击"仓库信息"可切换到仓库页面
 * - 侧边栏「知识库 → 仓库信息」菜单可导航到 /knowledge/warehouse
 *
 * 背景：PR #536 补全了 sidebar-menu.js 的仓库信息菜单项和 KbLayout.vue 的
 * TAB_ROUTES 映射，e2e-scope 门禁要求有 E2E 测试覆盖变更的 UI 文件。
 */
test.describe('仓库信息 §4.4 — KbLayout 入口', () => {
  test('KbLayout Tab 栏存在仓库信息 Tab', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_wh_menu_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'E2E WH Menu'
    })
    await injectSession(page, session)

    // 访问知识库任意子页面，Tab 栏应始终可见
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    // 验证 Tab 栏中有"仓库信息" Tab
    const warehouseTab = page.locator('.el-tabs__item:has-text("仓库信息")')
    await expect(warehouseTab).toBeVisible()
  })

  test('直接访问 /knowledge/warehouse 时仓库信息 Tab 高亮', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_wh_nav_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'E2E WH Nav'
    })
    await injectSession(page, session)

    await page.goto('/knowledge/warehouse')
    await page.waitForLoadState('networkidle')

    // 仓库信息 Tab 应处于高亮（aria-selected）或 active 状态
    const warehouseTab = page.locator('.el-tabs__item:has-text("仓库信息")')
    await expect(warehouseTab).toBeVisible()
    // Element Plus active tab has .is-active class
    await expect(warehouseTab).toHaveClass(/is-active/)
  })

  test('点击 KbLayout 仓库信息 Tab 可切换到仓库页面', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_wh_tab_click_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'E2E WH Tab Click'
    })
    await injectSession(page, session)

    // 从档案台账 Tab 开始
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    // 点击"仓库信息" Tab
    await page.locator('.el-tabs__item:has-text("仓库信息")').click()
    await page.waitForLoadState('networkidle')

    // URL 应变为 /knowledge/warehouse
    await expect(page).toHaveURL(/\/knowledge\/warehouse/)
  })

  test('侧边栏「知识库 → 仓库信息」菜单可导航到仓库页面', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_wh_sidebar_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'E2E WH Sidebar'
    })
    await injectSession(page, session)

    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    // 等待侧边栏渲染完成
    await page.waitForSelector('.sidebar-container, .sidebar-menu', { timeout: 10000 })

    // 先 hover "知识库" 子菜单标题以展开子菜单
    const knowledgeSubmenu = page.locator('.sidebar-menu .el-sub-menu__title').filter({ hasText: '知识库' })
    await knowledgeSubmenu.hover()
  await expect(page.locator('.sidebar-menu .el-menu-item.sub-menu-item, .sidebar-menu .el-sub-menu .el-menu-item').filter({ hasText: '仓库信息' }).first()).toBeVisible({ timeout: 5000 }).catch(() => {})

    // 点击展开后的"仓库信息"菜单项（el-menu-item 子菜单项）
    const warehouseMenuItem = page.locator('.sidebar-menu .el-menu-item.sub-menu-item, .sidebar-menu .el-sub-menu .el-menu-item').filter({ hasText: '仓库信息' }).first()
    await warehouseMenuItem.click()
    await page.waitForLoadState('networkidle')

    // 应到达仓库页面
    await expect(page).toHaveURL(/\/knowledge\/warehouse/)
  })
})
