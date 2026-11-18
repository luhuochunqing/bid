import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'
import { scoreItemOptionTitle } from '../src/utils/scoreItemOptionDisplay.js'

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
    // 先挂起 score-items 请求等待，避免「加载中但尚未展示空数据警告」的瞬时不可判态导致一致性断言 flaky
    const scoreItemsSettled = page
      .waitForResponse((r) => r.url().includes('/score-items'), { timeout: 10000 })
      .catch(() => null)
    await recommendBtn.click()
    await scoreItemsSettled

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

    // 状态一致性断言：评分项下拉要么有选项，要么明确提示去解析，禁止「壳在数据空」不可判状态
    const emptyAlert = drawer.getByText('暂无评分项数据').first()
    await scoringSelect.click()
    const optionCount = await page.locator('.el-select-dropdown:visible').last()
      .locator('.el-select-dropdown__item').count().catch(() => 0)
    expect(optionCount > 0 || await emptyAlert.isVisible()).toBeTruthy()

    const firstCard = drawer.locator('.recommend-grid .case-card').first()
    if (await firstCard.isVisible().catch(() => false)) {
      await expect(firstCard).not.toContainText(/复用\s+\d+\s*次/)
      await expect(firstCard.getByRole('button', { name: /复用/ })).toBeVisible()
    }
  })

  test('在历史参考和待核验证明之间切换时展示对应结果', async ({ page }) => {
    const projectId = process.env.XIYU_E2E_EVIDENCE_PROJECT_ID
    test.skip(!projectId, '需要测试环境中包含证明条款和普通方案条款的固定项目')
    const proofId = process.env.XIYU_E2E_EVIDENCE_PROOF_ITEM_ID
    const referenceId = process.env.XIYU_E2E_EVIDENCE_REFERENCE_ITEM_ID
    expect(proofId).toBeTruthy()
    expect(referenceId).toBeTruthy()
    expect(proofId).not.toBe(referenceId)

    await loginAsRole(page, '/bidAdmin')
    await page.goto(`/project/${projectId}`)
    await page.locator('.el-step__title').filter({ hasText: /^标书制作$/ }).click()
    const projectPath = `/api/projects/${projectId}`
    const requestItems = page.waitForResponse(response =>
      new URL(response.url()).pathname === `${projectPath}/score-items`
      && response.request().method() === 'GET'
    )
    await page.getByRole('button', { name: 'AI智能推荐案例' }).click()
    const itemsResponse = await requestItems
    expect(itemsResponse.ok()).toBe(true)
    const items = await itemsResponse.json()

    const drawer = page.getByRole('dialog', { name: 'AI 智能案例推荐' })
    const warning = drawer.getByText('当前推荐缺少可核验的证明材料，请人工核验原始材料。', { exact: true })
    const referenceHeading = drawer.getByText('历史案例参考', { exact: false })
    const cards = drawer.locator('.recommend-grid .case-card')

    async function selectAndRecommend(itemId) {
      const item = items.find(row => String(row.id) === itemId)
      expect(item, `项目 ${projectId} 缺少评分项 ${itemId}`).toBeDefined()
      const recommendation = page.waitForResponse(response =>
        new URL(response.url()).pathname === `${projectPath}/cases/recommend`
        && response.request().method() === 'POST'
        && String(response.request().postDataJSON()?.scoreItemId) === itemId
      )
      await drawer.getByRole('combobox').click()
      await page.getByRole('option', { name: scoreItemOptionTitle(item), exact: true }).click()
      const response = await recommendation
      expect(response.ok()).toBe(true)
      return response.json()
    }

    if (String(items[0]?.id) === proofId) await selectAndRecommend(referenceId)
    const proof = await selectAndRecommend(proofId)
    expect(proof).toMatchObject({ evidenceReviewRequired: true, total: 0, items: [] })
    await expect(warning).toBeVisible()
    await expect(referenceHeading).toHaveCount(0)
    await expect(cards).toHaveCount(0)

    const reference = await selectAndRecommend(referenceId)
    expect(reference.evidenceReviewRequired).toBe(false)
    expect(reference.items.length).toBeGreaterThan(0)
    await expect(warning).toHaveCount(0)
    await expect(referenceHeading).toBeVisible()
    await expect(cards.first()).toBeVisible()
  })

  test('案例库页面案例卡片展示正常', async ({ page }) => {
    await loginAsRole(page, '/bidAdmin')
    await page.goto('/knowledge/case')
    await page.waitForSelector('.case-card, .el-empty', { timeout: 10000 })

    // 验证页面标题（h2 实际为 "案例库"）
    await expect(page.getByText('案例库').first()).toBeVisible()

    // 如果有案例卡片，验证基本结构
    const firstCard = page.locator('.case-card').first()
    if (await firstCard.isVisible().catch(() => false)) {
      // 验证评分项标题存在
      await expect(firstCard.locator('.scoring-title')).toBeVisible()
      // 验证创建时间前缀存在（PRD 精简后卡片不再展示复用次数）
      await expect(firstCard).toContainText('创建时间：')
      // 验证复用按钮存在
      await expect(firstCard.getByRole('button', { name: /复用/ })).toBeVisible()
    }
  })
})
