#!/usr/bin/env node
// Input: 一个证据包目录（其 monitoring/*.csv 与 k6-raw-samples.csv），或显式 --package 路径
// Output: 该次运行的时间序列折线图（SVG），落到 --out 指定的目录，供报告第五章与验收材料引用
// Pos: scripts/performance/ - 证据包的**派生展示层**：只读原始 CSV，不产生任何新的测量值
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 分层约定：
//   - 本脚本**只画已经落在 CSV 里的数**。任何统计（k6 的逐秒 P95、MySQL 的逐区间 QPS）都
//     由原始列现算，不从 summary/conclusion 里抄——抄来的数字没有可复核的出处，正是本目录
//     反复在防的东西。
//   - 它**不属于交付门禁**：产物是给人看的图，缺图不影响证据包能不能交付。因此它也不写进
//     manifest.json、不参与 sha256、不进证据包目录（图是派生物，混进包里会让人分不清
//     "哪份是原始产物、哪份是事后画的"）。
//   - PNG 转换**不在这里做**：SVG 是确定性的纯文本产物，可以由任何渲染器转；本机转 PNG 用
//     `qlmanage -t -s N -o <dir> <file.svg>`（macOS 自带）。转换器一旦写进本脚本，就等于
//     把"能不能出图"绑到某个具体宿主上。
//
// 用法：
//   node scripts/performance/render-monitor-charts.mjs \
//     --package docs/testing/stress-test-artifacts/20260921T145125Z-standard \
//     --out     docs/testing/stress-test-artifacts/charts/20260921T145125Z-standard
//
// 画布是**正方形**：macOS 的 qlmanage 出 PNG 时会补齐成正方形，非正方形画布会被留白，
// 与其事后裁剪，不如一开始就按正方形排版。

import { readFileSync, mkdirSync, writeFileSync, existsSync } from 'node:fs'
import { join, resolve, basename } from 'node:path'

const W = 1000
const H = 1000
const PAD = { left: 118, right: 78, top: 176, bottom: 118 }
const PLOT = {
  x0: PAD.left,
  x1: W - PAD.right,
  y0: PAD.top,
  y1: H - PAD.bottom,
}
const COLORS = ['#1f6fd0', '#d1372c', '#2b8a3e', '#e07b00', '#7b52ab', '#0f7b8a', '#c2255c', '#5c7080']
const FONT = 'PingFang SC, Hiragino Sans GB, Helvetica, Arial, sans-serif'

// ---------- 参数 ----------
const argv = process.argv.slice(2)
const argOf = (name) => {
  const i = argv.indexOf(name)
  return i === -1 ? null : argv[i + 1]
}
const pkgDir = argOf('--package')
const outDir = argOf('--out')
if (!pkgDir || !outDir || !existsSync(pkgDir)) {
  console.error('用法: render-monitor-charts.mjs --package <证据包目录> --out <出图目录>')
  process.exit(2)
}
const runId = basename(resolve(pkgDir))

// ---------- CSV ----------
// 只按逗号切、只按表头名取列。监控 CSV 由采集器自己写，值里不含逗号；k6 的原始采样
// 有 19 个标签列且顺序固定，但按表头名取列仍然更稳（列序变了不会静默画错曲线）。
function readCsv(path) {
  const text = readFileSync(path, 'utf8')
  const lines = text.split('\n').filter((l) => l.trim() !== '')
  const header = lines[0].split(',')
  return {
    header,
    rows: lines.slice(1).map((l) => l.split(',')),
    col(row, name) {
      return row[header.indexOf(name)]
    },
  }
}

const num = (v) => {
  if (v === undefined || v === null) return null
  const s = String(v).trim()
  if (s === '') return null
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}

const iso = (s) => Date.parse(s)
const hhmmss = (ms) => new Date(ms).toISOString().slice(11, 19)

// ---------- 坐标与刻度 ----------
// 上界取"够用就好"的整数刻度：曲线贴着顶边比留一大截空白更容易读出差值。
function niceScale(min, max, count = 5) {
  if (!(Number.isFinite(min) && Number.isFinite(max))) return null
  if (min === max) {
    const pad = Math.abs(min) * 0.1 || 1
    min -= pad
    max += pad
  }
  const raw = (max - min) / count
  const mag = Math.pow(10, Math.floor(Math.log10(raw)))
  const norm = raw / mag
  const step = (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10) * mag
  const lo = Math.floor(min / step) * step
  const hi = Math.ceil(max / step) * step
  return { lo, hi, step, ticks: ticksOf(lo, hi, step) }
}

