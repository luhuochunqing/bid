#!/usr/bin/env node
// Input: staged/pre-commit trigger; all .vue / .js / .ts files under src/
// Output: warning list of ungoverned API URLs (upload endpoints)
// Pos: scripts/ — pre-commit gate, runs non-blocking
// 维护声明: 本脚本与 src/api/upload.js 的 UPLOAD_ENDPOINTS 保持同步，新增业务上传类型时需同时更新白名单。

import { readFileSync, readdirSync } from 'fs'
import { join, extname, relative } from 'path'
import { fileURLToPath } from 'url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const ROOT = join(__dirname, '..')
const SRC = join(ROOT, 'src')

function shouldCheck(file) {
  const ext = extname(file)
  if (!['.vue'].includes(ext)) return false
  if (file.includes('.spec.')) return false
  return true
}

const KNOWN_GOVERNED_FILES = [
  'src/views/Project/stages/DraftingStage.vue',
  'src/views/Project/stages/ResultConfirmStage.vue',
  'src/views/Project/stages/ClosureStage.vue',
  'src/views/Project/stages/InitiationStage.vue',
  'src/views/Project/stages/RetrospectiveStage.vue',
  'src/views/Project/stages/components/EvaluationEvidenceUpload.vue',
]

const ALLOWED_PATTERNS = [
  /getUploadUrl\(/,
  /uploadFile\(/,
  /\/api\/projects\/\$\{/,
  /\/api\/tenders\/\$\{/,
  /action="#"/, /action='#'/,
  /auto-upload="false"/, /:auto-upload="false"/,
]

function walkDir(dir) {
  const results = []
  try {
    const entries = readdirSync(dir, { withFileTypes: true })
    for (const entry of entries) {
      const fullPath = join(dir, entry.name)
      if (entry.isDirectory()) {
        if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'dist') continue
        results.push(...walkDir(fullPath))
      } else if (entry.isFile() && shouldCheck(fullPath)) {
        results.push(fullPath)
      }
    }
  } catch { /* skip */ }
  return results
}

function main() {
  const files = walkDir(SRC)
  let errors = [], warnings = []

  for (const file of files) {
    const content = readFileSync(file, 'utf-8')
    const lines = content.split('\n')
    const relPath = relative(ROOT, file)

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i]
      if (line.includes("'/api/upload'") || line.includes('"/api/upload"') || line.includes('`/api/upload`')) {
        if (KNOWN_GOVERNED_FILES.includes(relPath)) {
          warnings.push(`[WARN] ${relPath}:${i+1} 已标治理但仍有残留 — ${line.trim()}`)
        } else {
          errors.push(`[ERROR] ${relPath}:${i+1} 硬编码上传 URL（请使用 uploadApi.getUploadUrl）— ${line.trim()}`)
        }
      }
    }
  }

  if (errors.length) {
    console.error('\n❌ 违反门禁的上传 URL：'); errors.forEach(e => console.error('  ' + e))
  }
  if (warnings.length) {
    console.warn('\n⚠️  建议治理的上传绑定：'); warnings.forEach(w => console.warn('  ' + w))
  }
  if (!errors.length && !warnings.length) console.log('\n✅ 所有上传 URL 均已治理，无硬编码上传端点残留。')
  console.log(`\n扫描结果：${errors.length} 个错误，${warnings.length} 个警告`)
  if (process.argv.includes('--fail') && errors.length > 0) process.exit(1)
}

main()
