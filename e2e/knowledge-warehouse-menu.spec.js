import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * 仓库信息 §4.4 — 知识库仓库入口验证
 *
 * 覆盖范围：
 * - 侧边栏「知识库」菜单中存在"仓库信息"子菜单项
 * - 直接访问 /knowledge/warehouse 时仓库管理页面正常加载
 * - 从其他知识库页面通过侧边栏菜单可导航到 /knowledge/warehouse
 *
 * 背景：KbLayout.vue 已重构为纯 router-view 包装（无 Tab 栏），
 * 仓库信息入口统一通过侧边栏菜单导航。测试用 admin 角色确保拥有
 * knowledge + knowledge-warehouse 子权限。
 */
test.describe('仓库信息 §4.4 — 知识库仓库入口', () => {
  test('直接访问 /knowledge/warehouse 时仓库管理页面正常加载', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_wh_nav_${Date.now()}`,
      role: 'admin',
      fullName: 'E2E WH Nav'
    })
    await injectSession(page, session)

    await page.goto('/knowledge/warehouse')
    await page.waitForLoadState('domcontentloaded')

    // 仓库管理页面标题应可见（验证页面正确加载）
    await expect(page.getByRole('heading', { name: '仓库管理' })).toBeVisible({ timeout: 10000 })
  })

  test('侧边栏「知识库」菜单中存在仓库信息子菜单项', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_wh_menu_${Date.now()}`,
      role: 'admin',
      fullName: 'E2E WH Menu'
    })
    await injectSession(page, session)

    // 访问知识库任意子页面，侧边栏应可见
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('domcontentloaded')

    // 等待侧边栏渲染完成
    await page.waitForSelector('.sidebar-container, .sidebar-menu', { timeout: 10000 })

    // hover "知识库" 子菜单标题以展开子菜单
    const knowledgeSubmenu = page.locator('.sidebar-menu .el-sub-menu__title').filter({ hasText: '知识库' })
    await knowledgeSubmenu.hover()

    // 验证子菜单中存在"仓库信息"项
    const warehouseMenuItem = page
      .locator('.sidebar-menu .el-menu-item.sub-menu-item, .sidebar-menu .el-sub-menu .el-menu-item')
      .filter({ hasText: '仓库信息' })
      .first()
    await expect(warehouseMenuItem).toBeVisible({ timeout: 5000 })
  })

  test('从其他知识库页面通过侧边栏菜单可导航到仓库页面', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_wh_sidebar_${Date.now()}`,
      role: 'admin',
      fullName: 'E2E WH Sidebar'
    })
    await injectSession(page, session)

    await page.goto('/knowledge/archive')
    await page.waitForLoadState('domcontentloaded')

    // 等待侧边栏渲染完成
    await page.waitForSelector('.sidebar-container, .sidebar-menu', { timeout: 10000 })

    // hover "知识库" 子菜单标题以展开子菜单
    const knowledgeSubmenu = page.locator('.sidebar-menu .el-sub-menu__title').filter({ hasText: '知识库' })
    await knowledgeSubmenu.hover()

    // 点击展开后的"仓库信息"菜单项
    const warehouseMenuItem = page
      .locator('.sidebar-menu .el-menu-item.sub-menu-item, .sidebar-menu .el-sub-menu .el-menu-item')
      .filter({ hasText: '仓库信息' })
      .first()
    await expect(warehouseMenuItem).toBeVisible({ timeout: 5000 })
    await warehouseMenuItem.click()

    // 应到达仓库页面（toHaveURL 自带等待）
    await expect(page).toHaveURL(/\/knowledge\/warehouse/)

    // 仓库管理页面标题应可见
    await expect(page.getByRole('heading', { name: '仓库管理' })).toBeVisible()
  })
})
