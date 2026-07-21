import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

const E2E_PASSWORD = 'XiyuDemo!2026'

// 抽取项目创建逻辑：后端 ProjectRequest 强制要求 tenderId/managerId/teamMembers/startDate/endDate
// extraTeamMembers: 可选，额外加入 teamMembers 的用户 ID 数组（用于跨角色权限测试）
async function createEvaluationProject(session, suffix, namePrefix, extraTeamMembers = []) {
  // 1. 先创建标讯（ProjectRequest.tenderId @NotNull @Positive）
  const tenderRes = await fetch(`${apiBaseUrl}/api/tenders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
    body: JSON.stringify({
      title: `${namePrefix} 标讯 ${suffix}`,
      source: 'E2E',
      budget: 100000,
      deadline: new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 19),
      status: 'TRACKING'
    })
  })
  const tenderData = await tenderRes.json().catch(() => null)
  const tenderId = tenderData?.data?.id

  // 2. 创建项目（补齐所有 @NotNull/@NotEmpty 字段）
  const teamMembers = [session.user.id, ...extraTeamMembers.filter(id => id !== session.user.id)]
  const projRes = await fetch(`${apiBaseUrl}/api/projects`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
    body: JSON.stringify({
      name: `${namePrefix} ${suffix}`,
      tenderId,
      status: 'BIDDING',
      managerId: session.user.id,
      teamMembers,
      startDate: new Date().toISOString().slice(0, 19),
      endDate: new Date(Date.now() + 90 * 86400000).toISOString().slice(0, 19)
    })
  })
  return projRes.json()
}

test.describe('project evaluation flow §3.3.1.3', () => {
  // EvaluationStage.vue 重构后（CO-495/CO-550/CO-571）：
  // - URL: /project/:id/:stage（router 是 project/:id/:stage，非 /stages/evaluation）
  // - 状态选项: .status-chip（非 .el-select），3 个选项（无「评标中」，CO-495 删除）
  // - 评标说明: .notes-section textarea
  // - 提交按钮: .btn-container 内 el-button（文本「提交」或「已提交」）
  // - 附件: CO-550 取消必填
  test('bid_admin can transition evaluation sub-stage and submit', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_ev_admin_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 评标管理员'
    })

    const projData = await createEvaluationProject(session, suffix, 'E2E 评标测试项目')
    expect(projData?.data?.id).toBeTruthy()
    const projectId = projData.data.id

    await injectSession(page, session)

    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            id: session.user.id,
            username: session.user.username,
            fullName: session.user.name,
            role: session.user.role,
            token: session.token,
            permissions: ['project:evaluate', 'task.review', 'lead.assign']
          }
        })
      })
    })

    // 导航到项目详情页 - 评标阶段（router: /project/:id/:stage）
    await page.goto(`/project/${projectId}/evaluation`)
    // 等待评标状态卡片可见（EvaluationStage.vue 渲染标志）
    await expect(page.getByText('评标状态', { exact: true })).toBeVisible({ timeout: 15_000 })

    // 验证页面元素存在
    // 评标状态卡片
    await expect(page.getByText('评标状态', { exact: true })).toBeVisible()
    // 3 个状态选项（CO-495 删除「评标中」，CO-571 调整顺序）
    await expect(page.locator('.status-chip')).toHaveCount(3)
    await expect(page.locator('.status-chip').filter({ hasText: '评标结果已出，待上会' })).toBeVisible()
    await expect(page.locator('.status-chip').filter({ hasText: '评标结果公示' })).toBeVisible()
    // 注意：「评标结果已出」是「评标结果已出，待上会」的子串，必须用 exact 匹配避免 strict mode violation
    await expect(page.getByText('评标结果已出', { exact: true })).toBeVisible()

    // 评标情况说明 textarea
    await expect(page.locator('.notes-section textarea')).toBeVisible()

    // 选择状态：评标结果已出，待上会
    await page.locator('.status-chip').filter({ hasText: '评标结果已出，待上会' }).click()

    // 填写评标情况说明
    await page.locator('.notes-section textarea').fill('E2E 测试：评标完成，待上会定标')

    // 点击提交
    await page.getByRole('button', { name: '提交' }).click()

    // 验证成功提示
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })
  })

  test('bid_specialist can view evaluation page', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const adminSession = await ensureApiSession({
      username: `e2e_ev_as_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 评标管理员'
    })

    // 先创建 staff session，再把 staff 加入项目 teamMembers
    // 否则 staff 没有项目权限，访问 /project/:id/evaluation 会被重定向到 /notifications
    const staffSession = await ensureApiSession({
      username: `e2e_ev_spec_${suffix}`,
      role: 'bid-Team',
      fullName: 'E2E 评标专员'
    })

    const projData = await createEvaluationProject(
      adminSession,
      suffix,
      'E2E 评标权限测试',
      [staffSession.user.id] // 把 staff 加入 teamMembers
    )
    expect(projData?.data?.id).toBeTruthy()
    const projectId = projData.data.id

    await injectSession(page, staffSession)

    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            id: staffSession.user.id,
            username: staffSession.user.username,
            fullName: staffSession.user.name,
            role: 'bid-Team',
            token: staffSession.token,
            permissions: ['project:evaluate']
          }
        })
      })
    })

    await page.goto(`/project/${projectId}/evaluation`)
    // 等待评标状态卡片可见（EvaluationStage.vue 渲染标志）
    await expect(page.getByText('评标状态', { exact: true })).toBeVisible({ timeout: 15_000 })

    // bid_specialist should see the page (view permission)
    // bid-Team 角色 editable=true，应能看到可点击的状态选项
    await expect(page.locator('.status-chip--clickable')).toHaveCount(3)
  })

  // CO-550: 开标一览表已取消必填，此测试的业务逻辑已过期
  test.skip('submit evaluation without evidence file shows warning', async () => {
    // CO-550 取消了附件必填校验，未上传评标文件时提交不再报错
    // 此测试已过期，保留骨架以便后续业务变更时参考
  })
})
