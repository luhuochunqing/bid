// Input: 证据目录 + 由 run-stress-test.sh 注入的**声明**（MONITOR_SCOPE / MONITOR_REQUIRED_COMPONENTS /
//        MISSING_ARTIFACTS / DELIVERY_BLOCKERS / SECRET_SCAN_STATUS）
// Output: 本模块自己复核出来的产物清单 / 缺失项 / 逐组件可用采样行数 / 凭证扫描结论 / 合并去重后的交付阻塞项
// Pos: scripts/performance/ - 清单生成器的**独立复核层**：runner 声明的每一条，这里都自己再算一遍
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 为什么必须独立复核，而不是照抄 runner 注入的值：
//   清单对外回答的是"这份包能不能交付"。若它只是把调用方的说法抄进 JSON，那么它的全部结论都
//   等价于"调用方说它对"——调用方漏算一样（新增一份产物却没加进清单、某份 CSV 只有表头、
//   扫描被跳过）时，清单会**一致地**给出一个错误的 evidence_complete=true，而且看不出任何异常。
//   实测过这条路径：隔离只跑本脚本时，清单曾给出与产物实况相反的结论。
//   两边独立算出同一个结论，才是这份清单值得第三方信任的理由。
//
// 独立性 ≠ 各写一份实现：契约（组件集合、CSV 表头、有效列号）仍只有 monitor-spec.sh 一个来源，
// 本模块通过 `bash monitor-spec.sh --print-json` 读它——而不是让 runner 把列号透传进来
// （调用方一漏传，清单就少一层校验），也不是去 parse 它的 shell 语法（语法一改就静默失效）。
//
// 错误分两类，由调用方映射成不同退出码：
//   UsageError     调用方给的东西本身不合法（组件名拼错、声明为空）→ 用法错误
//   ToolchainError 工具链自身不可用（规格读不出来、阻塞项文案缺失）→ 拒绝执行

import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'

export class UsageError extends Error {}
export class ToolchainError extends Error {}

// 数值判据必须与 evidence-completeness.sh 的 csv_usable_rows 完全一致：两边算出的
// usable 行数不同，就会出现"runner 说 6 行、清单说 0 行"而两边都不报错的局面。
const NUMERIC = /^-?[0-9]+(\.[0-9]+)?$/

// 读监控规格（组件集合 / CSV 文件名 / 有效列号）。规格自检失败时 monitor-spec.sh 非 0 退出，
// 此时**拒绝生成清单**：读不出规格就无法复核产物，而一份无法复核的清单比没有清单更危险。
export function readMonitorSpec(scriptDir) {
  const res = spawnSync('bash', [path.join(scriptDir, 'monitor-spec.sh'), '--print-json'], {
    encoding: 'utf8',
    maxBuffer: 8 * 1024 * 1024,
  })
  if (res.error || res.status !== 0) {
    throw new ToolchainError(
      `monitor-spec.sh --print-json 执行失败（${res.error ? String(res.error) : `退出码 ${res.status}`}）：${(res.stderr ?? '').trim()}`,
    )
  }
  try {
    return JSON.parse(res.stdout)
  } catch (error) {
    throw new ToolchainError(`monitor-spec.sh --print-json 的输出不是合法 JSON：${String(error)}`)
  }
}

