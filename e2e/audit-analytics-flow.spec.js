// @ui-cover:audit,dashboard
import { test, expect } from '@playwright/test'
import { ensureApiSession } from './auth-helpers.js'
const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1323'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }

  return payload
}

// H13 后 access token 走 HttpOnly Set-Cookie（body.token=null），统一复用 auth-helpers 会话逻辑
async function ensureSession() {
  const session = await ensureApiSession({
    username: process.env.COMMERCIAL_E2E_USERNAME || `eri101_${Date.now()}`,
    role: '/bidAdmin',
    fullName: 'ERI-101 E2E',
    password: process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026',
  })

  return {
    token: session.token,
    user: {
      id: session.user.id,
      name: session.user.name,
      username: session.user.username,
      email: session.user.email,
      role: String(session.user.role || '').toLowerCase(),
    },
  }
}

async function authedJson(path, session, options = {}) {
  const headers = {
    Authorization: `Bearer ${session.token}`,
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(options.headers || {}),
  }
  return requestJson(`${apiBaseUrl}${path}`, { ...options, headers })
}

function auditQueryPath(params, basePath = '/api/audit') {
  return `${basePath}?${new URLSearchParams(params).toString()}`
}

async function waitForAuditItem(session, params, matchesItem, basePath = '/api/audit') {
  let items = []

  await expect.poll(async () => {
    const payload = await authedJson(auditQueryPath(params, basePath), session)
    items = Array.isArray(payload?.data?.items) ? payload.data.items : []
    return items.some(matchesItem) ? 1 : 0
  }, {
    message: 'Expected operation log item was not visible before timeout',
    intervals: [500, 1000],
    timeout: 15000,
  }).toBe(1)

  return items.find(matchesItem)
}

test('dashboard, operation log and audit log screens render one real key operation record', async ({ page }) => {
  const session = await ensureSession()
  const suffix = Date.now()

  const qualification = await authedJson('/api/knowledge/qualifications', session, {
    method: 'POST',
    body: JSON.stringify({
      name: `ERI-101 操作日志资质 ${suffix}`,
      type: 'CONSTRUCTION',
      level: 'FIRST',
      subjectType: 'COMPANY',
      subjectName: '西域数智化投标管理平台',
      category: 'LICENSE',
      issueDate: '2026-04-01',
      expiryDate: '2027-04-01',
      certificateNo: `ERI-AUDIT-${suffix}`,
      issuer: 'E2E 操作日志测试中心',
      agency: 'E2E 操作日志测试中心',
      agencyContact: 'E2E 联系人',
      certScope: '一般项目',
      holderName: '西域数智',
      fileUrl: '',
    }),
  })
  expect(qualification?.data?.id).toBeTruthy()

  const qualificationId = String(qualification.data.id)
  const auditItem = await waitForAuditItem(
    session,
    { keyword: qualificationId, action: 'CREATE', module: 'qualification' },
    (item) => String(item.target) === qualificationId && item.detail === '创建资质'
  )
  expect(auditItem).toMatchObject({
    actionType: 'create',
    detail: '创建资质',
    target: qualificationId,
  })
  const operationItem = await waitForAuditItem(
    session,
    { keyword: qualificationId, action: 'CREATE', module: 'qualification' },
    (item) => String(item.target) === qualificationId && item.detail === '创建资质',
    '/api/audit/my'
  )
  expect(operationItem).toMatchObject({
    actionType: 'create',
    detail: '创建资质',
    target: qualificationId,
  })

  await page.context().addCookies([{ name: "access_token", value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: "Lax" }, { name: "access_token", value: session.token, url: frontendBaseUrl, httpOnly: true, sameSite: "Lax" }])
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)

  await page.goto('/analytics/dashboard')
  await expect(page.getByRole('heading', { name: '数据分析' })).toBeVisible()
  // M0~M4 重构后的板块锚点（原'投入产出分析/中标率趋势'已随旧 Dashboard 下线）
  await expect(page.getByText('关键指标')).toBeVisible()
  await expect(page.getByText('多维度趋势分析')).toBeVisible()
  await expect(page.locator('.kpi-card').first()).toBeVisible()

  await page.goto('/operation-logs')
  await expect(page.getByRole('heading', { name: '操作日志' })).toBeVisible()
  await expect(page.getByText('今日操作')).toBeVisible()

  await page.getByPlaceholder('搜索操作内容/对象').fill(qualificationId)
  await page.getByRole('button', { name: /搜索/ }).click()

  const qualificationAuditRow = page
    .locator('.audit-table .el-table__row')
    .filter({ hasText: qualificationId })
    .filter({ hasText: '创建资质' })
    .first()
  await expect(qualificationAuditRow).toBeVisible()
  await expect(qualificationAuditRow).toContainText('创建资质')

  // 注：/bidAdmin 权限清单仅含 operation-logs（无 audit-logs），
  // /audit-logs 会被守卫重定向回工作台，审计页断言随权限模型收敛移除
})
