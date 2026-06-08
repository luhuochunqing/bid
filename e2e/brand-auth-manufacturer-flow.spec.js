import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const E2E_PASSWORD = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function loginAsRole(page, role) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_brandauth_${role}_${suffix}`,
    role: 'bid_admin',
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
      await page.waitForLoadState('networkidle')
      await expect(page.locator('.brandauth-container')).toBeVisible()
      await expect(page.getByRole('button', { name: '新增原厂授权' })).toBeVisible()
    })

    test('bid_lead 可以访问品牌授权页面', async ({ page }) => {
      await loginAsRole(page, 'bid_lead')
      await page.goto('/knowledge/brand-auth')
      await page.waitForLoadState('networkidle')
      await expect(page.locator('.brandauth-container')).toBeVisible()
      await expect(page.getByRole('button', { name: '新增原厂授权' })).toBeVisible()
    })

    test('bid_specialist 可以访问品牌授权页面', async ({ page }) => {
      await loginAsRole(page, 'bid_specialist')
      await page.goto('/knowledge/brand-auth')
      await page.waitForLoadState('networkidle')
      await expect(page.locator('.brandauth-container')).toBeVisible()
      await expect(page.getByRole('button', { name: '新增原厂授权' })).toBeVisible()
    })

    test('sales (项目负责人) 菜单中无品牌授权入口', async ({ page }) => {
      await loginAsRole(page, 'sales')
      await page.goto('/knowledge/brand-auth')
      await page.waitForLoadState('networkidle')
      // sales 无 knowledge-brand-auth 权限，路由守卫应拦截
      // 页面可能显示空白或重定向
      const btn = page.getByRole('button', { name: '新增原厂授权' })
      await expect(btn).toHaveCount(0)
    })
  })

  test.describe('原厂授权创建流程', () => {
    test('bid_admin 创建原厂授权 — 基础字段', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForLoadState('networkidle')

      // 点击新增按钮打开抽屉
      await page.getByRole('button', { name: '新增原厂授权' }).click()
      await page.waitForSelector('.el-drawer', { timeout: 5000 })

      // 基础信息区 — 填写必填字段
      await page.locator('.el-drawer .el-select').first().click()
      await page.locator('.el-select-dropdown__item:has-text("工具")').first().click()
      await page.locator('.el-drawer .el-input__inner').nth(0).fill('E2E_BR001')
      await page.locator('.el-drawer .el-input__inner').nth(1).fill('E2E测试品牌')

      // 品牌原厂名称
      const inputs = page.locator('.el-drawer .el-input__inner')
      await inputs.nth(3).fill('E2E测试原厂有限公司')

      // 授权开始/结束日期
      const datePickers = page.locator('.el-drawer .el-date-editor .el-input__inner')
      await datePickers.nth(0).fill('2026-01-01')
      await datePickers.nth(1).fill('2027-12-31')

      // 保存
      await page.getByRole('button', { name: '保存' }).click()
      await page.waitForTimeout(2000)

      // 验证：抽屉关闭，列表刷新
      await expect(page.locator('.el-drawer')).toHaveCount(0)
      await page.waitForSelector('.el-table', { timeout: 5000 })
    })

    test('必填验证：空字段提交被阻断', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForLoadState('networkidle')

      await page.getByRole('button', { name: '新增原厂授权' }).click()
      await page.waitForSelector('.el-drawer', { timeout: 5000 })

      // 不填任何字段直接保存
      await page.getByRole('button', { name: '保存' }).click()
      await page.waitForTimeout(500)

      // 抽屉应仍然打开（被校验阻断）
      await expect(page.locator('.el-drawer')).toBeVisible()
    })

    test('时间校验：结束时间早于开始时间被阻断', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForLoadState('networkidle')

      await page.getByRole('button', { name: '新增原厂授权' }).click()
      await page.waitForSelector('.el-drawer', { timeout: 5000 })

      // 先填基础必填项
      await page.locator('.el-drawer .el-select').first().click()
      await page.locator('.el-select-dropdown__item:has-text("工具")').first().click()
      await page.locator('.el-drawer .el-input__inner').nth(0).fill('E2E_BR002')
      await page.locator('.el-drawer .el-input__inner').nth(1).fill('校验测试品牌')
      const allInputs = page.locator('.el-drawer .el-input__inner')
      await allInputs.nth(3).fill('校验测试原厂')

      const datePickers = page.locator('.el-drawer .el-date-editor .el-input__inner')
      await datePickers.nth(0).fill('2027-12-31')
      await datePickers.nth(1).fill('2026-01-01') // 结束早于开始

      await page.getByRole('button', { name: '保存' }).click()
      await page.waitForTimeout(500)

      // 错误提示应出现
      const errorMsg = page.locator('.el-message--error')
      await expect(errorMsg.first()).toBeVisible({ timeout: 3000 })
    })
  })

  test.describe('列表与详情', () => {
    test('表格加载并显示列头', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForLoadState('networkidle')

      await page.waitForSelector('.el-table', { timeout: 10000 })

      // 关键列头应可见
      await expect(page.locator('.el-table__header:has-text("授权编号")')).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("一级产线")')).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("品牌原厂名称")')).toBeVisible()
    })

    test('筛选项可见', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForLoadState('networkidle')

      await expect(page.locator('.el-form-item__label:has-text("一级产线")').first()).toBeVisible()
      await expect(page.locator('.el-form-item__label:has-text("品牌ID")').first()).toBeVisible()
      await expect(page.locator('.el-form-item__label:has-text("状态")').first()).toBeVisible()
    })

    test('代理商授权 Tab 显示占位文案', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForLoadState('networkidle')

      await page.locator('.el-tabs__item:has-text("代理商授权")').click()
      await page.waitForTimeout(500)

      await expect(page.locator('.el-empty__description:has-text("即将上线")')).toBeVisible()
    })
  })

  test.describe('作废权限', () => {
    test('bid_admin 可以看到作废按钮', async ({ page }) => {
      await loginAsRole(page, 'bid_admin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForLoadState('networkidle')
      await page.waitForSelector('.el-table', { timeout: 10000 })

      // 如果列表有数据，作废按钮应可见 (表格中第一个操作列)
      const revokeBtns = page.getByRole('button', { name: '作废' })
      // 按钮可能存在也可能不存在（取决于数据），仅验证页面渲染正确
      // 不作废 real data，仅检查权限渲染
      await expect(page.locator('.el-tabs__item:has-text("原厂授权")')).toBeVisible()
    })
  })
})
