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
        industry: 'INDUSTRY',
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
    await page.goto(`/knowledge/case/detail?id=${caseId}`)
    await expect(page.getByText(`E2E 案例复盘 ${suffix}`)).toBeVisible()
  })
})
