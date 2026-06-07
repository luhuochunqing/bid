// Input: Playwright E2E + mock AI provider
// Output: 蓝图 4.1.1.2.1 AI 案例沉淀 — 6 步异步任务模型的端到端验证
// Pos: e2e/ - Playwright end-to-end coverage
// 维护声明: 修改 KnowledgeCaseController 沉淀相关端点或 ProjectStage 阶段机时同步更新本测试。

import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

async function apiRequest(path, session, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
    },
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${path} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }
  return payload
}

function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

async function createTenderAndProject(session, name) {
  const suffix = Date.now() + Math.floor(Math.random() * 1000)
  const tender = await apiRequest('/api/tenders', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `${name} ${suffix}`,
      source: 'Playwright',
      budget: 100000,
      deadline: toLocalDateTimeString(new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)),
      status: 'TRACKING',
    })
  })
  const tenderId = tender?.data?.id
  expect(tenderId).toBeTruthy()

  const project = await apiRequest('/api/projects', session, {
    method: 'POST',
    body: JSON.stringify({
      name: `${name}项目 ${suffix}`,
      tenderId,
      status: 'BIDDING',
      managerId: session.user.id,
      teamMembers: [session.user.id],
      startDate: toLocalDateTimeString(new Date()),
      endDate: toLocalDateTimeString(new Date(Date.now() + 90 * 24 * 60 * 60 * 1000)),
    })
  })
  const projectId = project?.data?.id
  expect(projectId).toBeTruthy()
  return { projectId, tenderId }
}

