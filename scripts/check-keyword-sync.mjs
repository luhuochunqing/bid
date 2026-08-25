#!/usr/bin/env node
// Input: backend PriceScoringKeywords.java + document-converter-sidecar/converter.py
// Output: 同步比对报告到 stdout，exit 0 = 同步 / 1 = 漂移
// Pos: scripts/ - 跨语言关键词契约同步检查
// 维护声明: 若两侧常量改名/移位，同步更新本脚本的 JAVA_FILE / PY_FILE 与解析正则。
//
// check-keyword-sync.mjs — 报价评分参数关键词跨语言契约守卫（PR !2399 防复发）
//
// 背景: Java 侧 PriceScoringKeywords（LONG_PRICE_PARAMS/SHORT_PRICE_PARAMS）与
// Python 侧 converter.py（PRICE_PARAM_LONG_KEYWORDS/PRICE_PARAM_SHORT_KEYWORDS）
// 是同一语义的两份实现。此前仅靠注释约束同步，改一侧忘另一侧会静默回退到截断行为。
//
// 判定:
//   两侧均不存在        → PASS（契约尚未建立，如 PR !2399 合入前）
//   仅一侧存在          → FAIL（单边新增契约 = 高危漂移信号）
//   两侧均存在但集合不等 → FAIL（打印差集）
//   两侧均存在且一致    → PASS
//
// 用法: node scripts/check-keyword-sync.mjs

import { readFileSync, existsSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const PY_FILE = join(ROOT, "document-converter-sidecar", "converter.py");
const JAVA_PRIMARY = join(
  ROOT,
  "backend/src/main/java/com/xiyu/bid/biddraftagent/domain/PriceScoringKeywords.java",
);

function findJavaFile() {
  if (existsSync(JAVA_PRIMARY)) return JAVA_PRIMARY;
  // 常量类若搬家，全仓兜底定位（限 backend 源码）
  try {
    const out = execFileSync(
      "git",
      ["ls-files", "backend/src/main/java/**/PriceScoringKeywords.java"],
      { cwd: ROOT, encoding: "utf8" },
    ).trim();
    if (!out) return null;
    return join(ROOT, out.split("\n")[0]);
  } catch {
    return null;
  }
}

/** 从 Java 常量文件提取 LONG/SHORT 词表（管道分隔字符串） */
function parseJava(text) {
  const grab = (name) => {
    const m = text.match(new RegExp(name + '\\s*=\\s*"([^"]+)"'));
    if (!m) return null;
    return new Set(m[1].split("|").map((s) => s.trim()).filter(Boolean));
  };
  return { long: grab("LONG_PRICE_PARAMS"), short: grab("SHORT_PRICE_PARAMS") };
}

/** 从 Python 源码提取两个关键词元组（支持跨行括号与单行两种形态） */
function parsePython(text) {
  const grabTuple = (name) => {
    // 跨行形态: NAME = (\n "a", "b",\n )  或单行形态: NAME = ("a", "b")
    const m = text.match(
      new RegExp(name + "\\s*=\\s*\\(([^)]*)\\)", "s"),
    );
    if (!m) return null;
    const items = [...m[1].matchAll(/"([^"]*)"/g)].map((x) => x[1]);
    return items.length ? new Set(items) : null;
  };
  return {
    long: grabTuple("PRICE_PARAM_LONG_KEYWORDS"),
    short: grabTuple("PRICE_PARAM_SHORT_KEYWORDS"),
  };
}

function diffSets(a, b) {
  const onlyA = [...a].filter((x) => !b.has(x));
  const onlyB = [...b].filter((x) => !a.has(x));
  return { onlyA, onlyB };
}

function main() {
  const javaFile = findJavaFile();
  const pyExists = existsSync(PY_FILE);

  const java = javaFile ? parseJava(readFileSync(javaFile, "utf8")) : null;
  const py = pyExists ? parsePython(readFileSync(PY_FILE, "utf8")) : null;

  const javaHas = !!(java && java.long && java.short);
  const pyHas = !!(py && py.long && py.short);

  // 契约尚未建立（两侧均无）：放行
  if (!javaHas && !pyHas) {
    console.log("[keyword-sync] PASS — 契约两侧均不存在（关键词契约尚未建立，跳过）");
    process.exit(0);
  }

  // 单边存在：高危
  if (javaHas !== pyHas) {
    const present = javaHas ? `Java 侧 (${javaFile})` : `Python 侧 (${PY_FILE})`;
    const missing = javaHas
      ? "Python 侧 document-converter-sidecar/converter.py 缺 PRICE_PARAM_LONG_KEYWORDS/SHORT_KEYWORDS"
      : "Java 侧缺 PriceScoringKeywords.LONG_PRICE_PARAMS/SHORT_PRICE_PARAMS";
    console.error(`[keyword-sync] FAIL — 单边契约: ${present} 有关键词表，但 ${missing}`);
    console.error("[keyword-sync] 跨语言关键词契约必须两侧同时维护，否则静默回退到截断行为。");
    process.exit(1);
  }

  if (javaFile && (!java.long || !java.short)) {
    console.error(`[keyword-sync] FAIL — ${javaFile} 解析失败：找不到 LONG/SHORT_PRICE_PARAMS 常量`);
    process.exit(1);
  }
  if (!py.long || !py.short) {
    console.error(`[keyword-sync] FAIL — ${PY_FILE} 解析失败：找不到 PRICE_PARAM_LONG/SHORT_KEYWORDS 元组`);
    process.exit(1);
  }

  let bad = false;
  for (const [label, j, p] of [
    ["LONG", java.long, py.long],
    ["SHORT", java.short, py.short],
  ]) {
    const { onlyA, onlyB } = diffSets(j, p);
    if (onlyA.length === 0 && onlyB.length === 0) {
      console.log(`[keyword-sync] ${label} 词表一致 (${j.size} 个关键词)`);
      continue;
    }
    bad = true;
    console.error(`[keyword-sync] ${label} 词表不一致:`);
    if (onlyA.length) console.error(`  仅 Java 有: ${onlyA.join(", ")}`);
    if (onlyB.length) console.error(`  仅 Python 有: ${onlyB.join(", ")}`);
  }

  if (bad) {
    console.error("[keyword-sync] FAIL — 请同步两侧关键词表后重试");
    process.exit(1);
  }
  console.log("[keyword-sync] PASS — Java/Python 报价参数关键词表一致");
}

main();
