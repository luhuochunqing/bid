// Input: dist/assets/ directory with built JS chunks
// Output: warnings when inlined composable functions lack required reactivity calls
// Pos: scripts/ - Build-time composable integrity guardrail
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * check-inlined-composables.cjs (v2)
 *
 * 构建后校验：扫描异步 chunk 中被 Rollup 内联的 composable 函数是否完整。
 *
 * 问题背景：
 *   Vite/Rollup 在构建异步 chunk 时，会将跨文件 import 的小型 composable 内联到
 *   chunk 中。实测发现 tree-shaking 可能错误截断内联后的函数体——只保留部分代码
 *   （如某个 ref 初始化），丢失其余 ref 创建、方法定义和 return 语句。调用方解构
 *   得到 undefined，导致组件崩溃。
 *
 * 增强内容（vs v1）：
 *   1. 使用可匹配嵌套大括号的状态机，而非单层 [^}]
 *   2. 放宽参数匹配：任意两参数 function \w+\([a-z],[a-z]\) 均纳入检测
 *   3. 降低告警阈值：函数体 < 150 字符 + 缺少 ref/reactive/return 任一项即告警
 *   4. 对已内联到组件 <script setup> 中的函数不产生假阳性（只有独立 composable
 *      文件被内联时才会生成具名函数签名）
 *
 * 用法：node scripts/check-inlined-composables.cjs [dist-dir]
 * 默认 dist-dir：dist/assets/
 */

const fs = require('fs')
const path = require('path')

const DIST_DIR = process.argv[2] || (() => {
  const candidates = ['dist/assets', 'dist']
  for (const d of candidates) {
    if (fs.existsSync(d)) return d
  }
  return 'dist/assets'
})()

const VENDOR_KEYWORDS = ['vendor-', 'element-plus-', 'echarts-', 'vue-vendor-']
const MIN_FUNC_BODY_CHARS = 150

/**
 * 提取完整的大括号块，支持嵌套
 * 从 startIdx（'{' 的下一个字符）开始，返回匹配的结束索引（含 '}'）
 */
function extractBraceBlock(content, startIdx) {
  if (content[startIdx - 1] !== '{') return -1
  let depth = 1
  let i = startIdx
  while (i < content.length && depth > 0) {
    const ch = content[i]
    if (ch === '{') depth++
    else if (ch === '}') depth--
    i++
  }
  return depth === 0 ? i : -1
}

/**
 * 检查字符串是否包含 Vue ref/reactive/computed 调用
 */
