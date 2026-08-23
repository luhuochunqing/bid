// @ui-cover:resource
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession } from './auth-helpers.js'

// 平台账号 API 契约（PlatformAccountController，类级 hasAuthority('resource')）：
// - 请求体 PlatformAccountCreateRequest：accountName/username 必填，contactPerson 为用户 ID
// - 响应统一 ApiResponse 包装（数据在 .data 字段）
// - 借用 POST /{id}/borrow：{borrowedBy, dueHours, purpose}
test.describe('资源管理 - 招标平台账号管理全流程', () => {
  const suffix = () => `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`

  function accountPayload(s, overrides = {}) {
    return {
      accountName: `E2E测试平台_${s}`,
      url: 'https://e2e-test.example.com',
      username: `test_user_${s}`,
      password: 'TestPass@2026',
      hasCa: false,
      remarks: 'E2E测试账号',
      ...overrides,
    }
  }

  test('平台账号 CRUD + 借阅/归还 全流程', async () => {
    const s = suffix()
    const adminSession = await ensureApiSession({
      username: `e2e_acct_admin_${s}`,
      role: 'ADMIN',
      fullName: 'E2E 账号管理员'
    })
    const authHeaders = {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${adminSession.token}`,
    }

    // 步骤1: 创建平台账号
    const createRes = await fetch(`${apiBaseUrl}/api/platform/accounts`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify(accountPayload(s)),
    })
    expect(createRes.ok).toBeTruthy()
    const created = await createRes.json()
    const accountId = created?.data?.id
    expect(accountId).toBeTruthy()

    // 步骤2: 查询账号列表（返回 ApiResponse<List>）
    const listRes = await fetch(`${apiBaseUrl}/api/platform/accounts`, {
      headers: { Authorization: `Bearer ${adminSession.token}` },
    })
    expect(listRes.ok).toBeTruthy()
    const listData = await listRes.json()
    expect((listData?.data || []).length).toBeGreaterThan(0)

    // 步骤3: 借阅账号
    const borrowRes = await fetch(`${apiBaseUrl}/api/platform/accounts/${accountId}/borrow`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({
        borrowedBy: adminSession.user.id,
        purpose: 'E2E测试借用',
        dueHours: 48,
      }),
    })
    expect(borrowRes.ok).toBeTruthy()

    // 步骤4: 归还账号
    const returnRes = await fetch(`${apiBaseUrl}/api/platform/accounts/${accountId}/return`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminSession.token}` },
    })
    expect(returnRes.ok).toBeTruthy()

    // 步骤5: 更新账号
    const updateRes = await fetch(`${apiBaseUrl}/api/platform/accounts/${accountId}`, {
      method: 'PUT',
      headers: authHeaders,
      body: JSON.stringify(accountPayload(s, {
        accountName: `E2E测试平台_${s}_updated`,
        remarks: 'E2E测试账号-已更新',
      })),
    })
    expect(updateRes.ok).toBeTruthy()
  })

  test('平台账号密码查看审计', async () => {
    const s = suffix()
    const adminSession = await ensureApiSession({
      username: `e2e_acct_pwd_${s}`,
      role: 'ADMIN',
      fullName: 'E2E 密码审计测试'
    })

    // 创建账号（绑定联系人为当前管理员，密码查看权限要求绑定联系人）
    const createRes = await fetch(`${apiBaseUrl}/api/platform/accounts`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`,
      },
      body: JSON.stringify(accountPayload(s, {
        accountName: `E2E密码审计_${s}`,
        url: 'https://e2e-pwd.example.com',
        username: `pwd_user_${s}`,
        password: 'SecretPwd!2026',
        contactPerson: adminSession.user.id,
      })),
    })
    expect(createRes.ok).toBeTruthy()
    const created = await createRes.json()
    const accountId = created?.data?.id
    expect(accountId).toBeTruthy()

    // 绑定联系人可查看明文密码（PasswordRevealResponse，含审计 ID 与有效期）
    const getPwdRes = await fetch(`${apiBaseUrl}/api/platform/accounts/${accountId}/password`, {
      headers: { Authorization: `Bearer ${adminSession.token}` },
    })
    expect(getPwdRes.ok).toBeTruthy()
    const pwdData = await getPwdRes.json()
    expect(pwdData?.data?.password).toBeTruthy()
  })

  test('平台账号权限验证 - 无 resource 权限角色不可新增', async () => {
    const s = suffix()
    // bid-OtherDept（跨部门协同人员）不含 resource 权限点，所有平台账号端点应 403
    const otherDeptSession = await ensureApiSession({
      username: `e2e_acct_otherdept_${s}`,
      role: 'bid-OtherDept',
      fullName: 'E2E 跨部门协同'
    })

    const createRes = await fetch(`${apiBaseUrl}/api/platform/accounts`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${otherDeptSession.token}`,
      },
      body: JSON.stringify(accountPayload(s, {
        accountName: `E2E无权限_${s}`,
        username: `no_perm_${s}`,
      })),
    })
    expect(createRes.status).toBe(403)

    // 列表同样受类级 resource 权限拦截
    const listRes = await fetch(`${apiBaseUrl}/api/platform/accounts`, {
      headers: { Authorization: `Bearer ${otherDeptSession.token}` },
    })
    expect(listRes.status).toBe(403)
  })
})
