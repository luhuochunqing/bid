import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('§44.1.1.2 案例库 — 复用与详情流程', () => {
  test('案例网格加载并显示精简后的筛选区', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_casereuse_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 案例复用 Admin'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('load')

    // 精简后的筛选区：仅关键词/客户类型/项目类型 + 查询/重置
    await expect(page.getByPlaceholder('搜索评分项/要素...')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.el-form-item').filter({ hasText: '客户类型' }).locator('.el-select')).toBeVisible()
    await expect(page.locator('.el-form-item').filter({ hasText: '项目类型' }).locator('.el-select')).toBeVisible()
    await expect(page.getByRole('button', { name: '查询' })).toBeVisible()
    await expect(page.getByRole('button', { name: '重置' })).toBeVisible()

    // 已删除的筛选项不应再出现
    await expect(page.locator('.el-form-item').filter({ hasText: '项目状态' })).toHaveCount(0)
    await expect(page.locator('.el-form-item').filter({ hasText: '上传时间' })).toHaveCount(0)
    await expect(page.getByRole('radio', { name: '最热复用' })).toHaveCount(0)
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
    await page.waitForLoadState('load')

    // 查找卡片复用按钮（空库时跳过）
    const reuseBtn = page.locator('.case-card').getByRole('button', { name: '复用' }).first()
    if (await reuseBtn.isVisible()) {
      await reuseBtn.click()
      // 应有成功 toast
      await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })
    }
  })

  test('案例详情弹窗不再包含置顶/下架操作', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_detail_actions_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 详情操作 Admin'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('load')

    // 点击第一个案例卡片打开详情弹窗（空库时跳过）
    const firstCard = page.locator('.case-card').first()
    if (await firstCard.isVisible()) {
      await firstCard.click()
      await expect(page.getByText('案例元信息')).toBeVisible({ timeout: 5000 })
      // 置顶/下架按钮已按需求移除
      await expect(page.getByRole('button', { name: '下架' })).toHaveCount(0)
      await expect(page.getByRole('button', { name: '置顶' })).toHaveCount(0)
    }
  })

  test('案例详情弹窗显示元信息与查看源项目/标书原文按钮', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_dialog_detail_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 弹窗详情 Admin'
    })

    await injectSession(page, session)
    await page.goto('/knowledge/case')
    await page.waitForLoadState('load')

    // 点击第一个案例卡片打开弹窗（空库时跳过）
    const firstCard = page.locator('.case-card').first()
    if (await firstCard.isVisible()) {
      await firstCard.click()
      // 元信息区块按 PRD 2.3.3 展示
      await expect(page.getByText('评分项详细要素原文')).toBeVisible({ timeout: 5000 })
      await expect(page.getByText('案例元信息')).toBeVisible()
      await expect(page.getByText('招标主体')).toBeVisible()
      // 相似案例/复用记录区块已按需求移除
      await expect(page.getByText('相似案例')).toHaveCount(0)
      await expect(page.getByText('复用记录')).toHaveCount(0)
      // 底部操作按钮
      await expect(page.getByRole('button', { name: '查看源项目' })).toBeVisible()
      await expect(page.getByRole('button', { name: '标书原文' })).toBeVisible()
    }
  })
})
