// Input: gap analysis table from docs/plans/*-plan.md
// Output: BLOCKER if form/field gaps exist without a completed field-by-field checklist
// Pos: scripts/ - Field-level blueprint alignment gate
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// This gate validates that every form field gap flagged as "本次做" in the
// gap analysis table has actually been implemented. It parses the gap table
// and checks the source files for evidence of the field.

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const __dirname2 = fileURLToPath(import.meta.url)
const scriptDir2 = path.dirname(__dirname2)

// Field types that should be validated
const FIELD_PATTERNS = {
  // Frontend: Vue form fields
  contact: [
    /contact/i,
    /联系人/,
  ],
  phone: [
    /contactPhone/i,
    /contact_phone/i,
    /phone/i,
    /联系方式/,
  ],
  deadline: [
    /deadline/i,
    /registrationDeadline/i,
    /报名截止/,
  ],
  bidOpeningTime: [
    /bidOpeningTime/i,
    /bid_open/i,
    /开标时间/,
  ],
  region: [
    /region/i,
    /总部所在地/,
  ],
  priority: [
    /priority/i,
    /优先级/,
  ],
  customerType: [
    /customerType/i,
    /客户类型/,
  ],
  sourceType: [
    /sourceType/i,
    /source/i,
    /来源平台/,
    /来源类型/,
  ],
  purchaser: [
    /purchaser/i,
    /招标主体/,
    /业主单位/,
  ],
  description: [
    /description/i,
    /description/i,
    /标讯描述/,
    /项目描述/,
  ],
  attachments: [
    /attachment/i,
    /upload/i,
    /文件上传/,
    /附件上传/,
  ],
  sourceDocument: [
    /sourceDocument/i,
    /标讯文件/,
  ],
}

// Patterns that indicate a field is validated (has rules/binding)
const VALIDATION_PATTERNS = {
  formField: [
    /el-input/,
    /el-select/,
    /el-date-picker/,
    /el-upload/,
    /v-model\s*=\s*["']form\./,
  ],
  requiredRule: [
    /required\s*:\s*true/,
    /\{ required: true/,
    /validator.*required/i,
  ],
  backendValidation: [
    /@NotNull/,
    /@NotBlank/,
    /@NotEmpty/,
    /@Size/,
    /@Min/,
    /@Max/,
    /@Valid/,
  ],
}

function parseArgs(argv) {
  const options = {
    failOnMissing: true,
    verbose: false,
    planFile: null,
  }

  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === '--warn') {
      options.failOnMissing = false
    } else if (arg === '--verbose' || arg === '-v') {
      options.verbose = true
    } else if (arg === '--plan') {
      options.planFile = argv[++i]
    } else if (arg === '--help') {
      console.log(`Usage: node scripts/check-blueprint-fields.mjs [--plan <file>] [--warn] [-v]
  --plan <file>   path to specific plan file to check (default: all plans with gaps)
  --warn           warn instead of blocking
  -v               verbose output
Exit code: 0 = pass, 1 = block/warn, 2 = error`)
      process.exit(0)
    }
  }
  return options
}

function parseGapTable(content) {
  // Extract the gap analysis table (markdown table with |---|---| structure)
  const tableMatch = content.match(/\|[\s\S]*?\|[\s\S]*?\|[\s\S]*?\|[\s\S]*?\n/gi)
  if (!tableMatch) return []

  const rows = []
  for (const line of tableMatch) {
    // Skip separator rows (|---|---|)
    if (line.match(/^\|[\s\-|:]+\|/)) continue

    const cells = line.split('|').filter((c, i) => i > 0 && i < line.split('|').length - 1)
    if (cells.length < 2) continue

    // Normalize cells
    const normalized = cells.map(c => c.trim())

    // Detect field name column (has "字段" or "field" or numbered list patterns)
    const fieldCol = normalized.findIndex(c =>
      c.includes('字段') || c.includes('field') || /^\d+$/.test(c) || c.includes('编号')
    )

    if (fieldCol === -1) continue

    const gapCol = normalized.findIndex((c, i) =>
      i > fieldCol && (c.includes('缺失') || c.includes('gap') || c.includes('差距') || c.includes('✅') || c.includes('❌'))
    )

    // Find "本次做" marker in the row
    const isThisSprint = line.includes('本次做') || line.includes('[ ]') || line.includes('P0') || line.includes('P1')

    rows.push({
      raw: line,
      cells: normalized,
      fieldCol,
      gapCol,
      isThisSprint,
    })
  }

  return rows
}

function checkFieldInSource(fieldName, sourceDir, extensions = ['.vue', '.js', '.java']) {
  // Try each pattern for the field
  const patterns = FIELD_PATTERNS[fieldName] || [new RegExp(fieldName, 'i')]

  for (const ext of extensions) {
    const searchDir = sourceDir || path.join(process.cwd(), 'src', 'views', 'Bidding')
    if (!fs.existsSync(searchDir)) continue

    const found = searchDirRecursive(searchDir, ext, patterns)
    if (found) return found
  }

  return null
}

function searchDirRecursive(dir, ext, patterns) {
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      const result = searchDirRecursive(fullPath, ext, patterns)
      if (result) return result
    } else if (entry.name.endsWith(ext)) {
      try {
        const content = fs.readFileSync(fullPath, 'utf8')
        for (const pattern of patterns) {
          if (pattern.test(content)) {
            return path.relative(process.cwd(), fullPath)
          }
        }
      } catch {
        // skip unreadable files
      }
    }
  }
  return null
}

