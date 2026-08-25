import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('knowledge case review', () => {
  test('case library renders and case detail is accessible', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_case_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Case Admin'
    })

    // Seed a case  // @ui-cover:knowledge
    const caseRes = await fetch(`${apiBaseUrl}/api/knowledge/cases`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        title: `E2E 案例复盘 ${suffix}`,
        industry: 'OTHER',
        outcome: 'WON',
        amount: 100,
        projectDate: '2026-01-01',
        description: 'E2E 测试案例',
        customerName: 'E2E客户',
        tags: ['E2E'],
        highlights: ['自动化测试']
      })
    })
    const caseId = (await caseRes.json())?.data?.id
    expect(caseId).toBeTruthy()

    await injectSession(page, session)
    // 独立案例详情页已下线（/knowledge/case/detail 路由不存在，案例详情并入案例库抽屉）；
    // Case 实体的 UI 出口为 AI 方案复用页（/ai-center/solution-reuse 读 /api/knowledge/cases）
    await page.goto('/ai-center/solution-reuse')
    const keywordInput = page.locator('input').first()
    await keywordInput.fill(`E2E 案例复盘 ${suffix}`)
    await page.getByRole('button', { name: /搜索|查询/ }).first().click()
    await expect(page.getByText(`E2E 案例复盘 ${suffix}`).first()).toBeVisible({ timeout: 10000 })
  })
})
