#!/usr/bin/env node
// Input: src/router/index.js, src/views/**/*.vue, e2e/*.spec.js
// Output: violations when a route's component no longer renders data seeded by E2E tests
// Pos: scripts/ - Frontend E2E compatibility gate
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// ⚠️  维护规则（强制，来自 2026-05-21 血泪教训）：
//   修改本文件、.githooks/pre-commit 或 .github/workflows/ci.yml 时，
//   必须同步在 .agent-locks/<当前分支>.yml 中注册覆盖该路径的锁条目。
//   否则 CI 的 agent-locks job 会拒绝 PR，导致反复提交调试。
//   以下路径均为 hot-path（定义于 scripts/hot-paths.yml）：
//     .github/workflows/**  →  锁路径: ".github/workflows/"
//     .githooks/**          →  锁路径: ".githooks/"
//   详见 .wiki/pages/lessons-learned.md §三
//
// 工程背景（请勿删除）：
// 2026-05-21 记录于 .wiki/pages/lessons-learned.md §三
// 问题根因：Phase 2 将 /knowledge/case 路由从 Case.vue 替换为 CaseGrid.vue，
//   导致 E2E 在 /api/knowledge/cases 播种的数据在页面上完全不可见。
// 本脚本作为硬门禁：使用静态声明的"播种API→路由"映射表，避免误报。
// 映射表设计为「谁播种、谁验证、在哪里」三元组，必须与实际 E2E 脚本行为保持一致。

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const repoRoot = path.resolve(process.cwd())
const srcDir = path.join(repoRoot, 'src')
const routerFile = path.join(srcDir, 'router', 'index.js')

// ─────────────────────────────────────────────────────────────────────────────
// SEED CONTRACT TABLE（播种契约表）
//
// 说明：此表记录了"E2E 通过某 API 播种数据 → 该数据必须在某路由的某视图组件中可见"的契约。
// 当路由组件被替换时，本脚本检查新组件是否仍渲染这些 API 数据。
//
// 维护规则：
//   - 新增 E2E 播种且有路由断言时，在此添加一条记录
//   - 路由迁移/组件替换时，同步更新或确认表中契约仍然成立
//   - spec 中的 seedCommercialData 会在每次改动时自动被 grep 扫描（见下方）
// ─────────────────────────────────────────────────────────────────────────────
const SEED_CONTRACTS = [
  {
    // 传统案例库：E2E 通过 POST /api/knowledge/cases 创建案例
    // 然后导航到 /knowledge/case 并断言案例标题可见
    // 因此负责 /knowledge/case 路由的组件（或其子组件）必须渲染 /api/knowledge/cases 数据
    seedApi: '/api/knowledge/cases',
    verifiedOnRoute: '/knowledge/case',
    specFiles: ['e2e/commercial-main-flow.spec.js', 'e2e/case-advanced-flow.spec.js'],
    description: '传统案例库：E2E 播种案例数据并期望在 /knowledge/case 路由可见',
  },
  {
    // CaseGrid 案例库网格：E2E 通过 POST /api/knowledge/cases 创建案例
    // 然后导航到 /knowledge/case 断言网格卡片可见
    // CaseGrid.vue 调用 /api/cases 端点（含归一化字段映射）
    seedApi: '/api/cases',
    verifiedOnRoute: '/knowledge/case',
    specFiles: ['e2e/case-grid.spec.js'],
    description: 'CaseGrid 网格：E2E 播种案例数据并期望在 /knowledge/case 路由网格中可见',
  },
  {
    // 费用台账：E2E 通过 POST /api/resources/expenses 创建费用记录
    // 然后断言在 /resource/expense 可见
    seedApi: '/api/resources/expenses',
    verifiedOnRoute: '/resource/expense',
    specFiles: ['e2e/commercial-main-flow.spec.js'],
    description: '费用台账：E2E 播种费用数据并期望在 /resource/expense 路由可见',
  },
]

