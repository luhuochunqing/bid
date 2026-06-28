#!/usr/bin/env bash
# Input: staged application*.yml files from git index
# Output: detects modifications to critical Flyway config keys in Spring profiles
# Pos: scripts/ — Flyway 配置守卫（Flyway config guard）
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Rule (hard block):
#   application*.yml 中的 spring.flyway 关键配置项不可随意修改：
#     - locations        (迁移目录，改了会导致迁移不执行/找不到)
#     - baseline-version (基线版本，改了会导致部分迁移静默跳过)
#     - baseline-on-migrate (基线策略，改了会导致迁移行为变化)
#     - validateOnMigrate (校验开关，改了会导致 checksum mismatch 静默放过)
#   修改这些配置项需要显式确认（FLYWAY_ALLOW_CONFIG_EDIT=1）。
#
# 工程背景（请勿删除）：
# 2026-06-28 V1106 事故复盘发现 5 个 profile 的 Flyway 配置不一致：
#   - application.yml:       baseline-on-migrate=true, baseline-version=0
#   - application-dev.yml:   baseline-on-migrate=true, baseline-version=72
#   - application-mysql.yml: baseline-on-migrate=true, baseline-version=1050
#   - application-prod.yml:  baseline-on-migrate=false, 无 baseline-version
#   - application-e2e.yml:   baseline-on-migrate=false, 无 baseline-version
# 历史事故（2026-06-28 CO-361 看板空白）：
#   V101 因 baseline-version=1050 静默跳过，task_status_dict 表无种子数据，
#   导致 /api/task-status-dict 返回 [] → 看板空白。修改 baseline 配置无任何告警。
# 本脚本将"Flyway 配置修改"前移到 pre-commit 拦截，避免静默行为再次引发事故。
#
# 逃生阀：FLYWAY_ALLOW_CONFIG_EDIT=1 可绕过（仅在大版本升级/迁移策略调整时使用）。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo '')"
if [ -z "$ROOT_DIR" ]; then
  echo "flyway-config-guard: not in a git repo, skipping."
  exit 0
fi
cd "$ROOT_DIR"

script_name="$(basename "$0")"

red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }
green() { printf '\033[32m%s\033[0m\n' "$*" >&2; }
yellow(){ printf '\033[33m%s\033[0m\n' "$*" >&2; }

# 逃生阀
if [ "${FLYWAY_ALLOW_CONFIG_EDIT:-0}" = "1" ]; then
  yellow "${script_name}: FLYWAY_ALLOW_CONFIG_EDIT=1 set, skipping Flyway config guard."
  yellow "  ⚠ 修改 Flyway 配置可能导致迁移行为变化，请确认是否真的需要修改。"
  exit 0
fi

# 检测 staged 的 application*.yml 修改
STAGED_CONFIG=$(git diff --cached --name-only --diff-filter=AMRT | \
  grep -E "^backend/src/main/resources/application.*\.yml$" || true)

if [ -z "$STAGED_CONFIG" ]; then
  echo "${script_name}: no application*.yml staged, skipping."
  exit 0
fi

# Flyway 关键配置项（不可随意修改）
CRITICAL_KEYS=(
  "locations"
  "baseline-version"
  "baseline-on-migrate"
  "validateOnMigrate"
)

# 收集违规项到临时文件（避免 while 管道子 shell 变量丢失）
TMP_VIOLATIONS=$(mktemp)
trap 'rm -f "$TMP_VIOLATIONS"' EXIT

while IFS= read -r file; do
  [ -z "$file" ] && continue

  # 获取 staged 的 diff（只看新增/修改行）
  DIFF=$(git diff --cached -- "$file" 2>/dev/null || true)
  [ -z "$DIFF" ] && continue

  # 检查每个关键配置项
  for key in "${CRITICAL_KEYS[@]}"; do
    # 匹配 +/- 行含 flyway 配置项（如 "    locations: classpath:db/migration-mysql"）
    CHANGED=$(echo "$DIFF" | grep -E "^[+-].*${key}:" || true)
    if [ -n "$CHANGED" ]; then
      echo "  ${file}: ${key}" >> "$TMP_VIOLATIONS"
      echo "$CHANGED" | while IFS= read -r line; do
        echo "    ${line}" >> "$TMP_VIOLATIONS"
      done
    fi
  done
done <<< "$STAGED_CONFIG"

VIOLATIONS=$(wc -l < "$TMP_VIOLATIONS" | tr -d ' ')

if [ "$VIOLATIONS" -eq 0 ]; then
  echo "${script_name}: no critical Flyway config changes detected, skipping."
  exit 0
fi

red "❌ ${script_name}: 检测到 Flyway 关键配置修改"
red ""
red "以下 application*.yml 中的 Flyway 配置项被修改："
cat "$TMP_VIOLATIONS" | sed 's/^/  /' >&2
red ""
red "工程背景："
red "  Flyway 配置的静默行为是历史故障的元凶："
red "  - baseline-version 改动会导致部分迁移静默跳过（2026-06-28 CO-361 看板空白事故）"
red "  - locations 改动会导致迁移目录错误（2026-06-28 V1106 列缺失事故）"
red "  - validateOnMigrate 改动会导致 checksum mismatch 静默放过"
red "  - baseline-on-migrate 改动会导致迁移执行行为变化"
red ""
red "当前各 profile 配置（请勿误改）："
red "  application.yml:       baseline-on-migrate=true,  baseline-version=0"
red "  application-dev.yml:   baseline-on-migrate=true,  baseline-version=72"
red "  application-mysql.yml: baseline-on-migrate=true,  baseline-version=1050"
red "  application-prod.yml:  baseline-on-migrate=false, 无 baseline-version"
red "  application-e2e.yml:   baseline-on-migrate=false, 无 baseline-version"
red ""
red "修复方案："
red "  1. 如果是误改，请 git checkout -- <file>"
red "  2. 如果确实需要修改（如大版本升级、迁移策略调整）："
red '     a) 在 commit message 中说明修改原因和影响范围'
red "     b) 用逃生阀绕过：FLYWAY_ALLOW_CONFIG_EDIT=1 git commit ..."
red '     c) PR 描述中标注"Flyway 配置变更"，需要 reviewer 重点审查'
red "     d) 部署前必须跑 flyway validate 确认 DB 一致性"
red ""
red "逃生阀：FLYWAY_ALLOW_CONFIG_EDIT=1 可绕过本检查。"
exit 1
