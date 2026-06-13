// Input: Vue component source files (full scan or --staged)
// Output: list of composables that should be inlined per RULES.md §2.6
// Pos: scripts/ - Source-level composable placement lint
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

#!/usr/bin/env node
/**
 * check-composable-placement.mjs
 *
 * Pre-commit 源码检测：扫描暂存区中新增/修改的 .vue 文件，
 * 检测是否 import 了满足"唯一引用 + ≤80行"条件的独立 composable 文件，
 * 并发出 warning 建议内联。
 *
 * 规则依据：RULES.md §2.5 Composable 内联规则
 *   当组合式函数满足以下全部条件时，必须直接写在组件的 <script setup> 内：
 *   1. 唯一引用：当前只被一个组件使用
 *   2. 小型逻辑：预估行数 ≤ 80 行
 *   3. 无复用预期：未来 2 个迭代内没有多组件复用的计划
 *
 * 用法：
 *   node scripts/check-composable-placement.mjs           # 扫描所有 .vue 文件
 *   node scripts/check-composable-placement.mjs --staged  # 仅扫描暂存区
 *
 * 退出码：0=无问题, 1=有告警
 */

import fs from 'fs'
import path from 'path'
import { spawnSync } from 'child_process'

const ROOT = process.cwd()
const SRC_DIR = path.join(ROOT, 'src')

// 检查是否有 --staged 参数
const STAGED_ONLY = process.argv.includes('--staged')

/**
 * 获取需要检查的 .vue 文件列表
 */
function getVueFiles() {
  if (STAGED_ONLY) {
    const result = spawnSync('git', ['diff', '--cached', '--name-only', '--diff-filter=AM'], {
      cwd: ROOT, encoding: 'utf-8'
    })
    if (result.error || result.status !== 0) {
      console.error('  git diff --cached 失败:', result.error?.message || result.stderr)
      process.exit(1)
    }
    return result.stdout.split('\n')
      .filter(f => f.startsWith('src/') && f.endsWith('.vue'))
      .map(f => path.join(ROOT, f))
      .filter(f => fs.existsSync(f))
  }

  // 全量扫描
  const files = []
  function walk(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true })
    for (const e of entries) {
      const full = path.join(dir, e.name)
      if (e.isDirectory() && !e.name.startsWith('.') && e.name !== 'node_modules') walk(full)
      else if (e.isFile() && e.name.endsWith('.vue')) files.push(full)
    }
  }
  walk(SRC_DIR)
  return files
}

/**
 * 从 .vue 文件中提取 import 的 composable 路径
 */
function extractComposableImports(content, vueFile) {
  const imports = []
  // import { useXxx } from './path/to/useXxx.js'
  // import { useXxx } from '../useXxx.js'
  const IMPORT_RE = /import\s*\{([^}]+)\}\s*from\s*['"](\.[^'"]*\/use[A-Z][^'"]*)['"]\s*;?/g

  let match
  while ((match = IMPORT_RE.exec(content)) !== null) {
    const names = match[1].split(',').map(n => n.trim())
    const modulePath = match[2]
    for (const name of names) {
      if (name.startsWith('use') && /^use[A-Z]/.test(name)) {
        imports.push({ name, modulePath })
      }
    }
  }
  return imports
}

/**
 * 解析模块路径为绝对路径
 */
function resolveModulePath(vueFile, modulePath) {
  const dir = path.dirname(vueFile)
  const resolved = path.resolve(dir, modulePath)
  // 尝试扩展名
  for (const ext of ['.js', '.ts', '.mjs']) {
    const withExt = resolved.endsWith(ext) ? resolved : resolved + ext
    if (fs.existsSync(withExt)) return withExt
  }
  return resolved
}

/**
 * 检查一个 composable 文件是否 ≤80 行且唯一引用
 */
function checkComposable(compFile, compName) {
  if (!fs.existsSync(compFile)) return null

  const stat = fs.statSync(compFile)
  const content = fs.readFileSync(compFile, 'utf-8')
  const lines = content.split('\n').length

  // 检查行数是否 ≤80
  if (lines > 80) return null

  // 检查唯一引用：查找哪些 .vue 文件引用了这个 composable
  const funcRef = new RegExp(`from\\s*['"]\\.\\/([^'"]*${path.basename(compFile, path.extname(compFile))})['"']|from\\s*['"]\\.\\.\\/[^'"]*${path.basename(compFile, path.extname(compFile))}['"']`)

  let refCount = 0
  function countRefs(dir) {
    if (!fs.existsSync(dir)) return
    const entries = fs.readdirSync(dir, { withFileTypes: true })
    for (const e of entries) {
      if (e.name.startsWith('.') || e.name === 'node_modules') continue
      const full = path.join(dir, e.name)
      if (e.isDirectory()) countRefs(full)
      else if (e.name.endsWith('.vue') && full !== compFile) {
        const vueContent = fs.readFileSync(full, 'utf-8')
        if (funcRef.test(vueContent)) refCount++
      }
    }
  }
  countRefs(SRC_DIR)

  return {
    file: compFile,
    name: compName,
    lines,
    refCount,
    shouldInline: refCount <= 1 // 唯一引用或自引用
  }
}

function main() {
  const mode = STAGED_ONLY ? '暂存区' : '全量'
  console.log(`\n🔍 检测小型独立 composable 是否需要内联 [${mode}]...`)
  console.log()

  const vueFiles = getVueFiles()
  let totalVueFiles = 0
  let totalWarnings = 0

  for (const vueFile of vueFiles) {
    const content = fs.readFileSync(vueFile, 'utf-8')
    const composableImports = extractComposableImports(content, vueFile)

    if (composableImports.length === 0) continue
    totalVueFiles++

    for (const { name, modulePath } of composableImports) {
      const compFile = resolveModulePath(vueFile, modulePath)
      const result = checkComposable(compFile, name)

      if (result && result.shouldInline && result.refCount <= 1) {
        const relFile = path.relative(SRC_DIR, result.file)
        totalWarnings++
        console.log(`  ⚠️  ${relFile}`)
        console.log(`     Composable: ${result.name} (${result.lines} 行, 引用数: ${result.refCount})`)
        console.log(`     ${path.relative(ROOT, vueFile)}`)
        console.log(`     建议: 按 RULES.md §2.5 规则内联到组件 <script setup> 中`)
        console.log()
      }
    }
  }

  if (totalWarnings > 0) {
    console.log(`   检查文件数: ${vueFiles.length} 个 .vue 文件`)
    console.log(`   含外部 composable 引用的文件数: ${totalVueFiles}`)
    console.log(`   问题: ⚠️  ${totalWarnings} 个 composable 建议内联\n`)
    process.exit(0) // 仅 warning，不阻断
  } else {
    console.log(`   检查文件数: ${vueFiles.length} 个 .vue 文件`)
    console.log(`   问题: ✅ 无\n`)
  }
}

main()