function hasVueReactivityCall(body) {
  return /ref\s*\(/.test(body) ||
         /reactive\s*\(/.test(body) ||
         /computed\s*\(/.test(body) ||
         /shallowRef\s*\(/.test(body) ||
         /shallowReactive\s*\(/.test(body)
}

/**
 * 检查函数体是否包含 return 语句
 */
function hasReturnStatement(body) {
  return /\breturn\s+/.test(body)
}

/**
 * 检查函数体是否包含可观察的副作用（API 调用、ElMessage 等）
 */
function hasObservableSideEffect(body) {
  return /\.(then|catch|finally)\s*\(/.test(body) ||
         /\bawait\b/.test(body) ||
         /ElMessage\./.test(body) ||
         /api\w*\./.test(body) ||
         /\.\$emit/.test(body) ||
         /\bemit\b/.test(body)
}

/**
 * 检查函数名是否像 composable（useXxx 模式）
 */
function isComposableName(name) {
  return /^use[A-Z]/.test(name)
}

function isVendorChunk(filename) {
  return VENDOR_KEYWORDS.some(k => filename.includes(k))
}

function checkChunk(filePath, filename) {
  if (!filename.endsWith('.js')) return []
  if (isVendorChunk(filename)) return []

  const content = fs.readFileSync(filePath, 'utf-8')
  const warnings = []

  // 正则：匹配 function name(a,b) { 或 function name(a,b, 换行后 {
  const FUNC_PATTERN = /function\s+(\w+)\s*\(([a-z](?:\s*,\s*[a-z]){1,3})\)\s*\{/g

  let match
  while ((match = FUNC_PATTERN.exec(content)) !== null) {
    const funcName = match[1]
    const params = match[2]
    const bodyStart = match.index + match[0].length // '{' 之后的字符

    // 只检查 composable 命名模式的函数
    if (!isComposableName(funcName)) continue

    const bodyEnd = extractBraceBlock(content, bodyStart)
    if (bodyEnd === -1) continue

    const body = content.substring(bodyStart, bodyEnd - 1) // 去掉最后的 '}'

    // 跳过大型函数（不会被错误截断）
    if (body.length > 500) continue

    const hasRef = hasVueReactivityCall(body)
    const hasReturn = hasReturnStatement(body)
    const hasSideEffect = hasObservableSideEffect(body)
    const isShort = body.length < MIN_FUNC_BODY_CHARS

    // 判定规则：
    //   - 短函数（< 150 字符）缺少 ref/reactive 调用 OR 缺少 return → 可疑
    //   - 中等函数（150-500 字符）缺少 return → 可疑
    let severity = null
    if (isShort && (!hasRef || !hasReturn)) {
      severity = 'ERROR'
    } else if (!isShort && !hasReturn) {
      severity = 'WARN'
    }

    if (severity) {
      warnings.push({
        file: filename,
        funcName,
        params: params.trim(),
        bodyLength: body.length,
        hasRef,
        hasReturn,
        hasSideEffect,
        severity
      })
    }
  }

  // 第二遍扫描：查找 esbuild 内联模式的匿名函数赋值
  // Rollup 有时会将 composable 内联为 const useXxx = function(e,o) { ... }
  const ANON_PATTERN = /(\w+)\s*=\s*function\s*\(([a-z](?:\s*,\s*[a-z]){1,3})\)\s*\{/g
  while ((match = ANON_PATTERN.exec(content)) !== null) {
    const funcName = match[1]
    const params = match[2]
    if (!isComposableName(funcName)) continue

    const bodyStart = match.index + match[0].length
    const bodyEnd = extractBraceBlock(content, bodyStart)
    if (bodyEnd === -1) continue

    const body = content.substring(bodyStart, bodyEnd - 1)
    if (body.length > 500) continue

    const hasRef = hasVueReactivityCall(body)
    const hasReturn = hasReturnStatement(body)
    const isShort = body.length < MIN_FUNC_BODY_CHARS

    if (isShort && (!hasRef || !hasReturn)) {
      warnings.push({
        file: filename,
        funcName,
        params: params.trim(),
        bodyLength: body.length,
        hasRef,
        hasReturn,
        hasSideEffect: hasObservableSideEffect(body),
        severity: 'ERROR'
      })
    }
  }

  return warnings
}

function main() {
  console.log(`\n🔍 检查异步 chunk 内联 composable 完整性...`)
  console.log(`   扫描目录: ${DIST_DIR}\n`)

  let totalFiles = 0
  let totalWarnings = 0

  if (!fs.existsSync(DIST_DIR)) {
    console.log(`   ⚠️  目录不存在，跳过检查`)
    console.log(`      (构建尚未执行或 DIST_DIR 配置不正确)`)
    return
  }

  const files = fs.readdirSync(DIST_DIR)
  for (const f of files) {
    const filePath = path.join(DIST_DIR, f)
    if (!fs.statSync(filePath).isFile()) continue
    totalFiles++
    const warnings = checkChunk(filePath, f)
    for (const w of warnings) {
      totalWarnings++
      const flag = w.severity === 'ERROR' ? '❌' : '⚠️'
      console.log(`  ${flag} ${w.file}`)
      console.log(`     函数: ${w.funcName}(${w.params})`)
      console.log(`     体长: ${w.bodyLength} 字符 (短函数阈值: ${MIN_FUNC_BODY_CHARS})`)
      console.log(`     含 ref/reactive/computed: ${w.hasRef}, 含 return: ${w.hasReturn}`)
      if (w.hasSideEffect) console.log(`     含副作用调用: 是`)
      console.log()
    }
  }

  console.log(`   扫描文件数: ${totalFiles}`)
  if (totalWarnings > 0) {
    console.log(`   问题: ❌ ${totalWarnings} 个可疑内联函数`)
    console.log(``)
    console.log(`💡 提示: 上述文件中的 composable 函数体可能不完整。`)
    console.log(`   内联后的 composable 通常应包含 ref()/reactive()/computed() 调用`)
    console.log(`   和 return 语句。如果函数体被截断，应将 composable 内联到调用组件中，`)
    console.log(`   或确保 composable 不满足 Rollup 错误内联的触发条件。`)
    console.log(`   详见 RULES.md §2.5 Composable 内联规则。`)
    process.exit(1)
  } else {
    console.log(`   问题: ✅ 无`)
  }
}

main()
