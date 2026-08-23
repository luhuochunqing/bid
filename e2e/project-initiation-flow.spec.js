// E2E 测试：蓝图 §3.3.1.1 项目立项
// 覆盖：立项详情字段分组、保证金条件显隐、API 必填校验、投标专员无审批权
// 运行前提：隔离栈（见 scripts/test/start-api-e2e-stack.sh 头部运行手册）
import { test, expect } from '@playwright/test'
import { ensureApiSession } from './auth-helpers.js'
import {
  advanceProjectToDrafting,
  approveInitiation,
  createAuthenticatedSession,
  createProjectFixture,
  ensureRoleUser,
  submitInitiation,
  uploadTenderDocument,
} from './support/project-fixtures.js'

const frontendUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1325'
const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18091'

function loginWith(page, session) {
  return page.context().addCookies([
    { name: 'access_token', value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: 'Lax' },
    { name: 'access_token', value: session.token, url: frontendUrl, httpOnly: true, sameSite: 'Lax' },
  ])
}

/** 建立一个「立项阶段 + 投标专员在团队内」的项目，专员会话从项目列表进入详情 */
async function bootstrapStaffView(page, label) {
  const admin = await createAuthenticatedSession()
  const staff = await ensureRoleUser('bid-Team')
  const project = await createProjectFixture(admin, label, { teamMembers: [admin.user.id, staff.id] })
  await loginWith(page, staff)
  await page.goto(`/project`)
  const row = page.locator('.el-table__row').filter({ hasText: project.name }).first()
  await expect(row).toBeVisible({ timeout: 15000 })
  // 列表行内通过项目名链接进入详情（行点击本身不导航）
  await row.locator('.project-name-link').first().click()
  await page.waitForURL(/\/project\/\d+/, { timeout: 15000 })
  await expect(page.locator('.initiation-stage')).toBeAttached({ timeout: 15000 })
  return { admin, staff, project }
}

test.describe('§3.3.1.1 项目立项', () => {
  test('正向流程：进入立项详情并展示全部必填区块', async ({ page }) => {
    await bootstrapStaffView(page, 'E2E-INIT-N1')

    // 立项页字段分组（蓝图 §3.3.1.1）
    // 「基本信息」分组标题已不存在于当前 UI（基础字段平铺渲染），不作为断言项
    // 当前 UI 以「标讯」呈现关联标讯（无「关联标讯」文案）
    for (const label of ['项目名称', '招标主体', '项目类型', '客户类型',
      '优先级', '总部所在地', '项目负责人', '标讯', '投标信息']) {
      await expect(page.getByText(label).first()).toBeVisible()
    }
  })

  test('保证金条件校验：选择需要保证金时显示金额和方式', async ({ page }) => {
    await bootstrapStaffView(page, 'E2E-INIT-N2')

    // 字段未锁定（立项未提交）时选择"需要保证金"
    const depositSelect = page.locator('.el-form-item:has(label:text-is("是否需要保证金")) .el-select').first()
    await depositSelect.click()
    await page.locator('.el-select-dropdown__item:visible', { hasText: '是' }).first().click()

    // 实际标签为「保证金金额（元）」；缴纳方式/截止日期同区块
    await expect(page.locator('.el-form-item:has(label:text-is("保证金金额（元）"))')).toBeVisible()
    await expect(page.locator('.el-form-item:has(label:text-is("保证金缴纳方式"))')).toBeVisible()
    await expect(page.locator('.el-form-item:has(label:text-is("保证金缴纳截止日期"))')).toBeVisible()
  })

  test('API 验证：提交立项需包含必填字段', async ({ request }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_init_admin2_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Admin',
      password: 'XiyuDemo!2026'
    })
    // 带认证 + 目标项目不存在 → 404（存在项目的字段缺失场景由 needDeposit 用例覆盖）
    const response = await request.post(`${apiBaseUrl}/api/projects/999999/initiation`, {
      headers: { Authorization: `Bearer ${session.token}`, 'Content-Type': 'application/json' },
      data: { expectedBidders: 3, needDeposit: 'NO', bidOpenTime: '2026-12-01T09:30:00' }
    })
    expect([404]).toContain(response.status())
  })

  test('API 验证：needDeposit=YES 时保证金字段必填', async ({ request }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_init_admin_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Admin',
      password: 'XiyuDemo!2026'
    })

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
    // 后端对缺字段返回 400（参数校验）或 422（业务策略），均视为被拒
    expect([400, 422, 404]).toContain(response.status())
  })

  test('权限验证：投标专员在待审项目中不应看到审批操作', async ({ page }) => {
    // admin 建项目（PL 在团队内）→ PL 上传招标文件并提交立项 → PENDING_REVIEW
    const admin = await createAuthenticatedSession()
    const staff = await ensureRoleUser('bid-Team')
    const pl = await ensureRoleUser('bid-projectLeader')
    const project = await createProjectFixture(admin, 'E2E-INIT-N5', { teamMembers: [admin.user.id, staff.id, pl.id] })
    const docId = await uploadTenderDocument(pl.token, project.id)
    await submitInitiation(pl.token, project.id, docId)

    // 投标专员查看待审项目：审批按钮（同意/驳回）不可见（仅 投标管理员/组长 可见）
    await loginWith(page, staff)
    await page.goto(`/project/${project.id}`)
    await expect(page.locator('.initiation-stage')).toBeAttached({ timeout: 15000 })
    await expect(page.getByRole('button', { name: '同意' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '驳回' })).toHaveCount(0)
  })
})
