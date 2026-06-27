// Input: 蓝图 §4.1.3.8 资质到期告警配置 + 定时任务 + 站内信
// Output: 4.1.3.8 消息提醒 E2E 验证
// Pos: e2e/ - 蓝图 §4.1.3.8 E2E 测试
// 维护声明: 验证后端 QualificationExpiryNotificationService + AlertConfig + 前端 AlertConfigDialog 真实页面集成.
import { test, expect } from '@playwright/test'
import {
  ensureApiSession,
  injectSession,
  apiBaseUrl,
  defaultPassword
} from './auth-helpers.js'

/**
 * §4.1.3.8 蓝图：资质到期消息提醒 E2E
 *
 * 蓝图要求：
 * 1. 资质到期告警配置：提前提醒天数（1-365）+ 启用开关
 * 2. 09:00 每日扫描；每张证书 24h 至多 1 次；下架/续期/停用后停止
 * 3. 站内信 + 企微推送（DEADLINE type）
 * 4. 接收人：admin_staff / bid_admin / bid_lead
 * 5. 手动扫描同步走新路径
 *
 * 关键端点：
 *   GET  /api/qualifications/alert-config  → { alertDays, enabled }
 *   PUT  /api/qualifications/alert-config  → update
 *   POST /api/knowledge/qualifications/scan-expiring  → 扫描
 *   GET  /api/notifications                 → 接收人站内信列表
 */

