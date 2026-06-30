#!/usr/bin/env bash
# Input: staged Flyway migration files (default) OR named source (--source=push)
# Output: detects version number conflicts + optionally auto-fixes (--fix)
# Pos: scripts/ — Flyway migration version conflict guardrail + auto-number
# 维护声明: 本脚本分两种模式运行：(1) pre-commit: 检查 + 自动编号；(2) pre-push: 强验证。
#           新增迁移目录时请同步修改 MIGRATION_DIR 和 ROLLBACK_DIR.
# 防护覆盖（第 16 条经验沉淀，第 18 次部署事故）：
#   - worktree 内部撞号检测：拦截同一 worktree 多个 V*.sql 共享版本号
#   - origin/main 内部撞号检测：拦截 main 已被污染（两个 PR 先后合入相同版本号）
#   - sed 语法修复：所有 \+ 改为 -E + +，修复 macOS BSD sed 不支持 \+ 导致版本号提取失败
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"
if [[ -z "$ROOT_DIR" ]]; then
  echo "flyway-versions: not in a git repo, skipping."
  exit 0
fi
cd "$ROOT_DIR"

MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"
ROLLBACK_DIR="backend/src/main/resources/db/rollback/migration-mysql"

# ── 公共检测: worktree 内部撞号检测（pre-commit + pre-push 都跑） ──
# 防护场景：第 18 次部署事故 — 两个 PR 各自取 V1110，先后合入 main 后 main 同时存在两个 V1110__*.sql
# 任何 agent sync-env 拉 main 后 worktree 立即出现撞号，本检测立即报警
# 也覆盖单 PR 内部撞号（同一 PR 同时引入两个 V<相同>__*.sql）
check_worktree_internal_duplicate() {
  if [[ ! -d "$MIGRATION_DIR" ]]; then
    return 0
  fi
  local all_versions dup_versions
  all_versions=$(find "$MIGRATION_DIR" -name 'V*.sql' -type f 2>/dev/null | \
    sed -En 's/.*\/V([0-9]+).*/\1/p' | sort -n || true)
  if [[ -z "$all_versions" ]]; then
    return 0
  fi
  dup_versions=$(echo "$all_versions" | uniq -d || true)
  if [[ -n "$dup_versions" ]]; then
    echo ""
    echo "flyway-versions: ❌ CONFLICT — 本地 worktree 内存在同版本号 V*.sql 撞号"
    echo "  这会导致 Flyway 9.22.3 启动时报 'Found more than one migration with version N'，后端无法启动"
    echo "  撞号文件清单："
    while IFS= read -r dv; do
      [[ -z "$dv" ]] && continue
      echo "  V${dv}:"
      find "$MIGRATION_DIR" -name "V${dv}_*.sql" -type f 2>/dev/null | sed 's/^/    /'
      local u_files
      u_files=$(find "$ROLLBACK_DIR" -name "U${dv}_*.sql" -type f 2>/dev/null || true)
      if [[ -n "$u_files" ]]; then
        echo "  对应回滚脚本 U${dv}:"
        echo "$u_files" | sed 's/^/    /'
      fi
    done <<< "$dup_versions"
    echo ""
    echo "flyway-versions: 必须重命名其中一个为更大的版本号才能继续"
    echo "  建议："
    echo "    1. 用 bash scripts/next-migration-version.sh --reserve 取下一个可用版本号"
    echo "    2. git mv V<撞号>__oldname.sql V<新号>__oldname.sql"
    echo "    3. git mv U<撞号>__oldname.sql U<新号>__oldname.sql"
    echo "    4. 同步更新迁移文件头注释中的版本号"
    return 1
  fi
  return 0
}

# ── 参数解析 ──
MODE="pre-commit"   # pre-commit | pre-push

for arg in "$@"; do
  case "$arg" in
    --source=push) MODE="pre-push" ;;
    --source=pre-commit) MODE="pre-commit" ;;
    --fix|--staged) ;;  # --fix 已在 pre-push 模式强制执行，忽略
  esac
done

