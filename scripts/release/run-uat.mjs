// Input: UAT base URLs, report directory, and release-run identifiers
// Output: UAT execution report JSON and supporting artifact paths
// Pos: scripts/release/ - Release automation and rehearsal helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import fs from 'node:fs'
import path from 'node:path'

const apiBaseUrl = process.env.UAT_API_BASE_URL || 'http://127.0.0.1:18080'
const webBaseUrl = process.env.UAT_WEB_BASE_URL || 'http://127.0.0.1:1314'
const reportDir = process.env.REPORT_DIR || path.resolve(process.cwd(), 'docs/reports')
const runId = new Date().toISOString().replace(/[:.]/g, '-')
const suffix = Date.now().toString().slice(-6)

const state = {
  passed: [],
  failed: [],
  defects: [],
  artifacts: {},
}

function recordPass(name, detail) {
  state.passed.push({ name, detail })
}

function recordFail(name, detail, priority = 'P1') {
  state.failed.push({ name, detail, priority })
  state.defects.push({ priority, name, detail })
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
  })

  const text = await response.text()
  let body = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = text
    }
  }

  return { response, body }
}

async function expectOk(name, fn) {
  try {
    await fn()
  } catch (error) {
    recordFail(name, error instanceof Error ? error.message : String(error))
  }
}

function requireTruthy(value, message) {
  if (!value) {
    throw new Error(message)
  }
}

function requireStatus(response, allowed, message) {
  if (!allowed.includes(response.status)) {
    throw new Error(`${message} (status=${response.status})`)
  }
}

function apiData(body) {
  if (body && typeof body === 'object' && 'data' in body) {
    return body.data
  }
  return body
}

