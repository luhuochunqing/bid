#!/usr/bin/env node
// Input: 一份 Markdown 交付文（默认压测证据附录），其正文里的图片路径相对该 .md 所在目录
// Output: 单文件 HTML——图片以 base64 内嵌，浏览器打开后 ⌘A / Ctrl+A → ⌘C 即可整篇粘进飞书
// Pos: scripts/performance/ - 交付文档的**排版层**：把已定稿的 Markdown 变成"能一键复制"的载体
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 为什么需要这一层：飞书文档的编辑区是虚拟滚动的富文本编辑器，用浏览器自动化往里灌长文
// 又慢又脆（每轮一个新 Node 进程 + CDP 往返，任何遍历 DOM 取 rect 的写法都会触发全页重排
// 而超时）。而"从网页里选中 → 复制 → 粘贴"是飞书最熟的一条输入路径：浏览器会把
// `text/html`（含 `<img src="data:...">`）放进剪贴板，飞书据此还原标题、表格、引用块和图片。
// 所以这里不碰飞书，只负责把文档做成**一个能被整篇选中的 HTML**。
//
// 分层约定：
//   - 图片**内嵌为 data URI**，不是相对路径：单文件才能整篇复制；相对路径的图在浏览器里
//     选中复制时，剪贴板里只有链接、没有像素，粘进飞书会变成空图。
//   - 样式**写在元素上**（inline style），不用 `<style>` 块：飞书的粘贴解析读的是元素的
//     计算/内联样式，`<style>` 里的表格边框与表头底色到那边会整片丢掉，表格会塌成无框文本。
//   - 本脚本**不改一个字**：它只做标记与内联样式，正文内容全部来自输入的那份 Markdown。
//     改文案请改 .md，再重跑本脚本——否则两份就开始各说各话。
//
// 用法：
//   node scripts/performance/render-appendix-doc.mjs \
//     --md  docs/testing/STRESS_TEST_EVIDENCE_APPENDIX_2026-09-21.md \
//     --out docs/testing/STRESS_TEST_EVIDENCE_APPENDIX_2026-09-21.html

import { readFileSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { Marked } from 'marked'

const argv = process.argv.slice(2)
const argOf = (name) => {
  const i = argv.indexOf(name)
  return i === -1 ? null : argv[i + 1]
}
const mdPath = argOf('--md')
const outPath = argOf('--out')
if (!mdPath || !outPath) {
  console.error('用法: render-appendix-doc.mjs --md <输入.md> --out <输出.html>')
  process.exit(2)
}

const mdDir = dirname(resolve(mdPath))
const src = readFileSync(mdPath, 'utf8')

// ---------- Markdown → HTML ----------
const marked = new Marked({ gfm: true, breaks: false })
let html = marked.parse(src)

// ---------- 图片内嵌 ----------
// 只认相对路径的 src；外链原样留着（本工具不该悄悄把外部资源抓进来）。
let embedded = 0
const missing = []
html = html.replace(/<img([^>]*?)\ssrc="([^"]+)"([^>]*?)>/g, (all, pre, src, post) => {
  if (/^(https?:|data:)/i.test(src)) return all
  const abs = resolve(mdDir, decodeURIComponent(src))
  let bytes
  try {
    bytes = readFileSync(abs)
  } catch {
    missing.push(src)
    return all
  }
  const ext = abs.slice(abs.lastIndexOf('.') + 1).toLowerCase()
  const mime = ext === 'svg' ? 'image/svg+xml' : ext === 'jpg' || ext === 'jpeg' ? 'image/jpeg' : 'image/png'
  embedded++
  // 宽度写死在元素上：图是 2000 px 见方的渲染产物，比文档栏宽宽得多；不写宽度时
  // 粘进飞书会按原始像素铺开、撑出页面，而飞书是否自动回缩不可控。
  const style = ' style="width:100%;max-width:820px;height:auto;display:block;margin:10px 0 4px"'
  return `<img${pre} src="data:${mime};base64,${bytes.toString('base64')}"${post}${style}>`
})

// ---------- 图片说明文字 ----------
// Markdown 里的 `**图 N · 标题**：说明…` 紧跟在图片后面，渲染出来是一个普通段落。
// 给它一点区分度（小字号 + 灰蓝色 + 左侧竖线），贴进飞书后仍是一段正文，但读起来像图注。
html = html.replace(/<p><strong>(图\s*\d+\s*·[^<]*)<\/strong>/g, '<p class="figcap"><strong>$1</strong>')