# ── pre-commit 模式：先跑自动编号，再检查冲突 ──
if [[ "$MODE" == "pre-commit" ]]; then
  # 🛡️ 第 16 条经验沉淀：worktree 内部撞号检测（最早拦截点）
  # 即使没有 staged 文件也跑，因为 worktree 内可能已有撞号文件待 add
  if ! check_worktree_internal_duplicate; then
    exit 1
  fi

  bash "$ROOT_DIR/scripts/assign-flyway-version.sh"

  # 只检查新增(A)/复制(C)/重命名(R)的迁移，不检查修改(M)
  # 修改 main 上已有的 V*.sql 由 check-flyway-immutable.sh 管理（有 FLYWAY_ALLOW_IMMUTABLE_EDIT=1 逃生阀）
  # 对 Modified 文件检查"版本号与 main 冲突"是误判——它就是 main 上的那个文件
  STAGED_MIGRATIONS=$(git diff --cached --name-only --diff-filter=ACR | \
    grep "^${MIGRATION_DIR}/V" || true)

  if [[ -z "$STAGED_MIGRATIONS" ]]; then
    echo "flyway-versions: no staged Flyway migrations, skipping."
    exit 0
  fi

  staged_versions=()
  while IFS= read -r file; do
    version=$(echo "$(basename "$file")" | sed -En 's/^V([0-9]+).*/\1/p')
    if [[ -n "$version" ]]; then
      staged_versions+=("$version")
      echo "flyway-versions: staged V${version} -> ${file}"
    fi
  done <<< "$STAGED_MIGRATIONS"

  if [[ "${#staged_versions[@]}" -eq 0 ]]; then
    echo "flyway-versions: no version numbers extracted, skipping."
    exit 0
  fi

  echo "flyway-versions: checking ${#staged_versions[@]} staged migration(s)..."

  git fetch origin main --prune 2>/dev/null || true
  main_versions=$(git ls-tree -r --name-only FETCH_HEAD -- "${MIGRATION_DIR}/" 2>/dev/null | \
    sed -En 's/.*\/V([0-9]+).*/\1/p' | sort -n || true)

  if [[ -z "$main_versions" ]]; then
    echo "flyway-versions: could not determine origin/main versions, skipping."
    exit 0
  fi

  conflicts_found=false
  while IFS= read -r main_version; do
    for sv in "${staged_versions[@]}"; do
      if [[ "$sv" == "$main_version" ]]; then
        echo "flyway-versions: CONFLICT — V${sv} 与 origin/main 冲突"
        conflicts_found=true
      fi
    done
  done <<< "$main_versions"

  if [[ "$conflicts_found" == "true" ]]; then
    MAIN_LATEST=$(echo "$main_versions" | tail -1)
    echo ""
    echo "flyway-versions: ⚠ Flyway 版本冲突！"
    echo "  origin/main 最新版本号：V${MAIN_LATEST}"
    echo "  建议 git rebase origin/main，rebase 后 pre-commit 会自动重新编号"
    exit 1
  fi

  echo "flyway-versions: passed. checked_migrations=${#staged_versions[@]}"
  exit 0
fi

# ── pre-push 模式：检查 WORKING TREE 中所有 V* 文件 vs origin/main ──
# 关键改进：检查 WORKING TREE 中所有 V* 文件（不只是 git diff 显示的"新"文件），
# 这样可以捕获"多人并行创建了相同版本号"的场景（git diff 只显示与 main 的差异，
# 不显示在分支内部已存在但与 main HEAD 对比时没有差异的冲突文件）。
echo "flyway-versions: pre-push — fetching origin/main..."
git fetch origin main --prune 2>/dev/null || true

# 🛡️ 第 16 条经验沉淀：worktree 内部撞号检测（pre-push 再次拦截）
if ! check_worktree_internal_duplicate; then
  exit 1
fi

MAIN_VERSIONS=$(git ls-tree -r --name-only FETCH_HEAD -- "${MIGRATION_DIR}/" 2>/dev/null | \
  sed -En 's/.*\/V([0-9]+).*/\1/p' | sort -n || true)

