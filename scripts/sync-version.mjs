// Input: repository root VERSION and version-bearing metadata files
// Output: rewrites package.json and backend/pom.xml to match root VERSION
// Pos: scripts/ - Repository governance and maintenance scripts
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'

const rootDir = path.resolve(new URL('.', import.meta.url).pathname, '..')
const versionFile = path.join(rootDir, 'VERSION')
const packageFile = path.join(rootDir, 'package.json')
const pomFile = path.join(rootDir, 'backend', 'pom.xml')

function readVersion() {
  const version = fs.readFileSync(versionFile, 'utf8').trim()
  if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(version)) {
    throw new Error(`VERSION 文件格式非法: ${version}`)
  }
  return version
}

function syncPackageJson(version) {
  const packageJson = JSON.parse(fs.readFileSync(packageFile, 'utf8'))
  packageJson.version = version
  fs.writeFileSync(packageFile, `${JSON.stringify(packageJson, null, 2)}\n`)
}

function syncPom(version) {
  const content = fs.readFileSync(pomFile, 'utf8')
  const pattern = /(<artifactId>bid-poc<\/artifactId>\s*<version>)([^<]+)(<\/version>)/s
  if (!pattern.test(content)) {
    throw new Error('未找到 backend/pom.xml 中的项目版本号节点')
  }
  const next = content.replace(pattern, `$1${version}$3`)
  fs.writeFileSync(pomFile, next)
}

const version = readVersion()
syncPackageJson(version)
syncPom(version)

console.log(`sync-version: updated package.json and backend/pom.xml to ${version}`)
