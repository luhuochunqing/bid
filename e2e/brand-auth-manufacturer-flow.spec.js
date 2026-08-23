import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const E2E_PASSWORD = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function loginAsRole(page, role) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    // 前缀必须短：username 硬上限 50 字符（RegisterRequest @Size），bid-projectLeader 角色名本身 17 字符
    username: `ba_${role}_${suffix}`,
    role,
    fullName: `E2E BrandAuth ${role}`,
    password: E2E_PASSWORD
  })
  await injectSession(page, session)
  return session
}

test.describe('品牌授权 §4.6a — 原厂授权', () => {
  // 唯一测试数据：同一 H2 栈内多次运行会累积同名记录，确定性 ID 会触发 strict mode violation
  const uid = Date.now().toString(36)

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

    test('sales (项目负责人) 无法进入品牌授权管理页（路由守卫拦截）', async ({ page }) => {
      await loginAsRole(page, 'bid-projectLeader')
      await page.goto('/knowledge/brand-auth')
      // 权限模型：无 knowledge-brand-auth 权限 → 守卫整页重定向，而非仅隐藏管理按钮
      await page.waitForURL(u => !u.pathname.includes('/knowledge/brand-auth'), { timeout: 10000 })
      await expect(page.getByRole('button', { name: '新增原厂授权' })).toHaveCount(0)
    })
  })

  test.describe('原厂授权创建流程', () => {
    test('bid_admin 创建原厂授权 — 基础字段', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })

      await page.getByRole('button', { name: '新增原厂授权' }).click()
      const drawer = page.getByRole('dialog', { name: '新增原厂授权' })
      await drawer.waitFor({ state: 'visible', timeout: 5000 })

      await page.locator('.el-drawer .el-select').first().click()
      await page.locator('.el-select-dropdown__item:visible', { hasText: '工具' }).first().click()
      // 按语义定位字段（nth 索引会因 进口/国产 radio 无 input 而错位）
      const brandId = `E2E_BR_${uid}_1`
      await drawer.locator('.el-form-item:has(label:text-is("品牌 ID")) input').fill(brandId)
      await drawer.locator('.el-form-item:has(label:text-is("品牌")) input').fill(`E2E测试品牌_${uid}`)
      await drawer.locator('.el-radio:has-text("进口")').click()
      await drawer.locator('.el-form-item:has(label:text-is("品牌原厂名称")) input').fill('E2E测试原厂有限公司')

      const datePickers = page.locator('.el-drawer .el-date-editor .el-input__inner')
      await datePickers.nth(0).fill('2026-01-01')
      await datePickers.nth(1).fill('2027-12-31')

      await page.getByRole('button', { name: '保存' }).click()
      await drawer.waitFor({ state: 'hidden', timeout: 5000 })
      await page.waitForSelector('.el-table', { timeout: 5000 })
      // 回归断言：新增成功后列表必须展示新创建的记录（防止字段路径解析错误导致列表永远空数组）
      await expect(page.getByLabel('原厂授权', { exact: true }).getByText(brandId)).toBeVisible({ timeout: 5000 })
      await expect(page.getByLabel('原厂授权', { exact: true }).getByText(`E2E测试品牌_${uid}`)).toBeVisible({ timeout: 5000 })
    })

    test('必填验证：空字段提交被阻断', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })

      await page.getByRole('button', { name: '新增原厂授权' }).click()
      const drawer = page.getByRole('dialog', { name: '新增原厂授权' })
      await drawer.waitFor({ state: 'visible', timeout: 5000 })

      await page.getByRole('button', { name: '保存' }).click()
      await expect(drawer).toBeVisible()
    })

    test('时间校验：结束时间早于开始时间被阻断', async ({ page }) => {
      await loginAsRole(page, '/bidAdmin')
      await page.goto('/knowledge/brand-auth')
      await page.waitForSelector('.brandauth-container', { timeout: 10000 })

      await page.getByRole('button', { name: '新增原厂授权' }).click()
      const drawer = page.getByRole('dialog', { name: '新增原厂授权' })
      await drawer.waitFor({ state: 'visible', timeout: 5000 })

      await page.locator('.el-drawer .el-select').first().click()
      await page.locator('.el-select-dropdown__item:visible', { hasText: '工具' }).first().click()
      await drawer.locator('.el-form-item:has(label:text-is("品牌 ID")) input').fill(`E2E_BR_${uid}_2`)
      await drawer.locator('.el-form-item:has(label:text-is("品牌")) input').fill('校验测试品牌')
      await drawer.locator('.el-radio:has-text("进口")').click()
      await drawer.locator('.el-form-item:has(label:text-is("品牌原厂名称")) input').fill('校验测试原厂')

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

      // 页面为双 Tab（原厂/代理商）各含一张表，需 .first() 收敛到激活 Tab 的表头
      await expect(page.locator('.el-table__header:has-text("授权编号")').first()).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("一级产线")').first()).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("品牌原厂名称")').first()).toBeVisible()
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
      const drawer = page.getByRole('dialog', { name: '新增代理商授权' })
      await drawer.waitFor({ state: 'visible', timeout: 5000 })

      await page.locator('.el-drawer .el-select').first().click()
      await page.locator('.el-select-dropdown__item:visible', { hasText: '刀具' }).first().click()
      // 按语义定位字段（nth 索引会因 radio/日期框混入 .el-input__inner 而错位）
      const agentBrandId = `E2E_AG_${uid}`
      await drawer.locator('.el-form-item:has(label:text-is("品牌 ID")) input').fill(agentBrandId)
      const agentBrandName = `E2E代理品牌_${uid}`
      await drawer.locator('.el-form-item:has(label:text-is("品牌")) input').fill(agentBrandName)
      await drawer.locator('.el-radio:has-text("进口")').click()
      await drawer.locator('.el-form-item:has(label:text-is("品牌原厂名称")) input').fill('E2E原厂')
      await drawer.locator('.el-form-item:has(label:text-is("代理商名称")) input').fill('E2E代理商')

      // agent 模式无通用授权起止（manufacturer 专属），只有 授权1起止 + 授权2起止 共 4 个日期。
      // 业务规则：授权2（转授权）不得超出授权1 区间 → auth2End < auth1End
      const datePickers = drawer.locator('.el-date-editor .el-input__inner')
      await datePickers.nth(0).fill('2026-01-01')
      await datePickers.nth(1).fill('2027-11-30')
      await datePickers.nth(2).fill('2026-02-01')
      await datePickers.nth(3).fill('2027-06-30')

      await page.getByRole('button', { name: '保存' }).click()
      await drawer.waitFor({ state: 'hidden', timeout: 5000 })
      // 回归断言：新增成功后列表必须展示新创建的代理商授权记录
      await expect(page.getByLabel('代理商授权').getByText(agentBrandId)).toBeVisible({ timeout: 5000 })
      await expect(page.getByLabel('代理商授权').getByText(agentBrandName)).toBeVisible({ timeout: 5000 })
    })
  })
})
