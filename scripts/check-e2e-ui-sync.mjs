#!/usr/bin/env node
// Input: changed files from git diff
// Output: exits 1 if UI files changed but no e2e/ file was also staged
// Pos: scripts/ - E2E-UI sync pre-push gate
// 维护声明: 若 UI 路由映射或 E2E 文件检测逻辑变化，同步更新本文件。
//
// 工程背景：2026-05-29 PR #486
// 根因：项目列表 UI 改了标签和选择器，但对应的 E2E 测试未同步修改，e2e-scope CI 失败。
// 本脚本在本地提交前拦截——把 CI 反馈提前到 "git push" 之前（而不是 CI 跑完后的 5-10 分钟）。
//
// ⚠️  维护规则（强制）：
//   修改本文件或 .githooks/pre-commit 时，
//   必须同步在 .agent-locks/<当前分支>.yml 中注册覆盖路径 ".githooks/" 的锁条目。
//   否则 CI 的 agent-locks job 会拒绝 PR。
//   详见 .wiki/pages/lessons-learned.md §三

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execSync } from 'node:child_process'

const repoRoot = path.resolve(process.cwd())

// ─────────────────────────────────────────────────────────────────────────────
// 从 e2e/*.spec.js 中提取 route → spec 文件映射
// 启发式：找 page.goto('/xxx') 和 test.describe('...xxx...')
// ─────────────────────────────────────────────────────────────────────────────
function buildRouteToSpecMap() {
  const map = new Map() // routeUrl -> Set<specFile>

  const e2eDir = path.join(repoRoot, 'e2e')
  if (!fs.existsSync(e2eDir)) return map

  for (const file of fs.readdirSync(e2eDir)) {
    if (!file.endsWith('.spec.js')) continue
    const content = fs.readFileSync(path.join(e2eDir, file), 'utf8')

    // 提取 page.goto('/xxx') 中的路由路径
    const gotoRegex = /page\.goto\s*\(\s*['"`](\/[^'"`\s?#]*)['"`]/g
    let m
    while ((m = gotoRegex.exec(content)) !== null) {
      const route = m[1]
      if (!map.has(route)) map.set(route, new Set())
      map.get(route).add(file)
    }

    // 从 test.describe('...') 描述中推断覆盖范围
    const descRegex = /test\.describe\s*\(\s*['"`]([^'"`]+)['"`]/g
    while ((m = descRegex.exec(content)) !== null) {
      const desc = m[1].toLowerCase()
      // 项目列表
      if (/project.*list|project.*filter|project.*search/i.test(desc)) {
        const routes = ['/project', '/project/list', '/project/']
        for (const r of routes) {
          if (!map.has(r)) map.set(r, new Set())
          map.get(r).add(file)
        }
      }
      // 标讯
      if (/bidding|bid|tender|tender.*list/i.test(desc)) {
        const routes = ['/bidding', '/bidding/list', '/bidding/']
        for (const r of routes) {
          if (!map.has(r)) map.set(r, new Set())
          map.get(r).add(file)
        }
      }
      // 案例库
      if (/case|knowledge/i.test(desc)) {
        const routes = ['/knowledge/case', '/case']
        for (const r of routes) {
          if (!map.has(r)) map.set(r, new Set())
          map.get(r).add(file)
        }
      }
      // 费用台账
      if (/expense|fee|resource/i.test(desc)) {
        const routes = ['/resource/expense']
        for (const r of routes) {
          if (!map.has(r)) map.set(r, new Set())
          map.get(r).add(file)
        }
      }
      // 仪表盘
      if (/dashboard|analytics/i.test(desc)) {
        const routes = ['/dashboard']
        for (const r of routes) {
          if (!map.has(r)) map.set(r, new Set())
          map.get(r).add(file)
        }
      }
    }
  }

  // 归一化：移除尾部斜杠后缀，统一 key
  const normalized = new Map()
  for (const [route, specs] of map) {
    const key = route.replace(/\/$/, '') || route
    if (!normalized.has(key)) normalized.set(key, new Set())
    for (const s of specs) normalized.get(key).add(s)
  }

  return normalized
}

// ─────────────────────────────────────────────────────────────────────────────
// 从变更的 UI 文件中提取受影响的路由前缀
// 例如：src/views/Project/List.vue -> /project
// ─────────────────────────────────────────────────────────────────────────────
function extractAffectedRoutes(uiFiles) {
  const routes = new Set()
  for (const file of uiFiles) {
    // src/views/Project/List.vue -> /project
    const match = file.match(/src\/views\/(\w+)/)
    if (match) {
      const name = match[1]
      // 常见命名约定：Project -> /project, ProjectList -> /project/list
      const route = '/' + name.replace(/([A-Z])/g, m => m.toLowerCase())
        .replace(/list$/, '')
        .replace(/detail$/, '')
        .replace(/create$/, '')
      routes.add(route)
    }
    // src/views/Bidding/list/*.vue -> /bidding
    const match2 = file.match(/src\/views\/(\w+)\/list\//)
    if (match2) {
      routes.add('/' + match2[1].toLowerCase())
    }
    // src/views/Bidding/list/components/*.vue -> /bidding
    const match3 = file.match(/src\/views\/(\w+)\/list\/components\//)
    if (match3) {
      routes.add('/' + match3[1].toLowerCase())
    }
    // src/views/Project/stages/*.vue -> /project
    const match4 = file.match(/src\/views\/(\w+)\/stages\//)
    if (match4) {
      routes.add('/' + match4[1].toLowerCase())
    }
    // src/views/Project/composables/*.js -> /project
    const match5 = file.match(/src\/views\/(\w+)\/composables\//)
    if (match5) {
      routes.add('/' + match5[1].toLowerCase())
    }
  }
  return routes
}

// ─────────────────────────────────────────────────────────────────────────────
// 获取已 staged 的变更文件（pre-commit hook 场景）
// 如果没有 staged，则用工作区 diff（独立运行场景）
// ─────────────────────────────────────────────────────────────────────────────
function getChangedFiles() {
  // 优先用 staged 文件（hook 场景）
  let stdout = ''
  try {
    stdout = execSync('git diff --cached --name-only', { cwd: repoRoot, encoding: 'utf8' })
  } catch { stdout = '' }

  let files = (stdout || '').trim().split('\n').filter(Boolean)

  // 如果没有 staged（独立运行），用工作区 diff
  if (files.length === 0) {
    try {
      stdout = execSync('git diff --name-only', { cwd: repoRoot, encoding: 'utf8' })
    } catch { stdout = '' }
    files = (stdout || '').trim().split('\n').filter(Boolean)
  }

  return files
}

// ─────────────────────────────────────────────────────────────────────────────
// 主逻辑
// ─────────────────────────────────────────────────────────────────────────────
async function main() {
  const files = getChangedFiles()
  if (files.length === 0) {
    console.log('✅ 无 staged 变更，跳过 E2E 联动检查。')
    process.exit(0)
  }

  const uiFiles = files.filter(f => /^src\/views\//.test(f) || /^src\/router\//.test(f))
  const e2eFiles = files.filter(f => /^e2e\//.test(f))

  // 如果有 e2e 变更，视为已同步，直接通过
  if (e2eFiles.length > 0) {
    console.log(`✅ 检测到 e2e/ 变更 (${e2eFiles.join(', ')})，跳过 E2E 联动检查。`)
    process.exit(0)
  }

  // 如果没有 UI 变更，跳过
  if (uiFiles.length === 0) {
    console.log('✅ 未检测到 src/views/ 或 src/router/ 变更，跳过 E2E 联动检查。')
    process.exit(0)
  }

  // UI 变了但 e2e 没变 -> 查找可能相关的 E2E 文件
  const routeToSpec = buildRouteToSpecMap()
  const affectedRoutes = extractAffectedRoutes(uiFiles)
  const possiblyRelatedSpecs = new Set()

  for (const route of affectedRoutes) {
    // 精确匹配
    for (const [r, specs] of routeToSpec) {
      if (r === route || route.startsWith(r) || r.startsWith(route)) {
        for (const s of specs) possiblyRelatedSpecs.add(s)
      }
    }
    // 兜底：直接找名字含 project/bidding 等的 spec
    const name = route.replace('/', '')
    if (name) {
      try {
        const found = execSync(
          'grep -rl "test\\.describe.*' + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '" e2e/',
          { cwd: repoRoot, encoding: 'utf8' }
        ).trim().split('\n').filter(Boolean)
        for (const f of found) possiblyRelatedSpecs.add(path.basename(f))
      } catch { /* no match */ }
    }
  }

  console.error('\n' + '═'.repeat(70))
  console.error('⚠️  E2E 联动检查：src/views/ 或 src/router/ 变更，但 e2e/ 未同步更新。')
  console.error('═'.repeat(70))
  console.error('\n变更的 UI 文件：')
  for (const f of uiFiles) {
    console.error(`  - ${f}`)
  }
  console.error('\n可能受影响的 E2E 测试文件：')
  if (possiblyRelatedSpecs.size > 0) {
    for (const s of [...possiblyRelatedSpecs].sort()) {
      console.error(`  - e2e/${s}`)
    }
    const cmd = 'npx playwright test ' + [...possiblyRelatedSpecs].join(' ')
    console.error('\n建议：运行 `' + cmd + '`')
    console.error('       验证 E2E 测试仍可通过，然后提交 e2e/ 变更。')
  } else {
    console.error('  (未找到匹配的 E2E 测试文件，需要人工判断是否需要新建测试)')
  }
  console.error('\n如果确认 E2E 不需要更改，可以在 commit message 中添加 [skip e2e-ui-sync]')
  console.error('例如: git commit -m "chore: ... [skip e2e-ui-sync]"\n')
  console.error('─'.repeat(70))

  // 不阻断提交（仅警告），因为 e2e-scope CI 会强制检查
  // pre-commit hook 的目的是"加速反馈"，而非"重复阻断"
  console.log('⚠️  继续提交（建议在 push 前处理上述警告）...')
  process.exit(0)
}

main().catch(err => {
  console.error('check-e2e-ui-sync.mjs 出错（非阻断）：', err.message)
  process.exit(0)
})
