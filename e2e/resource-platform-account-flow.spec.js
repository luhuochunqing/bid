// @ui-cover:resource
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('资源管理 - 招标平台账号管理全流程', () => {
  const suffix = () => `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`

  test('平台账号 CRUD + 借阅/归还 全流程', async ({ page }) => {
    const s = suffix()
    const adminSession = await ensureApiSession({
      username: `e2e_acct_admin_${s}`,
      role: 'ADMIN',
      fullName: 'E2E 账号管理员'
    })

    // 步骤1: 创建平台账号
    const createRes = await fetch(`${apiBaseUrl}/api/platform/accounts`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({
        platformName: `E2E测试平台_${s}`,
        url: 'https://e2e-test.example.com',
        username: `test_user_${s}`,
        password: 'TestPass@2026',
        contactPerson: '测试联系人',
        contactPhone: '13800138000',
        contactEmail: `test_${s}@example.com`,
        accountType: 'GOV_PROCUREMENT',
        hasCa: false,
        remarks: 'E2E测试账号',
        custodianId: adminSession.user.id,
        custodianName: adminSession.user.name
      })
    })
    expect(createRes.ok).toBeTruthy()
    const account = await createRes.json()
    const accountId = account.id
    expect(accountId).toBeTruthy()

    // 步骤2: 查询账号列表
    const listRes = await fetch(`${apiBaseUrl}/api/platform/accounts?page=0&size=20`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(listRes.ok).toBeTruthy()
    const listData = await listRes.json()
    expect(listData.content || listData.length).toBeTruthy()

    // 步骤3: 借阅账号
    const borrowRes = await fetch(`${apiBaseUrl}/api/platform/accounts/${accountId}/borrow`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({
        purpose: 'E2E测试借用',
        projectId: null,
        dueHours: 48
      })
    })
    expect(borrowRes.ok).toBeTruthy()

    // 步骤4: 归还账号
    const returnRes = await fetch(`${apiBaseUrl}/api/platform/accounts/${accountId}/return`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      }
    })
    expect(returnRes.ok).toBeTruthy()

    // 步骤5: 更新账号
    const updateRes = await fetch(`${apiBaseUrl}/api/platform/accounts/${accountId}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({
        platformName: `E2E测试平台_${s}_updated`,
        url: 'https://e2e-test.example.com',
        username: `test_user_${s}`,
        contactPerson: '测试联系人',
        contactPhone: '13800138001',
        accountType: 'GOV_PROCUREMENT',
        hasCa: false
      })
    })
    expect(updateRes.ok).toBeTruthy()
  })

  test('平台账号密码查看审计', async ({ page }) => {
    const s = suffix()
    const adminSession = await ensureApiSession({
      username: `e2e_acct_pwd_${s}`,
      role: 'ADMIN',
      fullName: 'E2E 密码审计测试'
    })

    // 创建账号
    const createRes = await fetch(`${apiBaseUrl}/api/platform/accounts`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({
        platformName: `E2E密码审计_${s}`,
        url: 'https://e2e-pwd.example.com',
        username: `pwd_user_${s}`,
        password: 'SecretPwd!2026',
        contactPerson: '测试',
        contactPhone: '13800138000',
        accountType: 'GOV_PROCUREMENT',
        hasCa: false,
        custodianId: adminSession.user.id,
        custodianName: adminSession.user.name
      })
    })
    expect(createRes.ok).toBeTruthy()
    const account = await createRes.json()

    // 管理员可查看明文密码
    const getPwdRes = await fetch(`${apiBaseUrl}/api/platform/accounts/${account.id}/password`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(getPwdRes.ok).toBeTruthy()
    const pwdData = await getPwdRes.json()
    expect(pwdData.password).toBeTruthy()
  })

  test('平台账号权限验证 - 非管理员不可新增', async ({ page }) => {
    const s = suffix()
    const specialistSession = await ensureApiSession({
      username: `e2e_acct_spec_${s}`,
      role: 'BID_SPECIALIST',
      fullName: 'E2E 投标专员'
    })

    // 投标专员尝试创建账号应被拒绝
    const createRes = await fetch(`${apiBaseUrl}/api/platform/accounts`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${specialistSession.token}`
      },
      body: JSON.stringify({
        platformName: `E2E无权限_${s}`,
        url: 'https://test.example.com',
        username: `no_perm_${s}`,
        password: 'Test123',
        contactPerson: '测试',
        contactPhone: '13800138000',
        accountType: 'GOV_PROCUREMENT',
        hasCa: false,
        custodianId: specialistSession.user.id,
        custodianName: specialistSession.user.name
      })
    })
    expect(createRes.status).toBe(403)

    // 投标专员可查看列表
    const listRes = await fetch(`${apiBaseUrl}/api/platform/accounts?page=0&size=5`, {
      headers: { Authorization: `Bearer ${specialistSession.token}` }
    })
    expect(listRes.ok).toBeTruthy()
  })
})
