// Input: repository root VERSION, package.json, backend/pom.xml
// Output: exits non-zero when cross-runtime version metadata drifts
// Pos: scripts/ - Repository governance and maintenance scripts
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'

const rootDir = path.resolve(new URL('.', import.meta.url).pathname, '..')
const versionFile = path.join(rootDir, 'VERSION')
const packageFile = path.join(rootDir, 'package.json')
const pomFile = path.join(rootDir, 'backend', 'pom.xml')

function readTrimmed(filePath) {
  return fs.readFileSync(filePath, 'utf8').trim()
}

function readPackageVersion() {
  return JSON.parse(fs.readFileSync(packageFile, 'utf8')).version
}

function readPomVersion() {
  const content = fs.readFileSync(pomFile, 'utf8')
  const match = content.match(/<artifactId>bid-poc<\/artifactId>\s*<version>([^<]+)<\/version>/s)
  if (!match) {
    throw new Error('无法从 backend/pom.xml 解析项目版本号')
  }
  return match[1].trim()
}

const expected = readTrimmed(versionFile)
const checks = [
  ['VERSION', expected],
  ['package.json', readPackageVersion()],
  ['backend/pom.xml', readPomVersion()],
]

const mismatches = checks.filter(([, actual]) => actual !== expected)

if (mismatches.length > 0) {
  console.error(`版本不一致，根 VERSION=${expected}`)
  mismatches.forEach(([label, actual]) => {
    console.error(`- ${label}: ${actual}`)
  })
  process.exit(1)
}

console.log(`version-sync: ok (${expected})`)
