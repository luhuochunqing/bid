// Input: production base URLs, smoke credentials, CRM smoke mode, and report output directory
// Output: production smoke verification reports with go/no-go verdict
// Pos: scripts/release/ - Release automation and rehearsal helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import fs from 'node:fs'
import path from 'node:path'

const apiBaseUrl = process.env.PRODUCTION_API_BASE_URL
const webBaseUrl = process.env.PRODUCTION_WEB_BASE_URL
const username = process.env.PROD_SMOKE_USERNAME
const password = process.env.PROD_SMOKE_PASSWORD
const reportDir = process.env.REPORT_DIR || path.resolve(process.cwd(), 'docs/reports')
const prometheusMode = (process.env.PROMETHEUS_MODE || 'protected').toLowerCase()
const crmSmokeMode = (process.env.CRM_SMOKE_MODE || 'optional').toLowerCase()
const crmSmokePageSize = Number.parseInt(process.env.CRM_SMOKE_PAGE_SIZE || '1', 10)
const runId = new Date().toISOString().replace(/[:.]/g, '-')

if (!apiBaseUrl || !webBaseUrl || !username || !password) {
  console.error('Missing required env: PRODUCTION_API_BASE_URL, PRODUCTION_WEB_BASE_URL, PROD_SMOKE_USERNAME, PROD_SMOKE_PASSWORD')
  process.exit(1)
}

if (!['skip', 'optional', 'required'].includes(crmSmokeMode)) {
  console.error('CRM_SMOKE_MODE must be one of: skip, optional, required')
  process.exit(1)
}

if (!Number.isInteger(crmSmokePageSize) || crmSmokePageSize < 1 || crmSmokePageSize > 100) {
  console.error('CRM_SMOKE_PAGE_SIZE must be an integer between 1 and 100')
  process.exit(1)
}

const state = {
  passed: [],
  failed: [],
  overall: 'GO',
}

function recordPass(name, detail) {
  state.passed.push({ name, detail })
}

