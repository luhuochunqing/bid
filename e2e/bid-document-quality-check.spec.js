import { test, expect } from '@playwright/test'
import { injectSession } from './auth-helpers.js'

test.describe('AI标书质量核查', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await injectSession(page, { username: 'bid_admin', role: 'bid_admin' })
    await page.goto('/projects')
    await page.waitForLoadState('networkidle')
  })

  test('标书编制页面应显示AI标书质量核查按钮', async ({ page }) => {
    // 点击第一个项目进入详情
    const firstProject = page.locator('.project-card, .project-item, [data-testid="project-card"]').first()
    if (await firstProject.isVisible().catch(() => false)) {
      await firstProject.click()
    } else {
      // 如果没有项目卡片，尝试直接访问一个项目详情
      await page.goto('/projects/1')
    }

    await page.waitForLoadState('networkidle')

    // 切换到标书制作标签
    const draftingTab = page.locator('.el-tabs__item:has-text("标书制作"), [role="tab"]:has-text("标书制作")').first()
    if (await draftingTab.isVisible().catch(() => false)) {
      await draftingTab.click()
      await page.waitForTimeout(500)
    }

    // 验证AI标书质量核查按钮存在
    const qualityCheckButton = page.locator('button:has-text("AI标书质量核查")').first()
    await expect(qualityCheckButton).toBeVisible()
  })

  test('点击AI标书质量核查按钮应触发检查', async ({ page }) => {
    // 进入项目详情标书制作页
    await page.goto('/projects/1')
    await page.waitForLoadState('networkidle')

    const draftingTab = page.locator('.el-tabs__item:has-text("标书制作"), [role="tab"]:has-text("标书制作")').first()
    if (await draftingTab.isVisible().catch(() => false)) {
      await draftingTab.click()
      await page.waitForTimeout(500)
    }

    // 点击质量核查按钮
    const qualityCheckButton = page.locator('button:has-text("AI标书质量核查")').first()
    if (await qualityCheckButton.isVisible().catch(() => false)) {
      await qualityCheckButton.click()

      // 等待检查完成（出现成功提示或结果区域）
      await page.waitForTimeout(2000)

      // 验证有成功提示或结果展示
      const successMsg = page.locator('.el-message--success, .el-notification__content:has-text("标书质量核查完成")')
      const resultArea = page.locator('.bid-files-area, .quality-check-result').first()

      const hasSuccess = await successMsg.isVisible().catch(() => false)
      const hasResult = await resultArea.isVisible().catch(() => false)

      expect(hasSuccess || hasResult).toBeTruthy()
    }
  })

  test('AI标书质量核查按钮应受权限控制', async ({ page }) => {
    // 使用无权限用户登录
    await page.goto('/login')
    await injectSession(page, { username: 'task_executor', role: 'task_executor' })
    await page.goto('/projects/1')
    await page.waitForLoadState('networkidle')

    const draftingTab = page.locator('.el-tabs__item:has-text("标书制作"), [role="tab"]:has-text("标书制作")').first()
    if (await draftingTab.isVisible().catch(() => false)) {
      await draftingTab.click()
      await page.waitForTimeout(500)
    }

    // 验证AI标书质量核查按钮不存在
    const qualityCheckButton = page.locator('button:has-text("AI标书质量核查")')
    const count = await qualityCheckButton.count()
    expect(count).toBe(0)
  })
})