// ---------- 元素内联样式 ----------
// 表头底色与单元格边框必须写在元素上：飞书粘贴时不读 <style> 块。
const CELL = 'border:1px solid #c9d1d9;padding:7px 11px;vertical-align:top;line-height:1.6'
const styleInline = (s) =>
  s
    .replace(/<table>/g, '<table style="border-collapse:collapse;width:100%;margin:14px 0;font-size:15px">')
    .replace(/<thead>/g, '<thead style="background:#f2f5f8">')
    .replace(/<th(\s+align="[a-z]+")?>/g, (m, al) => `<th${al || ''} style="${CELL};font-weight:600;text-align:${al && al.includes('right') ? 'right' : al && al.includes('center') ? 'center' : 'left'}">`)
    .replace(/<td(\s+align="[a-z]+")?>/g, (m, al) => `<td${al || ''} style="${CELL};text-align:${al && al.includes('right') ? 'right' : al && al.includes('center') ? 'center' : 'left'}">`)
    .replace(/<blockquote>/g, '<blockquote style="margin:14px 0;padding:10px 16px;border-left:4px solid #1f6fd0;background:#f6f9fc;color:#2b3a4a">')
    .replace(/<h1>/g, '<h1 style="font-size:26px;margin:26px 0 12px;line-height:1.4">')
    .replace(/<h2>/g, '<h2 style="font-size:22px;margin:26px 0 12px;line-height:1.4;border-bottom:1px solid #e3e7ea;padding-bottom:6px">')
    .replace(/<h3>/g, '<h3 style="font-size:18px;margin:22px 0 10px;line-height:1.4">')
    .replace(/<h4>/g, '<h4 style="font-size:16px;margin:18px 0 8px;line-height:1.4">')
    .replace(/<p>/g, '<p style="margin:11px 0;line-height:1.8">')
    .replace(/<hr>/g, '<hr style="border:none;border-top:1px solid #e3e7ea;margin:24px 0">')
    .replace(/<code>/g, '<code style="background:#f2f5f8;padding:1px 5px;border-radius:3px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:14px">')
    .replace(/<pre>/g, '<pre style="background:#f6f8fa;padding:12px 14px;border-radius:5px;overflow-x:auto;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px;line-height:1.6">')
    .replace(/<table style="border-collapse/g, '<table style="border-collapse')
html = styleInline(html)

// 图注段落：图注在 .md 里就是一个普通段落，这里只加一点视觉区分，不改字。
html = html.replace(
  /<p class="figcap">/g,
  '<p class="figcap" style="margin:2px 0 20px;padding-left:10px;border-left:3px solid #d6dde4;color:#4b555e;font-size:14px;line-height:1.75">'
)

// ---------- 外壳 ----------
// 取文档的**第一个标题行**做 <title>：交付章节是插进报告正文的一段，它自身没有 H1
// （报告用 `#` 当文档名、`##` 当章节号，本节是 `## 九、`），所以不能只认 `^# `。
const title = (src.match(/^#{1,6}\s+(.+)$/m) || [, '压测证据附录'])[1].trim()
const out = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>${title}</title>
</head>
<body style="margin:0;background:#eef1f4">
<!--
  整篇选中即可复制：⌘A（Ctrl+A）→ ⌘C（Ctrl+C）→ 粘进飞书文档。
  图片已内嵌为 data URI，复制时会连像素一起进剪贴板；样式写在元素上，飞书能读到。
  本文件由 scripts/performance/render-appendix-doc.mjs 生成，请勿手工编辑——
  要改内容请改同名 .md 再重跑脚本。
-->
<article style="max-width:900px;margin:0 auto;padding:48px 56px 64px;background:#fff;
  font-family:'PingFang SC','Hiragino Sans GB','Microsoft YaHei',Helvetica,Arial,sans-serif;
  font-size:16px;color:#101418;line-height:1.8;-webkit-font-smoothing:antialiased">
${html}</article>
</body>
</html>
`

writeFileSync(outPath, out)
const kb = (n) => `${(n / 1024).toFixed(0)} KB`
console.log(`render-appendix-doc: 已生成 ${outPath}`)
console.log(`  正文 ${kb(Buffer.byteLength(src))} → HTML ${kb(Buffer.byteLength(out))}（内嵌图片 ${embedded} 张）`)
if (missing.length) {
  console.error(`  以下图片没找到，已按原样保留相对路径（粘进飞书会变成空图）：`)
  for (const m of missing) console.error(`    - ${m}`)
  process.exit(1)
}