function recordFail(name, detail, priority = 'P0') {
  state.failed.push({ name, detail, priority })
  state.overall = 'NO-GO'
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

function requireStatus(response, allowed, message) {
  if (!allowed.includes(response.status)) {
    throw new Error(`${message} (status=${response.status})`)
  }
}

function requireTruthy(value, message) {
  if (!value) {
    throw new Error(message)
  }
}

function apiData(body) {
  if (body && typeof body === 'object' && 'data' in body) {
    return body.data
  }
  return body
}

function isApiSuccess(body) {
  if (!body || typeof body !== 'object') {
    return false
  }
  if ('success' in body) {
    return body.success === true
  }
  if ('code' in body) {
    return body.code === 0 || body.code === '0'
  }
  return true
}

function extractAccessToken(response) {
  const setCookie = response.headers.get('set-cookie') || ''
  const match = setCookie.match(/access_token=([^;]+)/)
  return match ? match[1] : null
}

async function main() {
  fs.mkdirSync(reportDir, { recursive: true })

  await expectOk('生产前端首页可访问', async () => {
    const response = await fetch(webBaseUrl)
    requireStatus(response, [200], 'Frontend homepage is unavailable')
    recordPass('生产前端首页可访问', webBaseUrl)
  })

  await expectOk('生产健康检查返回 UP', async () => {
    const { response, body } = await fetchJson(`${apiBaseUrl}/actuator/health`)
    requireStatus(response, [200], 'Health endpoint is unavailable')
    requireTruthy(body?.status === 'UP', 'Health endpoint did not report UP')
    recordPass('生产健康检查返回 UP', `${apiBaseUrl}/actuator/health`)
  })

  await expectOk('Prometheus 暴露策略符合预期', async () => {
    const response = await fetch(`${apiBaseUrl}/actuator/prometheus`)
    if (prometheusMode === 'skip') {
      recordPass('Prometheus 暴露策略符合预期', 'skipped by PROMETHEUS_MODE=skip')
      return
    }

    if (prometheusMode === 'public') {
      requireStatus(response, [200], 'Prometheus endpoint must be public')
      const text = await response.text()
      requireTruthy(text.includes('jvm_') || text.includes('process_'), 'Prometheus payload is empty')
      recordPass('Prometheus 暴露策略符合预期', 'public metrics reachable')
      return
    }

    requireStatus(response, [200, 401, 403], 'Prometheus endpoint exposure is unexpected')
    if (response.status === 200) {
      const text = await response.text()
      requireTruthy(text.includes('jvm_') || text.includes('process_'), 'Prometheus payload is empty')
      recordPass('Prometheus 暴露策略符合预期', 'metrics reachable')
      return
    }

    recordPass('Prometheus 暴露策略符合预期', `protected with status=${response.status}`)
  })

  let token = null

  await expectOk('生产 smoke 账号可登录', async () => {
    const { response, body } = await fetchJson(`${apiBaseUrl}/api/auth/login`, {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    requireStatus(response, [200], 'Smoke user login failed')
    token = apiData(body)?.token || extractAccessToken(response)
    requireTruthy(token, 'Smoke user login did not return token')
    recordPass('生产 smoke 账号可登录', username)
  })

  const authed = (endpoint) =>
    fetchJson(`${apiBaseUrl}${endpoint}`, {
      headers: { Authorization: `Bearer ${token}` },
    })

  await expectOk('当前用户信息可读取', async () => {
    const { response, body } = await authed('/api/auth/me')
    requireStatus(response, [200], 'Current user endpoint failed')
    requireTruthy(apiData(body)?.username === username, 'Current user does not match smoke account')
    recordPass('当前用户信息可读取', username)
  })

  const readChecks = [
    ['Dashboard 概览可读', '/api/analytics/overview'],
    ['标讯列表可读', '/api/tenders?page=1&pageSize=1'],
    ['项目列表可读', '/api/projects?page=1&pageSize=1'],
    ['资质列表可读', '/api/knowledge/qualifications?page=1&pageSize=1'],
    ['案例列表可读', '/api/knowledge/cases?page=1&pageSize=1'],
    ['模板列表可读', '/api/knowledge/templates?page=1&pageSize=1'],
    ['费用列表可读', '/api/resources/expenses?page=1&pageSize=1'],
    ['BAR 资产列表可读', '/api/resources/bar-assets?page=1&pageSize=1'],
  ]

  for (const [name, endpoint] of readChecks) {
    await expectOk(name, async () => {
      const { response, body } = await authed(endpoint)
      requireStatus(response, [200], `${endpoint} failed`)
      requireTruthy(apiData(body) !== null, `${endpoint} returned empty payload`)
      recordPass(name, endpoint)
    })
  }

  await expectOk('CRM page-list 只读查询契约正常', async () => {
    if (crmSmokeMode === 'skip') {
      recordPass('CRM page-list 只读查询契约正常', 'skipped by CRM_SMOKE_MODE=skip')
      return
    }

    const { response, body } = await fetchJson(`${apiBaseUrl}/api/xiyu/crm/chances/page-list`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        pageIndex: 1,
        pageSize: crmSmokePageSize,
        body: { selectAll: true },
      }),
    })
    if (response.status === 409) {
      throw new Error('CRM page-list returned 409 Conflict; token/config regression likely')
    }
    requireStatus(response, [200], 'CRM page-list failed')
    requireTruthy(isApiSuccess(body), 'CRM page-list response is not successful')

    const data = apiData(body)
    const list = data?.list || data?.records || data?.content || []
    requireTruthy(Array.isArray(list), 'CRM page-list data list is not an array')
    const totalCount = Number(data?.totalCount ?? data?.total ?? data?.totalElements ?? list.length)
    requireTruthy(Number.isFinite(totalCount), 'CRM page-list total count is not numeric')
    if (crmSmokeMode === 'required') {
      requireTruthy(list.length > 0 || totalCount > 0, 'CRM page-list returned empty result in required mode')
    }
    recordPass('CRM page-list 只读查询契约正常', `mode=${crmSmokeMode} status=200 total=${totalCount} rows=${list.length}`)
  })

  await expectOk('CRM search-by-tender 可返回匹配商机（空结果阻断发布）', async () => {
    const probeVars = ['PROBE_API_BASE_URL', 'PROBE_USERNAME', 'PROBE_PASSWORD', 'PROBE_GROUP_NAME', 'PROBE_EVALUATION_DATE']
    const missing = probeVars.filter((key) => !process.env[key])
    if (missing.length > 0) {
      recordPass('CRM search-by-tender 可返回匹配商机（空结果阻断发布）', `skipped, missing env: ${missing.join(', ')}`)
      return
    }

    const { execFileSync } = await import('node:child_process')
    const probePath = new URL('../crm-opportunity-probe.mjs', import.meta.url).pathname
    try {
      execFileSync(process.execPath, [probePath], {
        env: process.env,
        stdio: 'pipe',
        encoding: 'utf-8',
      })
    } catch (error) {
      throw new Error(`CRM probe failed: ${error.stderr || error.message}`)
    }
    recordPass('CRM search-by-tender 可返回匹配商机（空结果阻断发布）', `group=${process.env.PROBE_GROUP_NAME} date=${process.env.PROBE_EVALUATION_DATE}`)
  })

  const jsonPath = path.join(reportDir, `prod-smoke-report-${runId}.json`)
  const mdPath = path.join(reportDir, `prod-smoke-report-${runId}.md`)
  fs.writeFileSync(jsonPath, `${JSON.stringify(state, null, 2)}\n`)

  const lines = [
    '# Production Smoke Report',
    '',
    `- Time: ${new Date().toISOString()}`,
    `- API Base URL: ${apiBaseUrl}`,
    `- Web Base URL: ${webBaseUrl}`,
    `- Verdict: ${state.overall}`,
    `- Passed Checks: ${state.passed.length}`,
    `- Failed Checks: ${state.failed.length}`,
    '',
    '## Passed',
    ...(state.passed.length === 0 ? ['- None'] : state.passed.map((item) => `- ${item.name}: ${item.detail}`)),
    '',
    '## Failed',
    ...(state.failed.length === 0 ? ['- None'] : state.failed.map((item) => `- ${item.priority} ${item.name}: ${item.detail}`)),
  ]
  fs.writeFileSync(mdPath, `${lines.join('\n')}\n`)

  console.log(jsonPath)
  console.log(mdPath)

  if (state.failed.length > 0) {
    process.exit(1)
  }
}

await main()
