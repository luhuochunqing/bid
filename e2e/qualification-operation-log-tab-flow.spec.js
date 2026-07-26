// Input: 资质详情抽屉 Tab 2 "操作日志" 的 Playwright E2E
// Output: 4.1.3.7 操作日志 Tab 数据接入 验证（重写版，匹配实际 el-table UI）
// Pos: e2e/ - 蓝图 §4.1.3.7 E2E 测试
// 维护声明: 验证后端 QualificationAuditController + 前端 OperationLogTab 真实页面集成.
import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession, apiBaseUrl, defaultPassword } from './auth-helpers.js'

/**
 * §4.1.3.7 资质详情抽屉 - 操作日志 Tab 数据接入 E2E
 *
 * 实际 UI：
 *   QualDetailDrawer（data-testid="qd-tabs"）有两个 Tab：
 *     - 基本信息（data-testid="qd-tab-basic"）
 *     - 操作日志（data-testid="qd-tab-audit"）
 *   OperationLogTab 用 el-table 渲染：
 *     - 表格 data-testid="qd-op-log-table"
 *     - 行 class="qd-op-log-item"（row-class-name）
 *     - 时间单元格 span data-testid="qd-op-log-time"
 *     - 操作人单元格 span data-testid="qd-op-log-operator"
 *     - 操作类型 el-tag class="op-log-tab__action"
 *
 * 关键端点：
 *   GET  /api/qualifications/{id}/audit-logs  → { success, data: [...] }
 *   GET  /api/knowledge/qualifications        → list（含 id）
 *   POST /api/knowledge/qualifications       → create（@Auditable 自动产生 1 条 "create" 日志）
 */

