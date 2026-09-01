#!/usr/bin/env node
// Input: document-converter-sidecar/{converter.py,app.py} + backend compliance SidecarStreamDocumentAdapter.java
// Output: 同步比对报告到 stdout，exit 0 = 契约成立 / 1 = 漂移
// Pos: scripts/ - Sidecar /convert 结构化 pages 字段跨语言契约守卫（PR !2492 防复发）
// 维护声明: 若两侧字段改名/移位，同步更新本脚本的 PY_FILES / JAVA_FILE 与字段字面量表。
//
// check-pages-contract.mjs — /convert 响应 pages 字段跨语言契约守卫
//
// 背景: Java SidecarStreamDocumentAdapter 优先消费 /convert 响应的结构化 pages
// （[{pageNumber, text}]，PDF 物理页），缺省静默降级到虚拟分页兜底。降级是
// 不报错的静默行为——若 Python 端字段改名/漏发，质量核查将无声退回"整篇 1 页"
// 的旧行为（lessons #198 同源坑）。本脚本断言两端契约字面量同现。
//
// 判定:
//   两端字段字面量均存在   → PASS
//   任一端缺失             → FAIL（打印缺失端与字段清单）
//
// 用法: node scripts/check-pages-contract.mjs

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const PY_FILES = [
  join(ROOT, "document-converter-sidecar", "converter.py"),
  join(ROOT, "document-converter-sidecar", "app.py"),
];
const JAVA_FILE = join(
  ROOT,
  "backend/src/main/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapter.java",
);

// 生产端（Python）：converter.py 产出 pages 元素键，app.py 响应字段
const PRODUCER_FIELDS = [
  { file: "converter.py", field: '"pageNumber"', label: "pages[].pageNumber 键" },
  { file: "converter.py", field: '"text"', label: "pages[].text 键" },
  { file: "app.py", field: '"pages"', label: "/convert 响应 pages 字段" },
];
// 消费端（Java）：adapter 解析字段（JsonNode path 调用）
const CONSUMER_FIELDS = [
  { file: "SidecarStreamDocumentAdapter.java", field: 'path("pages")', label: "pages 字段解析" },
  { file: "SidecarStreamDocumentAdapter.java", field: 'path("pageNumber")', label: "pageNumber 解析" },
  { file: "SidecarStreamDocumentAdapter.java", field: 'path("text")', label: "text 解析" },
];

function readIfExists(path) {
  try {
    return readFileSync(path, "utf8");
  } catch {
    return null;
  }
}

function checkSide(name, fields, readFn) {
  const missing = [];
  for (const { file, field, label } of fields) {
    const text = readFn(file);
    if (text === null || !text.includes(field)) {
      missing.push(`${label}（${file} 中未找到 ${field}）`);
    }
  }
  return missing;
}

const producerTextCache = new Map();
const consumerTextCache = new Map();
for (const f of PY_FILES) producerTextCache.set(f, readIfExists(f));
consumerTextCache.set("SidecarStreamDocumentAdapter.java", readIfExists(JAVA_FILE));

const producerMissing = checkSide(
  "生产端(Python)",
  PRODUCER_FIELDS,
  (file) => producerTextCache.get(PY_FILES.find((f) => f.endsWith(file))),
);
const consumerMissing = checkSide(
  "消费端(Java)",
  CONSUMER_FIELDS,
  (file) => consumerTextCache.get(file),
);

if (producerMissing.length === 0 && consumerMissing.length === 0) {
  console.log("[pages-contract] PASS — pages/pageNumber/text 契约两端同现");
  process.exit(0);
}

if (producerMissing.length > 0) {
  console.error("[pages-contract] FAIL — 生产端(Python) 契约字段缺失:");
  for (const m of producerMissing) console.error(`  - ${m}`);
}
if (consumerMissing.length > 0) {
  console.error("[pages-contract] FAIL — 消费端(Java) 契约字段缺失:");
  for (const m of consumerMissing) console.error(`  - ${m}`);
}
console.error("修复: 恢复字段名，或两侧同步改名（ SidecarStreamDocumentAdapter.java + document-converter-sidecar/{converter.py,app.py} ）");
process.exit(1);
