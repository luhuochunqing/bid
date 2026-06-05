// Input: release candidate metadata and report directory paths
// Output: release signoff packet files and summary artifacts
// Pos: scripts/release/ - Release automation and rehearsal helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(scriptDir, '../..')
const reportDir = path.resolve(rootDir, 'docs/reports')
const releaseCandidate = process.env.RELEASE_CANDIDATE || 'manual'
const uatOwner = process.env.UAT_OWNER || 'QA'

function latestFile(prefix, extension) {
  const files = fs
    .readdirSync(reportDir)
    .filter((name) => name.startsWith(prefix) && name.endsWith(extension))
    .sort()

  if (files.length === 0) {
    throw new Error(`No report found for prefix ${prefix}`)
  }

  return path.join(reportDir, files[files.length - 1])
}

const uatJsonPath = latestFile('uat-report-', '.json')
const restoreReportPath = latestFile('rehearsal-restore-', '.md')
const uat = JSON.parse(fs.readFileSync(uatJsonPath, 'utf8'))

const now = new Date().toISOString()
const outputPath = path.join(reportDir, `formal-uat-signoff-${now.replace(/[:.]/g, '-')}.md`)

const defectLines =
  uat.defects.length === 0
    ? ['- None']
    : uat.defects.map((item) => `- ${item.priority} ${item.name}: ${item.detail}`)

const lines = [
  '# Formal UAT Signoff Packet',
  '',
  `- Release Candidate: ${releaseCandidate}`,
  `- Generated At: ${now}`,
  `- UAT Owner: ${uatOwner}`,
  `- Automated UAT Report: ${path.relative(rootDir, uatJsonPath)}`,
  `- Restore Rehearsal Report: ${path.relative(rootDir, restoreReportPath)}`,
  `- Automated Verdict: ${uat.overall}`,
  '',
  '## Automated Summary',
  `- Passed Scenarios: ${uat.passed.length}`,
  `- Failed Scenarios: ${uat.failed.length}`,
  '',
  '## Known Defects At Signoff Time',
  ...defectLines,
  '',
  '## Business Signoff',
  '| Role | Name | Decision | Date | Notes |',
  '| --- | --- | --- | --- | --- |',
  '| 业务负责人 |  | Go / No-Go |  |  |',
  '| 销售代表 |  | Go / No-Go |  |  |',
  '| 财务/资源代表 |  | Go / No-Go |  |  |',
  '| QA |  | Go / No-Go |  |  |',
  '| 技术负责人 |  | Go / No-Go |  |  |',
  '',
  '## Final Decision',
  '- Final Go / No-Go:',
  '- Signed Release Candidate:',
  '- Follow-up Actions:',
]

fs.writeFileSync(outputPath, `${lines.join('\n')}\n`)
console.log(outputPath)
