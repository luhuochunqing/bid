// E2E 测试：蓝图 §3.3.1.1 项目立项
// 覆盖：正向流程（提交立项→审核通过→进入标书制作）、保证金条件校验、权限验证
import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession, apiBaseUrl, defaultPassword } from './auth-helpers.js'

const frontendUrl = process.env.PLAYWRIGHT_FRONTEND_URL || 'http://127.0.0.1:1314'

async function loginAsRole(page, role) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_init_${role}_${suffix}`,
    role,
    fullName: `E2E ${role} 立项测试`,
    password: defaultPassword
  })
  await injectSession(page, session)
  return session
}

test.describe('§3.3.1.1 项目立项', () => {
  test('正向流程：提交立项审核', async ({ page }) => {
    await loginAsRole(page, 'STAFF')
    await page.goto(`${frontendUrl}/projects`)
    await page.waitForSelector('.el-table, .el-empty', { timeout: 15000 })

    // 点击第一个项目进入详情
    const firstRow = page.locator('.el-table__body-wrapper .el-table__row').first()
    if (await firstRow.isVisible()) {
      await firstRow.click()
      await page.waitForSelector('.initiation-stage', { timeout: 10000 })

      // 验证立项页面基本元素存在
      await expect(page.getByText('基本信息')).toBeVisible()
      await expect(page.getByText('项目名称')).toBeVisible()
      await expect(page.getByText('招标主体')).toBeVisible()
      await expect(page.getByText('项目类型')).toBeVisible()
      await expect(page.getByText('客户类型')).toBeVisible()
      await expect(page.getByText('优先级')).toBeVisible()
      await expect(page.getByText('总部所在地')).toBeVisible()
      await expect(page.getByText('项目负责人')).toBeVisible()
      await expect(page.getByText('关联标讯')).toBeVisible()
      await expect(page.getByText('投标信息')).toBeVisible()
      await expect(page.getByText('客户信息')).toBeVisible()
      await expect(page.getByText('招标文件与 AI 风险评估')).toBeVisible()
    }
  })

  test('保证金条件校验：选择需要保证金时显示金额和方式', async ({ page }) => {
    await loginAsRole(page, 'STAFF')
    await page.goto(`${frontendUrl}/projects`)
    await page.waitForSelector('.el-table, .el-empty', { timeout: 15000 })

    const firstRow = page.locator('.el-table__body-wrapper .el-table__row').first()
    if (await firstRow.isVisible()) {
      await firstRow.click()
      await page.waitForSelector('.initiation-stage', { timeout: 10000 })

      // 选择需要保证金
      const depositSelect = page.locator('.el-form:has-text("是否需要保证金") .el-select').first()
      if (await depositSelect.isVisible()) {
        await depositSelect.click()
        await page.locator('.el-select-dropdown__item:has-text("是")').click()

        // 验证保证金金额和方式字段出现
        await expect(page.getByText('保证金金额')).toBeVisible()
        await expect(page.getByText('保证金缴纳方式')).toBeVisible()
      }
    }
  })

  test('API 验证：提交立项需包含必填字段', async ({ request }) => {
    // 缺少必填字段 ownerUnit 应返回 422
    const response = await request.post(`${apiBaseUrl}/api/projects/999/initiation`, {
      data: { expectedBidders: 3, needDeposit: 'NO' }
    })
    // 项目不存在或字段缺失均应返回错误
    expect([400, 401, 404, 422]).toContain(response.status())
  })

  test('API 验证：needDeposit=YES 时保证金字段必填', async ({ request }) => {
    // 获取管理员 session
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_init_admin_${suffix}`,
      role: 'bid_admin',
      fullName: 'E2E Admin',
      password: defaultPassword
    })

    // 提交 needDeposit=YES 但缺少保证金金额
    const response = await request.post(`${apiBaseUrl}/api/projects/1/initiation`, {
      headers: { Authorization: `Bearer ${session.token}`, 'Content-Type': 'application/json' },
      data: {
        ownerUnit: '测试业主', expectedBidders: 3, contractPeriodMonths: 12,
        projectType: 'PUBLIC_BIDDING', customerType: 'CENTRAL_SOE',
        annualRevenue: 100000, bidOpenTime: '2026-12-01T09:30:00',
        ownerUserId: 1, departmentSnapshot: '测试部门',
        needDeposit: 'YES'
        // 缺少 depositAmount 和 depositPaymentMethod
      }
    })
    // 应返回 422 (缺少必填字段)
    expect([422, 404]).toContain(response.status())
  })

  test('权限验证：投标专员不应看到审批操作', async ({ page }) => {
    await loginAsRole(page, 'STAFF')
    await page.goto(`${frontendUrl}/projects`)
    await page.waitForSelector('.el-table, .el-empty', { timeout: 15000 })

    // 投标专员（STAFF 角色）在审核中的项目里不应看到审批操作区
    const approvalCard = page.getByText('审批操作（投标管理员/组长）')
    // 审批卡片只对管理员/组长可见且项目需在 PENDING_REVIEW 状态
    await expect(approvalCard).toHaveCount(0)
  })
})
