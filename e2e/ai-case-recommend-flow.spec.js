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

async function gotoFirstProjectDraftingStage(page) {
  await page.goto('/project')
  await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
  const firstRow = page.locator('.el-table__row').first()
  if (!(await firstRow.isVisible().catch(() => false))) return false
  // 点项目名称链接（行内"查看详情"链接）
  await firstRow.locator('.project-name-link').first().click()
  // 等路由跳到 /project/<id>
  await page.waitForURL(/\/project\/\d+/, { timeout: 15000 }).catch(() => null)
  // 等 el-tabs nav 出现
  await page.waitForSelector('.el-tabs__nav', { timeout: 15000 }).catch(() => null)
  // 切换到"标书制作" Tab（DRAFTING stage，label 是"标书制作"而非"标书编制"）
  const draftingTab = page.locator('.el-tabs__item').filter({ hasText: '标书制作' }).first()
  if (!(await draftingTab.isVisible().catch(() => false))) return false
  await draftingTab.click()
  // 等待 DraftingStage 渲染：bid-header-actions 区域出现
  await page.waitForSelector('.bid-header-actions, .el-button', { timeout: 10000 }).catch(() => null)
  return true
}

test.describe('AI智能案例推荐', () => {
  test('标书编制阶段可见 AI 智能推荐按钮', async ({ page }) => {
    await loginAsRole(page, '/bidAdmin')
    const reached = await gotoFirstProjectDraftingStage(page)
    if (!reached) {
      test.skip(true, '演示数据无项目可进入 DRAFTING stage')
      return
    }
    // 验证 AI 智能推荐按钮可见
    const recommendBtn = page.locator('.bid-header-actions').getByRole('button', { name: /AI智能推荐案例/ })
    await expect(recommendBtn).toBeVisible()
  })

  test('bid_specialist 可见 AI 智能推荐按钮', async ({ page }) => {
    await loginAsRole(page, 'bid-Team')
    const reached = await gotoFirstProjectDraftingStage(page)
    if (!reached) {
      test.skip(true, '演示数据无项目可进入 DRAFTING stage')
      return
    }
    const recommendBtn = page.locator('.bid-header-actions').getByRole('button', { name: /AI智能推荐案例/ })
    await expect(recommendBtn).toBeVisible()
  })

  test('打开 AI 推荐抽屉并验证基础结构', async ({ page }) => {
    await loginAsRole(page, '/bidAdmin')
    const reached = await gotoFirstProjectDraftingStage(page)
    if (!reached) {
      test.skip(true, '演示数据无项目可进入 DRAFTING stage')
      return
    }

    const recommendBtn = page.locator('.bid-header-actions').getByRole('button', { name: /AI智能推荐案例/ })
    if (!(await recommendBtn.isVisible().catch(() => false))) {
      test.skip(true, 'AI 智能推荐按钮在 DRAFTING stage 不可见')
      return
    }
    await recommendBtn.click()

    // 等待抽屉打开（el-drawer open state）
    const drawer = page.locator('.el-drawer.open, .el-drawer__open').first()
    await expect(drawer).toBeVisible({ timeout: 5000 })

    // 验证抽屉标题
    const drawerTitle = drawer.locator('.el-drawer__header').filter({ hasText: 'AI 智能推荐' })
    await expect(drawerTitle).toBeVisible()

    // 验证评分项下拉存在（Element Plus el-select 在抽屉内）
    const scoringSelect = drawer.locator('.el-select').first()
    await expect(scoringSelect).toBeVisible()

    // 验证关键词输入存在
    const keywordInput = drawer.locator('input[placeholder*="关键词"]').first()
    await expect(keywordInput).toBeVisible()
  })

  test('案例库页面案例卡片展示正常', async ({ page }) => {
    await loginAsRole(page, '/bidAdmin')
    await page.goto('/knowledge/case')
    await page.waitForSelector('.case-card, .el-empty', { timeout: 10000 })

    // 验证页面标题
    await expect(page.getByText('AI 案例库网格').first()).toBeVisible()

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
