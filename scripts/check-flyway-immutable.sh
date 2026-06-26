#!/usr/bin/env bash
# Input: staged Flyway migration files from git index
# Output: detects modifications/renames of V*.sql migrations that already exist on origin/main
# Pos: scripts/ — Flyway 已发布迁移不可变门禁（immutable published migration guard）
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Rule (hard block):
#   A V{version}.sql migration that already exists on origin/main MUST NOT be
#   modified (M) or renamed (R/C/T). Changing its bytes changes the Flyway
#   checksum; the production flyway_schema_history still records the old
#   checksum, so the next backend boot fails with "Migration checksum mismatch".
#
# 与 check-flyway-versions.sh 的职责划分（零重叠）：
#   - check-flyway-versions.sh      管"新增迁移版本号撞 origin/main"（A 状态 + 版本号冲突）
#   - check-flyway-immutable.sh     管"修改/重命名 origin/main 已存在的迁移"（M/R/C/T 状态）  ← 本脚本
#
# 工程背景（请勿删除）：
# 2026-06-26 部署 53bbf8a34 时事故（详见 docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md §13.5）
# 问题根因：commit 407587394 把已发布的 V1096__add_users_full_name_pinyin.sql 重命名为
#   V1096__add_users_employee_number_pinyin.sql 并改了内容。生产 flyway_schema_history 仍记录
#   旧 checksum（-1142450772），仓库文件新 checksum（-1670359032）。新 jar 启动时 Flyway
#   validateOnMigrate=true 检测到 9 个版本 checksum mismatch（V76/V100/V102/V1063/V1040/
#   V1081/V1088/V1092/V1096），拒绝启动，health check 4 分钟超时。
#   当晚靠手动 flyway repair 对齐 checksum 才恢复，耗时且高风险。
# 本脚本将此检查前移到 pre-commit，让"改已发布迁移"在提交时就被拦截。
#
# 逃生阀：FLYWAY_ALLOW_IMMUTABLE_EDIT=1 可绕过（仅在紧急修复时使用，配合既有 agent-lock 留痕）。
#   绕过后仍需在生产部署前手动执行 flyway repair，且应在 PR 描述里说明理由。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo '')"
if [ -z "$ROOT_DIR" ]; then
  echo "flyway-immutable: not in a git repo, skipping."
  exit 0
fi
cd "$ROOT_DIR"

MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"
script_name="$(basename "$0")"

red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }
green() { printf '\033[32m%s\033[0m\n' "$*" >&2; }
yellow(){ printf '\033[33m%s\033[0m\n' "$*" >&2; }

# 逃生阀
if [ "${FLYWAY_ALLOW_IMMUTABLE_EDIT:-0}" = "1" ]; then
  yellow "${script_name}: FLYWAY_ALLOW_IMMUTABLE_EDIT=1 set, skipping immutable-migration check."
  yellow "  ⚠ 改已发布迁移会导致 checksum mismatch，部署前必须手动 flyway repair。"
  exit 0
fi

# name-status 输出格式：
#   M<tab>path              修改
#   R100<tab>old<tab>new    重命名（R 后跟相似度）
#   C100<tab>old<tab>new    复制
#   T<tab>path              类型变更
# 只看 V*.sql（正向迁移），rollback U*.sql 不受此门禁约束。
# 注意：pathspec 用目录（不带 /V 后缀，否则 git 按"名为 V 的条目"字面匹配，匹配不到 V1095__*.sql），
# 再用 grep 过滤 V 开头的 .sql，与 check-flyway-rollback.sh 的写法保持一致。
STAGED_STATUS=$(git diff --cached --name-status --diff-filter=MRTC -- "${MIGRATION_DIR}/" | \
  grep -E "/V[0-9]+__.*\.sql$" || true)

if [ -z "$STAGED_STATUS" ]; then
  echo "${script_name}: no modified/renamed Flyway V migrations staged, skipping."
  exit 0
fi