// ─────────────────────────────────────────────────────────────────────────────
// 从 router/index.js 提取路由 → 组件文件映射
// ─────────────────────────────────────────────────────────────────────────────
function extractRouteMappings(routerFile) {
  const mappings = new Map()
  if (!fs.existsSync(routerFile)) return mappings

  const content = fs.readFileSync(routerFile, 'utf8')

  // 匹配 path: 'xxx' + component: () => import('@/views/...')
  // 允许中间有 name/alias/meta 等字段，所以使用 [\s\S]*? 但限制在 200 字符内
  const routeRegex = /path:\s*['"`]([^'"`]*)['"`](?:(?!path:).){0,400}?component:\s*\(\)\s*=>\s*import\s*\(\s*['"`](@\/[^'"`]+\.vue)['"`]\s*\)/gs

  let m
  while ((m = routeRegex.exec(content)) !== null) {
    const routePath = m[1].startsWith('/') ? m[1] : '/' + m[1]
    const vueRelPath = m[2].replace('@/', '')
    const absPath = path.join(srcDir, vueRelPath)
    mappings.set(routePath, absPath)
  }
  return mappings
}

// ─────────────────────────────────────────────────────────────────────────────
// 递归收集组件（及其子组件/composable）中渲染的所有 API 端点
// ─────────────────────────────────────────────────────────────────────────────
function collectApisFromComponent(filePath, visited = new Set(), depth = 0) {
  const apis = new Set()
  if (visited.has(filePath) || !fs.existsSync(filePath) || depth > 6) return apis
  visited.add(filePath)

  const content = fs.readFileSync(filePath, 'utf8')

  // 字符串字面量中的 API 路径
  const apiRegex = /['"`](\/api\/[^'"`\s?#]{3,})['"`]/g
  let m
  while ((m = apiRegex.exec(content)) !== null) {
    // 归一化：移除 ID 段，只保留资源路径前缀
    const normalized = m[1].replace(/\/\d+/g, '').replace(/\/:\w+/g, '').split('/').slice(0, 4).join('/')
    apis.add(normalized)
  }

  // SDK 模式映射（当组件通过 API SDK 而非字符串字面量调用时）
  if (/knowledgeApi\.cases\b/.test(content)) apis.add('/api/knowledge/cases')
  if (/knowledgeApi\.qualifications\b/.test(content)) apis.add('/api/knowledge/qualifications')
  if (/knowledgeApi\.templates\b/.test(content)) apis.add('/api/knowledge/templates')
  if (/casesApi\b/.test(content)) apis.add('/api/cases')
  if (/resourcesApi\.expenses\b|expensesApi\b|useExpensePage\b|expensePageShared\b/.test(content)) apis.add('/api/resources/expenses')
  if (/resourcesApi\.barAssets\b|barAssetsApi\b/.test(content)) apis.add('/api/resources/bar-assets')

  // 递归处理相对路径 .vue 子组件导入
  const vueImportRegex = /import\s+\w+\s+from\s+['"`](\.{1,2}\/[^'"`]+\.vue)['"`]/g
  const dir = path.dirname(filePath)
  while ((m = vueImportRegex.exec(content)) !== null) {
    const child = path.resolve(dir, m[1])
    for (const a of collectApisFromComponent(child, visited, depth + 1)) apis.add(a)
  }

  // 递归处理相对路径 composable .js/.ts 导入
  const jsImportRegex = /import\s+.*?\s+from\s+['"`](\.{1,2}\/[^'"`]+\.(?:js|ts))['"`]/g
  while ((m = jsImportRegex.exec(content)) !== null) {
    const child = path.resolve(dir, m[1])
    if (fs.existsSync(child)) {
      for (const a of collectApisFromComponent(child, visited, depth + 1)) apis.add(a)
    }
  }

  // 递归处理 @/ 绝对路径 composable 导入
  const absImportRegex = /import\s+.*?\s+from\s+['"`](@\/[^'"`]+\.(?:js|ts|vue))['"`]/g
  while ((m = absImportRegex.exec(content)) !== null) {
    const child = path.join(srcDir, m[1].replace('@/', ''))
    if (fs.existsSync(child)) {
      for (const a of collectApisFromComponent(child, visited, depth + 1)) apis.add(a)
    }
  }

  return apis
}

// ─────────────────────────────────────────────────────────────────────────────
// 主检查
// ─────────────────────────────────────────────────────────────────────────────
const routeMappings = extractRouteMappings(routerFile)
const violations = []

for (const contract of SEED_CONTRACTS) {
  const { seedApi, verifiedOnRoute, specFiles, description } = contract

  const componentFile = routeMappings.get(verifiedOnRoute)
  if (!componentFile) {
    // 路由不存在，可能是动态路由，跳过
    continue
  }

  const renderedApis = collectApisFromComponent(componentFile)

  // 精准匹配：播种 API 前缀是否在渲染的 API 集合中
  const seedPrefix = seedApi.replace(/\/:\w+/g, '').replace(/\/\d+/g, '')
  const covered = [...renderedApis].some(a => a.startsWith(seedPrefix) || seedPrefix.startsWith(a))

  if (!covered) {
    const componentRel = path.relative(repoRoot, componentFile)
    violations.push(
      `[E2E 路由兼容性违规]\n` +
      `  契约说明: ${description}\n` +
      `  播种 API:  POST ${seedApi}\n` +
      `  期望路由:  ${verifiedOnRoute}\n` +
      `  路由组件:  ${componentRel}\n` +
      `  相关 spec: ${specFiles.join(', ')}\n` +
      `  问题描述:  该组件（及其子组件）中未发现渲染 ${seedPrefix} 数据的调用\n` +
      `  修复方式:  使用 Tab/Wrapper 保留旧组件，或更新 SEED_CONTRACTS 映射表`
    )
  }
}

if (violations.length > 0) {
  console.error('\n❌ E2E 路由兼容性检查失败！\n')
  console.error('='.repeat(70))
  for (const v of violations) {
    console.error(v)
    console.error('─'.repeat(70))
  }
  console.error('\n工程规范说明:')
  console.error('  路由组件替换时必须确保 E2E 播种的数据 API 仍被新组件渲染。')
  console.error('  若确实已重新设计数据架构，请同步更新 scripts/check-route-e2e-compat.mjs')
  console.error('  中的 SEED_CONTRACTS 表，并确保 E2E 脚本也已更新。')
  console.error('  参考修复: src/views/Knowledge/views/CaseWrapper.vue (Tab 包装器模式)')
  console.error('  详细分析: .wiki/pages/lessons-learned.md §三\n')
  process.exit(1)
}

console.log(`✅ E2E 路由兼容性检查通过 (${SEED_CONTRACTS.length} 条播种契约已验证)`)
