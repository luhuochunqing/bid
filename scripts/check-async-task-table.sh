#!/usr/bin/env bash
# Input: staged (pre-commit) or commit-range (pre-push) Flyway/Java files
# Output: fail if a new *_export_task / *_import_task table appears
# Pos: scripts/ — 禁止再按模块复制异步导入/导出任务表
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 工程背景（lessons-learned §139）：
#   基线已有通用表 export_tasks，但 /api/export 走同步临时文件、不写该表。
#   后来仓库/业绩/人员/CA/平台账号/标讯各自复制了一张同构任务表。
#   本门禁拦住第 7 张，逼后来者接 export_tasks + AbstractExportTaskStateService。
#
# 逃生阀：ASYNC_TASK_TABLE_ALLOW_NEW=1（PR 描述必须写明为何不能用通用表）
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo '')"
if [ -z "$ROOT_DIR" ]; then
  echo "async-task-table: not in a git repo, skipping."
  exit 0
fi
cd "$ROOT_DIR"

script_name="$(basename "$0")"
red()    { printf '\033[31m%s\033[0m\n' "$*" >&2; }
yellow() { printf '\033[33m%s\033[0m\n' "$*" >&2; }

# 逃生阀：ASYNC_TASK_TABLE_ALLOW_NEW=1
if [ "${ASYNC_TASK_TABLE_ALLOW_NEW:-0}" = "1" ]; then
  yellow "${script_name}: ASYNC_TASK_TABLE_ALLOW_NEW=1 set, skipping."
  exit 0
fi

# 已存在的模块专表 + 通用表。新增名称一律拦截。
ALLOWED='
export_tasks
warehouse_export_task
warehouse_import_task
personnel_import_task
platform_account_import_task
ca_certificate_import_task
tender_import_task
performance_export_task
'

if [ "${1:-}" = "--push" ]; then
  BASE="${GATE_BASE:-origin/main}"
  FILES=$(git diff --name-only --diff-filter=AM "$BASE"..HEAD -- \
    backend/src/main/resources/db/migration-mysql \
    backend/src/main/java 2>/dev/null || true)
  cat_file() { git show "HEAD:$1" 2>/dev/null || true; }
else
  FILES=$(git diff --cached --name-only --diff-filter=AM -- \
    backend/src/main/resources/db/migration-mysql \
    backend/src/main/java 2>/dev/null || true)
  cat_file() { git show ":$1" 2>/dev/null || true; }
fi

if [ -z "${FILES:-}" ]; then
  echo "${script_name}: no staged/ranged SQL or Java, skipping."
  exit 0
fi

offenders=""
while IFS= read -r path; do
  [ -z "$path" ] && continue
  content=$(cat_file "$path")
  [ -z "$content" ] && continue
  case "$path" in
    *.sql)
      found=$(printf '%s\n' "$content" | tr 'A-Z' 'a-z' | \
        grep -oE 'create[ \t]+table([ \t]+if[ \t]+not[ \t]+exists)?[ \t]+`?[a-z0-9_]+_(export|import)_task`?' || true)
      ;;
    *.java)
      found=$(printf '%s\n' "$content" | tr 'A-Z' 'a-z' | \
        grep -oE '@table\([^)]*name[ \t]*=[ \t]*["'\''][a-z0-9_]+_(export|import)_task["'\'']' || true)
      ;;
    *)
      continue
      ;;
  esac
  while IFS= read -r hit; do
    [ -z "$hit" ] && continue
    name=$(printf '%s' "$hit" | grep -oE '[a-z0-9_]+_(export|import)_task' | head -1)
    [ -z "$name" ] && continue
    if printf '%s' "$ALLOWED" | grep -qx "$name"; then
      continue
    fi
    offenders="${offenders}${path}: ${name}"$'\n'
  done <<< "$found"
done <<< "$FILES"

if [ -z "$offenders" ]; then
  echo "${script_name}: no new *_export_task / *_import_task tables."
  exit 0
fi

red "❌ ${script_name}: 禁止再新建模块专属异步导入/导出任务表"
red ""
red "检测到："
printf '%s' "$offenders" | while IFS= read -r line; do
  [ -n "$line" ] && red "  $line"
done
red ""
red "原因：基线已有 export_tasks（ExportTask.java），状态机已有 AbstractExportTaskStateService。"
red "仓库/业绩/CA/平台账号/人员/标讯已经各复制过一套同构表（lessons-learned §139）。"
red ""
red "正确做法："
red "  导出 → 写入 export_tasks（按 data_type 区分），不要 CREATE TABLE xxx_export_task"
red "  导入 → 同样扩通用表或复用已有 import 表 + type 列，不要再抄 V1084/V1085"
red ""
red "逃生阀：ASYNC_TASK_TABLE_ALLOW_NEW=1（PR 必须写明为何不能用 export_tasks）"
exit 1
