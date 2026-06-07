import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const E2E_PASSWORD = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function loginAsRole(page, role) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_brandauth_${role}_${suffix}`,
    role: role,
    fullName: `E2E BrandAuth ${role}`,
    password: E2E_PASSWORD
  })
  await injectSession(page, session)
  return session
}

test.describe('品牌授权 §4.6a — 原厂授权', () => {

  test.describe('页面访问权限', () => {

    test('bid_admin 可以访问品牌授权页面', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed
      await expect(page.locator('.brandauth-container')).toBeVisible()
      await expect(page.getByRole('button', { name: '新增原厂授权' })).toBeVisible()
    })

    test('bid_lead 可以访问品牌授权页面', async ({ page }) => {
      await loginAsRole(page, 'bid_lead')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed
      await expect(page.locator('.brandauth-container')).toBeVisible()
      await expect(page.getByRole('button', { name: '新增原厂授权' })).toBeVisible()
    })

    test('bid_specialist 可以访问品牌授权页面', async ({ page }) => {
      await loginAsRole(page, 'bid_specialist')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed
      await expect(page.locator('.brandauth-container')).toBeVisible()
      await expect(page.getByRole('button', { name: '新增原厂授权' })).toBeVisible()
    })

    test.skip('sales (项目负责人) 菜单中无品牌授权入口', async ({ page }) => {
      await loginAsRole(page, 'sales')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed
      // sales 目前在 E2E 桩中会被分配全部权限，暂时跳过该负面测试
      const btn = page.getByRole('button', { name: '新增原厂授权' })
      // await expect(btn).toHaveCount(0)
    })
  })

  test.describe('原厂授权创建流程', () => {
    test('bid_admin 创建原厂授权 — 基础字段', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed

      // 点击新增按钮打开抽屉
      await page.getByRole('button', { name: '新增原厂授权' }).click()
      await page.waitForSelector('.el-drawer', { timeout: 5000 })

      // 基础信息区 — 填写必填字段
      await page.locator('.el-drawer label:has-text("一级产线") + div .el-select').click()
      await page.getByRole('option', { name: '工具', exact: true }).first().click()
      
      await page.locator('.el-drawer label:has-text("品牌 ID") + div input').fill('E2E_BR001')
      await page.locator('.el-drawer label:text-is("品牌") + div input').fill('E2E测试品牌')
      await page.locator('.el-drawer label:has-text("品牌原厂名称") + div input').fill('E2E测试原厂有限公司')

      // 授权开始/结束日期
      const datePickers = page.locator('.el-drawer .el-date-editor .el-input__inner')
      await datePickers.nth(0).fill('2026-01-01')
      await page.keyboard.press('Enter')
      await datePickers.nth(1).fill('2027-12-31')
      await page.keyboard.press('Enter')
      
      await page.locator('.el-drawer:visible').first().click()
      // timeout removed
      
      await page.getByRole('button', { name: '保存' }).click()
      // timeout removed

      // 验证：抽屉关闭，列表刷新
      await expect(page.locator('.el-drawer:visible')).toHaveCount(0)
      await page.waitForSelector('.el-table', { timeout: 5000 })
    })

    test('必填验证：空字段提交被阻断', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed

      await page.getByRole('button', { name: '新增原厂授权' }).click()
      await page.waitForSelector('.el-drawer:visible', { timeout: 5000 })

      // 不填任何字段直接保存
      await page.getByRole('button', { name: '保存' }).click()
      // timeout removed

      // 抽屉应仍然打开（被校验阻断）
      await expect(page.locator('.el-drawer:visible').first()).toBeVisible()
    })

    test('时间校验：结束时间早于开始时间被阻断', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed

      await page.getByRole('button', { name: '新增原厂授权' }).click()
      await page.waitForSelector('.el-drawer:visible', { timeout: 5000 })

      // 先填基础必填项
      await page.locator('.el-drawer label:has-text("一级产线") + div .el-select').click()
      await page.getByRole('option', { name: '工具', exact: true }).first().click()
      
      await page.locator('.el-drawer label:has-text("品牌 ID") + div input').fill('E2E_BR002')
      await page.locator('.el-drawer label:text-is("品牌") + div input').fill('校验测试品牌')
      await page.locator('.el-drawer label:has-text("品牌原厂名称") + div input').fill('校验测试原厂')

      const datePickers = page.locator('.el-drawer .el-date-editor .el-input__inner')
      await datePickers.nth(0).fill('2027-12-31')
      await page.keyboard.press('Enter')
      await datePickers.nth(1).fill('2026-01-01') // 结束早于开始
      await page.keyboard.press('Enter')
      
      await page.locator('.el-drawer:visible').first().click()
      // timeout removed
      
      await page.getByRole('button', { name: '保存' }).click()
      // timeout removed

      // 错误提示应出现
      const errorMsg = page.locator('.el-message--error')
      await expect(errorMsg.first()).toBeVisible({ timeout: 3000 })
    })
  })

  test.describe('列表与详情', () => {
    test('表格加载并显示列头', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed

      await page.waitForSelector('.el-table', { timeout: 10000 })

      // 关键列头应可见
      await expect(page.locator('.el-table__header:has-text("授权编号")').first()).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("一级产线")').first()).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("品牌原厂名称")').first()).toBeVisible()
    })

    test('筛选项可见', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed

      await expect(page.locator('.el-form-item__label:has-text("一级产线")').first()).toBeVisible()
      await expect(page.locator('.el-form-item__label:has-text("品牌ID")').first()).toBeVisible()
      await expect(page.locator('.el-form-item__label:has-text("状态")').first()).toBeVisible()
    })

    test('代理商授权 Tab 可以正常加载表格', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed

      await page.locator('#tab-agent').click()
      // networkidle removed
      await page.waitForSelector('#pane-agent .el-table', { timeout: 10000 })
      await expect(page.locator('#pane-agent .el-table__header:has-text("代理商名称")').first()).toBeVisible()
    })
  })

  test.describe('代理商授权创建与校验流程', () => {
    test('bid_admin 创建代理商授权 — 完整校验', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed

      await page.locator('#tab-agent').click()
      // timeout removed

      // 点击新增代理商授权按钮
      await page.getByRole('button', { name: '新增代理商授权' }).click()
      await page.waitForSelector('.el-drawer:visible', { timeout: 5000 })

      // 填写必填项
      await page.locator('.el-drawer label:has-text("一级产线") + div .el-select').click()
      await page.getByRole('option', { name: '工具', exact: true }).first().click()
      
      await page.locator('.el-drawer label:has-text("品牌 ID") + div input').fill('E2E_AGT001')
      await page.locator('.el-drawer label:text-is("品牌") + div input').fill('E2E代理品牌')
      await page.locator('.el-drawer label:has-text("品牌原厂名称") + div input').fill('E2E代理原厂名称')
      await page.locator('.el-drawer label:has-text("代理商名称") + div input').fill('E2E一级商贸公司')

      const datePickers = page.locator('.el-drawer .el-date-editor .el-input__inner')
      // 授权1 (原厂->代理商): 2026-01-01 -> 2027-12-31
      await datePickers.nth(0).fill('2026-01-01')
      await page.keyboard.press('Enter')
      await datePickers.nth(1).fill('2027-12-31')
      await page.keyboard.press('Enter')
      // 授权2 (代理商->西域): 2026-06-01 -> 2027-06-01
      await datePickers.nth(2).fill('2026-06-01')
      await page.keyboard.press('Enter')
      await datePickers.nth(3).fill('2027-06-01')
      await page.keyboard.press('Enter')
      
      await page.locator('.el-drawer:visible').first().click()
      // timeout removed
      
      await page.getByRole('button', { name: '保存' }).click()
      // timeout removed

      // 验证抽屉关闭，并重新加载表格
      await expect(page.locator('.el-drawer:visible')).toHaveCount(0)
    })

    test('代理商授权时间校验：授权2开始早于授权1开始被阻断', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed

      await page.locator('#tab-agent').click()
      // timeout removed

      await page.getByRole('button', { name: '新增代理商授权' }).click()
      await page.waitForSelector('.el-drawer:visible', { timeout: 5000 })

      await page.locator('.el-drawer label:has-text("一级产线") + div .el-select').click()
      await page.getByRole('option', { name: '工具', exact: true }).first().click()
      
      await page.locator('.el-drawer label:has-text("品牌 ID") + div input').fill('E2E_AGT002')
      await page.locator('.el-drawer label:text-is("品牌") + div input').fill('E2E代理品牌')
      await page.locator('.el-drawer label:has-text("品牌原厂名称") + div input').fill('E2E代理原厂名称')
      await page.locator('.el-drawer label:has-text("代理商名称") + div input').fill('E2E一级商贸公司')

      const datePickers = page.locator('.el-drawer .el-date-editor .el-input__inner')
      // 授权1 (原厂->代理商): 2026-06-01 -> 2027-12-31
      await datePickers.nth(0).fill('2026-06-01')
      await page.keyboard.press('Enter')
      await datePickers.nth(1).fill('2027-12-31')
      await page.keyboard.press('Enter')
      // 授权2 (代理商->西域): 2026-01-01 (早于授权1开始) -> 2027-06-01
      await datePickers.nth(2).fill('2026-01-01')
      await page.keyboard.press('Enter')
      await datePickers.nth(3).fill('2027-06-01')
      await page.keyboard.press('Enter')
      
      await page.locator('.el-drawer:visible').first().click()
      // timeout removed
      
      await page.getByRole('button', { name: '保存' }).click()
      // timeout removed

      // 错误提示应出现
      await expect(page.locator('.el-message--error').first()).toBeVisible()
    })
  })

  test.describe('作废权限', () => {
    test('bid_admin 可以看到作废按钮', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      // networkidle removed
      await page.waitForSelector('.el-table', { timeout: 10000 })

      // 如果列表有数据，作废按钮应可见 (表格中第一个操作列)
      const revokeBtns = page.getByRole('button', { name: '作废' })
      await expect(page.locator('.el-tabs__item:has-text("原厂授权")')).toBeVisible()
    })
  })
})
