import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * §4.2.3 人工录入 E2E 测试。
 * 覆盖：bid_admin 登录 → 人工录入按钮 → 跳转 → 表单填写 → 保存 → 重复检测弹窗。
 *
 * 依赖：
 * - 后端已实现 TenderDeduplicationService.checkDuplicate()
 * - 后端已实现 TenderBasicInfoValidator.validateBasicInfo()
 * - 前端 BiddingPageHeader.vue emit('open-manual-add')
 * - 前端 useManualTenderCreate.js saveManualTender() 调用 tendersApi.create()
 *
 * 注意：E2E 动态注册用户时，role 参数映射到 RoleProfile.code（如 "bid_admin"），
 * 含 bidding.create 权限，可显示"人工录入"按钮。
 */
const E2E_PASSWORD = 'XiyuDemo!2026'

async function loginAsBidAdmin(page) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_manual_${suffix}`,
    role: '/bidAdmin',   // → RoleProfile 'bid_admin' 含 bidding.create
    fullName: 'E2E 人工录入测试'
  })
  await injectSession(page, session)
  return session
}

function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

function futureDateStr(days) {
  return toLocalDateTimeString(new Date(Date.now() + days * 86400000))
}

// 创建页表单已按 tender.entry schema 重排：无"招标人/采购人名称"字段，
// 区域为 cascader（选择总部所在地），日期 placeholder 带"选择"前缀。
// 去重四元组 = 招标主体 + 项目类型 + 报名截止时间 + 开标时间（TenderDeduplicationPolicy）。
async function fillManualTenderForm(page, purchaser, dates = {}) {
  await page.locator('input[placeholder="请输入项目名称"]').first().fill('E2E-人工录入标讯-' + purchaser.slice(-13))
  await page.locator('input[placeholder="请输入招标主体"]').fill(purchaser)
  await page.locator('input[placeholder="联系人姓名"]').first().fill('测试联系人')
  await page.locator('input[placeholder="手机号"]').first().fill('13800138000')

  const formItemByLabel = (label) => page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: label }) })

  // 总部所在地 cascader（北京市 → 北京市）
  await formItemByLabel('总部所在地').locator('.el-cascader').click()
  await page.locator('.el-cascader-panel .el-cascader-menu').nth(0).locator('.el-cascader-node').filter({ hasText: '北京市' }).first().click()
  await page.locator('.el-cascader-panel .el-cascader-menu').nth(1).locator('.el-cascader-node').filter({ hasText: '北京市' }).first().click()

  // 客户类型 / 优先级 / 项目类型（label 定位避免索引漂移）
  await formItemByLabel('客户类型').locator('.el-select').click()
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '央企' }).first().click()
  await formItemByLabel('优先级').locator('.el-select').click()
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'A 级' }).first().click()
  await formItemByLabel('项目类型').locator('.el-select').click()
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '工业品' }).first().click()

  // 日期（el-date-picker fill 后需 Enter 提交 v-model；分钟精度，秒位补 00）
  const dateStr = dates.dateStr || futureDateStr(10).slice(0, 16)
  const dateStr2 = dates.dateStr2 || futureDateStr(15).slice(0, 16)
  const deadlineInput = page.locator('input[placeholder="选择报名截止时间"]')
  await deadlineInput.click()
  await deadlineInput.fill(dateStr)
  await deadlineInput.press('Enter')
  const bidOpenInput = page.locator('input[placeholder="选择开标时间"]')
  await bidOpenInput.click()
  await bidOpenInput.fill(dateStr2)
  await bidOpenInput.press('Enter')
  return { dateStr, dateStr2 }
}

async function saveManualTender(page) {
  const saveButton = page.getByRole('button', { name: '保存' })
  await expect(saveButton).toBeEnabled({ timeout: 10000 })
  await saveButton.click()
}

test.describe('§4.2.3 人工录入', () => {

  test.beforeEach(async ({ page }) => {
    // 使用 bid_admin RoleProfile（含 bidding.create 权限）  // @ui-cover:bidding
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_manual_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E bid_admin'
    })
    await injectSession(page, session)
  })

  test('bid_admin 点击【人工录入】→ 跳转 /bidding/create', async ({ page }) => {
    await page.goto('/bidding')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // bid_admin 含 bidding.create 权限，【人工录入】按钮应可见
    const manualAddButton = page.getByRole('button', { name: '人工录入' })
    await expect(manualAddButton).toBeVisible()
    await manualAddButton.click()

    // 应跳转到创建页
    await expect(page).toHaveURL(/\/bidding\/create/)
  })

  test('填写基本信息（必填项）→ 保存 → 跳转详情页（编辑模式）', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    const purchaser = 'E2E-自动化测试标讯-' + Date.now()
    await fillManualTenderForm(page, purchaser)

    // 点击保存按钮
    await saveManualTender(page)

    // 应出现成功提示
    await expect(page.locator('.el-message--success').or(page.locator('.el-message')).filter({ hasText: '成功' }))
      .toBeVisible({ timeout: 8000 })

    // 应跳转到详情页（详情路由为 /bidding/:id）
    await expect(page).toHaveURL(/\/bidding\/\d+$/)
  })

  test('填写重复的招标主体+项目类型+截止时间+开标时间 → 保存 → 提示标讯已存在', async ({ page }) => {
    const session = await loginAsBidAdmin(page)
    const authHeaders = { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` }

    const uniqueSuffix = Date.now()
    const purchaser = 'E2E-重复检测-' + uniqueSuffix
    // UI 与 API seed 共用同一份分钟级时间（UI 保存时秒位固定为 :00，
    // 库内保留秒级精度，seed 若带随机秒会导致去重判定不相等）
    const dateStr = futureDateStr(10).slice(0, 16)
    const dateStr2 = futureDateStr(15).slice(0, 16)
    const seedDeadline = dateStr.replace(' ', 'T') + ':00'
    const seedBidOpen = dateStr2.replace(' ', 'T') + ':00'

    // 先通过 API 创建一笔标讯（与后续 UI 填写构成相同去重四元组）
    await fetch(`${apiBaseUrl}/api/tenders`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({
        title: 'E2E-重复检测标讯-' + uniqueSuffix,
        source: 'MANUAL_SINGLE',
        purchaserName: purchaser,
        projectType: '工业品',
        registrationDeadline: seedDeadline,
        deadline: seedDeadline,
        bidOpeningTime: seedBidOpen,
        region: '北京市北京市',
        priority: 'A',
        customerType: '央企',
        status: 'PENDING_ASSIGNMENT'
      })
    })

    // 打开创建页，填写相同的去重四元组
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    await fillManualTenderForm(page, purchaser, { dateStr, dateStr2 })

    // 点击保存
    await saveManualTender(page)

    // 应出现错误提示（409/400 响应，前端统一显示 ElMessage.error）
    await expect(page.locator('.el-message--error').filter({ hasText: /已存在/i }))
      .toBeVisible({ timeout: 10000 })
  })
})

// ---------------------------------------------------------------------------
// §2.5 标讯评估 — CRM商机字段交互（蓝图要求）
// ---------------------------------------------------------------------------
test.describe('§2.5 CRM商机字段（创建表单）', () => {
  test('创建页展示 CRM 商机中心关联指引（商机选择框已迁移至商机中心）', async ({ page }) => {
    const session = await loginAsBidAdmin(page)
    await injectSession(page, session)

    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    // CRM 商机选择框已从创建表单迁移：评估表 Tab 展示商机中心关联指引，且不再出现旧必填提示
    await page.locator('.el-tabs__item:has-text("项目评估表")').click()
    await expect(page.getByText('请在CRM商机中心编辑项目评估表后，关联到该标讯')).toBeVisible()
  })

  test('CRM商机选择框下方不应再有旧的“跟踪中状态必填”提示', async ({ page }) => {
    const session = await loginAsBidAdmin(page)
    await injectSession(page, session)

    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    // 确认旧提示文案已被移除（蓝图2.5 修复）
    await expect(page.getByText('标讯分配项目负责人后（跟踪中状态），此字段必填')).toHaveCount(0)
  })
})
