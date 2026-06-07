#!/usr/bin/env bash
# Input: none
# Output: stdout the next available Flyway migration version number, stderr for warnings
# Pos: scripts/ — migration version coordinator
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
# 用途: 从远程主分支和本地综合获取当前最大版本号，输出下一个可用版本。
#       防止多个 Agent 并行开发时取了相同版本号导致 Flyway 启动失败。
# 用法:
#   scripts/next-migration-version.sh             # 输出下一个可用版本号（如 1039）
#   scripts/next-migration-version.sh --check     # 检查本地是否有重复版本号
#   scripts/next-migration-version.sh --reserve   # 输出版本号并打印创建命令
#
# 维护声明:
#   - 必须 git fetch origin 以获取远程最新迁移文件
#   - 输出纯数字（无前导零），直接 pipe 给创建脚本用

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"
REMOTE_REF="origin/main"

cd "$ROOT_DIR"

MODE="${1:-}"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 从文件名列表提取版本号
extract_versions() {
  grep -oE 'V[0-9]+__' | sed 's/V//;s/__//'
}

# 检查重复
check_duplicates() {
  local duplicates
  duplicates=$(find "$MIGRATION_DIR" -name 'V*.sql' 2>/dev/null \
    | extract_versions \
    | sort -n \
    | uniq -d)

  if [[ -n "$duplicates" ]]; then
    echo -e "${RED}✗ 发现重复的 Flyway 版本号：${NC}" >&2
    for ver in $duplicates; do
      echo "  版本 V${ver} 的冲突文件：" >&2
      find "$MIGRATION_DIR" -name "V${ver}__*.sql" 2>/dev/null | sed 's/^/    - /' >&2
    done
    return 1
  fi
  return 0
}

# 获取最大版本（本地 + 远程）
get_max_version() {
  local local_max=0
  local remote_max=0

  # 本地最大版本
  if compgen -G "$MIGRATION_DIR/V*.sql" >/dev/null 2>&1; then
    local_max=$(find "$MIGRATION_DIR" -name 'V*.sql' \
      | extract_versions \
      | sort -n \
      | tail -1)
  fi

  # 远程最大版本（git fetch 后）
  if git cat-file -e "$REMOTE_REF:$MIGRATION_DIR" 2>/dev/null; then
    remote_max=$(git ls-tree -r --name-only "$REMOTE_REF" "$MIGRATION_DIR" 2>/dev/null \
      | extract_versions \
      | sort -n \
      | tail -1)
  fi

  # 取两者中的最大值 + 1
  local max=$local_max
  if [[ "$remote_max" -gt "$max" ]]; then
    max=$remote_max
  fi

  echo "$((max + 1))"
}

case "$MODE" in
  --check)
    echo "=== Flyway 版本冲突检测 ===" >&2
    if check_duplicates; then
      echo -e "${GREEN}✓ 无重复版本号${NC}" >&2
    fi
    ;;

  --reserve)
    # 预检冲突
    check_duplicates || true
    echo "" >&2
    echo "=== 预约新版本号 ===" >&2

    # 先 fetch 远程确保版本最新
    echo "  Fetching $REMOTE_REF ..." >&2
    git fetch origin main --prune 2>&1 | sed 's/^/  /' >&2

    next_ver=$(get_max_version)
    echo "" >&2
    echo -e "${GREEN}下一个可用版本: V${next_ver}__${NC}" >&2
    echo "" >&2
    echo "创建新迁移文件的命令：" >&2
    echo "  touch $MIGRATION_DIR/V${next_ver}__your_description.sql" >&2
    echo "  touch backend/src/main/resources/db/rollback/migration-mysql/U${next_ver}__your_description.sql" >&2
    echo "$next_ver"
    ;;

  *)
    # 默认：仅输出版本号（可 pipe）
    check_duplicates 2>/dev/null || true
    next_ver=$(get_max_version)
    echo "$next_ver"
    ;;
esac