async function main() {
  fs.mkdirSync(reportDir, { recursive: true })

  const username = `uat_admin_${suffix}`
  const password = process.env.UAT_TEST_PASSWORD || 'XiyuGoLive!2026'
  const email = `${username}@example.com`
  const fullName = 'Go Live UAT Admin'
  const tenderTitle = `UAT 标讯 ${suffix}`
  const projectName = `UAT 项目 ${suffix}`
  const qualificationName = `UAT 资质 ${suffix}`
  const caseTitle = `UAT 案例 ${suffix}`
  const templateName = `UAT 模板 ${suffix}`
  const assetName = `UAT BAR ${suffix}`
  const certificateSerialNo = `SERIAL-${suffix}`

  let token = null
  let userId = null
  let tenderId = null
  let projectId = null
  let qualificationId = null
  let caseId = null
  let templateId = null
  let expenseId = null
  let assetId = null
  let certificateId = null
  let backupMutationAssetId = null

  await expectOk('前端首页可访问', async () => {
    const response = await fetch(webBaseUrl)
    requireStatus(response, [200], 'Frontend preview is unavailable')
    recordPass('前端首页可访问', webBaseUrl)
  })

  await expectOk('健康检查可访问', async () => {
    const { response, body } = await fetchJson(`${apiBaseUrl}/actuator/health`)
    requireStatus(response, [200], 'Health endpoint is unavailable')
    requireTruthy(body?.status === 'UP', 'Health endpoint did not report UP')
    recordPass('健康检查可访问', 'actuator health returned UP')
  })

  await expectOk('Prometheus 暴露策略符合预期', async () => {
    const response = await fetch(`${apiBaseUrl}/actuator/prometheus`)
    requireStatus(response, [200, 401, 403], 'Prometheus endpoint exposure is unexpected')
    if (response.status === 200) {
      const text = await response.text()
      requireTruthy(text.includes('jvm_') || text.includes('process_'), 'Prometheus output is empty')
      recordPass('Prometheus 暴露策略符合预期', 'metrics exposed')
      return
    }
    recordPass('Prometheus 暴露策略符合预期', `protected with status=${response.status}`)
  })

  await expectOk('未授权访问被拒绝', async () => {
    const { response } = await fetchJson(`${apiBaseUrl}/api/projects`)
    requireTruthy([401, 403].includes(response.status), `Unexpected anonymous access status=${response.status}`)
    recordPass('未授权访问被拒绝', `status=${response.status}`)
  })

  await expectOk('注册管理员账号', async () => {
    const payload = { username, password, email, fullName, role: 'ADMIN' }
    const { response, body } = await fetchJson(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    requireStatus(response, [201], 'Register failed')
    const auth = apiData(body)
    token = auth?.token
    userId = auth?.id
    requireTruthy(token && userId, 'Register response missing token or user id')
    recordPass('注册管理员账号', username)
  })

  await expectOk('登录与当前用户查询', async () => {
    const { response, body } = await fetchJson(`${apiBaseUrl}/api/auth/login`, {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    requireStatus(response, [200], 'Login failed')
    const auth = apiData(body)
    token = auth?.token
    userId = auth?.id
    requireTruthy(token && userId, 'Login response missing token or user id')

    const me = await fetchJson(`${apiBaseUrl}/api/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    requireStatus(me.response, [200], '/api/auth/me failed')
    requireTruthy(apiData(me.body)?.username === username, 'Current user does not match login account')
    recordPass('登录与当前用户查询', username)
  })

  const authed = (method, endpoint, payload) =>
    fetchJson(`${apiBaseUrl}${endpoint}`, {
      method,
      headers: { Authorization: `Bearer ${token}` },
      ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
    })

  await expectOk('创建标讯', async () => {
    const { response, body } = await authed('POST', '/api/tenders', {
      title: tenderTitle,
      source: 'UAT',
      budget: 1250000,
      deadline: new Date(Date.now() + 14 * 24 * 3600 * 1000).toISOString(),
      status: 'TRACKING',
      aiScore: 88,
      riskLevel: 'LOW',
    })
    requireStatus(response, [201], 'Create tender failed')
    tenderId = apiData(body)?.id
    requireTruthy(tenderId, 'Create tender response missing id')
    recordPass('创建标讯', `tenderId=${tenderId}`)
  })

  await expectOk('创建项目', async () => {
    const startDate = new Date()
    const endDate = new Date(Date.now() + 30 * 24 * 3600 * 1000)
    const { response, body } = await authed('POST', '/api/projects', {
      name: projectName,
      tenderId,
      status: 'BIDDING',
      managerId: userId,
      teamMembers: [userId],
      startDate: startDate.toISOString(),
      endDate: endDate.toISOString(),
    })
    requireStatus(response, [201], 'Create project failed')
    projectId = apiData(body)?.id
    requireTruthy(projectId, 'Create project response missing id')
    recordPass('创建项目', `projectId=${projectId}`)
  })

  await expectOk('Dashboard 真实聚合数据可读', async () => {
    const { response, body } = await authed('GET', '/api/analytics/overview')
    requireStatus(response, [200], 'Dashboard overview failed')
    requireTruthy(apiData(body) !== null, 'Dashboard overview returned empty payload')
    recordPass('Dashboard 真实聚合数据可读', 'overview loaded')
  })

  await expectOk('Knowledge 主链路', async () => {
    let result = await authed('POST', '/api/knowledge/qualifications', {
      name: qualificationName,
      type: 'SERVICE',
      level: 'SECOND',
      issueDate: '2025-01-01',
      expiryDate: '2027-01-01',
      fileUrl: '/uat/qualification.pdf',
    })
    requireStatus(result.response, [201], 'Create qualification failed')
    qualificationId = apiData(result.body)?.id
    requireTruthy(qualificationId, 'Qualification id missing')

    result = await authed('POST', '/api/knowledge/cases', {
      title: caseTitle,
      industry: 'INFRASTRUCTURE',
      outcome: 'WON',
      amount: 980000,
      projectDate: '2025-06-01',
      description: 'UAT generated case',
    })
    requireStatus(result.response, [201], 'Create case failed')
    caseId = apiData(result.body)?.id
    requireTruthy(caseId, 'Case id missing')

    result = await authed('POST', '/api/knowledge/templates', {
      name: templateName,
      category: 'TECHNICAL',
      productType: 'SMART_CITY',
      industry: 'GOVERNMENT',
      documentType: 'TECHNICAL_PROPOSAL',
      fileUrl: '/uat/template.docx',
      tags: ['uat', 'release'],
      createdBy: userId,
    })
    requireStatus(result.response, [201], 'Create template failed')
    templateId = apiData(result.body)?.id
    requireTruthy(templateId, 'Template id missing')

    result = await authed('GET', `/api/knowledge/cases/${caseId}`)
    requireStatus(result.response, [200], 'Fetch case detail failed')
    requireTruthy(apiData(result.body)?.id === caseId, 'Case detail mismatch')

    recordPass('Knowledge 主链路', `qualification=${qualificationId}, case=${caseId}, template=${templateId}`)
  })

  await expectOk('费用审批与退还主链路', async () => {
    let result = await authed('POST', '/api/resources/expenses', {
      projectId,
      category: 'MATERIAL',
      amount: 1200,
      date: '2026-03-10',
      expenseType: '保证金',
      description: 'UAT expense',
      createdBy: username,
    })
    requireStatus(result.response, [200], 'Create expense failed')
    expenseId = apiData(result.body)?.id
    requireTruthy(expenseId, 'Expense id missing')

    result = await authed('POST', `/api/resources/expenses/${expenseId}/approve`, {
      result: 'APPROVED',
      comment: 'UAT approval',
      approver: username,
    })
    requireStatus(result.response, [200], 'Approve expense failed')
    requireTruthy(apiData(result.body)?.status === 'APPROVED', 'Expense did not enter APPROVED state')

    result = await authed('POST', `/api/resources/expenses/${expenseId}/return-request`, {
      actor: username,
      comment: 'Request return',
    })
    requireStatus(result.response, [200], 'Expense return-request failed')
    requireTruthy(apiData(result.body)?.status === 'RETURN_REQUESTED', 'Expense did not enter RETURN_REQUESTED state')

    result = await authed('POST', `/api/resources/expenses/${expenseId}/confirm-return`, {
      actor: username,
      comment: 'Confirm return',
    })
    requireStatus(result.response, [200], 'Expense confirm-return failed')
    requireTruthy(apiData(result.body)?.status === 'RETURNED', 'Expense did not enter RETURNED state')

    result = await authed('GET', '/api/resources/expenses/approval-records')
    requireStatus(result.response, [200], 'Fetch approval records failed')
    const records = apiData(result.body) || []
    requireTruthy(records.some((item) => String(item.expenseId) === String(expenseId)), 'Approval records were not created')

    recordPass('费用审批与退还主链路', `expenseId=${expenseId}`)
  })

  await expectOk('BAR 证书借用主链路', async () => {
    let result = await authed('POST', '/api/resources/bar-assets', {
      name: assetName,
      type: 'LICENSE',
      value: 20000,
      status: 'AVAILABLE',
      acquireDate: '2025-01-01',
      remark: 'UAT asset',
    })
    requireStatus(result.response, [200], 'Create bar asset failed')
    assetId = apiData(result.body)?.id
    requireTruthy(assetId, 'Bar asset id missing')

    result = await authed('POST', `/api/resources/bar-assets/${assetId}/certificates`, {
      type: 'UK',
      provider: 'UAT Provider',
      serialNo: certificateSerialNo,
      holder: username,
      location: 'UAT Cabinet',
      expiryDate: '2028-01-01',
      remark: 'UAT certificate',
    })
    requireStatus(result.response, [200], 'Create certificate failed')
    certificateId = apiData(result.body)?.id
    requireTruthy(certificateId, 'Certificate id missing')

    result = await authed('POST', `/api/resources/bar-assets/${assetId}/certificates/${certificateId}/borrow`, {
      borrower: username,
      projectId,
      purpose: 'UAT borrow',
      remark: 'UAT flow',
      expectedReturnDate: '2026-03-31',
    })
    requireStatus(result.response, [200], 'Borrow certificate failed')

    result = await authed('GET', `/api/resources/bar-assets/${assetId}/certificates/${certificateId}/borrow-records`)
    requireStatus(result.response, [200], 'Fetch certificate borrow records failed')
    requireTruthy((apiData(result.body) || []).length >= 1, 'Borrow records were not created')

    result = await authed('POST', `/api/resources/bar-assets/${assetId}/certificates/${certificateId}/return`, {
      remark: 'Returned in UAT',
    })
    requireStatus(result.response, [200], 'Return certificate failed')

    recordPass('BAR 证书借用主链路', `asset=${assetId}, certificate=${certificateId}`)
  })

  state.artifacts = {
    username,
    password,
    tenderTitle,
    projectName,
    qualificationName,
    caseTitle,
    templateName,
    assetName,
    certificateSerialNo,
    projectId,
    tenderId,
    qualificationId,
    caseId,
    templateId,
    expenseId,
    assetId,
    certificateId,
    apiBaseUrl,
    webBaseUrl,
  }

  const overall = state.failed.length === 0 ? 'Go' : 'No-Go'
  const reportPath = path.join(reportDir, `uat-report-${runId}.md`)
  const jsonPath = path.join(reportDir, `uat-report-${runId}.json`)

  const lines = [
    '# UAT Execution Report',
    '',
    `- 时间: ${new Date().toISOString()}`,
    `- 环境: api mode rehearsal`,
    `- API: ${apiBaseUrl}`,
    `- Web: ${webBaseUrl}`,
    `- 通过场景数: ${state.passed.length}`,
    `- 失败场景数: ${state.failed.length}`,
    `- 结论: ${overall}`,
    '',
    '## Passed',
    ...state.passed.map((item) => `- ${item.name}: ${item.detail}`),
    '',
    '## Failed',
    ...(state.failed.length === 0
      ? ['- None']
      : state.failed.map((item) => `- ${item.priority} ${item.name}: ${item.detail}`)),
    '',
    '## Defects',
    ...(state.defects.length === 0
      ? ['- None']
      : state.defects.map((item) => `- ${item.priority} ${item.name}: ${item.detail}`)),
  ]

  fs.writeFileSync(reportPath, `${lines.join('\n')}\n`)
  fs.writeFileSync(jsonPath, `${JSON.stringify({ overall, ...state }, null, 2)}\n`)

  console.log(reportPath)
  console.log(jsonPath)

  if (state.failed.length > 0) {
    process.exitCode = 1
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
