// @ui-cover:resource
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('resource CA management flow', () => {
  test('CA certificate can be created via API', async () => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_ca_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E CA Admin'
    })

    const qualRes = await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: `E2E CA ${suffix}`,
        certificateNo: `CA-${suffix}`,
        issuer: '测试CA机构',
        holderName: '测试人',
        expiryDate: '2027-06-30',
        status: 'valid'
      })
    })
    expect(qualRes.ok).toBeTruthy()
    const qualData = await qualRes.json()
    expect(qualData?.data?.id).toBeTruthy()
  })
})