# 拉 origin/main 的 V 版本号集合（用于判断"是否已发布"）
# 用 FETCH_HEAD 兜底；fetch 失败时降级为本地 refs/remotes/origin/main
# 注意：用 grep -oE 提取版本号（macOS BSD sed 不支持 \+，不能用 sed 's/.*V\([0-9]\+\).*/\1/'）
extract_version_number() {
  grep -oE "/V[0-9]+" | tr -d '/V' | sort -n -u
}
MAIN_VERSIONS=""
if git fetch origin main --quiet 2>/dev/null; then
  MAIN_VERSIONS=$(git ls-tree -r --name-only FETCH_HEAD -- "${MIGRATION_DIR}/" 2>/dev/null | extract_version_number || true)
fi
if [ -z "$MAIN_VERSIONS" ]; then
  MAIN_VERSIONS=$(git ls-tree -r --name-only origin/main -- "${MIGRATION_DIR}/" 2>/dev/null | extract_version_number || true)
fi

if [ -z "$MAIN_VERSIONS" ]; then
  echo "${script_name}: could not determine origin/main versions, skipping (offline?)."
  exit 0
fi

version_on_main() {
  local v="$1"
  # 在 MAIN_VERSIONS 里精确匹配版本号（行匹配）
  [ -n "$(printf '%s\n' "$MAIN_VERSIONS" | grep -Fx "$v")" ]
}

errors=0
checked=0

# 解析 name-status：第一个字段是状态字母（可能带数字如 R100），后续是路径
while IFS= read -r line; do
  [ -z "$line" ] && continue
  # 取状态字母首字符（R100 → R）和最后一个字段（新路径）
  status_char=$(printf '%s' "$line" | cut -c1)
  new_path=$(printf '%s' "$line" | awk '{print $NF}')
  base=$(basename "$new_path")
  version=$(printf '%s' "$base" | grep -oE "^V[0-9]+" | tr -d 'V')

  if [ -z "$version" ]; then
    continue
  fi

  checked=$((checked + 1))

  if version_on_main "$version"; then
    red "${script_name}: FAIL — ${base} 修改了已发布迁移 V${version}（origin/main 已存在）"
    red "  状态: ${status_char}（M=修改 R=重命名 C=复制 T=类型变更）"
    red "  改已发布迁移的字节会改变 Flyway checksum，但生产 flyway_schema_history 仍记录旧值，"
    red "  下次后端启动会报 'Migration checksum mismatch for version ${version}' 并拒绝启动。"
    errors=$((errors + 1))
  fi
done <<< "$STAGED_STATUS"

echo ""
echo "${script_name}: checked=${checked} errors=${errors}"

if [ "$errors" -gt 0 ]; then
  echo ""
  red "${script_name}: ${errors} 个已发布迁移被修改/重命名，禁止提交。"
  echo ""
  echo "  正确做法（按场景）："
  echo "    1. 需要新增列/索引/数据 → 新建下一个版本号的迁移文件"
  echo "       bash scripts/new-migration.sh  # 走自动编号流程（推荐）"
  echo "       或手动查最大版本号：git ls-tree -r --name-only origin/main -- ${MIGRATION_DIR}/ | grep -oE '/V[0-9]+' | tr -d '/V' | sort -n | tail -1"
  echo ""
  echo "    2. 必须撤销当前修改（恢复 origin/main 版本）："
  echo "       git checkout origin/main -- ${MIGRATION_DIR}/V<version>__*.sql"
  echo ""
  echo "    3. 极少数紧急修复（会破坏 checksum，需配套生产 flyway repair + PR 说明）："
  echo "       FLYWAY_ALLOW_IMMUTABLE_EDIT=1 git commit ..."
  echo "       部署前必须先跑 scripts/release/flyway-repair-runner.sh repair"
  echo "       详见 docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md §13.5"
  exit 1
fi

if [ "$checked" -gt 0 ]; then
  green "${script_name}: PASS — ${checked} 个修改/重命名的迁移均不在 origin/main（新增 OK）"
fi

exit 0