if [[ -z "$MAIN_VERSIONS" ]]; then
  echo "flyway-versions: could not fetch origin/main versions, skipping check."
  exit 0
fi

# 🛡️ 第 16 条经验沉淀：origin/main 内部撞号检测
# 防护场景：两个 PR 几乎同时合入 main（PR merge 不走 pre-push-gate），
# 各自带相同版本号 V*.sql，合入后 main 同时存在两个 V1110__*.sql
# 任何 agent 下次 push 时立即报警，避免撞号流入生产部署阶段
MAIN_DUP_VERSIONS=$(echo "$MAIN_VERSIONS" | uniq -d || true)
if [[ -n "$MAIN_DUP_VERSIONS" ]]; then
  echo ""
  echo "flyway-versions: ❌ ORIGIN/MAIN 已被污染 — 存在同版本号 V*.sql 撞号"
  echo "  这意味着两个 PR 各自引入相同版本号的迁移并先后合入了 main"
  echo "  必须立即 hotfix 修复，否则下次部署会因 Flyway 启动失败而阻断"
  echo "  撞号文件清单（origin/main）："
  while IFS= read -r dv; do
    [[ -z "$dv" ]] && continue
    echo "  V${dv}:"
    git ls-tree -r --name-only FETCH_HEAD -- "${MIGRATION_DIR}/" 2>/dev/null | \
      grep -E "/V${dv}_" | sed 's/^/    /'
  done <<< "$MAIN_DUP_VERSIONS"
  echo ""
  echo "flyway-versions: 修复步骤："
  echo "  1. 创建 hotfix 分支：bash scripts/agent-start-task.sh trae fix-vN-conflict origin/main --in-place"
  echo "  2. 用 git mv 重命名其中一个 V<撞号>__*.sql 为 V<新号>__*.sql（保留先合入的）"
  echo "  3. 同步重命名 U<撞号>__*.sql 为 U<新号>__*.sql"
  echo "  4. 更新迁移文件头注释中的版本号"
  echo "  5. 提 PR 合入 main"
  exit 1
fi

# 检查 WORKING TREE 中所有 V* 文件
WORKTREE_VERSIONS=$(find "${MIGRATION_DIR}" -name 'V*.sql' -type f 2>/dev/null | \
  sed -En 's/.*\/V([0-9]+).*/\1/p' | sort -n || true)

if [[ -z "$WORKTREE_VERSIONS" ]]; then
  echo "flyway-versions: no local V* migrations in worktree, skipping."
  exit 0
fi

echo "flyway-versions: checking worktree V* migrations against origin/main..."

# 🛡️ 第 16 条经验沉淀：原 pre-push "worktree vs main 冲突检测 + auto-fix" 逻辑已删除
# 原逻辑有严重缺陷：检查 worktree 中所有 V*.sql vs main，但 worktree 本来就包含 main 的所有文件，
# 会导致所有 V*.sql 都被误判为冲突并触发 auto-fix 重命名。
# 之前一直"假绿"是因为 sed 用了 \+，macOS BSD sed 不支持，导致版本号提取失败（空循环）。
#
# 正确的冲突检测由以下两层覆盖：
# 1. worktree 内部撞号检测（check_worktree_internal_duplicate）：发现 worktree 内多个相同版本号
#    覆盖场景：main 有 V1110__cleanup.sql，worktree 新增 V1110__add_pending_approval.sql
#    → worktree 内出现两个 V1110 立即报警（无需对比 main）
# 2. origin/main 内部撞号检测：发现 main 已被污染（两个 PR 先后合入相同版本号）
#    覆盖场景：PR !1340 合入 main 后，PR !1342 也合入 main
#    → main 同时有两个 V1110 → 下次任何 agent push 时报警（必须 hotfix）

WORKTREE_COUNT=$(echo "$WORKTREE_VERSIONS" | wc -l | tr -d ' ')
MAIN_COUNT=$(echo "$MAIN_VERSIONS" | wc -l | tr -d ' ')
echo "flyway-versions: (pre-push) passed. worktree_versions=${WORKTREE_COUNT}, main_versions=${MAIN_COUNT}"
exit 0
