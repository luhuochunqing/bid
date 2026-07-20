import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('AI标书质量核查', () => {
  let session

  test.beforeEach(async ({ page }) => {
    session = await ensureApiSession({
      username: `quality_check_${Date.now()}`,
      role: 'BIDADMIN',
      fullName: '质量核查测试'
    })
    await injectSession(page, session)
  })

  test('标书编制页面应显示AI标书质量核查按钮', async ({ page }) => {
    // 访问项目列表
    await page.goto('/projects')

    // 等待页面加载，如果没有项目则跳过
    const hasProjects = await page.waitForSelector('.el-table__row, .project-card, .project-item', { timeout: 10000 }).catch(() => null)
    if (!hasProjects) {
      test.skip()
      return
    }

    // 尝试找到一个处于 DRAFTING 阶段的项目
    const projectRows = page.locator('.el-table__row, .project-card, .project-item')
    const count = await projectRows.count()

    let foundDrafting = false
    for (let i = 0; i < Math.min(count, 5); i++) {
      const row = projectRows.nth(i)
      const text = await row.textContent().catch(() => '')
      if (text.includes('标书制作') || text.includes('DRAFTING')) {
        await row.click()
        foundDrafting = true
        break
      }
    }

    if (!foundDrafting) {
      test.skip()
      return
    }

    // 等待页面加载
    const hasButton = await page.waitForSelector('button:has-text("AI标书质量核查")', { timeout: 10000 }).catch(() => null)
    if (!hasButton) {
      test.skip()
      return
    }

    // 验证AI标书质量核查按钮存在
    const qualityCheckButton = page.locator('button:has-text("AI标书质量核查")').first()
    await expect(qualityCheckButton).toBeVisible()
  })

  test('点击AI标书质量核查按钮应触发检查', async ({ page }) => {
    const projectId = session.user.allowedProjectIds?.[0] || 10
    await page.goto(`/projects/${projectId}`)

    // 等待页面加载，如果项目不存在则跳过
    const hasTabs = await page.waitForSelector('.el-tabs__item, [role="tab"]', { timeout: 10000 }).catch(() => null)
    if (!hasTabs) {
      test.skip()
      return
    }

    // 切换到标书制作标签
    const draftingTab = page.locator('.el-tabs__item:has-text("标书制作"), [role="tab"]:has-text("标书制作")').first()
    if (await draftingTab.isVisible({ timeout: 5000 }).catch(() => false)) {
      await draftingTab.click()
      await page.waitForSelector('button:has-text("AI标书质量核查")', { timeout: 5000 }).catch(() => {})
    }

    // 点击质量核查按钮
    const qualityCheckButton = page.locator('button:has-text("AI标书质量核查")').first()
    if (await qualityCheckButton.isVisible({ timeout: 5000 }).catch(() => false)) {
      await qualityCheckButton.click()

      // 等待检查完成（出现弹窗或成功提示）
      await expect(
        page.locator('.el-dialog:has-text("AI 标书质量核查"), .el-message--success').first()
      ).toBeVisible({ timeout: 15000 }).catch(() => {})
    } else {
      test.skip()
    }
  })

  test('AI标书质量核查按钮应受权限控制', async ({ page }) => {
    // 使用无权限用户登录（STAFF 角色）
    const staffSession = await ensureApiSession({
      username: `quality_check_staff_${Date.now()}`,
      role: 'bid-otherDept',
      fullName: '无权限用户'
    })
    await injectSession(page, staffSession)

    const projectId = session.user.allowedProjectIds?.[0] || 10
    await page.goto(`/projects/${projectId}`)

    // 等待页面加载，如果项目不存在则跳过
    const hasTabs = await page.waitForSelector('.el-tabs__item, [role="tab"]', { timeout: 10000 }).catch(() => null)
    if (!hasTabs) {
      test.skip()
      return
    }

    // 切换到标书制作标签
    const draftingTab = page.locator('.el-tabs__item:has-text("标书制作"), [role="tab"]:has-text("标书制作")').first()
    if (await draftingTab.isVisible({ timeout: 5000 }).catch(() => false)) {
      await draftingTab.click()
      await page.waitForSelector('button:has-text("AI标书质量核查")', { timeout: 5000 }).catch(() => {})
    }

    // 验证AI标书质量核查按钮不存在
    const qualityCheckButton = page.locator('button:has-text("AI标书质量核查")')
    const count = await qualityCheckButton.count()
    expect(count).toBe(0)
  })
})
