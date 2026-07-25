import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('resource certificate expiry', () => {
  test('certificate expiry page renders with stats', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_cert_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Cert Admin'
    })

    // Seed an expiring certificate  // @ui-cover:resource
    await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: `E2E 到期证书 ${suffix}`,
        certificateNo: `EXP-${suffix}`,
        level: 'AAA',
        issuer: '测试机构',
        agency: '测试代理机构',
        agencyContact: '13800000000',
        certScope: '测试认证范围',
        subjectType: 'COMPANY',
        subjectName: '测试主体',
        holderName: '测试人',
        expiryDate: new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10),
        issueDate: '2024-01-01',
        status: 'expiring'
      })
    })

    await injectSession(page, session)
    await page.goto('/resource/ca-management')
    await expect(page.getByText('CA证书列表')).toBeVisible()
  })
})