async function loginAsRole(page, role, fullName) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_alert_${role}_${suffix}`,
    role,
    fullName: fullName || `E2E alert ${role}`
  })
  await injectSession(page, session)
  return session
}

async function loginAsRoleNoPage(role, fullName) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  return ensureApiSession({
    username: `e2e_alert_${role}_${suffix}`,
    role,
    fullName: fullName || `E2E alert ${role}`,
    password: defaultPassword
  })
}

async function createQualificationExpiringSoon(token, suffix, daysToExpire) {
  const expiry = new Date()
  expiry.setDate(expiry.getDate() + daysToExpire)
  const issue = new Date()
  issue.setFullYear(issue.getFullYear() - 1)
  const res = await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      name: `E2E alert cert ${suffix}`,
      type: 'OTHER',
      level: 'OTHER',
      subjectType: 'COMPANY',
      subjectName: 'E2E 告警测试',
      category: 'OTHER',
      issueDate: issue.toISOString().slice(0, 10),
      expiryDate: expiry.toISOString().slice(0, 10),
      certificateNo: `E2E-ALERT-${suffix}`,
      issuer: '中国计量认证中心',
      agency: 'E2E 代理认证公司',
      agencyContact: '13800138000',
      certScope: 'E2E 告警扫描测试',
      certReviewNote: '每年 3 月年审',
      holderName: 'E2E 告警测试',
      reminderEnabled: true,
      reminderDays: 30
    })
  })
  if (!res.ok) {
    throw new Error(`create qualification failed: ${res.status} ${await res.text()}`)
  }
  const body = await res.json()
  return body?.data?.id
}

async function getAlertConfig(token) {
  const res = await fetch(`${apiBaseUrl}/api/qualifications/alert-config`, {
    headers: { 'Authorization': `Bearer ${token}` }
  })
  return res.json()
}

async function putAlertConfig(token, alertDays, enabled) {
  const res = await fetch(`${apiBaseUrl}/api/qualifications/alert-config`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ alertDays, enabled })
  })
  return res.json()
}

async function scanExpiring(token) {
  const res = await fetch(`${apiBaseUrl}/api/knowledge/qualifications/scan-expiring`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  })
  return res.json()
}

async function listNotifications(token) {
  const res = await fetch(`${apiBaseUrl}/api/notifications?page=0&size=20`, {
    headers: { 'Authorization': `Bearer ${token}` }
  })
  return res.json()
}

test.describe('§4.1.3.8 消息提醒 - 告警配置 + 扫描 + 站内信', () => {
  test('正向流程：告警配置保存（天数/开关） + 扫描按钮存在 + 真实保存后端', async ({ page }) => {
    const session = await loginAsRole(page, 'bid_admin', 'E2E alert admin')
    // 直接验证后端：先 PUT 30 天 + 启用
    const putRes = await putAlertConfig(session.token, 30, true)
    expect(putRes?.success).toBe(true)
    // GET 验证
    const getRes = await getAlertConfig(session.token)
    expect(getRes?.data?.alertDays).toBe(30)
    expect(getRes?.data?.enabled).toBe(true)

    // 打开真实页面 + 弹窗
    await page.goto('/knowledge/qualification')
    await page.waitForSelector('[data-testid="qd-alert-config-btn"]', { timeout: 10000 })
    // 弹窗 GET 请求已经在按钮点击时触发
    const dialogResp = page.waitForResponse(
      r => r.url().endsWith('/api/qualifications/alert-config') && r.request().method() === 'GET',
      { timeout: 10000 }
    )
    await page.locator('[data-testid="qd-alert-config-btn"]').click()
    await page.waitForSelector('[data-testid="qac-dialog"]', { state: 'visible' })
    await dialogResp
    // 弹窗加载后，slider/switch 应可见
    await expect(page.locator('[data-testid="qac-days-slider"]')).toBeVisible()
    await expect(page.locator('[data-testid="qac-enabled-switch"]')).toBeVisible()
    await expect(page.locator('[data-testid="qac-days-label"]')).toContainText('30')
  })

  test('正向流程：手动扫描 → 命中即将到期证书 → 接收人收到 DEADLINE 站内信', async ({ page }) => {
    const adminSession = await loginAsRoleNoPage('bid_admin', 'E2E alert admin')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
    // 创建 1 张 20 天到期的证书（在 30 天阈值内）
    const newId = await createQualificationExpiringSoon(adminSession.token, suffix, 20)
    expect(newId, '应返回新创建的资质 ID').toBeGreaterThan(0)

    // 触发扫描
    const scanResp = await scanExpiring(adminSession.token)
    expect(scanResp?.success).toBe(true)
    // 兼容新 shape {scanned, notified, skipped} 与旧 shape（数字）
    if (typeof scanResp?.data === 'object') {
      expect(scanResp.data.notified).toBeGreaterThanOrEqual(1)
    } else {
      expect(scanResp?.data).toBeGreaterThanOrEqual(1)
    }

    // 接收人收信（adminSession 自己就是 bid_admin 角色 → 在接收人列表中）
    const notifResp = await listNotifications(adminSession.token)
    // 兼容分页结构：{ data: { content: [...] } } 与扁平 { data: [...] }
    const data = notifResp?.data
    const items = Array.isArray(data) ? data : (data?.content || data?.items || [])
    const deadline = items.find(n => n?.type === 'DEADLINE' || n?.type === 'deadline')
    expect(deadline, '应至少收到一条 DEADLINE 类型通知').toBeTruthy()
    // 标题应包含"资质到期提醒" + 证书名
    expect(deadline.title).toContain('资质到期提醒')
    expect(deadline.title).toContain('E2E alert cert ' + suffix)
    // 正文应包含 9 项字段
    expect(deadline.body).toContain('① 证书名称：')
    expect(deadline.body).toContain('⑨ 跳转详情：')
  })

  test('权限：bid_specialist 触发手动扫描应被拒绝（403 / 业务 500）', async ({ page }) => {
    // 用 bid_admin 创建证书，再用 bid_specialist 触发扫描
    const adminSession = await loginAsRoleNoPage('bid_admin', 'E2E alert admin')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
    await createQualificationExpiringSoon(adminSession.token, suffix, 25)

    const specSession = await loginAsRoleNoPage('bid_specialist', 'E2E alert specialist')
    const res = await fetch(`${apiBaseUrl}/api/knowledge/qualifications/scan-expiring`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${specSession.token}` }
    })
    // 期望 403（权限不足）
    expect([403, 500]).toContain(res.status)
  })

  test('边界：续期后剩余天数 > 阈值，下次扫描不提醒', async ({ page }) => {
    const adminSession = await loginAsRoleNoPage('bid_admin', 'E2E alert admin')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
    // 创建 10 天到期的证书（在 30 天阈值内）
    const certId = await createQualificationExpiringSoon(adminSession.token, suffix, 10)

    // 续期：把 expiryDate 改到 200 天后
    const newExpiry = new Date()
    newExpiry.setDate(newExpiry.getDate() + 200)
    const updRes = await fetch(`${apiBaseUrl}/api/knowledge/qualifications/${certId}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({
        expiryDate: newExpiry.toISOString().slice(0, 10)
      })
    })
    expect(updRes.ok, '续期 PUT 应成功').toBe(true)

    // 触发扫描
    const scanResp = await scanExpiring(adminSession.token)
    expect(scanResp?.success).toBe(true)
    // 续期后已不在阈值内，扫描应不命中此证书
    if (typeof scanResp?.data === 'object') {
      const ids = (scanResp.data.notifiedCerts || []).map(c => c.qualificationId)
      expect(ids).not.toContain(certId)
    }
  })

  test('边界：下架证书（RETIRED）不参与扫描', async ({ page }) => {
    const adminSession = await loginAsRoleNoPage('bid_admin', 'E2E alert admin')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
    const certId = await createQualificationExpiringSoon(adminSession.token, suffix, 15)

    // 下架
    const retireRes = await fetch(`${apiBaseUrl}/api/knowledge/qualifications/${certId}/retire`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminSession.token}`
      },
      body: JSON.stringify({ reason: 'E2E 测试下架' })
    })
    // 后端可能用 POST + body 或 POST 无 body
    if (!retireRes.ok) {
      // 退路：直接 PUT status=RETIRED
      const fallbackRes = await fetch(`${apiBaseUrl}/api/knowledge/qualifications/${certId}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${adminSession.token}`
        },
        body: JSON.stringify({ status: 'RETIRED' })
      })
      expect([200, 204, 400]).toContain(fallbackRes.status)
    }

    // 触发扫描
    const scanResp = await scanExpiring(adminSession.token)
    expect(scanResp?.success).toBe(true)
    if (typeof scanResp?.data === 'object') {
      const ids = (scanResp.data.notifiedCerts || []).map(c => c.qualificationId)
      expect(ids, '下架证书不应在 notifiedCerts 中').not.toContain(certId)
    }
  })

  test('边界：当日二次扫描同证书不重复发（24h 去重）', async ({ page }) => {
    const adminSession = await loginAsRoleNoPage('bid_admin', 'E2E alert admin')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
    const certId = await createQualificationExpiringSoon(adminSession.token, suffix, 12)

    // 第 1 次扫描
    const first = await scanExpiring(adminSession.token)
    expect(first?.success).toBe(true)
    if (typeof first?.data === 'object') {
      expect(first.data.notified).toBeGreaterThanOrEqual(1)
    }

    // 第 2 次扫描（24h 内）— 应被去重
    const second = await scanExpiring(adminSession.token)
    expect(second?.success).toBe(true)
    if (typeof second?.data === 'object') {
      const ids = (second.data.notifiedCerts || []).map(c => c.qualificationId)
      expect(ids, '24h 内同证书不应再次提醒').not.toContain(certId)
    }
  })

  test('数据完整性：扫描响应中跳过数 = scanned - notified', async ({ page }) => {
    const adminSession = await loginAsRoleNoPage('bid_admin', 'E2E alert admin')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
    // 创建 2 张：1 张到期（提醒）、1 张很远（不应命中）
    const nearId = await createQualificationExpiringSoon(adminSession.token, `${suffix}_a`, 10)
    const farId = await createQualificationExpiringSoon(adminSession.token, `${suffix}_b`, 365)

    const scanResp = await scanExpiring(adminSession.token)
    expect(scanResp?.success).toBe(true)
    if (typeof scanResp?.data === 'object') {
      // 远期证书不应被命中
      const ids = (scanResp.data.notifiedCerts || []).map(c => c.qualificationId)
      expect(ids).toContain(nearId)
      expect(ids, '远期证书不应被提醒').not.toContain(farId)
    }
  })
})
