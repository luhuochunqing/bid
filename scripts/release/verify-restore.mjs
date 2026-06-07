// Input: UAT report path, restore marker path, and rehearsal state environment variables
// Output: post-restore verification report JSON
// Pos: scripts/release/ - Release automation and rehearsal helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import fs from 'node:fs'
import path from 'node:path'

const apiBaseUrl = process.env.UAT_API_BASE_URL || 'http://127.0.0.1:18080'
const reportDir = process.env.REPORT_DIR || path.resolve(process.cwd(), 'docs/reports')
const stateDir = process.env.STATE_DIR || path.resolve(process.cwd(), '.rehearsal')
const uatReportPath = process.argv[2]
const markerPath = process.argv[3] || path.join(stateDir, 'restore-marker.json')

if (!uatReportPath) {
  console.error('Usage: node verify-restore.mjs <uat-report.json> [restore-marker.json]')
  process.exit(1)
}

const uat = JSON.parse(fs.readFileSync(uatReportPath, 'utf8'))
const marker = JSON.parse(fs.readFileSync(markerPath, 'utf8'))
const username = uat.artifacts?.username
const password = uat.artifacts?.password || process.env.UAT_TEST_PASSWORD || 'XiyuGoLive!2026'

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

const login = await fetchJson(`${apiBaseUrl}/api/auth/login`, {
  method: 'POST',
  body: JSON.stringify({ username, password }),
})

if (login.response.status !== 200) {
  throw new Error(`Login failed after restore with status ${login.response.status}`)
}

const token = login.body?.data?.token
const check = await fetchJson(`${apiBaseUrl}/api/resources/bar-assets/${marker.mutationAssetId}`, {
  headers: { Authorization: `Bearer ${token}` },
})

if (check.response.status !== 404) {
  throw new Error(`Restore verification failed; mutation asset still present (status=${check.response.status})`)
}

const reportPath = path.join(reportDir, `rehearsal-restore-${new Date().toISOString().replace(/[:.]/g, '-')}.md`)
const lines = [
  '# Release Rehearsal Restore Report',
  '',
  `- 时间: ${new Date().toISOString()}`,
  `- 验证账号: ${username}`,
  `- Mutation Asset ID: ${marker.mutationAssetId}`,
  `- Mutation Asset Name: ${marker.mutationAssetName}`,
  '- 结果: backup 之后创建的变更在 restore 后不可见，回滚验证通过',
]
fs.writeFileSync(reportPath, `${lines.join('\n')}\n`)
console.log(reportPath)
