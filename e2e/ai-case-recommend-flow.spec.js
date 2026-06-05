import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

async function loginAsRole(page, role) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_${role}_${suffix}`,
    role,
    fullName: `E2E ${role} 测试`
  })
  await injectSession(page, session)
  return session
}

test.describe('AI智能案例推荐', () => {
  test('标书编制阶段可见 AI 智能推荐按钮', async ({ page }) => {
    await loginAsRole(page, 'bid_admin')
    await page.goto('/projects')
    await page.waitForSelector('.el-table, .el-empty', { timeout: 10000 })

    // 如果有项目则进入第一个项目
    const firstRow = page.locator('.el-table__row').first()
    if (await firstRow.isVisible().catch(() => false)) {
      await firstRow.locator('td').first().click()
      await page.waitForTimeout(2000)

      // 切换到标书编制阶段
      const draftingTab = page.getByText('标书编制')
      if (await draftingTab.isVisible().catch(() => false)) {
        await draftingTab.click()
        await page.waitForTimeout(1000)

        // 验证 AI 智能推荐按钮可见
        const recommendBtn = page.getByRole('button', { name: /AI 智能推荐/ })
        await expect(recommendBtn).toBeVisible()
      }
    }
  })

  test('bid_specialist 可见 AI 智能推荐按钮', async ({ page }) => {
    await loginAsRole(page, 'bid_specialist')
    await page.goto('/projects')
    await page.waitForSelector('.el-table, .el-empty', { timeout: 10000 })

    const firstRow = page.locator('.el-table__row').first()
    if (await firstRow.isVisible().catch(() => false)) {
      await firstRow.locator('td').first().click()
      await page.waitForTimeout(2000)

      const draftingTab = page.getByText('标书编制')
      if (await draftingTab.isVisible().catch(() => false)) {
        await draftingTab.click()
        await page.waitForTimeout(1000)

        const recommendBtn = page.getByRole('button', { name: /AI 智能推荐/ })
        await expect(recommendBtn).toBeVisible()
      }
    }
  })

  test('打开 AI 推荐抽屉并验证基础结构', async ({ page }) => {
    await loginAsRole(page, 'bid_admin')
    await page.goto('/projects')
    await page.waitForSelector('.el-table, .el-empty', { timeout: 10000 })

    const firstRow = page.locator('.el-table__row').first()
    if (await firstRow.isVisible().catch(() => false)) {
      await firstRow.locator('td').first().click()
      await page.waitForTimeout(2000)

      const draftingTab = page.getByText('标书编制')
      if (await draftingTab.isVisible().catch(() => false)) {
        await draftingTab.click()
        await page.waitForTimeout(1000)

        const recommendBtn = page.getByRole('button', { name: /AI 智能推荐/ })
        if (await recommendBtn.isVisible().catch(() => false)) {
          await recommendBtn.click()
          await page.waitForTimeout(1000)

          // 验证抽屉标题
          const drawerTitle = page.locator('.el-drawer__header span').filter({ hasText: 'AI 智能推荐' })
          await expect(drawerTitle).toBeVisible()

          // 验证评分项下拉存在
          const scoringSelect = page.locator('.el-select').filter({ hasText: '评分项' })
          await expect(scoringSelect).toBeVisible()

          // 验证关键词输入存在
          const keywordInput = page.locator('input[placeholder*="关键词"]').first()
          await expect(keywordInput).toBeVisible()
        }
      }
    }
  })

  test('案例库页面案例卡片展示正常', async ({ page }) => {
    await loginAsRole(page, 'bid_admin')
    await page.goto('/knowledge/case')
    await page.waitForSelector('.case-card, .el-empty', { timeout: 10000 })

    // 验证页面标题
    await expect(page.getByText('AI 案例库网格').or(page.getByText('案例库'))).toBeVisible()

    // 如果有案例卡片，验证基本结构
    const firstCard = page.locator('.case-card').first()
    if (await firstCard.isVisible().catch(() => false)) {
      // 验证评分项标题存在
      await expect(firstCard.locator('.scoring-title')).toBeVisible()
      // 验证复用次数存在
      await expect(firstCard.locator('.reuse-stat')).toBeVisible()
      // 验证复用按钮存在
      await expect(firstCard.getByRole('button', { name: /复用/ })).toBeVisible()
    }
  })
})
