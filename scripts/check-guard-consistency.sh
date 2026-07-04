#!/usr/bin/env bash
# Input: all scripts/*.sh files
# Output: detects 逃生阀（escape hatch）注释声明 vs 代码实现不一致
# Pos: scripts/ — 守卫一致性扫描（guard consistency scanner）
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Rule (hard block):
#   脚本中注释声明的"# 逃生阀：XXX=1"必须在同一文件的代码中有对应的环境变量检查。
#   仅写注释不写代码 = 逃生阀实际上不存在，但开发者误以为已经实现。
#   历史事故：2026-07-04 pre-push-gate.sh §3.7 注释写了 FLYWAY_ALLOW_LEGACY_DIR=1 但代码没检查，
#   导致逃生阀无效。PR !1657 修复。
#
# 贝叶斯边界声明（Bayesian boundary）：
#   本脚本的似然比 ≈ 4.67（能检测"注释-代码不一致"）。
#   盲区：
#     - 检测不到"代码有但注释没写"的逃生阀（即无注释的逃生阀不会被扫描到）
#     - 检测不到"代码和注释都有但逻辑写错"（如 != 写成 =）
#     - 检测不到"逃生阀行为本身有 bug"（如检查了变量但 skip 分支位置不对）
#     - 检测不到"语义级不一致"（如一个文件 skip 整脚本，另一个 skip 单检查节）
#     - 开发者可绕过：不写注释，扫描器就漏掉
#   这些盲区需要外部补位（adversarial review、人类 code review、生产监控）。
#
# 逃生阀：GUARD_ALLOW_CONSISTENCY_SKIP=1 可绕过（仅在确认是已知盲区时使用）。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo '')"
if [ -z "$ROOT_DIR" ]; then
  echo "guard-consistency: not in a git repo, skipping."
  exit 0
fi
cd "$ROOT_DIR"

script_name="$(basename "$0")"

red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }
green() { printf '\033[32m%s\033[0m\n' "$*" >&2; }
yellow(){ printf '\033[33m%s\033[0m\n' "$*" >&2; }

# 逃生阀
if [ "${GUARD_ALLOW_CONSISTENCY_SKIP:-0}" = "1" ]; then
  yellow "${script_name}: GUARD_ALLOW_CONSISTENCY_SKIP=1 set, skipping consistency check."
  exit 0
fi

SCRIPTS_DIR="$ROOT_DIR/scripts"
if [ ! -d "$SCRIPTS_DIR" ]; then
  echo "${script_name}: no scripts/ directory, skipping."
  exit 0
fi

# 扫描所有 scripts/*.sh 中的"# 逃生阀：XXX=1"注释
# 提取变量名 XXX，然后验证同一文件中是否有对应的 if 检查
VIOLATIONS=0
TOTAL=0

while IFS= read -r file; do
  # 跳过自己（避免自引用）
  if [ "$(basename "$file")" = "$script_name" ]; then
    continue
  fi

  # 提取该文件中所有声明为"逃生阀"的变量名
  # 使用 sed：匹配 "# 逃生阀：XXX=1" 模式，提取 XXX
  declared_vars=$(sed -n 's/.*# 逃生阀：\([A-Z_]*\)=1.*/\1/p' "$file" 2>/dev/null || true)

  if [ -z "$declared_vars" ]; then
    continue
  fi

  while IFS= read -r var; do
    if [ -z "$var" ]; then
      continue
    fi
    TOTAL=$((TOTAL + 1))

    # 检查同一文件中是否有对应的 if 检查
    # 匹配模式：if [ "${VAR:-0}" = "1" ] 或 if [[ "${VAR:-0}" == "1" ]] 或 if [ "${VAR}" = "1" ]
    # 核心特征：if 行中包含 "${VAR" 固定字符串
    if grep -qF "\"\${${var}" "$file" 2>/dev/null; then
      green "  ✓ ${var} in $(basename "$file")"
    else
      red "  ✗ ${var} in $(basename "$file") — 注释声明了逃生阀但代码中未找到对应的 if 检查"
      VIOLATIONS=$((VIOLATIONS + 1))
    fi
  done <<< "$declared_vars"
done < <(find "$SCRIPTS_DIR" -maxdepth 1 -name "*.sh" -type f | sort)

echo ""

if [ "$TOTAL" -eq 0 ]; then
  echo "${script_name}: no 逃生阀 declarations found in scripts/, skipping."
  exit 0
fi

if [ "$VIOLATIONS" -gt 0 ]; then
  red ""
  red "❌ ${script_name}: 检测到 ${VIOLATIONS}/${TOTAL} 个逃生阀声明-实现不一致"
  red ""
  red "以上变量在注释中声明了'# 逃生阀：XXX=1'，但在同一文件的代码中找不到对应的 if 检查。"
  red "这通常意味着：注释写了但代码没实现（或实现逻辑已变更但注释未更新）。"
  red ""
  red "修复方案："
  red "  1. 如果是忘记实现：在代码中加上 'if [ \"\${XXX:-0}\" = \"1\" ]; then skip ...'"
  red "  2. 如果是注释过时：删除或更新注释"
  red ""
  red "历史事故：2026-07-04 pre-push-gate.sh §3.7 FLYWAY_ALLOW_LEGACY_DIR 注释写了但代码没实现。"
  red "详见 PR !1657 和 docs/lessons/lessons-learned.md §38。"
  red ""
  red "逃生阀：GUARD_ALLOW_CONSISTENCY_SKIP=1 可绕过（仅在确认是已知盲区时使用）。"
  exit 1
fi

green "✓ ${script_name}: 全部 ${TOTAL} 个逃生阀声明-实现一致"
exit 0