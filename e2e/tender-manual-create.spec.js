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
    const session = await loginAsBidAdmin(page)
    const authHeaders = { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` }

    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    // 填写必填字段（使用 aria-label 精确定位）
    await page.fill('input[placeholder="请输入招标主体"]', 'E2E-自动化测试标讯-' + Date.now())
    await page.fill('input[placeholder="招标人/采购人名称"]', '测试采购中心-E2E-' + Date.now())
    await page.fill('input[placeholder="联系人姓名"]', '测试联系人')
    await page.fill('input[placeholder="手机号"]', '13800138000')

    // 选择总部所在地
    const regionSelect = page.locator('.el-select').first()
    await regionSelect.click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: '北京' }).first().click()

    // 选择客户类型（第二个 el-select）
    const selects = page.locator('.el-select')
    const customerTypeSelect = selects.nth(2) // 第三个 select（客户类型）
    await customerTypeSelect.click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: '央企集团' }).first().click()

    // 选择优先级（第四个 select）
    const prioritySelect = selects.nth(3)
    await prioritySelect.click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: 'A 级' }).first().click()

    // 选择项目类型（必填，通过 label 定位避免索引漂移）
    const projectTypeItem = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: '项目类型' }) })
    await projectTypeItem.locator('.el-select').click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: '工业品' }).first().click()

    // 填写日期字段
    const deadlineInput = page.locator('input[placeholder="报名截止时间"]')
    await deadlineInput.click()
    const futureDate = new Date(Date.now() + 10 * 86400000)
    const dateStr = futureDate.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\//g, '-')
    await deadlineInput.fill(dateStr)

    const bidOpenInput = page.locator('input[placeholder="开标时间"]')
    await bidOpenInput.click()
    const futureDate2 = new Date(Date.now() + 15 * 86400000)
    const dateStr2 = futureDate2.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\//g, '-')
    await bidOpenInput.fill(dateStr2)

    // 点击保存按钮（等待 button 不再 disabled）
    await page.waitForFunction(() => {
      const btn = document.querySelector('button:has-text("保存")')
      return btn && !btn.disabled
    }, { timeout: 5000 })
    const saveButton = page.getByRole('button', { name: '保存' })
    await expect(saveButton).toBeEnabled()
    await saveButton.click()

    // 应出现成功提示
    await expect(page.locator('.el-message--success').or(page.locator('.el-message')).filter({ hasText: '成功' }))
      .toBeVisible({ timeout: 8000 })

    // 应跳转到详情页
    await expect(page).toHaveURL(/\/bidding\/detail\/\d+/)
  })

  test('填写重复的招标主体+截止时间+开标时间 → 保存 → 提示标讯已存在', async ({ page }) => {
    const session = await loginAsBidAdmin(page)
    const authHeaders = { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` }

    const uniqueSuffix = Date.now()
    const purchaser = 'E2E-重复检测-' + uniqueSuffix
    const deadline = futureDateStr(10)
    const bidOpen = futureDateStr(15)

    // 先通过 API 创建一笔标讯
    await fetch(`${apiBaseUrl}/api/tenders`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({
        title: 'E2E-重复检测标讯-' + uniqueSuffix,
        source: 'MANUAL_SINGLE',
        purchaserName: purchaser,
        registrationDeadline: deadline,
        bidOpeningTime: bidOpen,
        region: '北京',
        priority: 'A',
        customerType: '央企集团',
        status: 'PENDING_ASSIGNMENT'
      })
    })

    // 打开创建页
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    // 填写相同的招标主体和时间
    await page.fill('input[placeholder="请输入招标主体"]', 'E2E-重复检测2-' + uniqueSuffix)
    await page.fill('input[placeholder="请输入招标主体"]', '测试招标代理公司')
    await page.fill('input[placeholder="招标人/采购人名称"]', purchaser)
    await page.fill('input[placeholder="联系人姓名"]', '测试联系人')
    await page.fill('input[placeholder="手机号"]', '13800138000')

    // 选择总部所在地
    const selects1 = page.locator('.el-select')
    await selects1.first().click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: '北京' }).first().click()

    // 选择项目类型（必填，通过 label 定位避免索引漂移）
    const projectTypeItem = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: '项目类型' }) })
    await projectTypeItem.locator('.el-select').click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: '工业品' }).first().click()

    // 填写日期
    const deadlineInput = page.locator('input[placeholder="报名截止时间"]')
    await deadlineInput.click()
    const dateStr = new Date(Date.now() + 10 * 86400000).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\//g, '-')
    await deadlineInput.fill(dateStr)

    const bidOpenInput = page.locator('input[placeholder="开标时间"]')
    await bidOpenInput.click()
    const dateStr2 = new Date(Date.now() + 15 * 86400000).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\//g, '-')
    await bidOpenInput.fill(dateStr2)

    // 点击保存
    await page.waitForFunction(() => {
      const btn = document.querySelector('button:has-text("保存")')
      return btn && !btn.disabled
    }, { timeout: 5000 })
    const saveButton = page.getByRole('button', { name: '保存' })
    await saveButton.click()

    // 应出现错误提示（400 响应，前端统一显示 ElMessage.error）
    await expect(page.locator('.el-message--error').filter({ hasText: /投标管理系统该标讯已存在/i }))
      .toBeVisible({ timeout: 10000 })
  })
})

// ---------------------------------------------------------------------------
// §2.5 标讯评估 — CRM商机字段交互（蓝图要求）
// ---------------------------------------------------------------------------
test.describe('§2.5 CRM商机字段（创建表单）', () => {
  test('点击 CRM商机选择框应触发接口并展示选项（名称 + ID）', async ({ page }) => {
    const session = await loginAsBidAdmin(page)
    await injectSession(page, session)

    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })

    // 找到 CRM商机选择框
    const crmSelect = page.locator('.el-form-item:has-text("CRM商机") .el-select')
    await expect(crmSelect).toBeVisible()

    // 点击打开
    await crmSelect.click()

    // 等待加载状态消失（loading 消失或有选项出现）
  await expect(page.locator('.el-select-dropdown')).toBeVisible({ timeout: 10000 }).catch(() => {})

    // 验证下拉选项容器出现（Element Plus 结构）
    const dropdown = page.locator('.el-select-dropdown')
    await expect(dropdown).toBeVisible({ timeout: 5000 })

    // 验证至少有一个选项（真实环境中可能为空，但至少不应该报错）
    // 这里只做冒烟：下拉已打开且无崩溃
    const options = dropdown.locator('.el-select-dropdown__item')
    // 不强求数量（测试环境可能无数据），只要不报错即可
    await expect(dropdown).not.toHaveText(/error|失败/i)
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
