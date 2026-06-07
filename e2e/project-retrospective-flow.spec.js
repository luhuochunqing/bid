import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession, apiBaseUrl, defaultPassword } from './auth-helpers.js'
import { createProjectFixture } from './support/project-fixtures.js'

const frontendUrl = process.env.PLAYWRIGHT_BASE_URL || process.env.PLAYWRIGHT_FRONTEND_URL || 'http://127.0.0.1:1314'

async function loginAsRole(page, role) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_retro_${role}_${suffix}`,
    role,
    fullName: `E2E ${role} 复盘测试`,
    password: defaultPassword
  })
  await injectSession(page, session)
  return session
}

test.describe('§3.3.1.5 项目复盘', () => {
  test('正向流程：登记结果为中标并提交项目复盘', async ({ page, request }) => {
    const session = await loginAsRole(page, 'ADMIN')

    // 动态创建项目
    const project = await createProjectFixture(session, '项目复盘E2E')
    const projectId = project.id

    // 1. 确保先登记结果为中标 WON (如果不使用 API 登记，可以直接在 UI 操作，但 API 登记更可靠)
    try {
      await request.post(`${apiBaseUrl}/api/projects/${projectId}/result`, {
        headers: { Authorization: `Bearer ${session.token}`, 'Content-Type': 'application/json' },
        data: {
          resultType: 'WON',
          awardAmount: 3500000.00,
          contractStartDate: '2026-07-01',
          contractEndDate: '2027-06-30',
          evidenceFileIds: [1],
          summary: 'E2E测试中标结果登记'
        }
      })
    } catch (e) {
      // 如果已经登记过，忽略
    }

    // 2. 导航到该项目的复盘页面
    await page.goto(`${frontendUrl}/project/${projectId}`)
    await page.waitForLoadState('networkidle')
    
    // 3. 点击 "项目复盘" tab
    const retroTab = page.locator('.custom-stage-tabs .el-tabs__item').filter({ hasText: '项目复盘' })
    await expect(retroTab).toBeVisible({ timeout: 15000 })
    await retroTab.click()

    // 4. 验证复盘页面里的新字段和必填标记
    await expect(page.getByText('复盘会时间', { exact: true })).toBeVisible()
    await expect(page.getByText('会议形式', { exact: true })).toBeVisible()
    await expect(page.getByText('会议参与人', { exact: true })).toBeVisible()
    await expect(page.getByText('上传复盘报告', { exact: true })).toBeVisible()

    // 5. 填写复盘会信息
    // 填写复盘会时间
    const timeInput = page.locator('input[placeholder="选择复盘会时间"]')
    await timeInput.fill('2026-06-03 21:00:00')
    await page.keyboard.press('Enter')

    // 会议形式
    const typeSelect = page.locator('.el-form-item:has-text("会议形式") .el-select')
    await typeSelect.click()
    await page.locator('.el-select-dropdown__item:has-text("线上")').last().click()

    // 会议参与人
    const participantsInput = page.locator('input[placeholder="请输入会议参与人"]')
    await participantsInput.fill('张三, 李四, 王五')

    // 复盘总结
    const summaryInput = page.locator('textarea[placeholder="请输入项目复盘总结/决策说明"]')
    await summaryInput.fill('本次项目复盘非常顺利。')

    // 中标优势 (resultType === 'WON')
    const winFactorsInput = page.locator('textarea[placeholder="请输入中标优势"]')
    await winFactorsInput.fill('我们在技术实力和交付周期上更有竞争力。')

    // 改进措施与建议
    const improvementInput = page.locator('textarea[placeholder="请输入后续改进建议/应对措施"]')
    await improvementInput.fill('继续优化产品架构，提升服务品质。')

    // 6. 提交复盘
    await page.getByRole('button', { name: '提交复盘' }).click()

    // 7. 验证成功消息
    await expect(page.getByText('复盘已提交')).toBeVisible()
  })
})
