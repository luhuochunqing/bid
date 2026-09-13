import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

test.describe('full tender-to-project flow', () => {
  test('create tender -> evaluate -> bid -> project renders', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_flow_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Flow Admin'
    })

    // Step 1: Create tender via API  // @ui-cover:project
    const authHeaders = { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` }
    const tenderRes = await fetch(`${apiBaseUrl}/api/tenders`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({
        title: `流程测试标讯 ${suffix}`,
        source: 'E2E',
        budget: 500000,
        deadline: toLocalDateTimeString(new Date(Date.now() + 30 * 86400000)),
        status: 'TRACKING'
      })
    })
    expect(tenderRes.ok).toBeTruthy()
    const tenderData = await tenderRes.json()
    const tenderId = tenderData?.data?.id
    expect(tenderId).toBeTruthy()

    // Step 2: Evaluate tender
    const evalRes = await fetch(`${apiBaseUrl}/api/tenders/${tenderId}/evaluation/submit`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ evaluationResult: 'PASS', remark: '流程测试评估通过' })
    })
    expect(evalRes.ok).toBeTruthy()

    // Step 3: Bid on tender (create project)
    const projectRes = await fetch(`${apiBaseUrl}/api/tenders/${tenderId}/bid`, {
      method: 'POST',
      headers: authHeaders
    })
    expect(projectRes.ok).toBeTruthy()
    const projectData = await projectRes.json()
    const projectId = projectData?.data?.id || projectData?.data?.projectId
    expect(projectId).toBeTruthy()

    // Step 4: Verify project renders in frontend
    await injectSession(page, session)
    await page.goto('/project')
    await expect(page.getByText('投标项目列表')).toBeVisible()

    // Step 5: Verify project detail page
    await page.goto(`/project/${projectId}`)
    await expect(page).toHaveURL(/\/project\/.+$/)
  })
})
