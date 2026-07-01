import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

const E2E_PASSWORD = 'XiyuDemo!2026'

test.describe('project evaluation flow §3.3.1.3', () => {
  test('bid_admin can transition evaluation sub-stage and save form', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_ev_admin_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 评标管理员'
    })

    // Create a project via API
    const projRes = await fetch(`${apiBaseUrl}/api/projects`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: `E2E 评标测试项目 ${suffix}`,
        description: '用于 E2E 测试评标流程'
      })
    })
    const projData = await projRes.json()
    expect(projData?.data?.id).toBeTruthy()
    const projectId = projData.data.id

    await injectSession(page, session)

    // 拦截 /api/auth/me 避免路由守卫跳转
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

    // 导航到项目详情页 - 评标阶段
    await page.goto(`/project/${projectId}/stages/evaluation`)
    await page.waitForSelector('.evaluation-stage', { timeout: 10000 })

    // 验证页面元素存在
    await expect(page.locator('.evaluation-stage')).toBeVisible()
    await expect(page.locator('.evidence-card')).toBeVisible()
    await expect(page.getByText('评标文件')).toBeVisible()

    // 验证表单字段存在
    await expect(page.getByText('项目背景')).toBeVisible()
    await expect(page.getByText('竞争对手情况')).toBeVisible()

    // 验证评标状态面板存在
    await expect(page.getByText('评估状态信息')).toBeVisible()
    await expect(page.getByText('子阶段切换')).toBeVisible()

    // 填写表单并保存
    const backgroundInput = page.locator('textarea').first()
    await backgroundInput.fill(`E2E 测试项目背景 ${suffix}`)
    await page.getByRole('button', { name: '保存表单' }).click()

    // 验证保存成功提示
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })

    // 测试状态切换：切换到"待定标"
    await page.locator('.el-select').click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: '待定标' }).click()
    await page.getByRole('button', { name: '确认切换' }).click()

    // 填写评标情况说明
    const dialog = page.locator('.el-message-box')
    await expect(dialog).toBeVisible()
    await dialog.locator('textarea').fill('E2E 测试：评标完成，待上会定标')
    await dialog.getByRole('button', { name: '提交' }).click()

    // 验证切换成功
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })
  })

  test('bid_specialist can view evaluation page with limited actions', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const adminSession = await ensureApiSession({
      username: `e2e_ev_as_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 评标管理员'
    })

    // Create project as admin
    const projRes = await fetch(`${apiBaseUrl}/api/projects`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminSession.token}` },
      body: JSON.stringify({ name: `E2E 评标权限测试 ${suffix}` })
    })
    const projData = await projRes.json()
    expect(projData?.data?.id).toBeTruthy()
    const projectId = projData.data.id

    // Login as bid_specialist (STAFF with limited permissions)
    const staffSession = await ensureApiSession({
      username: `e2e_ev_spec_${suffix}`,
      role: 'STAFF',
      fullName: 'E2E 评标专员'
    })

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
            role: 'staff',
            token: staffSession.token,
            permissions: ['project:evaluate']
          }
        })
      })
    })

    await page.goto(`/project/${projectId}/stages/evaluation`)
    await page.waitForSelector('.evaluation-stage', { timeout: 10000 })

    // bid_specialist should see the page (view permission)
    await expect(page.locator('.evaluation-stage')).toBeVisible()

    // bid_specialist should NOT see "投标" or "弃标" manager-only buttons
    await expect(page.getByRole('button', { name: '投标' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '弃标' })).toHaveCount(0)
  })

  // CO-461: 未上传评标文件时提交失败
  test('submit evaluation without evidence file shows warning', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_ev_nofile_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 评标管理员'
    })

    // Create a project via API
    const projRes = await fetch(`${apiBaseUrl}/api/projects`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: `E2E 评标文件必填测试 ${suffix}`,
        description: '用于 E2E 测试评标文件必填校验'
      })
    })
    const projData = await projRes.json()
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

    await page.goto(`/project/${projectId}/stages/evaluation`)
    await page.waitForSelector('.evaluation-stage', { timeout: 10000 })

    // 选择评标状态
    await page.locator('.status-chip--clickable').filter({ hasText: '评标中' }).click()

    // 填写评标情况说明（必填）
    const notesTextarea = page.locator('.notes-section textarea')
    await notesTextarea.fill('E2E 测试：评标情况说明')

    // 不上传评标文件，直接点击提交
    await page.getByRole('button', { name: '提交' }).click()

    // 验证提示"请上传评标文件"
    await expect(page.locator('.el-message--warning').filter({ hasText: '请上传评标文件' })).toBeVisible({ timeout: 5000 })
  })
})
