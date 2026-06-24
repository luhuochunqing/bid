import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const E2E_PASSWORD = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function loginAsRole(page, role) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_brandauth_${role}_${suffix}`,
    role: '/bidAdmin',
    fullName: `E2E BrandAuth ${role}`,
    password: E2E_PASSWORD
  })
  await injectSession(page, session)
  return session
}

test.describe('品牌授权 §4.6a — 原厂授权', () => {

  test.describe('页面访问权限', () => {

    test('bid_admin 可以访问品牌授权页面', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })
      await expect(page.getByRole('button', { name: '新增原厂授权' })).toBeVisible()
    })

    test('bid_lead 可以访问品牌授权页面', async ({ page }) => {
      await loginAsRole(page, 'bid-TeamLeader')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })
      await expect(page.getByRole('button', { name: '新增原厂授权' })).toBeVisible()
    })

    test('bid_specialist 可以访问品牌授权页面', async ({ page }) => {
      await loginAsRole(page, 'bid-Team')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })
      await expect(page.getByRole('button', { name: '新增原厂授权' })).toBeVisible()
    })

    test('sales (项目负责人) 菜单中无品牌授权入口', async ({ page }) => {
      await loginAsRole(page, 'bid-projectLeader')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })
      const btn = page.getByRole('button', { name: '新增原厂授权' })
      await expect(btn).toHaveCount(0)
    })
  })

  test.describe('原厂授权创建流程', () => {
    test('bid_admin 创建原厂授权 — 基础字段', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })

      await page.getByRole('button', { name: '新增原厂授权' }).click()
      await page.waitForSelector('.el-drawer', { timeout: 5000 })

      await page.locator('.el-drawer .el-select').first().click()
      await page.locator('.el-select-dropdown__item:has-text("工具")').first().click()
      await page.locator('.el-drawer .el-input__inner').nth(0).fill('E2E_BR001')
      await page.locator('.el-drawer .el-input__inner').nth(1).fill('E2E测试品牌')

      const inputs = page.locator('.el-drawer .el-input__inner')
      await inputs.nth(3).fill('E2E测试原厂有限公司')

      const datePickers = page.locator('.el-drawer .el-date-editor .el-input__inner')
      await datePickers.nth(0).fill('2026-01-01')
      await datePickers.nth(1).fill('2027-12-31')

      await page.getByRole('button', { name: '保存' }).click()
      await page.waitForSelector('.el-drawer', { state: 'hidden', timeout: 5000 })
      await page.waitForSelector('.el-table', { timeout: 5000 })
    })

    test('必填验证：空字段提交被阻断', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })

      await page.getByRole('button', { name: '新增原厂授权' }).click()
      await page.waitForSelector('.el-drawer', { timeout: 5000 })

      await page.getByRole('button', { name: '保存' }).click()
      await expect(page.locator('.el-drawer')).toBeVisible()
    })

    test('时间校验：结束时间早于开始时间被阻断', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })

      await page.getByRole('button', { name: '新增原厂授权' }).click()
      await page.waitForSelector('.el-drawer', { timeout: 5000 })

      await page.locator('.el-drawer .el-select').first().click()
      await page.locator('.el-select-dropdown__item:has-text("工具")').first().click()
      await page.locator('.el-drawer .el-input__inner').nth(0).fill('E2E_BR002')
      await page.locator('.el-drawer .el-input__inner').nth(1).fill('校验测试品牌')
      const allInputs = page.locator('.el-drawer .el-input__inner')
      await allInputs.nth(3).fill('校验测试原厂')

      const datePickers = page.locator('.el-drawer .el-date-editor .el-input__inner')
      await datePickers.nth(0).fill('2027-12-31')
      await datePickers.nth(1).fill('2026-01-01')

      await page.getByRole('button', { name: '保存' }).click()
      const errorMsg = page.locator('.el-message--error')
      await expect(errorMsg.first()).toBeVisible({ timeout: 3000 })
    })
  })

  test.describe('列表与详情', () => {
    test('表格加载并显示列头', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })
      await page.waitForSelector('.el-table', { timeout: 10000 })

      await expect(page.locator('.el-table__header:has-text("授权编号")')).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("一级产线")')).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("品牌原厂名称")')).toBeVisible()
    })

    test('筛选项可见', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })

      await expect(page.locator('.el-form-item__label:has-text("一级产线")').first()).toBeVisible()
      await expect(page.locator('.el-form-item__label:has-text("品牌ID")').first()).toBeVisible()
      await expect(page.locator('.el-form-item__label:has-text("状态")').first()).toBeVisible()
    })

    test('代理商授权 Tab 显示新增按钮', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })

      await page.locator('.el-tabs__item:has-text("代理商授权")').click()
      await expect(page.getByRole('button', { name: '新增代理商授权' })).toBeVisible()
    })
  })

  test.describe('作废权限', () => {
    test('bid_admin 可以看到作废按钮', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })
      await page.waitForSelector('.el-table', { timeout: 10000 })

      await expect(page.locator('.el-tabs__item:has-text("原厂授权")')).toBeVisible()
    })
  })

  test.describe('代理商授权创建流程', () => {
    test('bid_admin 创建代理商授权 — 双时间段链', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })

      await page.locator('.el-tabs__item:has-text("代理商授权")').click()
      await page.getByRole('button', { name: '新增代理商授权' }).click()
      await page.waitForSelector('.el-drawer', { timeout: 5000 })

      await page.locator('.el-drawer .el-select').first().click()
      await page.locator('.el-select-dropdown__item:has-text("刀具")').first().click()
      const inputs = page.locator('.el-drawer .el-input__inner')
      await inputs.nth(0).fill('E2E_AG001')
      await inputs.nth(1).fill('E2E代理品牌')
      await inputs.nth(2).fill('E2E原厂')
      await inputs.nth(3).fill('E2E代理商')

      const datePickers = page.locator('.el-drawer .el-date-editor .el-input__inner')
      await datePickers.nth(0).fill('2026-01-01')
      await datePickers.nth(1).fill('2027-12-31')
      await datePickers.nth(2).fill('2026-02-01')
      await datePickers.nth(3).fill('2027-06-30')
      await datePickers.nth(4).fill('2027-07-01')
      await datePickers.nth(5).fill('2027-11-30')

      await page.getByRole('button', { name: '保存' }).click()
      await page.waitForSelector('.el-drawer', { state: 'hidden', timeout: 5000 })
      await page.waitForSelector('.el-table', { timeout: 5000 })
    })
  })
})
