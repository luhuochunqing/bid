// @ui-cover:resource
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

test.describe('资源管理 - CA证书全流程', () => {
  const suffix = () => `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`

  test('CA证书 CRUD + 借阅/审批/归还 全流程', async ({ page }) => {
    const s = suffix()
    const adminSession = await ensureApiSession({
      username: `e2e_ca_admin_${s}`,
      role: 'ADMIN',
      fullName: 'E2E CA管理员'
    })

    // 步骤1: 创建CA证书
    const createRes = await fetch(`${apiBaseUrl}/api/ca-certificates`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({
        platformIds: '1',
        caType: 'ENTITY_CA',
        sealType: 'OFFICIAL_SEAL',
        electronicAccount: `ca_${s}@test.com`,
        caPassword: 'test123',
        issuer: '测试CA机构',
        holderName: `测试人_${s}`,
        expiryDate: '2027-12-31',
        custodianId: adminSession.user.id,
        custodianName: adminSession.user.name,
        remarks: 'E2E测试CA证书'
      })
    })
    expect(createRes.ok).toBeTruthy()
    const createdCa = await createRes.json()
    const caId = createdCa.id
    expect(caId).toBeTruthy()

    // 验证概览统计包含新增的证书
    const overviewRes = await fetch(`${apiBaseUrl}/api/ca-certificates/overview`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(overviewRes.ok).toBeTruthy()
    const overview = await overviewRes.json()
    expect(overview.total).toBeGreaterThanOrEqual(1)

    // 步骤2: 查询列表
    const listRes = await fetch(`${apiBaseUrl}/api/ca-certificates?page=0&size=20`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(listRes.ok).toBeTruthy()
    const listData = await listRes.json()
    expect(listData.content.length).toBeGreaterThanOrEqual(1)

    // 步骤3: 发起借用申请
    const borrowRes = await fetch(`${apiBaseUrl}/api/ca-certificates/${caId}/borrow`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({
        applicantId: adminSession.user.id,
        applicantName: adminSession.user.name,
        purpose: 'E2E测试借用 - 投标使用',
        projectId: null,
        borrowDurationType: 'SHORT_TERM',
        expectedReturnDate: '2026-07-15'
      })
    })
    expect(borrowRes.ok).toBeTruthy()
    const borrowData = await borrowRes.json()
    const applicationId = borrowData.id
    expect(applicationId).toBeTruthy()

    // 步骤4: 查询待审批列表
    const pendingRes = await fetch(`${apiBaseUrl}/api/ca-certificates/pending-approvals`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(pendingRes.ok).toBeTruthy()
    const pendingList = await pendingRes.json()
    expect(pendingList.length).toBeGreaterThanOrEqual(1)

    // 步骤5: 审批通过
    const approveRes = await fetch(`${apiBaseUrl}/api/ca-certificates/borrow-applications/${applicationId}/approve`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({ comment: 'E2E测试审批通过' })
    })
    expect(approveRes.ok).toBeTruthy()
    const approved = await approveRes.json()
    expect(approved.status).toBe('APPROVED')

    // 步骤6: 获取借用事件记录
    const eventsRes = await fetch(`${apiBaseUrl}/api/ca-certificates/borrow-applications/${applicationId}/events`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(eventsRes.ok).toBeTruthy()
    const events = await eventsRes.json()
    expect(events.length).toBeGreaterThanOrEqual(2) // SUBMITTED + APPROVED
    expect(events.some(e => e.eventType === 'SUBMITTED')).toBeTruthy()
    expect(events.some(e => e.eventType === 'APPROVED')).toBeTruthy()

    // 步骤7: 登记归还
    const returnRes = await fetch(`${apiBaseUrl}/api/ca-certificates/borrow-applications/${applicationId}/return`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({
        actualReturnDate: '2026-06-15',
        returnNotes: 'E2E测试归还 - 完好'
      })
    })
    expect(returnRes.ok).toBeTruthy()
    const returned = await returnRes.json()
    expect(returned.status).toBe('RETURNED')

    // 步骤8: 获取该CA的借用申请列表
    const borrowAppsRes = await fetch(`${apiBaseUrl}/api/ca-certificates/${caId}/borrow-applications`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(borrowAppsRes.ok).toBeTruthy()
    const borrowApps = await borrowAppsRes.json()
    expect(borrowApps.length).toBeGreaterThanOrEqual(1)

    // 步骤9: 验证详情
    const detailRes = await fetch(`${apiBaseUrl}/api/ca-certificates/${caId}`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(detailRes.ok).toBeTruthy()
    const detail = await detailRes.json()
    expect(detail.caType).toBe('ENTITY_CA')
    expect(detail.sealType).toBe('OFFICIAL_SEAL')

    // 步骤10: 下架CA证书
    const deactivateRes = await fetch(`${apiBaseUrl}/api/ca-certificates/${caId}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(deactivateRes.ok).toBeTruthy()
  })

  test('CA证书申请驳回流程', async ({ page }) => {
    const s = suffix()
    const adminSession = await ensureApiSession({
      username: `e2e_ca_reject_${s}`,
      role: 'ADMIN',
      fullName: 'E2E CA驳回测试'
    })

    // 创建CA证书
    const createRes = await fetch(`${apiBaseUrl}/api/ca-certificates`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({
        platformIds: '1',
        caType: 'ENTITY_CA',
        sealType: 'LEGAL_PERSON_SEAL',
        electronicAccount: `ca_reject_${s}@test.com`,
        caPassword: 'test123',
        issuer: '测试CA机构',
        holderName: `驳回测试_${s}`,
        expiryDate: '2027-12-31',
        custodianId: adminSession.user.id,
        custodianName: adminSession.user.name,
        remarks: 'E2E测试驳回CA'
      })
    })
    expect(createRes.ok).toBeTruthy()
    const createdCa = await createRes.json()

    // 发起借用申请
    const borrowRes = await fetch(`${apiBaseUrl}/api/ca-certificates/${createdCa.id}/borrow`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({
        applicantId: adminSession.user.id,
        applicantName: adminSession.user.name,
        purpose: 'E2E测试驳回 - 需要驳回',
        projectId: null,
        borrowDurationType: 'SHORT_TERM',
        expectedReturnDate: '2026-07-15'
      })
    })
    expect(borrowRes.ok).toBeTruthy()
    const applicationId = (await borrowRes.json()).id

    // 驳回申请
    const rejectRes = await fetch(`${apiBaseUrl}/api/ca-certificates/borrow-applications/${applicationId}/reject`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({ comment: 'E2E测试驳回 - 缺少必要材料' })
    })
    expect(rejectRes.ok).toBeTruthy()
    const rejected = await rejectRes.json()
    expect(rejected.status).toBe('REJECTED')
  })

  test('CA证书筛选功能 - 按类型和印章类型', async ({ page }) => {
    const s = suffix()
    const adminSession = await ensureApiSession({
      username: `e2e_ca_filter_${s}`,
      role: 'ADMIN',
      fullName: 'E2E CA筛选测试'
    })

    // 按 caType 筛选
    const filterByCaType = await fetch(`${apiBaseUrl}/api/ca-certificates?caType=ENTITY_CA&size=5`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(filterByCaType.ok).toBeTruthy()

    // 按 sealType 筛选
    const filterBySealType = await fetch(`${apiBaseUrl}/api/ca-certificates?sealType=OFFICIAL_SEAL&size=5`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(filterBySealType.ok).toBeTruthy()

    // 按 keyword 搜索
    const filterByKeyword = await fetch(`${apiBaseUrl}/api/ca-certificates?keyword=CA&size=5`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(filterByKeyword.ok).toBeTruthy()

    // 按 borrowStatus 筛选
    const filterByBorrowStatus = await fetch(`${apiBaseUrl}/api/ca-certificates?borrowStatus=IN_STOCK&size=5`, {
      headers: { Authorization: `Bearer ${adminSession.token}` }
    })
    expect(filterByBorrowStatus.ok).toBeTruthy()
  })

  test('CA证书权限验证 - 非管理员不可新增', async ({ page }) => {
    const s = suffix()
    const specialistSession = await ensureApiSession({
      username: `e2e_ca_spec_${s}`,
      role: 'BID_SPECIALIST',
      fullName: 'E2E 投标专员'
    })

    // 投标专员尝试创建CA证书应被拒绝
    const createRes = await fetch(`${apiBaseUrl}/api/ca-certificates`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${specialistSession.token}`
      },
      body: JSON.stringify({
        platformIds: '1',
        caType: 'ENTITY_CA',
        sealType: 'OFFICIAL_SEAL',
        electronicAccount: `ca_no_perm_${s}@test.com`,
        expiryDate: '2027-12-31',
        custodianId: specialistSession.user.id,
        custodianName: specialistSession.user.name
      })
    })
    expect(createRes.status).toBe(403)

    // 投标专员可查看列表
    const listRes = await fetch(`${apiBaseUrl}/api/ca-certificates?size=5`, {
      headers: { Authorization: `Bearer ${specialistSession.token}` }
    })
    expect(listRes.ok).toBeTruthy()
  })
})