test.describe('4.1.1.2.1 AI 案例沉淀 — 6 步异步任务模型', () => {

  test('前置条件：项目未达 CLOSED + 无标书文件 + 无评分项时 readiness 报缺失', async () => {
    const session = await ensureApiSession({
      username: `e2e_precip_readiness_${Date.now()}`,
      role: 'ADMIN',
      fullName: '沉淀前置条件测试员'
    })
    const { projectId } = await createTenderAndProject(session, '沉淀前置条件')

    const readiness = await apiRequest(
      `/api/cases/precipitation-readiness?projectId=${projectId}`,
      session
    )

    expect(readiness?.canPrecipitate ?? readiness?.data?.canPrecipitate).toBe(false)
    const missing = readiness?.missingItems ?? readiness?.data?.missingItems ?? []
    expect(missing.length).toBeGreaterThan(0)
    const joined = missing.join('|')
    expect(joined).toMatch(/标书|BID|文件/)
    expect(joined).toMatch(/评分项|打分点/)
    expect(joined).toMatch(/CLOSED|结项|阶段/)
  })

  test('权限：STAFF 角色无法访问手动触发沉淀端点', async () => {
    const adminSession = await ensureApiSession({
      username: `e2e_precip_seed_${Date.now()}`,
      role: 'ADMIN',
      fullName: '沉淀权限测试 Admin'
    })
    const { projectId } = await createTenderAndProject(adminSession, '沉淀权限测试')

    const staffSession = await ensureApiSession({
      username: `e2e_precip_staff_${Date.now()}`,
      role: 'STAFF',
      fullName: '沉淀权限测试 STAFF'
    })

    const response = await fetch(
      `${apiBaseUrl}/api/cases/precipitate?projectId=${projectId}`,
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${staffSession.token}` }
      }
    )
    // @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')") 阻止 STAFF
    expect(response.status).toBeGreaterThanOrEqual(400)
  })

  test('前置条件不满足时手动触发被拒（4xx）', async () => {
    const session = await ensureApiSession({
      username: `e2e_precip_reject_${Date.now()}`,
      role: 'ADMIN',
      fullName: '沉淀拒绝测试员'
    })
    const { projectId } = await createTenderAndProject(session, '沉淀拒绝')

    const response = await fetch(
      `${apiBaseUrl}/api/cases/precipitate?projectId=${projectId}`,
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${session.token}` }
      }
    )
    expect(response.status).toBeGreaterThanOrEqual(400)
    const body = await response.json().catch(() => ({}))
    const errMsg = body?.message || body?.data?.message || JSON.stringify(body)
    expect(errMsg).toMatch(/前置条件|缺少|CLOSED|标书|评分项/)
  })

  test('页面访问：/knowledge/case 在 ADMIN 登录后可访问', async ({ page }) => {
    const session = await ensureApiSession({
      username: `e2e_precip_page_${Date.now()}`,
      role: 'ADMIN',
      fullName: '沉淀页面测试员'
    })
    await injectSession(page, session)
    const casesResponse = page.waitForResponse(
      (resp) => resp.url().includes('/api/cases') && resp.url().includes('page=0'),
      { timeout: 10000 }
    ).catch(() => null)
    await page.goto('/knowledge/case')
    await casesResponse
    await expect(page.getByText('AI 案例库网格').first()).toBeVisible({ timeout: 10000 })
  })

  test('完整闭环：手动触发沉淀被拒时 readiness 报缺失项', async () => {
    // E2E 栈能完整走完"项目结项+沉淀"的端到端需要 stage transition HTTP 端点
    // 当前 E2E 栈主要验证：readiness / 权限 / 拒绝路径 / 页面访问
    // 完整入库闭环通过单元测试 (ProjectClosedEventListenerTest) + Mock 验证，
    // 避免在 E2E 栈做昂贵的事务边界处理。
    const session = await ensureApiSession({
      username: `e2e_precip_gating_${Date.now()}`,
      role: 'ADMIN',
      fullName: '沉淀门禁测试员'
    })
    const { projectId } = await createTenderAndProject(session, '沉淀门禁')

    // 触发沉淀
    const triggerResp = await fetch(
      `${apiBaseUrl}/api/cases/precipitate?projectId=${projectId}`,
      { method: 'POST', headers: { Authorization: `Bearer ${session.token}` } }
    )
    expect(triggerResp.status).toBeGreaterThanOrEqual(400)

    // readiness 报缺失
    const readiness = await apiRequest(
      `/api/cases/precipitation-readiness?projectId=${projectId}`,
      session
    )
    expect(readiness?.canPrecipitate ?? readiness?.data?.canPrecipitate).toBe(false)
  })

  test('异常处理兜底：后端 missingItems 文本含前端可场景化分类的全部关键字', async () => {
    // 蓝图 4.1.1.2.1 异常处理：
    //   - 标书文件缺失 → 前端按钮 disabled，提示 "上传标书后即可触发"
    //   - 评分项为空   → 前端按钮 disabled，提示 "先在标书编制阶段完成招标文件解析"
    // 验证后端 missingItems 文本与前端 readinessToTooltip 关键词匹配（src/views/Project/stages/readinessTooltip.js），
    // 确保 disabled 状态下 el-tooltip 能渲染出场景化中文提示。
    const session = await ensureApiSession({
      username: `e2e_precip_abnormal_${Date.now()}`,
      role: 'ADMIN',
      fullName: '沉淀异常处理测试员'
    })
    const { projectId } = await createTenderAndProject(session, '沉淀异常处理')

    const readiness = await apiRequest(
      `/api/cases/precipitation-readiness?projectId=${projectId}`,
      session
    )
    expect(readiness?.canPrecipitate ?? readiness?.data?.canPrecipitate).toBe(false)
    const missing = readiness?.missingItems ?? readiness?.data?.missingItems ?? []
    expect(missing.length).toBeGreaterThan(0)
    const joined = missing.join('|')

    // 三类缺失项至少覆盖前两个：标书 / 评分项（项目未结项不是新建项目必出现）
    expect(joined).toMatch(/标书|BID|文件/)
    expect(joined).toMatch(/评分项|打分点|招标文件/)
    // 关键字必须与前端 readinessTooltip.js 的正则匹配
    expect(/标书|BID|文件/.test(joined)).toBe(true)
    expect(/评分项|打分点|招标文件/.test(joined)).toBe(true)
  })
})