// 本次声明要覆盖的组件。逐个白名单校验：拼错一个名字（sys 而不是 system）时，
// 下面的逐组件复核会因为找不到规格而**跳过这一项**——那本该是"这一项没采到"，
// 却静默退化成"没有这项要求"。runner 侧也校验了一遍，这里不省：清单的结论不能建立在
// "调用方肯定校验过"这个假设上。
//
// 分隔符同时接受逗号与空白：对外（--monitor-required-components / 清单字段）写的是逗号，
// 而 shell 内部遍历用空格更顺手，两处曾因此对不上——写入方按空格传、这里按逗号切，
// 于是 'system app-jvm mysql redis' 被当成**一个**组件名而判为未知，清单直接生成失败。
// 判据是**组件名**，分隔符不是契约的一部分，因此这里两种都认。
export function resolveRequiredComponents(raw, monitorScope, spec) {
  const names = String(raw ?? '')
    .split(/[,\s]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
  const known = Object.keys(spec)
  for (const name of names) {
    if (!spec[name]) {
      throw new UsageError(`MONITOR_REQUIRED_COMPONENTS 含未知组件 '${name}'（可用：${known.join('|')}）`)
    }
  }
  if (monitorScope !== 'skipped' && names.length === 0) {
    throw new UsageError('MONITOR_REQUIRED_COMPONENTS 为空，但 MONITOR_SCOPE 不是 skipped；两者矛盾')
  }
  return [...new Set(names)]
}

// 本包该有哪些产物。监控侧由**声明的组件范围**派生，不写死四件套：
// 写死时收窄声明（目标环境确实没有 Redis）会在清单里留下一串 null 产物项，
// 第三方看到的是"采了但没采到"，而不是"本次范围本就不含它"。
export function requiredArtifacts(monitorScope, required, spec) {
  const files = ['k6-summary.json', 'k6-raw-samples.csv', 'k6-console.log', 'k6-exit-code']
  if (monitorScope !== 'skipped') {
    // 这两份无条件要求：它们记录"采了什么、采到没有"，缺了就无法判断其余几份的成色。
    files.push('monitoring/monitoring.log', 'monitoring/component-status.csv')
    for (const name of required) files.push(`monitoring/${spec[name].csv}`)
  }
  return files
}

// 一份监控 CSV 里"有效采样行"的条数。判据是**这些列在该行齐备且为数值**（列号从 monitor-spec 来）。
// 只看文件大小是不够的：采集器在 mysql 客户端缺失、Redis 不可达、应用进程找不到时留下的
// 正是"只有表头"或"整行空值"的文件，非空、有行数，却撑不起报告第五章的任何结论。
export function csvUsableRows(file, cols) {
  let text
  try {
    text = fs.readFileSync(file, 'utf8')
  } catch {
    return 0
  }
  const lines = text.split('\n')
  let usable = 0
  // 第 1 行是表头，一律跳过（与 shell 侧的 awk `NR == 1 { next }` 对齐）
  for (let i = 1; i < lines.length; i++) {
    if (lines[i] === '') continue
    const cells = lines[i].split(',')
    let ok = true
    for (const col of cols) {
      const value = cells[col - 1]
      if (value === undefined || !NUMERIC.test(value)) {
        ok = false
        break
      }
    }
    if (ok) usable += 1
  }
  return usable
}

// 独立复核产物：文件不存在/为空 → 缺失；存在但一行可用数据都没有 → 同样计入缺失
// （它不是"没生成"，而是"生成了却没有数据"，两种处置方式相同，都不可交付）。
export function verifyArtifacts(dir, files, monitorScope, required, spec) {
  const missing = []
  for (const rel of files) {
    let stat = null
    try {
      stat = fs.statSync(path.join(dir, rel))
    } catch {
      stat = null
    }
    if (!stat || !stat.isFile() || stat.size === 0) missing.push(rel)
  }

  const components = []
  const issues = []
  if (monitorScope !== 'skipped') {
    for (const name of required) {
      const { csv, cols } = spec[name]
      const rel = `monitoring/${csv}`
      const usable = csvUsableRows(path.join(dir, rel), cols)
      components.push({ component: name, file: rel, verified_usable_rows: usable, verified_by: `第 ${cols.join(',')} 列需齐备且为数值` })
      if (usable < 1) {
        // 只写"缺这个文件"会让人以为是采集器没跑起来，而真实情况多半是"跑了、没采到"。
        issues.push(`${rel} 没有一行可用采样（第 ${cols.join(',')} 列需齐备且为数值，实测 ${usable} 行）`)
        if (!missing.includes(rel)) missing.push(rel)
      }
    }
  }
  return { missing, components, issues }
}

// 自己跑一遍凭证残留扫描，**不复用 runner 注入的状态**。
// 状态取扫描器退出码与 stdout 的 scan_status 两路来源的交集：任一路缺失或彼此不一致，
// 就收敛到 unverified —— 一个被截断或被重定向的扫描输出能给出"看上去正常"的假信号，
// 而这两路同时被同一原因破坏的可能性远小于任一路。
export function runSecretScan(dir, scriptDir) {
  const scanner = path.join(scriptDir, 'scrub-secrets.mjs')
  const res = spawnSync('node', [scanner, '--scan', dir], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  if (res.error) {
    return { status: 'unverified', unscanned: [], detail: `扫描器无法执行：${String(res.error)}` }
  }
  const out = `${res.stdout ?? ''}${res.stderr ?? ''}`
  const byExit = { 0: 'passed', 1: 'failed', 3: 'incomplete' }[res.status] ?? `exit_${res.status}`
  const line = out.match(/^scan_status=([a-z_]+)$/m)?.[1] ?? ''
  const unscannedRaw = out.match(/^scan_unscanned_files=(.*)$/m)?.[1] ?? ''
  const unscanned = unscannedRaw.split(',').map((s) => s.trim()).filter((s) => s.length > 0)
  const compact = out.trim().replace(/\s+/g, ' ')
  if (line === '') {
    return { status: 'unverified', unscanned, detail: `扫描器未输出可解析的 scan_status（退出码 ${res.status}）；原始输出：${compact}` }
  }
  if (line !== byExit) {
    return { status: 'unverified', unscanned, detail: `扫描器退出码 ${res.status}（判定 ${byExit}）与 stdout 的 scan_status=${line} 不一致；原始输出：${compact}` }
  }
  return { status: line, unscanned, detail: compact }
}

// 交付阻塞项文案的唯一来源。shell 侧（evidence-completeness.sh）读的是同一个文件，
// 因此"同一件事在两处各写一句话"这种漂移不可能发生（§7.4 跨语言契约）。
//
// 格式违规一律 ToolchainError（调用方 → exit 1），**不跳过、不兜底**：本文件是"本包为何
// 不可交付"这句话的唯一来源，跳过一行就等于让一条交付阻塞静默消失，而清单照样写
// evidence_complete=true。此前这里是 lenient 的（`tab <= 0 → continue`、重复 code 后写覆盖前），
// 于是"少一个 TAB"或"重复一个 code"这类**可见的格式破坏**会静默降级成跨语言契约漂移：
// 同一份 TSV，shell 的 awk 取第一行、JS 的 Map 取最后一行，两侧给出不同文案而都不报错。
// 判据必须与 shell 侧 blocker_catalog_assert() 逐条一致，否则又变成"两套判据"。
export function readBlockerTexts(scriptDir) {
  const file = path.join(scriptDir, 'delivery-blockers.tsv')
  let raw
  try {
    raw = fs.readFileSync(file, 'utf8')
  } catch (error) {
    throw new ToolchainError(`读不到交付阻塞项文案表 ${file}：${String(error)}`)
  }
  const texts = new Map()
  const lines = raw.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (line.includes('\r')) {
      throw new ToolchainError(`delivery-blockers.tsv 第 ${i + 1} 行含 CR（行尾必须是 LF）`)
    }
    if (line === '' || line.startsWith('#')) continue
    const tabs = line.split('\t').length - 1
    if (tabs !== 1) {
      throw new ToolchainError(`delivery-blockers.tsv 第 ${i + 1} 行不是恰好一个 TAB 分隔的 <code><TAB><文案>（实测 ${tabs} 个）`)
    }
    const [code, text] = line.split('\t')
    if (!/^[a-z][a-z0-9_]*$/.test(code)) {
      throw new ToolchainError(`delivery-blockers.tsv 第 ${i + 1} 行的 code '${code}' 不合规（只允许小写字母、数字与下划线，且以字母开头）`)
    }
    if (text === '' || text !== text.trim()) {
      throw new ToolchainError(`delivery-blockers.tsv 第 ${i + 1} 行（${code}）的文案为空或含首尾空白`)
    }
    if (texts.has(code)) {
      throw new ToolchainError(`delivery-blockers.tsv 第 ${i + 1} 行的 code '${code}' 重复（文案表必须是一对一映射）`)
    }
    texts.set(code, text)
  }
  if (texts.size === 0) {
    throw new ToolchainError('delivery-blockers.tsv 没有任何有效条目（每行 <code><TAB><文案>）')
  }
  return texts
}

// 阻塞项文案取不到时**拒绝生成清单**：静默跳过会让"本包不可交付"这件事凭空消失，
// 而清单照样写着 evidence_complete=true。
export function blockerText(texts, code) {
  const text = texts.get(code)
  if (!text) {
    throw new ToolchainError(`交付阻塞项 '${code}' 在 delivery-blockers.tsv 中没有对应文案`)
  }
  return text
}

// 交付阻塞项 = 调用方注入的 ∪ 本模块自己派生的，按字符串去重。
// 并集而不是覆盖：调用方可能知道本模块判不出的原因（例如它自己观察到的采集器异常），
// 本模块也可能发现调用方漏算的原因（例如某份产物是二进制、扫描根本没覆盖到它）。
// 取交集会让双方各自的发现都失效，取并集才是"两边都同意才叫可交付"。
//
// 注入的是 **code**，不是文案。文案一律由本模块拿 code 回查 TSV 得到：
//   1. 早先注入的是文案本身，于是调用方可以塞进任意一串**表外文本**直通清单
//      （实测 `DELIVERY_BLOCKERS=表外文本` 会原样进入 delivery_blockers），
//      单一来源名存实亡，而且表里没有的句子照样在对外交付物里出现；
//   2. 表外 code 现在会抛 ToolchainError → 调用方 exit 1：调用方与文案表对不上号，
//      说明两侧不是同一个版本，这时既不该写清单也不该判"可交付"，只能拒绝收尾。
export function collectDeliveryBlockers({ texts, injected, monitorScope, scan, runnerScanStatus }) {
  const blockers = []
  const push = (code) => {
    const text = blockerText(texts, code)
    if (!blockers.includes(text)) blockers.push(text)
  }
  for (const code of injected) push(code)
  // 本模块自己判出的扫描结论优先；它非 passed 时用它的状态选文案。
  if (scan.status !== 'passed') {
    push({ failed: 'secret_scan_residue', incomplete: 'secret_scan_incomplete' }[scan.status] ?? 'secret_scan_unverified')
  } else if (runnerScanStatus !== undefined && runnerScanStatus !== 'passed') {
    // 本模块认为干净、runner 认为不干净：两份结论对不上，任何一方都不能单独采信。
    push('secret_scan_unverified')
  }
  if (monitorScope === 'skipped') {
    // 本包只有 k6 侧证据。监控原始数据只能由**另一份包**补齐（采集器只能采本机，
    // 而 --no-monitor 的前提正是"本机不是被压目标"），因此它不可独立交付。
    push('external_monitoring_required')
  }
  return blockers
}