async function loginAsRole(page, role, fullName) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_oplog_${role}_${suffix}`,
    role,
    fullName: fullName || `E2E oplog ${role}`
  })
  await injectSession(page, session)
  return session
}

async function createQualificationWithAudit(token, suffix, certName) {
  // 调后端创建 1 个全新资质，会自动产生 1 条 "create" 审计日志
  const res = await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      name: certName || `E2E oplog test cert ${suffix}`,
      level: 'A',
      issueDate: '2024-01-15',
      expiryDate: '2027-12-31',
      certificateNo: `E2E-OPLOG-${suffix}`,
      issuer: '中国计量认证中心',
      agency: 'E2E 代理认证公司',
      agencyContact: '13800138000',
      certScope: 'E2E 操作日志 Tab 测试覆盖范围'
    })
  })
  if (!res.ok) {
    throw new Error(`create qualification failed: ${res.status} ${await res.text()}`)
  }
  const body = await res.json()
  return body?.data?.id
}

async function openNewlyCreatedDrawer(page, certName) {
  await page.goto('/knowledge/qualification')
  await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
  await page.waitForResponse(
    r => /\/api\/knowledge\/qualifications/.test(r.url()) && r.request().method() === 'GET',
    { timeout: 5000 }
  ).catch(() => { /* list may have arrived before this listener */ })

  // 用 name 文本定位行（避免分页和顺序依赖）
  const cell = page.locator('[data-testid="qual-table"] .el-table__row .cert-name', { hasText: certName }).first()
  await cell.waitFor({ state: 'visible', timeout: 10000 })
  await cell.click()
  await page.waitForSelector('[data-testid="qd-tabs"]', { timeout: 10000 })
}

test.describe('§4.1.3.7 操作日志 Tab 数据接入', () => {
  test('正向流程：详情抽屉 Tab 2 展示该资质的操作日志（真实数据）', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin', 'E2E oplog admin')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
    const certName = `E2E oplog test cert ${suffix}`
    const newId = await createQualificationWithAudit(session.token, suffix, certName)
    expect(newId, '应返回新创建的资质 ID').toBeGreaterThan(0)

    // 打开详情抽屉，监听 audit-logs 接口
    const auditResp = page.waitForResponse(
      r => /\/api\/qualifications\/\d+\/audit-logs$/.test(r.url()),
      { timeout: 10000 }
    )
    await openNewlyCreatedDrawer(page, certName)
    // 切换到"操作日志"Tab
    await page.getByRole('tab', { name: '操作日志' }).click()
    const resp = await auditResp
    expect(resp.status(), 'audit-logs 接口应 200').toBe(200)

    // 等表格行出现
    const items = page.locator('[data-testid="qd-op-log-table"] .qd-op-log-item')
    await expect(items.first(), '至少应有 1 条日志').toBeVisible({ timeout: 10000 })
    const count = await items.count()
    expect(count, '新创建资质应至少有 1 条 create 日志').toBeGreaterThanOrEqual(1)

    // 验证时间格式 YYYY-MM-DD HH:mm:ss
    const firstRow = items.first()
    const timeText = await firstRow.locator('[data-testid="qd-op-log-time"]').textContent()
    expect(timeText?.trim(), `时间应匹配 YYYY-MM-DD HH:mm:ss，实际：${timeText}`).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)

    // 验证操作人渲染（不应为空）
    const operator = await firstRow.locator('[data-testid="qd-op-log-operator"]').textContent()
    expect(operator?.trim().length, '操作人不应为空').toBeGreaterThan(0)

    // 验证有 action tag
    const action = await firstRow.locator('.op-log-tab__action').textContent()
    expect(action?.trim().length, '动作类型不应为空').toBeGreaterThan(0)

    // 动作应该是中文 label（如"新增"）
    const validActions = ['新增', '修改', '删除', '批量导入', '导出', '附件变更']
    expect(validActions, `动作 ${action} 应在有效集合内`).toContain(action?.trim())
  })

  test('时间倒序：多条日志时，最新一条在最上方', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin', 'E2E oplog admin')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
    const certName = `E2E oplog test cert ${suffix}`
    const newId = await createQualificationWithAudit(session.token, suffix, certName)

    // 直接走 API 看是否至少 1 条
    const directResp = await fetch(`${apiBaseUrl}/api/qualifications/${newId}/audit-logs`, {
      headers: { 'Authorization': `Bearer ${session.token}` }
    })
    const directBody = await directResp.json()
    expect(directBody?.success).toBe(true)
    expect(directBody?.data?.length).toBeGreaterThanOrEqual(1)

    await openNewlyCreatedDrawer(page, certName)
    await page.getByRole('tab', { name: '操作日志' }).click()
    await page.waitForSelector('[data-testid="qd-op-log-table"] .qd-op-log-item', { timeout: 10000 })

    // 收集所有时间并断言降序
    const items = page.locator('[data-testid="qd-op-log-table"] .qd-op-log-item')
    const count = await items.count()
    expect(count).toBeGreaterThanOrEqual(1)

    const timestamps = []
    for (let i = 0; i < count; i++) {
      const t = (await items.nth(i).locator('[data-testid="qd-op-log-time"]').textContent())?.trim()
      if (t) timestamps.push(t)
    }
    // 验证倒序
    for (let i = 1; i < timestamps.length; i++) {
      expect(timestamps[i - 1] >= timestamps[i], `时间应倒序：${timestamps[i - 1]} >= ${timestamps[i]}`).toBe(true)
    }
  })

  test('权限：bid_specialist 也能查看操作日志（不限 ADMIN / AUDITOR）', async ({ page }) => {
    const adminSession = await loginAsRole(page, '/bidAdmin', 'E2E oplog admin')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
    const certName = `E2E oplog test cert ${suffix}`
    const newId = await createQualificationWithAudit(adminSession.token, suffix, certName)

    // 切到 bid_specialist 身份
    const session2 = await loginAsRole(page, 'bid-Team', 'E2E oplog specialist')
    // 直接验证 API：bid_specialist 调用 audit-logs 应 200（不要求 ADMIN）
    const apiResp = await fetch(`${apiBaseUrl}/api/qualifications/${newId}/audit-logs`, {
      headers: { 'Authorization': `Bearer ${session2.token}` }
    })
    expect(apiResp.status, 'bid_specialist 应能读 audit-logs').toBe(200)
    const body = await apiResp.json()
    expect(body?.success).toBe(true)
    expect(Array.isArray(body?.data)).toBe(true)

    // 同样，UI 也能加载
    await openNewlyCreatedDrawer(page, certName)
    await page.getByRole('tab', { name: '操作日志' }).click()
    await page.waitForSelector('[data-testid="qd-op-log-table"] .qd-op-log-item', { timeout: 10000 })
    const items = page.locator('[data-testid="qd-op-log-table"] .qd-op-log-item')
    expect(await items.count(), 'bid_specialist 也能看到至少 1 条日志').toBeGreaterThanOrEqual(1)
  })

  test('边界：重复切换 tab 不报错且数据稳定', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin', 'E2E oplog admin')
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
    const certName = `E2E oplog test cert ${suffix}`
    await createQualificationWithAudit(session.token, suffix, certName)

    await openNewlyCreatedDrawer(page, certName)

    // 第一次进入 tab
    await page.getByRole('tab', { name: '操作日志' }).click()
    await page.waitForSelector('[data-testid="qd-op-log-table"] .qd-op-log-item', { timeout: 10000 })

    // 切回基本信息
    await page.getByRole('tab', { name: '基本信息' }).click()
    await expect(page.getByRole('tab', { name: '基本信息' })).toHaveAttribute('aria-selected', 'true', { timeout: 5000 })

    // 再次切到操作日志，不应报错且仍能渲染
    await page.getByRole('tab', { name: '操作日志' }).click()
    await expect(page.getByRole('tab', { name: '操作日志' })).toHaveAttribute('aria-selected', 'true', { timeout: 5000 })
    const items = page.locator('[data-testid="qd-op-log-table"] .qd-op-log-item')
    expect(await items.count()).toBeGreaterThanOrEqual(1)

    // 关闭抽屉
    await page.locator('[data-testid="qd-close-btn"]').click()
  })
})