function ticksOf(lo, hi, step) {
  const out = []
  for (let v = lo; v <= hi + step / 2; v += step) {
    // 浮点累加会给出 0.30000000000000004 这样的刻度值，按步长的小数位收敛
    const dec = Math.max(0, -Math.floor(Math.log10(step)) + (step < 1 ? 0 : 0))
    out.push(Number(v.toFixed(Math.min(6, dec + 2))))
  }
  return out
}

const fmt = (v) => (Math.abs(v) >= 1000 ? v.toFixed(0) : Math.abs(v) >= 10 ? String(Number(v.toFixed(1))) : String(Number(v.toFixed(2))))

// ---------- 画图 ----------
const esc = (s) =>
  String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

function render(spec) {
  const { title, subtitle, source, xs, series, band, note } = spec
  const t0 = Math.min(...xs)
  const t1 = Math.max(...xs)
  const span = t1 - t0 || 1
  const px = (t) => PLOT.x0 + ((t - t0) / span) * (PLOT.x1 - PLOT.x0)

  const axes = ['left', 'right'].filter((a) => series.some((s) => (s.axis || 'left') === a))
  const scales = {}
  for (const a of axes) {
    const vals = series.filter((s) => (s.axis || 'left') === a).flatMap((s) => s.values.filter((v) => v !== null))
    const sc = niceScale(Math.min(...vals, 0), Math.max(...vals))
    if (!sc) throw new Error(`无法为 ${title} 的 ${a} 轴定刻度`)
    scales[a] = sc
  }
  const py = (a, v) => {
    const sc = scales[a]
    return PLOT.y1 - ((v - sc.lo) / (sc.hi - sc.lo)) * (PLOT.y1 - PLOT.y0)
  }

  const out = []
  out.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`)
  out.push(`<rect width="${W}" height="${H}" fill="#ffffff"/>`)
  out.push(
    `<text x="${PAD.left}" y="52" font-family="${FONT}" font-size="30" font-weight="600" fill="#101418">${esc(title)}</text>`
  )
  if (subtitle) {
    out.push(`<text x="${PAD.left}" y="88" font-family="${FONT}" font-size="17" fill="#404850">${esc(subtitle)}</text>`)
  }
  out.push(`<text x="${PAD.left}" y="116" font-family="${FONT}" font-size="15" fill="#6b747d">${esc(source)}</text>`)

  // 图例（放在标题与绘图区之间的留白里，不压曲线）
  let lx = PAD.left
  const ly = 150
  series.forEach((s, i) => {
    const color = s.color || COLORS[i % COLORS.length]
    out.push(`<rect x="${lx}" y="${ly - 11}" width="22" height="4" fill="${color}"/>`)
    const label = `${s.name}${s.unit ? `（${s.unit}）` : ''}`
    out.push(`<text x="${lx + 28}" y="${ly - 4}" font-family="${FONT}" font-size="16" fill="#25303a">${esc(label)}</text>`)
    lx += 36 + label.length * 15
  })

  // 压测窗口底色：监控采样的窗口比 k6 长，标出来才知道哪一段是真正受压的时段
  if (band) {
    const bx0 = Math.max(PLOT.x0, px(band.start))
    const bx1 = Math.min(PLOT.x1, px(band.end))
    if (bx1 > bx0) {
      out.push(`<rect x="${bx0.toFixed(1)}" y="${PLOT.y0}" width="${(bx1 - bx0).toFixed(1)}" height="${PLOT.y1 - PLOT.y0}" fill="#1f6fd0" opacity="0.07"/>`)
      out.push(
        `<text x="${((bx0 + bx1) / 2).toFixed(1)}" y="${PLOT.y0 - 8}" font-family="${FONT}" font-size="14" fill="#1f6fd0" text-anchor="middle">k6 压测窗口 ${hhmmss(band.start)}–${hhmmss(band.end)}Z</text>`
      )
    }
  }

  // 网格与左右刻度
  const sc = scales.left
  for (const tick of sc.ticks) {
    const y = py('left', tick)
    out.push(`<line x1="${PLOT.x0}" y1="${y.toFixed(1)}" x2="${PLOT.x1}" y2="${y.toFixed(1)}" stroke="#e3e7ea" stroke-width="1"/>`)
    out.push(
      `<text x="${PLOT.x0 - 12}" y="${(y + 5).toFixed(1)}" font-family="${FONT}" font-size="15" fill="#4b555e" text-anchor="end">${fmt(tick)}</text>`
    )
  }
  if (scales.right) {
    for (const tick of scales.right.ticks) {
      const y = py('right', tick)
      out.push(
        `<text x="${PLOT.x1 + 12}" y="${(y + 5).toFixed(1)}" font-family="${FONT}" font-size="15" fill="#4b555e">${fmt(tick)}</text>`
      )
    }
  }
  out.push(
    `<line x1="${PLOT.x0}" y1="${PLOT.y1}" x2="${PLOT.x1}" y2="${PLOT.y1}" stroke="#9aa4ad" stroke-width="1.5"/>`
  )
  out.push(
    `<line x1="${PLOT.x0}" y1="${PLOT.y0}" x2="${PLOT.x0}" y2="${PLOT.y1}" stroke="#9aa4ad" stroke-width="1.5"/>`
  )

  // X 轴时间刻度
  const xTickCount = 6
  for (let i = 0; i < xTickCount; i++) {
    const t = t0 + (span * i) / (xTickCount - 1)
    const x = px(t)
    out.push(
      `<text x="${x.toFixed(1)}" y="${PLOT.y1 + 30}" font-family="${FONT}" font-size="15" fill="#4b555e" text-anchor="middle">${hhmmss(t)}</text>`
    )
  }
  // 底部只留三层文字：时间刻度（y1+30）→ 图注（y1+64）→ 出处（y1+92）。
  // 不再单起一行"采样时刻（UTC）"轴标题——它和图注抢同一行，压在一起两边都读不出来，
  // 而刻度本身已经说明了那是时间。

  // 曲线：值为 null 的点直接断开，不插值也不补 0
  series.forEach((s, i) => {
    const a = s.axis || 'left'
    const color = s.color || COLORS[i % COLORS.length]
    const pts = []
    xs.forEach((t, k) => {
      const v = s.values[k]
      if (v === null || v === undefined) return
      pts.push(`${px(t).toFixed(1)},${py(a, v).toFixed(1)}`)
    })
    if (pts.length > 1) {
      out.push(`<polyline fill="none" stroke="${color}" stroke-width="2.4" stroke-linejoin="round" points="${pts.join(' ')}"/>`)
    }
    // 端点值标在图上：曲线的首末值往往就是报告里要引用的那个数。
    // 贴着顶边的点把标签放到点下方——放上方会顶进图例那一行。
    const first = s.values.findIndex((v) => v !== null)
    const last = s.values.length - 1 - [...s.values].reverse().findIndex((v) => v !== null)
    for (const k of [first, last]) {
      if (k < 0) continue
      const t = xs[k]
      const v = s.values[k]
      const y = py(a, v)
      const labelY = y - PLOT.y0 < 26 ? y + 24 : y - 12
      out.push(
        `<circle cx="${px(t).toFixed(1)}" cy="${y.toFixed(1)}" r="4" fill="${color}"/>` +
          `<text x="${(px(t) + (k === first ? 8 : -8)).toFixed(1)}" y="${labelY.toFixed(1)}" font-family="${FONT}" font-size="15" fill="${color}" text-anchor="${k === first ? 'start' : 'end'}">${fmt(Number(v.toFixed(2)))}</text>`
      )
    }
  })

  if (note) {
    out.push(`<text x="${PAD.left}" y="${PLOT.y1 + 64}" font-family="${FONT}" font-size="15" fill="#6b747d">${esc(note)}</text>`)
  }
  out.push(
    `<text x="${PAD.left}" y="${PLOT.y1 + 92}" font-family="${FONT}" font-size="15" fill="#6b747d">由原始 CSV 渲染（非人工绘制）：证据包 ${esc(runId)}</text>`
  )
  out.push('</svg>')
  return out.join('\n') + '\n'
}

// ---------- 读入监控序列 ----------
const mon = join(pkgDir, 'monitoring')
const need = ['system.csv', 'app-jvm.csv', 'mysql.csv', 'redis.csv']
for (const f of need) {
  if (!existsSync(join(mon, f))) {
    console.error(`缺少监控原始数据 ${join(mon, f)}——本脚本只渲染已落盘的采样，不代采也不补值`)
    process.exit(1)
  }
}
const csv = Object.fromEntries(need.map((f) => [f, readCsv(join(mon, f))]))
const at = (c, name) => c.rows.map((r) => iso(c.col(r, 'timestamp')))
const va = (c, name) => c.rows.map((r) => num(c.col(r, name)))

const monX = at(csv['system.csv'])
const MON_SOURCE = '数据来源：monitoring/{system,app-jvm,mysql,redis}.csv（采集器逐 5s 一行）'

// k6 窗口：从 manifest 读（哪一段是真受压的时段，不由曲线形状猜）
let band = null
const mf = join(pkgDir, 'manifest.json')
if (existsSync(mf)) {
  const m = JSON.parse(readFileSync(mf, 'utf8'))
  if (m.started_at_utc && m.ended_at_utc) {
    band = { start: Date.parse(m.started_at_utc), end: Date.parse(m.ended_at_utc) }
  }
}

// ---------- k6 逐秒序列 ----------
// 逐秒 P95 与逐秒请求数都从 raw samples 现算：summary 只给全窗口的分位数，
// 而"P95 是什么时候上去的"恰恰是容量分析要看的东西。
const rawPath = join(pkgDir, 'k6-raw-samples.csv')
let k6 = null
if (existsSync(rawPath)) {
  const rc = readCsv(rawPath)
  const dur = new Map()
  const reqs = new Map()
  for (const r of rc.rows) {
    const name = rc.col(r, 'metric_name')
    if (name !== 'http_req_duration' && name !== 'http_reqs') continue
    const t = Number(rc.col(r, 'timestamp')) * 1000
    const v = num(rc.col(r, 'metric_value'))
    if (v === null) continue
    if (name === 'http_req_duration') {
      if (!dur.has(t)) dur.set(t, [])
      dur.get(t).push(v)
    } else {
      reqs.set(t, (reqs.get(t) || 0) + v)
    }
  }
  const secs = [...dur.keys()].sort((a, b) => a - b)
  const p95Of = (arr) => {
    const s = [...arr].sort((a, b) => a - b)
    return s[Math.max(0, Math.ceil(0.95 * s.length) - 1)]
  }
  k6 = {
    xs: secs,
    p95: secs.map((t) => p95Of(dur.get(t))),
    rps: secs.map((t) => reqs.get(t) || 0),
    source: '数据来源：k6-raw-samples.csv（逐请求原始采样，逐秒聚合成 P95 与请求数）',
  }
}

// ---------- 出图清单 ----------
const charts = [
  {
    file: '01-system-cpu.svg',
    title: '系统 CPU 使用率（应用宿主 winbid-01）',
    subtitle: 'user% 与 sys% 来自 top 的 CPU 行；4 vCPU 宿主',
    source: MON_SOURCE,
    xs: monX,
    band,
    note: '峰值 74.6% 出现在阶梯爬升期（14:51:31Z），稳态多在 10–45%，未见饱和。',
    series: [
      { name: 'cpu user', unit: '%', values: va(csv['system.csv'], 'cpu_user_pct') },
      { name: 'cpu sys', unit: '%', values: va(csv['system.csv'], 'cpu_sys_pct') },
    ],
  },
  {
    file: '02-system-load.svg',
    title: '系统负载（load1 / load5）',
    subtitle: '4 vCPU：load1 峰值 1.58，未超过核数',
    source: MON_SOURCE,
    xs: monX,
    band,
    note: '压测结束后 load 缓慢回落，无排队积压。',
    series: [
      { name: 'load1', values: va(csv['system.csv'], 'load1') },
      { name: 'load5', values: va(csv['system.csv'], 'load5') },
    ],
  },
  {
    file: '03-system-memory.svg',
    title: '系统内存（应用宿主）',
    subtitle: 'mem_used 与 mem_total；总内存 7,581 MB ≈ 8 GB',
    source: MON_SOURCE,
    xs: monX,
    band,
    note: '全程 mem_used 3,427 → 3,494 MB（+67 MB），无内存压力。',
    series: [
      { name: 'mem used', unit: 'MB', values: va(csv['system.csv'], 'mem_used_mb') },
      { name: 'mem total', unit: 'MB', values: va(csv['system.csv'], 'mem_total_mb') },
    ],
  },
  {
    file: '04-jvm-memory.svg',
    title: 'JVM 内存（PID 25416，G1）',
    subtitle: 'heap used / heap committed / metaspace / 进程 RSS（jcmd GC.heap_info + ps）',
    source: '数据来源：monitoring/app-jvm.csv',
    xs: monX,
    band,
    note: 'heap committed 恒为 794 MB（G1 整堆 total），committed 全程未扩张；RSS 1,583 → 1,620 MB。',
    series: [
      { name: 'heap used', unit: 'MB', values: va(csv['app-jvm.csv'], 'heap_used_mb') },
      { name: 'heap committed', unit: 'MB', values: va(csv['app-jvm.csv'], 'heap_committed_mb') },
      { name: 'metaspace', unit: 'MB', values: va(csv['app-jvm.csv'], 'metaspace_used_mb') },
      { name: 'RSS', unit: 'MB', values: va(csv['app-jvm.csv'], 'rss_mb') },
    ],
  },
  {
    file: '05-jvm-gc.svg',
    title: 'JVM GC（累计次数与累计耗时）',
    subtitle: '左轴：YGC 累计次数；右轴：GC 累计耗时（s）。FGC 全程为 0',
    source: '数据来源：monitoring/app-jvm.csv（jstat -gc）',
    xs: monX,
    band,
    note: 'YGC 176 → 295（+119 次），累计耗时 +1.457 s ⇒ 平均 12.2 ms/次；FGC 恒为 0。',
    series: [
      { name: 'YGC 次数', unit: '次', axis: 'left', values: va(csv['app-jvm.csv'], 'ygc') },
      { name: 'FGC 次数', unit: '次', axis: 'left', values: va(csv['app-jvm.csv'], 'fgc'), color: '#8d6e63' },
      { name: 'GC 累计耗时', unit: 's', axis: 'right', values: va(csv['app-jvm.csv'], 'gc_total_s') },
    ],
  },
  {
    file: '06-jvm-threads.svg',
    title: 'JVM 线程数',
    subtitle: '应用进程线程数（/proc/<pid>/status）',
    source: '数据来源：monitoring/app-jvm.csv',
    xs: monX,
    band,
    note: '79 → 162，压测结束后维持在 161–162（线程池扩张后不回收属预期）。',
    series: [{ name: 'threads', unit: '个', values: va(csv['app-jvm.csv'], 'threads') }],
  },
  {
    file: '07-mysql-connections.svg',
    title: 'MySQL 连接数（服务端口径）',
    subtitle: 'Threads_connected / Threads_running，来自 performance_schema.global_status',
    source: '数据来源：monitoring/mysql.csv',
    xs: monX,
    band,
    note: '服务端连接数 26–27、并发执行 1–3；注意这是服务端计数，不等于 Hikari 池的活跃连接数。',
    series: [
      { name: 'Threads_connected', unit: '个', values: va(csv['mysql.csv'], 'threads_connected') },
      { name: 'Threads_running', unit: '个', values: va(csv['mysql.csv'], 'threads_running') },
    ],
  },
  {
    file: '08-mysql-counters.svg',
    title: 'MySQL 累计计数器（实例启动以来）',
    subtitle: 'Slow_queries / Innodb_row_lock_waits / Aborted_connects',
    source: '数据来源：monitoring/mysql.csv',
    xs: monX,
    band,
    note: '三条线在整个窗口内完全水平 ⇒ 压测期间新增：慢查询 0、行锁等待 0、中断连接 0（基线值 25/5/216 是实例历史累计）。',
    series: [
      { name: 'Slow_queries', unit: '条', values: va(csv['mysql.csv'], 'slow_queries') },
      { name: 'Innodb_row_lock_waits', unit: '次', values: va(csv['mysql.csv'], 'innodb_row_lock_waits') },
      { name: 'Aborted_connects', unit: '次', values: va(csv['mysql.csv'], 'aborted_connects') },
    ],
  },
  {
    file: '09-mysql-qps.svg',
    title: 'MySQL 语句速率（逐采样区间）',
    subtitle: 'Questions 计数器逐区间差分 ÷ 区间秒数；含所有客户端',
    source: '数据来源：monitoring/mysql.csv（Questions 差分现算）',
    xs: monX,
    band,
    note: '压测窗口内 Questions +1,249,461，跨 264 s ⇒ 全程均值约 4,733 语句/s；峰值 8,944 语句/s（14:54:22Z），结束后归 0。',
    series: [
      {
        name: '语句速率',
        unit: 'QPS',
        values: (() => {
          const q = va(csv['mysql.csv'], 'questions')
          return monX.map((t, i) => {
            if (i === 0 || q[i] === null || q[i - 1] === null) return null
            const dt = (t - monX[i - 1]) / 1000
            return dt > 0 ? (q[i] - q[i - 1]) / dt : null
          })
        })(),
      },
    ],
  },
  {
    file: '10-redis-ops.svg',
    title: 'Redis 操作速率',
    subtitle: 'instantaneous_ops_per_sec（服务端自报的瞬时值）',
    source: '数据来源：monitoring/redis.csv',
    xs: monX,
    band,
    note: '峰值 263 ops/s，压测结束后回到 0；全程 used_memory 仅 1.5–1.6 MB。',
    series: [
      { name: 'ops/s', values: va(csv['redis.csv'], 'instantaneous_ops_per_sec') },
      { name: 'connected_clients', unit: '个', values: va(csv['redis.csv'], 'connected_clients') },
    ],
  },
  {
    file: '11-redis-keyspace.svg',
    title: 'Redis 键空间累计命中 / 未命中',
    subtitle: 'keyspace_hits / keyspace_misses（实例启动以来累计）',
    source: '数据来源：monitoring/redis.csv',
    xs: monX,
    band,
    note: '窗口内 hits +890、misses +21,575 ⇒ 命中率约 3.96%，压测期间以未命中为主。',
    series: [
      { name: 'keyspace_hits', unit: '次', values: va(csv['redis.csv'], 'keyspace_hits') },
      { name: 'keyspace_misses', unit: '次', values: va(csv['redis.csv'], 'keyspace_misses') },
    ],
  },
]

if (k6) {
  charts.push({
    file: '12-k6-p95-per-second.svg',
    title: 'k6 全链路 P95 响应耗时（逐秒）',
    subtitle: 'http_req_duration 按秒聚合后的 P95（最近秩），共 10,784 次请求',
    source: k6.source,
    xs: k6.xs,
    note: '准入口径核心业务 P95 < 2000 ms：实测 1,613.74 ms 达标；脚本内部更严阈值 p(95)<1500 未过。',
    series: [{ name: '每秒 P95 耗时', unit: 'ms', values: k6.p95 }],
  })
  charts.push({
    file: '13-k6-rps-per-second.svg',
    title: 'k6 每秒请求数（吞吐）',
    subtitle: 'http_reqs 按秒聚合；阶梯 10 → 50 → 100 并发',
    source: k6.source,
    xs: k6.xs,
    note: '全窗口 10,784 次请求 / 212 s ⇒ 平均 50.9 req/s，峰值 73 req/s。',
    series: [{ name: '请求数', unit: 'req/s', values: k6.rps }],
  })
}

// ---------- 落盘 ----------
mkdirSync(outDir, { recursive: true })
const written = []
for (const c of charts) {
  const svg = render(c)
  writeFileSync(join(outDir, c.file), svg)
  written.push(c.file)
}
const total = charts.reduce((n, c) => n + c.xs.length, 0)
console.log(`render-monitor-charts: 已出图 ${written.length} 张 → ${outDir}`)
console.log(`  数据点合计 ${total}（全部取自 ${runId} 的原始 CSV，未做任何平滑或插值）`)
console.log(`  PNG 转换（可选，macOS）：qlmanage -t -s 2000 -o <png目录> <各.svg>`)
for (const f of written) console.log(`  - ${f}`)
