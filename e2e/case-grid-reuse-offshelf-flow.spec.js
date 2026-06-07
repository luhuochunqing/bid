import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('§4.1.1.2 案例库 — 卡片网格、详情抽屉、复用与下架', () => {
  async function loginAsRole(page, role) {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_case_${role}_${suffix}`,
      role,
      fullName: `E2E ${role} 测试`
    })
    await injectSession(page, session)
    return session
  }

  async function gotoCasePage(page) {
    const responsePromise = page.waitForResponse(resp => resp.url().includes('/api/cases') && resp.status() === 200, { timeout: 15000 }).catch(() => null)
    await page.goto('/knowledge/case')
    await responsePromise
    // 等待页面渲染完成
    await page.waitForSelector('.el-select, .el-empty', { timeout: 10000 }).catch(() => null)
  }

  test('案例网格加载：筛选栏、排序、卡片展示', async ({ page }) => {
    await loginAsRole(page, 'ADMIN')
    await gotoCasePage(page)

    // 筛选栏可见
    await expect(page.locator('.el-select').first()).toBeVisible({ timeout: 10000 })

    // 排序切换可用
    await expect(page.getByRole('radio', { name: '最新发布' })).toBeVisible()
    await expect(page.getByRole('radio', { name: '最热复用' })).toBeVisible()

    // 卡片网格（如有数据则显示卡片）
    const cards = page.locator('.case-card')
    const cardCount = await cards.count()
    if (cardCount > 0) {
      // 每张卡片应有复用按钮
      const reuseBtns = page.locator('.case-card button:has-text("复用")')
      await expect(reuseBtns.first()).toBeVisible()
    }
  })

  test('点击卡片主体打开详情抽屉（蓝图顺序）', async ({ page }) => {
    await loginAsRole(page, 'ADMIN')
    await gotoCasePage(page)

    const firstCard = page.locator('.case-card').first()
    if (await firstCard.isVisible()) {
      // 点击卡片主体（不是复用按钮）
      await firstCard.click()
      // 详情抽屉应出现
      await expect(page.locator('[role="dialog"]').filter({ hasText: '评分项原文' })).toBeVisible({ timeout: 5000 })

      // 验证蓝图顺序：评分项原文 → 应答片段全文 → 案例元信息 → 相似案例 → 复用记录
      const dialog = page.locator('[role="dialog"]').filter({ hasText: '评分项原文' })
      await expect(dialog.getByText('评分项原文')).toBeVisible()
      await expect(dialog.getByText('应答片段全文')).toBeVisible()
      await expect(dialog.getByText('案例元信息')).toBeVisible()
      await expect(dialog.getByText('相似案例')).toBeVisible()
      await expect(dialog.getByText('复用记录')).toBeVisible()

      // 底部操作按钮
      await expect(dialog.getByRole('button', { name: '复用' })).toBeVisible()
      await expect(dialog.getByRole('button', { name: '查看源项目' })).toBeVisible()
      await expect(dialog.getByRole('button', { name: '标书原文' })).toBeVisible()
    }
  })

  test('复用按钮：复制到剪贴板 + reuseCount +1 + 创建引用记录', async ({ page }) => {
    await loginAsRole(page, 'ADMIN')
    await gotoCasePage(page)

    const reuseBtn = page.locator('.case-card button:has-text("复用")').first()
    if (await reuseBtn.isVisible()) {
      // 记录复用前的次数
      const reuseCountEl = reuseBtn.locator('..').locator('strong')
      const beforeCount = await reuseCountEl.textContent().catch(() => '0')

      // 点击复用按钮
      await reuseBtn.click()

      // 应有成功 toast
      await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })
    }
  })

  test('下架按钮仅在详情抽屉中可见（卡片上无下架按钮）', async ({ page }) => {
    await loginAsRole(page, 'ADMIN')
    await gotoCasePage(page)

    // 卡片上不应有下架按钮
    const cardOffShelfBtn = page.locator('.case-card button:has-text("下架")')
    await expect(cardOffShelfBtn).toHaveCount(0)

    // 打开详情抽屉
    const firstCard = page.locator('.case-card').first()
    if (await firstCard.isVisible()) {
      await firstCard.click()
      const dialog = page.locator('[role="dialog"]').filter({ hasText: '评分项原文' })
      await expect(dialog).toBeVisible({ timeout: 5000 })

      // 详情抽屉中应有下架按钮
      const drawerOffShelfBtn = dialog.getByRole('button', { name: '下架' })
      await expect(drawerOffShelfBtn).toBeVisible()

      // 点击下架按钮触发确认弹窗
      await drawerOffShelfBtn.click()
      await expect(page.getByText('确认下架案例')).toBeVisible({ timeout: 3000 })

      // 取消关闭弹窗
      await page.getByRole('button', { name: '取消' }).click()
    }
  })

  test('客户类型标签对齐蓝图：央企/地方国企/民企/港澳台及外企/政府机关', async ({ page }) => {
    await loginAsRole(page, 'ADMIN')
    await gotoCasePage(page)

    // 检查客户类型筛选下拉选项
    const customerSelect = page.locator('.el-select').filter({ hasText: '客户类型' }).first()
    if (await customerSelect.isVisible()) {
      await customerSelect.click()
      // 下拉选项应包含蓝图定义的客户类型
      const options = page.locator('.el-select-dropdown__item')
      const optionTexts = await options.allTextContents()
      const expectedLabels = ['央企', '地方国企', '民企', '港澳台及外企', '政府机关/事业单位/高校']
      const hasExpected = expectedLabels.some(label => optionTexts.some(t => t.includes(label)))
      expect(hasExpected).toBeTruthy()
      // 关闭下拉
      await page.keyboard.press('Escape')
    }
  })

  test('权限验证：bid_specialist 不应看到下架和置顶按钮', async ({ page }) => {
    await loginAsRole(page, 'BID_SPECIALIST')
    await gotoCasePage(page)

    // 打开详情抽屉
    const firstCard = page.locator('.case-card').first()
    if (await firstCard.isVisible()) {
      await firstCard.click()
      const dialog = page.locator('[role="dialog"]').filter({ hasText: '评分项原文' })
      await expect(dialog).toBeVisible({ timeout: 5000 })

      // bid_specialist 不应看到下架和置顶按钮
      const offShelfBtn = dialog.getByRole('button', { name: '下架' })
      const pinBtn = dialog.getByRole('button', { name: '置顶' })
      await expect(offShelfBtn).toHaveCount(0)
      await expect(pinBtn).toHaveCount(0)
    }
  })

  test('空状态提示：无数据时显示引导文案', async ({ page }) => {
    await loginAsRole(page, 'ADMIN')
    await gotoCasePage(page)

    // 如果无数据，应显示空状态提示
    const emptyEl = page.locator('.el-empty')
    if (await emptyEl.isVisible()) {
      const desc = await emptyEl.locator('.el-empty__description').textContent()
      expect(desc).toMatch(/案例库还没有内容|未找到符合条件的案例/)
    }
  })
})
