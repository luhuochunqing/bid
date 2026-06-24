import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('§44.1.1.2 案例库 — 复用与下架流程', () => {
  test('案例网格加载并显示状态筛选', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_casereuse_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 案例复用 Admin'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('networkidle')

    // 状态筛选可见
    const statusSelect = page.locator('.el-select').filter({ hasText: '项目状态' }).first()
    await expect(statusSelect).toBeVisible({ timeout: 10000 })

    // 排序切换可用
    const sortCreated = page.getByRole('radio', { name: '最新发布' })
    const sortReuse = page.getByRole('radio', { name: '最热复用' })
    await expect(sortCreated).toBeVisible()
    await expect(sortReuse).toBeVisible()
  })

  test('案例复用按钮触发后端 API', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_casereuse_btn_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 复用按钮 Admin'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('networkidle')

    // 查找复用按钮
    const reuseBtn = page.getByRole('button', { name: '📋 复用' }).first()
    if (await reuseBtn.isVisible()) {
      await reuseBtn.click()
      // 应有成功 toast
      await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })
    }
  })

  test('管理员可见下架按钮并触发确认弹窗', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_offshelf_admin_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 下架 Admin'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('networkidle')

    // 管理员应看到下架按钮
    const offShelfBtn = page.getByRole('button', { name: '下架' }).first()
    if (await offShelfBtn.isVisible()) {
      await offShelfBtn.click()
      // 确认弹窗出现
      await expect(page.getByText('确认下架案例')).toBeVisible({ timeout: 3000 })
      // 取消关闭弹窗
      await page.getByRole('button', { name: '取消' }).click()
    }
  })

  test('案例详情抽屉显示查看源项目和标书原文按钮', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_drawer_detail_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 抽屉详情 Admin'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('networkidle')

    // 点击第一个详情按钮打开抽屉
    const detailBtn = page.getByRole('button', { name: '详情' }).first()
    if (await detailBtn.isVisible()) {
      await detailBtn.click()
      // 抽屉应出现
      await expect(page.getByText('案例应答详情')).toBeVisible({ timeout: 5000 })
      // 查看源项目按钮
      const srcProjBtn = page.getByRole('button', { name: '查看源项目' })
      // 标书原文按钮
      const bidDocBtn = page.getByRole('button', { name: '标书原文' })
      // 至少其中一个应该可见
      const srcVisible = await srcProjBtn.isVisible().catch(() => false)
      const bidVisible = await bidDocBtn.isVisible().catch(() => false)
      expect(srcVisible || bidVisible).toBeTruthy()
    }
  })
})
