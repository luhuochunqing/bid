// Input: real API base URL and sales-user credentials
// Output: k6 performance report metrics for 200 concurrent sales-user workflow
// Pos: scripts/performance/ - Real API load-testing scripts
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'

const apiBaseUrl = (__ENV.API_BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '')
const defaultUsername = __ENV.K6_USERNAME || '小王'
const defaultPassword = __ENV.K6_PASSWORD || 'XiyuDemo!2026'
const skipExport = String(__ENV.K6_SKIP_EXPORT || 'false').toLowerCase() === 'true'
const completeUpload = String(__ENV.K6_COMPLETE_UPLOAD || 'false').toLowerCase() === 'true'
const skipTenderUpload = String(__ENV.K6_SKIP_TENDER_UPLOAD || 'false').toLowerCase() === 'true'
const syncBidAgentParse = String(__ENV.K6_BID_AGENT_SYNC_PARSE || 'false').toLowerCase() === 'true'
const tenderFilePath = __ENV.TENDER_FILE_PATH || ''
const tenderFileName = __ENV.TENDER_FILE_NAME || 'performance-tender.docx'
const tenderFileType = __ENV.TENDER_FILE_TYPE || 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
const tenderFileBytes = tenderFilePath ? open(tenderFilePath, 'b') : null

const users = parseUsers()
const workflowErrors = new Counter('xiyu_workflow_errors')
const workflowSuccessRate = new Rate('xiyu_workflow_success_rate')
const loginTrend = new Trend('xiyu_login_duration')
const uploadInitTrend = new Trend('xiyu_tender_upload_init_duration')

export const options = {
  stages: [
    { duration: '3m', target: 200 },
    { duration: '10m', target: 200 },
    { duration: '5m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    xiyu_login_duration: ['p(95)<800'],
    xiyu_tender_upload_init_duration: ['p(95)<2000'],
    xiyu_workflow_success_rate: ['rate>0.99'],
  },
}

export default function () {
  const user = users[__VU % users.length]
  const token = login(user)
  if (!token) {
    workflowSuccessRate.add(false)
    workflowErrors.add(1)
    sleep(1)
    return
  }

  const authHeaders = {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  }

  const projects = loadProjects(authHeaders)
  const projectId = projects.length > 0 ? projects[0].id : null

  if (projectId) {
    get(`/api/projects/${projectId}`, authHeaders, 'project detail')
    get(`/api/projects/${projectId}/tasks`, authHeaders, 'project tasks')
    get(`/api/projects/${projectId}/documents`, authHeaders, 'project documents')
  }

  get('/api/analytics/overview', authHeaders, 'dashboard overview')
  get('/api/analytics/product-lines', authHeaders, 'dashboard product lines')
  if (!skipExport) {
    post('/api/export/excel', JSON.stringify({ dataType: 'projects', params: {}, async: false }), authHeaders, 'export projects')
  }
  exerciseTenderUpload(authHeaders)

  if (syncBidAgentParse && projectId && tenderFileBytes) {
    exerciseBidAgentSyncParse(projectId, token)
  }

  workflowSuccessRate.add(true)
  sleep(Math.random() * 2 + 1)
}

function parseUsers() {
  if (!__ENV.SALES_USERS) {
    return [{ username: defaultUsername, password: defaultPassword }]
  }
  const parsed = JSON.parse(__ENV.SALES_USERS)
  if (!Array.isArray(parsed) || parsed.length === 0) {
    throw new Error('SALES_USERS must be a non-empty JSON array')
  }
  return parsed
}

function login(user) {
  const started = Date.now()
  const response = http.post(`${apiBaseUrl}/api/auth/login`, JSON.stringify(user), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
  })
  loginTrend.add(Date.now() - started)
  const ok = check(response, {
    'login status is 200': (res) => res.status === 200,
    'login returns token': (res) => Boolean(res.json('data.token')),
  })
  if (!ok) {
    return null
  }
  return response.json('data.token')
}

function loadProjects(authHeaders) {
  const response = get('/api/projects', authHeaders, 'project list')
  if (!response || response.status !== 200) {
    return []
  }
  const data = response.json('data')
  return Array.isArray(data) ? data : []
}

function exerciseTenderUpload(authHeaders) {
  if (skipTenderUpload) {
    return
  }

  const payload = {
    fileName: tenderFileName,
    expectedFileSize: Number(__ENV.K6_TENDER_EXPECTED_SIZE || 10485760),
  }
  const started = Date.now()
  const init = post('/api/tenders/upload-init', JSON.stringify(payload), authHeaders, 'tender upload init')
  uploadInitTrend.add(Date.now() - started)

  if (!completeUpload || !init || init.status !== 200) {
    return
  }

  const uploadId = init.json('data.uploadId')
  if (!uploadId) {
    return
  }
  post(
    '/api/tenders/upload-complete',
    JSON.stringify({ uploadId, pageCount: Number(__ENV.K6_TENDER_PAGE_COUNT || 200), priority: 5 }),
    authHeaders,
    'tender upload complete'
  )
}

function exerciseBidAgentSyncParse(projectId, token) {
  const form = {
    file: http.file(tenderFileBytes, tenderFileName, tenderFileType),
  }
  const response = http.post(`${apiBaseUrl}/api/projects/${projectId}/bid-agent/tender-documents`, form, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: '120s',
    tags: { name: 'bid agent tender document sync parse' },
  })
  check(response, {
    'bid-agent parse accepted': (res) => [200, 400, 409, 413, 429].includes(res.status),
  })
}

function get(path, params, name) {
  const response = http.get(`${apiBaseUrl}${path}`, { ...params, tags: { name } })
  check(response, { [`${name} status is not 5xx`]: (res) => res.status < 500 })
  if (response.status >= 500) {
    workflowErrors.add(1)
  }
  return response
}

function post(path, body, params, name) {
  const response = http.post(`${apiBaseUrl}${path}`, body, { ...params, tags: { name } })
  check(response, { [`${name} status is not 5xx`]: (res) => res.status < 500 })
  if (response.status >= 500) {
    workflowErrors.add(1)
  }
  return response
}
