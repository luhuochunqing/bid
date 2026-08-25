#!/usr/bin/env bash
# Input: document-converter-sidecar/converter.py 与 backend/src/main/java/com/xiyu/bid/biddraftagent/domain/PriceScoringKeywords.java
# Output: 退出码 0 表示跨语言报价评分关键词完全同步；退出码 1 打印差异与修复指引
# Pos: scripts/报价评分跨语言关键词同步校验门禁
# 维护声明: 维护者按项目SOP；若报价参数集合发生变动，必须同步更新两处源文件并保证本脚本校验通过。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - << 'PYEOF'
import re
import sys
from pathlib import Path

repo_root = Path.cwd()

java_file = repo_root / "backend/src/main/java/com/xiyu/bid/biddraftagent/domain/PriceScoringKeywords.java"
py_file = repo_root / "document-converter-sidecar/converter.py"

if not java_file.exists():
    print(f"❌ 找不到 Java 常量文件: {java_file}", file=sys.stderr)
    sys.exit(1)

if not py_file.exists():
    print(f"❌ 找不到 Python 转换文件: {py_file}", file=sys.stderr)
    sys.exit(1)

java_text = java_file.read_text(encoding="utf-8")
py_text = py_file.read_text(encoding="utf-8")

# Extract Java LONG and SHORT
long_match = re.search(r'LONG_PRICE_PARAMS\s*=\s*"([^"]+)"', java_text)
short_match = re.search(r'SHORT_PRICE_PARAMS\s*=\s*"([^"]+)"', java_text)

if not long_match or not short_match:
    print("❌ 无法从 Java 文件解析 LONG_PRICE_PARAMS 或 SHORT_PRICE_PARAMS", file=sys.stderr)
    sys.exit(1)

java_long = set(long_match.group(1).split("|"))
java_short = set(short_match.group(1).split("|"))

# Extract Python LONG and SHORT
py_long_match = re.search(r'PRICE_PARAM_LONG_KEYWORDS\s*=\s*\(([\s\S]*?)\)', py_text)
py_short_match = re.search(r'PRICE_PARAM_SHORT_KEYWORDS\s*=\s*\(([\s\S]*?)\)', py_text)

if not py_long_match or not py_short_match:
    print("❌ 无法从 Python 文件解析 PRICE_PARAM_LONG_KEYWORDS 或 PRICE_PARAM_SHORT_KEYWORDS", file=sys.stderr)
    sys.exit(1)

py_long = set(re.findall(r'"([^"]+)"', py_long_match.group(1)))
py_short = set(re.findall(r'"([^"]+)"', py_short_match.group(1)))

errors = []
if java_long != py_long:
    diff_j = java_long - py_long
    diff_p = py_long - java_long
    if diff_j:
        errors.append(f"Java 独有长词参数: {diff_j}")
    if diff_p:
        errors.append(f"Python 独有长词参数: {diff_p}")

if java_short != py_short:
    diff_j = java_short - py_short
    diff_p = py_short - java_short
    if diff_j:
        errors.append(f"Java 独有短词参数: {diff_j}")
    if diff_p:
        errors.append(f"Python 独有短词参数: {diff_p}")

if errors:
    print("❌ 报价评分关键词跨语言同步校验失败：", file=sys.stderr)
    for err in errors:
        print(f"  - {err}", file=sys.stderr)
    print("\n修复指引: 请同步更新 PriceScoringKeywords.java 与 converter.py 中的关键词定义。", file=sys.stderr)
    sys.exit(1)

print(f"✅ 跨语言报价关键词完全同步 (长词 {len(java_long)} 个, 短词 {len(java_short)} 个)")
PYEOF
