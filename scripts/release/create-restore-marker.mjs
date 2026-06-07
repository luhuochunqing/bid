// Input: UAT report path, report directory, and restore-state environment variables
// Output: restore marker JSON for release rehearsal verification
// Pos: scripts/release/ - Release automation and rehearsal helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import fs from 'node:fs'
import path from 'node:path'

const apiBaseUrl = process.env.UAT_API_BASE_URL || 'http://127.0.0.1:18080'
const reportDir = process.env.REPORT_DIR || path.resolve(process.cwd(), 'docs/reports')
const stateDir = process.env.STATE_DIR || path.resolve(process.cwd(), '.rehearsal')
const uatReportPath = process.argv[2]

if (!uatReportPath) {
  console.error('Usage: node create-restore-marker.mjs <uat-report.json>')
  process.exit(1)
}

const uat = JSON.parse(fs.readFileSync(uatReportPath, 'utf8'))
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
  return { response, body: text ? JSON.parse(text) : null }
}

const login = await fetchJson(`${apiBaseUrl}/api/auth/login`, {
  method: 'POST',
  body: JSON.stringify({ username, password }),
})

if (login.response.status !== 200) {
  throw new Error(`Login failed with status ${login.response.status}`)
}

const token = login.body?.data?.token
const suffix = Date.now().toString().slice(-6)
const create = await fetchJson(`${apiBaseUrl}/api/resources/bar-assets`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}` },
  body: JSON.stringify({
    name: `POST-BACKUP-MUTATION-${suffix}`,
    type: 'OTHER',
    value: 100,
    status: 'AVAILABLE',
    acquireDate: '2026-03-10',
    remark: 'This asset must disappear after restore',
  }),
})

if (create.response.status !== 200) {
  throw new Error(`Create restore marker failed with status ${create.response.status}`)
}

const output = {
  username,
  mutationAssetId: create.body?.data?.id,
  mutationAssetName: create.body?.data?.name,
}

fs.mkdirSync(stateDir, { recursive: true })
const outputPath = path.join(stateDir, 'restore-marker.json')
fs.writeFileSync(outputPath, `${JSON.stringify(output, null, 2)}\n`)
console.log(outputPath)