function checkValidationEvidence(fieldName, sourceDir) {
  // Check if field has validation rules
  const formFiles = []
  const backendFiles = []

  const searchDir = sourceDir || path.join(process.cwd(), 'src', 'views', 'Bidding')
  const backendDir = path.join(process.cwd(), 'backend', 'src', 'main', 'java')

  // Vue files
  if (fs.existsSync(searchDir)) {
    for (const pattern of VALIDATION_PATTERNS.formField) {
      const found = searchDirRecursive(searchDir, '.vue', [pattern])
      if (found) formFiles.push(found)
    }
  }

  // Java backend files
  if (fs.existsSync(backendDir)) {
    for (const pattern of VALIDATION_PATTERNS.backendValidation) {
      const found = searchDirRecursive(backendDir, '.java', [pattern])
      if (found) backendFiles.push(found)
    }
  }

  return { formFiles: [...new Set(formFiles)], backendFiles: [...new Set(backendFiles)] }
}

function findPlanFiles(repoRoot, section) {
  const plansDir = path.join(repoRoot, 'docs', 'plans')
  if (!fs.existsSync(plansDir)) return []

  const files = fs.readdirSync(plansDir).filter(f => f.endsWith('.md'))

  if (section) {
    const pattern = new RegExp(section.replace(/\./g, '\\.') + '.*plan\\.md', 'i')
    return files.filter(f => pattern.test(f)).map(f => path.join(plansDir, f))
  }

  return files.map(f => path.join(plansDir, f))
}

function main() {
  const repoRoot = process.cwd()
  const options = parseArgs(process.argv)
  const planFiles = options.planFile
    ? [path.resolve(options.planFile)]
    : findPlanFiles(repoRoot, null)

  const allResults = []

  for (const planFile of planFiles) {
    if (!fs.existsSync(planFile)) continue

    const content = fs.readFileSync(planFile, 'utf8')
    const fileName = path.relative(repoRoot, planFile)

    // Skip if this is not a blueprint section plan
    const sectionMatch = fileName.match(/4\.\d+\.\d+/)
    if (!sectionMatch) continue

    const rows = parseGapTable(content)
    const thisSprintRows = rows.filter(r => r.isThisSprint)

    if (thisSprintRows.length === 0) continue

    for (const row of thisSprintRows) {
      const fieldName = row.cells[row.fieldCol]?.replace(/\s+/g, '').toLowerCase() || ''
      const gapDesc = row.gapCol >= 0 ? row.cells[row.gapCol] : ''

      // Check if the gap is "存在" or "缺失"
      const hasGap = gapDesc.includes('存在') || gapDesc.includes('缺失') || gapDesc.includes('❌') || gapDesc.includes('gap')

      if (!hasGap) continue

      // Map field name to source patterns
      const sourcePath = path.join(repoRoot, 'src', 'views', 'Bidding')
      const backendPath = path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'xiyu', 'bid')

      const vueFile = checkFieldInSource(fieldName, sourcePath, ['.vue'])
      const jsFile = checkFieldInSource(fieldName, sourcePath, ['.js'])
      const javaFile = checkFieldInSource(fieldName, backendPath, ['.java'])

      const found = vueFile || jsFile || javaFile
      const validation = found ? checkValidationEvidence(fieldName, sourcePath) : null

      allResults.push({
        fileName,
        fieldName,
        gapDesc,
        vueFile,
        jsFile,
        javaFile,
        found,
        validation,
        hasValidation: validation && (validation.formFiles.length > 0 || validation.backendFiles.length > 0),
      })
    }
  }

  const unimplemented = allResults.filter(r => !r.found)
  const noValidation = allResults.filter(r => r.found && !r.hasValidation)
  const implemented = allResults.filter(r => r.found && r.hasValidation)

  if (options.verbose) {
    console.log(`check-blueprint-fields: ${allResults.length} fields analyzed`)
    if (implemented.length > 0) {
      console.log(`  ✅ implemented + validated: ${implemented.length}`)
      for (const r of implemented) {
        console.log(`     ✓ ${r.fieldName} (${r.vueFile || r.jsFile || r.javaFile})`)
      }
    }
    if (noValidation.length > 0) {
      console.log(`  ⚠️  implemented but no validation evidence: ${noValidation.length}`)
      for (const r of noValidation) {
        console.log(`     ~ ${r.fieldName} (${r.vueFile || r.jsFile || r.javaFile}) — no required rule found`)
      }
    }
    if (unimplemented.length > 0) {
      console.log(`  ❌ not found in source: ${unimplemented.length}`)
      for (const r of unimplemented) {
        console.log(`     ✗ ${r.fieldName} — ${r.gapDesc}`)
      }
    }
  }

  if (unimplemented.length > 0) {
    console.error('check-blueprint-fields: BLOCKED — unimplemented fields found')
    console.error('')
    for (const r of unimplemented) {
      console.error(`  ❌ §${r.fileName.match(/4\.\d+\.\d+/)?.[0] || '?'} | ${r.fieldName}`)
      console.error(`     Gap: ${r.gapDesc}`)
    }
    console.error('')
    console.error(`Total: ${unimplemented.length} field(s) still missing`)
    process.exit(options.failOnMissing ? 1 : 0)
  }

  // Pass but warn about missing validation
  if (noValidation.length > 0 && options.verbose) {
    console.log(`check-blueprint-fields: passed (${noValidation.length} fields lack validation rules — add required:true in form or @NotNull/@Valid in backend)`)
  } else {
    console.log(`check-blueprint-fields: passed (${implemented.length} fields validated)`)
  }
  process.exit(0)
}

try {
  main()
} catch (error) {
  console.error(`check-blueprint-fields: internal error — ${error.message}`)
  process.exit(2)
}

export { parseGapTable, checkFieldInSource, checkValidationEvidence }
