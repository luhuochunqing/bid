#!/usr/bin/env bash
# Input: git status, staged diff
# Output: colored pass/fail report to stdout, exit 0 or 1
# Pos: scripts/ - pre-push quality gate
# 维护声明: 若门禁检查项或依赖路径变化，同步更新本脚本。

# pre-push-gate.sh — 推送前质量门禁
# 用法: bash scripts/pre-push-gate.sh [--skip-tests] [--skip-e2e-check]
# 环境变量:
#   PRE_PUSH_GATE=0   完全跳过门禁（不运行任何检查）
#   CI_MODE=true       自动化模式（不依赖交互式前端构建）
#   WIKI_CHECK_MODE=warning|error  Wiki 健康检查模式（默认：2 周过渡期 warning，之后 error）
#   WIKI_CHECK_SKIP=1  完全跳过 Wiki 健康检查（仅限紧急修复）
# 退出码: 0 = 通过, 1 = 拦截

set -euo pipefail

# ── 参数解析 ──────────────────────────────────────────────────
SKIP_TESTS=false
EXPLICIT_SKIP_TESTS=false
SKIP_E2E_CHECK=false

for arg in "$@"; do
  case "$arg" in
    --skip-tests)      SKIP_TESTS=true; EXPLICIT_SKIP_TESTS=true ;;
    --skip-e2e-check)   SKIP_E2E_CHECK=true ;;
    --help|-h)
      echo "用法: $0 [--skip-tests] [--skip-e2e-check]"
      echo "  --skip-tests      显式跳过前端测试/构建及新增根因/token 回归"
      echo "  --skip-e2e-check  跳过 E2E-UI 联动检查"
      echo "  PRE_PUSH_GATE=0    完全跳过门禁（等同于 --skip-tests --skip-e2e-check）"
      exit 0
      ;;
  esac
done

# agent/* 分支在本地 pre-push 时跳过前端测试和 E2E-UI 联动检查。
# 完整前端测试/build 留给 PR / CI 门禁执行，避免本地 push 因前端测试
# 进程挂起或超时阻塞。核心架构、Flyway、锁、行预算等门禁仍保留。
branch="$(git branch --show-current 2>/dev/null || true)"
if [[ "$branch" == agent/* ]]; then
  SKIP_TESTS=true
  SKIP_E2E_CHECK=true
fi

# ── 完全绕过 ────────────────────────────────────────────────
if [ "${PRE_PUSH_GATE:-1}" = "0" ]; then
  echo "⚠  PRE_PUSH_GATE=0 — 跳过全部推送前门禁检查"
  exit 0
fi

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# 推送前变更检测基准：merge-base(origin/main, HEAD)，等价 origin/main...HEAD 的三 dot 语义。
# pre-push 时 git diff --cached 为空（改动已在 commit），所有变更检测必须基于 commit range。
GATE_BASE=$(git merge-base origin/main HEAD 2>/dev/null || echo origin/main)

# 后端变更检测：无 backend/ 改动则跳过 3 个后端 mvn 架构测试（省 ~7 min JVM 启动+编译）。
BACKEND_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE '^backend/' || true)
# 前端源码变更检测：有 src/ 改动时，哪怕是 agent/* 分支也必须执行前端编译构建校验，杜绝模板语法与编译器宏错误。
FRONTEND_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE '^src/' || true)

# 合并 4 个后端架构测试为一次 mvn 调用（省 3 次 JVM 启动 + 重复编译），逐项结果从 surefire XML 读取以保留精度。
# EntityTableMigrationCoverageTest（2026-07-04 CO-483/484 P0 事故 P2 守卫）：
#   扫描所有 @Table 实体，验证有对应 CREATE TABLE 迁移且未误放 db/migration/。
#   与 §3.7 互补：§3.7 拦 commit 范围内新增 V/B 文件误放，本测试全量扫描 @Table 实体。
_BACKEND_MVN_RAN=""
backend_mvn_run() {
  if [ -z "$_BACKEND_MVN_RAN" ]; then
    cd "$ROOT_DIR/backend"
    # || true 是必要的：set -euo pipefail 下 mvn 失败会直接退出脚本，
    # || true 让脚本继续到 surefire_failed() 检查（无 XML 报告时返回"失败"）。
    # §0.6 已在前面独立检查 mvn compile，编译失败会给出准确的错误信息。
    mvn test -Dtest='ArchitectureTest,FlywayRollbackScriptCoverageTest,EntityTableMigrationCoverageTest,ResponsibilityArchitectureTest,PreAuthorizeAuthorityOssMappingCoverageTest' -q >/dev/null 2>&1 || true
    cd "$ROOT_DIR"
    _BACKEND_MVN_RAN=1
  fi
}
surefire_failed() {
  local class="$1"
  local xml
  # Surefire 报告文件名包含完整包路径，需兼容 com.xiyu.bid.support.FlywayRollbackScriptCoverageTest 等子包类。
  xml=$(find "$ROOT_DIR/backend/target/surefire-reports" -maxdepth 1 -name "TEST-*.${class}.xml" -print -quit 2>/dev/null)
  if [ -z "$xml" ] || [ ! -f "$xml" ]; then
    return 0  # 无报告 → 保守视为失败
  fi
  grep -qE '<testsuite[^>]*((failures|errors)="[1-9])' "$xml"
}

# FBR-I1 真实 DOCX→Sidecar 回放所需的 Python（import docx, lxml），候选顺序与
# CrossFormatDocumentSourceReplayTest/根因脚本一致：env SIDECAR_PYTHON → sidecar .venv → python3。
# 命中时 export SIDECAR_PYTHON，供随后 mvn JVM 继承。
sidecar_python_usable() {
  local cand
  for cand in "${SIDECAR_PYTHON:-}" "$ROOT_DIR/document-converter-sidecar/.venv/bin/python" python3; do
    [ -n "$cand" ] || continue
    command -v "$cand" >/dev/null 2>&1 || continue
    if (cd "$ROOT_DIR/document-converter-sidecar" && "$cand" -B -c 'import docx, lxml' >/dev/null 2>&1); then
      export SIDECAR_PYTHON="$cand"
      return 0
    fi
  done
  return 1
}

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0
SKIPD=0

pass() { echo -e "${GREEN}✓${NC} $1"; PASS=$((PASS + 1)); }
fail() { echo -e "${RED}✗${NC} $1"; FAIL=$((FAIL + 1)); }
skip() { echo -e "${YELLOW}⊘${NC} $1 (skipped)"; SKIPD=$((SKIPD + 1)); }

echo "=== 推送前门禁 $(date '+%H:%M:%S') ==="
echo ""

# ── 0. 持久 Worktree 完整性检查 ─────────────────────────
echo "── 持久 Worktree 完整性 ──"
if bash "$ROOT_DIR/scripts/check-worktree-protection.sh" 2>/dev/null; then
  pass "持久 Worktree 完整且共享 gitdir"
else
  fail "持久 Worktree 缺失或 gitdir 分叉 — 请先修复再推送!"
fi

# ── 0.5 Git 合并冲突标记扫描 ─────────────────────────────
# 工程背景（2026-07-11 PR !2012 事故）：
#   commit 92f241ac8 (PR !2010) 直接把 stash apply 产生的冲突标记提交到 main，
#   导致 main 编译失败，阻断第 77 次部署。pre-push-gate 当时没有编译检查，未拦截。
# 扫描 commit 范围内所有文件，检测残留的冲突标记。
echo "── Git 冲突标记扫描 ──"
if [ "${BACKEND_CHANGED:-0}" -eq 0 ] && ! git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -qE '.'; then
  skip "冲突标记扫描（无文件变更）"
else
  CONFLICT_HITS=$(git diff "$GATE_BASE"..HEAD 2>/dev/null | grep -cE '^\+(<<<<<<< |>>>>>>> |=======$)' || true)
  if [ "$CONFLICT_HITS" -gt 0 ]; then
    fail "Git 冲突标记残留 — 检测到 $CONFLICT_HITS 处冲突标记（<<<<<<< / ======= / >>>>>>>）。运行 git status 检查未解决的合并冲突。"
  else
    pass "Git 冲突标记扫描"
  fi
fi

# ── 0.6 后端编译检查 ─────────────────────────────────────
# 工程背景（2026-07-11 PR !2012 事故）：
#   pre-push-gate.sh 原本只有 mvn test（带 || true），编译失败被静默吞掉，
#   surefire 无 XML 报告时 surefire_failed() 返回 0（视为失败）但 fail 不可靠。
#   新增独立 mvn compile 检查，编译失败立即阻断，不依赖 surefire 报告。
echo "── 后端编译 ──"
if [ ! -d "$ROOT_DIR/backend" ]; then
  skip "非 Java 项目"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "mvn compile（无 backend/ 变更）"
else
  if (cd "$ROOT_DIR/backend" && mvn compile -q 2>&1); then
    pass "mvn compile"
  else
    fail "mvn compile — 后端编译失败。检查语法错误、冲突标记、缺失依赖。"
  fi
fi

# ── 1. 架构检查 ──────────────────────────────────────────
echo "── 架构合规 ──"
if [ ! -d "$ROOT_DIR/backend" ]; then
  skip "非 Java 项目"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "ArchitectureTest（无 backend/ 变更）"
else
  backend_mvn_run
  if surefire_failed ArchitectureTest; then
    fail "ArchitectureTest — Controller 可能直接依赖了 Repository 或 Entity"
  else
    pass "ArchitectureTest"
  fi
fi

# ── 2. 回滚脚本覆盖 ──────────────────────────────────────
echo "── 回滚脚本覆盖 ──"
if [ ! -d "$ROOT_DIR/backend" ]; then
  skip "非 Java 项目"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "FlywayRollbackScriptCoverageTest（无 backend/ 变更）"
else
  backend_mvn_run
  if surefire_failed FlywayRollbackScriptCoverageTest; then
    fail "FlywayRollbackScriptCoverageTest — 新迁移缺回滚脚本或 source header"
  else
    pass "FlywayRollbackScriptCoverageTest"
  fi
fi

# ── 2.4 @PreAuthorize 权限键 OSS 映射覆盖（CO-560 防复发守卫，2026-08-27 补入） ──
# 工程背景：ce2b4b939（specs/055 competitor）注册权限键但漏同步 application.yml 映射源，
#   main 基线带伤：PreAuthorizeAuthorityOssMappingCoverageTest 在 main 上持续红。
#   OSS 用户访问缺映射权限键的接口会一律 403。
echo "── @PreAuthorize 权限键 OSS 映射覆盖 ──"
if [ ! -d "$ROOT_DIR/backend" ]; then
  skip "非 Java 项目"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "PreAuthorizeAuthorityOssMappingCoverageTest（无 backend/ 变更）"
else
  backend_mvn_run
  if surefire_failed PreAuthorizeAuthorityOssMappingCoverageTest; then
    fail "PreAuthorizeAuthorityOssMappingCoverageTest — 新增 @PreAuthorize(hasAuthority) 权限键未在 application.yml menu-code-to-permission-key-mappings 配置来源（OSS 用户将 403）"
  else
    pass "PreAuthorizeAuthorityOssMappingCoverageTest"
  fi
fi

# ── 2.5 @Table 实体建表迁移覆盖（CO-483/484 P0 事故 P2 守卫） ──
# 工程背景（2026-07-04 第 40 次部署 P0 事故）：
#   BidReviewAssignmentEntity 标了 @Table(name="bid_review_assignment")，
#   但建表迁移 V123 被误放在 db/migration/（Flyway 不读），表从未创建，运行时 500。
# 与 §3.7 互补：§3.7 拦 commit 范围内新增 V/B 文件误放目录；本测试全量扫描所有 @Table 实体。
echo "── @Table 实体建表迁移覆盖 ──"
if [ ! -d "$ROOT_DIR/backend" ]; then
  skip "非 Java 项目"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "EntityTableMigrationCoverageTest（无 backend/ 变更）"
else
  backend_mvn_run
  if surefire_failed EntityTableMigrationCoverageTest; then
    fail "EntityTableMigrationCoverageTest — @Table 实体缺 CREATE TABLE 迁移，或迁移误放 db/migration/。修复：在 db/migration-mysql/ 创建 V<version>__create_<table>.sql，或将误放文件 git mv 到 migration-mysql/"
  else
    pass "EntityTableMigrationCoverageTest"
  fi
fi

# ── 2.6. FBR-I1 真实 DOCX→Sidecar 全规则绑定回放 ────────
# 2026-09-29 教训（lesson 418）：FullRuleSourceReplayTest/CrossFormatDocumentSourceReplayTest
# 从未进入 pre-push 列表，纯靠人肉全量跑；无 sidecar Python 依赖时又产生 8F+19E 环境污染假红，
# 误导 1382 排查方向。现纳入门禁：有可用 Python 就实跑（真实守卫），没有则显式黄灯 skip
# （不是静默放行——skip 消息给出修复命令，开发宿主必须实跑）。
echo "── 真实 Sidecar 全规则绑定回放 ──"
if [ ! -d "$ROOT_DIR/backend" ]; then
  skip "FBR-I1 回放（非 Java 项目）"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "FBR-I1 回放（无 backend/ 变更）"
elif ! sidecar_python_usable; then
  skip "FBR-I1 回放（sidecar Python 缺 python-docx/lxml — 本机未实跑；安装: python3 -m venv document-converter-sidecar/.venv && document-converter-sidecar/.venv/bin/pip install -r document-converter-sidecar/requirements.txt）"
else
  # 评审 P2（2026-10-02）：旧写法 `|| true` 吞掉 Maven 失败，残留的上一次绿色 Surefire
  # 报告会让本段误报 PASS。修复：先删除这两个类的旧报告，再检查 Maven 真实退出码，
  # 最后才读本次报告（缺报告仍由 surefire_failed 保守判失败）。
  find "$ROOT_DIR/backend/target/surefire-reports" -maxdepth 1 \
    \( -name 'TEST-*.FullRuleSourceReplayTest.xml' -o -name 'TEST-*.CrossFormatDocumentSourceReplayTest.xml' \
       -o -name '*FullRuleSourceReplayTest.txt' -o -name '*CrossFormatDocumentSourceReplayTest.txt' \) \
    -delete 2>/dev/null || true
  replay_rc=0
  ( cd "$ROOT_DIR/backend" && mvn test -Dtest='FullRuleSourceReplayTest,CrossFormatDocumentSourceReplayTest' -q >/dev/null 2>&1 ) || replay_rc=$?
  if [ "$replay_rc" -ne 0 ]; then
    fail "FullRule/CrossFormat 回放 Maven 执行失败（exit=${replay_rc}，含编译/依赖错误，勿只看报告）— 运行 mvn test -Dtest='FullRuleSourceReplayTest,CrossFormatDocumentSourceReplayTest' 查看"
  elif surefire_failed FullRuleSourceReplayTest || surefire_failed CrossFormatDocumentSourceReplayTest; then
    fail "FullRule/CrossFormat 真实 DOCX 绑定回放红 — 评分项规则原文恢复（SOURCE vs MODEL_CLAIM）或 Sidecar 转换形状回归，运行 mvn test -Dtest='FullRuleSourceReplayTest,CrossFormatDocumentSourceReplayTest' 查看"
  else
    pass "FullRule/CrossFormat 真实 DOCX 绑定回放"
  fi
fi

# ── 3. Flyway 版本号冲突检查 ─────────────────────────────
# pre-push 模式下 check-flyway-versions.sh 会强制 auto-fix（无用户选择）
echo "── Flyway 版本号 ──"
if bash "$ROOT_DIR/scripts/check-flyway-versions.sh" --source=push --fix 2>/dev/null; then
  pass "Flyway 迁移版本号无冲突"
else
  fail "Flyway 版本冲突已自动修复，请执行：
    git add backend/src/main/resources/db/migration-mysql/ backend/src/main/resources/db/rollback/migration-mysql/
    git commit --amend --no-edit
    git push ..."
fi

# ── 3.6. Flyway DB vs 源码一致性检查（可选，需 DB 凭据）───
# 工程背景（2026-06-26 第5/6次部署事故）：
#   V1100 源码缺失 + V1039 failed migration 都是因为历史手动操作未同步源码，
#   部署时才发现。本检查在 pre-push 阶段提前发现"DB 已执行但源码缺失"。
# 可选原因：pre-push 阶段无法保证有 DB 凭据（CI 环境才有）。
# 行为：
#   - DB 可用 + 发现不一致 → fail（阻断推送）
#   - DB 不可用 → skip（不阻塞）
#   - 仅当 backend/ 有变更时执行（无 backend 变更时跳过）
# 环境变量：
#   FLYWAY_CHECK_ENV=dev|prod（默认 dev，使用 .env.mysql）
#   FLYWAY_DB_SYNC_SKIP=1 强制跳过
echo "── Flyway DB 同步 ──"
if [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "Flyway DB 同步检查（无 backend/ 变更）"
elif [ "${FLYWAY_DB_SYNC_SKIP:-0}" = "1" ]; then
  skip "Flyway DB 同步检查（FLYWAY_DB_SYNC_SKIP=1）"
elif [ ! -f "$ROOT_DIR/.env.mysql" ] && [ ! -f "/etc/xiyu-bid/backend.env" ]; then
  skip "Flyway DB 同步检查（无 DB 凭据文件）"
else
  # 使用 fail 模式：DB 已执行但源码缺失则阻断推送
  if FLYWAY_CHECK_FAIL=1 bash "$ROOT_DIR/scripts/check-flyway-db-source-sync.sh" 2>&1 | tail -20; then
    pass "Flyway DB 同步检查通过"
  else
    fail "Flyway DB 同步检查失败 — DB 已执行的迁移在源码中缺失。修复：补全对应迁移文件。参考: docs/release/deploy-report-2026-06-26-5th.md（V1100 案例）"
  fi
fi



# ── 3.5 Schema 语义冲突检测 ───────────────────────────
echo "── Schema 冲突 ──"
if bash "$ROOT_DIR/scripts/check-schema-conflicts.sh" 2>/dev/null; then
  pass "Schema 语义无冲突"
else
  skip "Schema 冲突检测异常（不影响推送）"
fi

# ── 3.7 Flyway 迁移目录守卫（commit 范围） ───────────────
# 工程背景（2026-07-04 第 40 次部署 P0 事故）：
#   CO-483/484 PR !1637 在 kimi worktree 把 V123__add_bid_review_assignment.sql
#   放在 db/migration/（历史目录，Flyway 不读），kimi worktree 未装 pre-commit hook，
#   .githooks/pre-commit 中的 check-flyway-migration-dir.sh 没机会拦截。
#   导致 bid_review_assignment 表从未创建，/api/projects/{id}/stage 500。
# 修复策略：在 pre-push-gate.sh 中也调用同一守卫，扫描 commit 范围（不仅是 staged）
#   中的 V*.sql/B*.sql 是否误放在 db/migration/。
# pre-push 通过 scripts/git 包装器在所有 worktree 都生效（不依赖 install-githooks.sh）。
# 逃生阀：FLYWAY_ALLOW_LEGACY_DIR=1（仅限已记录豁免场景，需在 PR 描述说明理由）
echo "── Flyway 迁移目录守卫 ──"
LEGACY_DIR="$ROOT_DIR/backend/src/main/resources/db/migration"
if [ "${FLYWAY_ALLOW_LEGACY_DIR:-0}" = "1" ]; then
  skip "Flyway 迁移目录守卫（FLYWAY_ALLOW_LEGACY_DIR=1 逃生阀）"
elif [ ! -d "$LEGACY_DIR" ]; then
  skip "Flyway 迁移目录守卫（无 legacy 目录）"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "Flyway 迁移目录守卫（无 backend/ 变更）"
else
  # 扫描 commit 范围内被新增/修改/重命名的 V*.sql / B*.sql 在 legacy 目录
  LEGACY_OFFENDERS=$(git diff --name-status --diff-filter=AMRCT "$GATE_BASE"..HEAD -- "${LEGACY_DIR#$ROOT_DIR/}/" 2>/dev/null | \
    grep -E "/[VB][0-9]+__.*\.sql$" || true)
  if [ -n "$LEGACY_OFFENDERS" ]; then
    fail "Flyway 迁移目录守卫 — 检测到 V/B 迁移放在历史目录 db/migration/"
    echo ""
    echo "  以下 commit 范围内的文件误放在 db/migration/（Flyway 不读取此目录）："
    printf '%s\n' "$LEGACY_OFFENDERS" | while IFS=$'\t' read -r status path; do
      echo "    ${status}  ${path}"
    done
    echo ""
    echo "  修复：git mv <path> backend/src/main/resources/db/migration-mysql/<new-name>"
    echo "  版本号需用 scripts/next-migration-version.sh 重新分配"
    echo "  逃生阀：FLYWAY_ALLOW_LEGACY_DIR=1 bash scripts/pre-push-gate.sh"
  else
    pass "Flyway 迁移目录守卫（commit 范围无 V/B 文件放在 db/migration/）"
  fi
fi
echo "── 异步任务表复制拦截 ──"
if [ "${ASYNC_TASK_TABLE_ALLOW_NEW:-0}" = "1" ]; then
  skip "异步任务表复制拦截（ASYNC_TASK_TABLE_ALLOW_NEW=1 逃生阀）"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "异步任务表复制拦截（无 backend/ 变更）"
elif GATE_BASE="${GATE_BASE:-origin/main}" bash "$ROOT_DIR/scripts/check-async-task-table.sh" --push; then
  pass "异步任务表复制拦截"
else
  fail "异步任务表复制拦截 — 检测到新建 *_export_task / *_import_task。接 export_tasks + AbstractExportTaskStateService（lessons §139）。逃生阀 ASYNC_TASK_TABLE_ALLOW_NEW=1"
fi
# ── 3.9. 自定义表单 schema 覆盖式迁移拦截（教训 #61 防复发） ──
# 工程背景（2026-07-16 用户反馈）：
#   V1007/V1120/V1166/V1167 都是无条件 UPDATE form_definition_registry SET schema_json，
#   覆盖用户在 /settings/workflow-forms 配置的显隐/必填/排序/标签。
#   教训 #61：schema 迁移必须 merge，不能覆盖。
# 本门禁拦截新增的覆盖式 schema 迁移，要求加 -- SAFE-SCHEMA-MERGE: <理由> 注释豁免。
# 与 check-flyway-immutable.sh 互补：
#   - 修改已发布迁移 → check-flyway-immutable.sh 拦截（Flyway checksum 不可变）
#   - 新增迁移用覆盖式 UPDATE → 本门禁拦截
# 逃生阀：FORM_SCHEMA_MIGRATION_SKIP=1（仅限已记录豁免场景，需在 PR 描述说明理由）
echo "── 自定义表单 schema 覆盖拦截 ──"
if [ ! -d "$ROOT_DIR/backend/src/main/resources/db/migration-mysql" ]; then
  skip "自定义表单 schema 覆盖拦截（无迁移目录）"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "自定义表单 schema 覆盖拦截（无 backend/ 变更）"
elif [ "${FORM_SCHEMA_MIGRATION_SKIP:-0}" = "1" ]; then
  skip "自定义表单 schema 覆盖拦截（FORM_SCHEMA_MIGRATION_SKIP=1 逃生阀）"
else
  if bash "$ROOT_DIR/scripts/check-form-schema-migration.sh" --range "$GATE_BASE" 2>&1; then
    pass "自定义表单 schema 覆盖拦截"
  else
    fail "自定义表单 schema 覆盖拦截 — 新增迁移中 UPDATE form_definition_registry SET schema_json 无 SAFE 豁免。改为 merge 模式或加 -- SAFE-SCHEMA-MERGE: <理由>。详见 docs/lessons/lessons-learned.md #61。逃生阀：FORM_SCHEMA_MIGRATION_SKIP=1"
  fi
fi

# ── 3.8. hasAnyRole 双轨制拦截 (spec-024/033) ───────────────
echo "── hasAnyRole 双轨制拦截 ──"
if [ "${BACKEND_CHANGED:-0}" -eq 1 ]; then
  OFFENDERS=$(git diff --unified=0 "$GATE_BASE"..HEAD -- backend/src/main/java | grep '^+.*@PreAuthorize("hasAnyRole(' | grep -v 'SAFE:' || true)
  if [ -n "$OFFENDERS" ]; then
    fail "发现新增了单纯的 hasAnyRole 权限守卫，违反 spec-024/033 权限双轨制治理规则。
    请改用 hasAnyAuthority('permissionKey', 'ROLE_xxx') 以同时兼容前端菜单权限与后端角色，或在行尾加 // SAFE: 豁免。
    违规行：
    $OFFENDERS"
  else
    pass "无违规的 hasAnyRole 新增"
  fi
else
  skip "hasAnyRole 拦截（无 backend/ 变更）"
fi

# ── 4. 锁孤儿检查 ───────────────────────────────────────
echo "── 锁孤儿检查 ──"
if [ -f "$ROOT_DIR/package.json" ]; then
  if grep -q 'agent:lock-check:changed' "$ROOT_DIR/package.json" 2>/dev/null; then
    if npm run agent:lock-check:changed --silent 2>/dev/null; then
      pass "agent-lock 无孤儿锁"
    else
      skip "可能残留非 hot-path 锁"
    fi
  fi
fi

# ── 5. 行预算 ───────────────────────────────────────────
echo "── 行预算 ──"
if [ -f "$ROOT_DIR/package.json" ]; then
  if node "$ROOT_DIR/scripts/check-line-budgets.mjs" --base "$GATE_BASE" 2>/dev/null; then
    pass "line-budget"
  else
    fail "line-budget — 新建文件超过 300 行限制"
  fi
fi

# ── 5.4 重复实现检查（防造轮子）─────────────────────────
# 逃生阀：DUPLICATE_IMPL_SKIP=1
echo "── 重复实现（防造轮子）──"
DUP_CODE_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE '^(src/|backend/src/main/java/)' || true)
if [ "${DUPLICATE_IMPL_SKIP:-0}" = "1" ]; then
  skip "重复实现检查（DUPLICATE_IMPL_SKIP=1 逃生阀）"
elif [ "${DUP_CODE_CHANGED:-0}" -eq 0 ]; then
  skip "重复实现检查（无 src/ 或 backend Java 变更）"
else
  if node "$ROOT_DIR/scripts/check-duplicate-implementations.mjs" --base "$GATE_BASE" --head HEAD; then
    pass "重复实现检查"
  else
    fail "重复实现检查 — 新增代码与仓库已有实现重复，应复用现有代码。豁免：声明上一行加 // REUSE-OK: <path> — <原因>。逃生阀：DUPLICATE_IMPL_SKIP=1"
  fi
fi

# ── 5.5. 429 友好提示覆盖拦截（spec-034 防复发） ─────────
# 工程背景（2026-07-11 部署后反馈）：
#   全局 axios interceptor 已将 HTTP 429 包装为友好提示“请求过于频繁，请稍后再试”，
#   但业务层 catch 块中直接 ElMessage.error 会覆盖该提示，用户仍看到原始 AxiosError。
#   Account.vue / CAManagement.vue 等页面因此继续暴露 raw 429。
# 本门禁在 pre-push 阶段拦截新增的业务层 API catch 块中直接调用 ElMessage.error。
# 已存在的 71 处历史债务不在本次拦截范围，仅阻止新增。
echo "── 429 提示覆盖拦截 ──"
if [ -f "$ROOT_DIR/package.json" ]; then
  if node "$ROOT_DIR/scripts/check-429-error-override.mjs" "$GATE_BASE" 2>&1; then
    pass "429 提示覆盖拦截"
  else
    fail "429 提示覆盖拦截 — 新增 API catch 块中直接 ElMessage.error。替换为 notifyErrorUnlessRateLimit(error, 'fallback')。详见 src/api/error-utils.js"
  fi
fi

# ── 5.6. 跨语言关键词契约同步（PR !2399 防复发） ─────────
# Java PriceScoringKeywords（LONG/SHORT_PRICE_PARAMS）与 Python converter.py
# （PRICE_PARAM_LONG/SHORT_KEYWORDS）是同一语义的两份实现，此前仅靠注释约束同步。
# 仅当本次推送触及契约两侧文件时执行；契约尚未建立（两侧均无）时自动放行。
echo "── 跨语言关键词契约 ──"
CONTRACT_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE 'PriceScoringKeywords\.java$|document-converter-sidecar/converter\.py$' || true)
if [ "$CONTRACT_CHANGED" -eq 0 ]; then
  skip "keyword-sync（未触及契约文件）"
else
  if node "$ROOT_DIR/scripts/check-keyword-sync.mjs" 2>&1; then
    pass "keyword-sync（Java/Python 关键词表一致）"
  else
    fail "keyword-sync — Java/Python 报价参数关键词表不一致或单边新增，运行 node scripts/check-keyword-sync.mjs 查看差集"
  fi
fi

# ── 5.6.1. /convert 结构化 pages 跨语言契约（PR !2492 防复发） ──
# Sidecar 响应 pages 字段与 Java adapter 消费是同一契约的两端，漂移会静默降级
# 到虚拟分页兜底（lessons #198 同源坑）。触及契约任一端文件时执行。
echo "── pages 契约 ──"
PAGES_CONTRACT_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE 'document-converter-sidecar/(converter\.py|app\.py)$|SidecarStreamDocumentAdapter\.java$' || true)
if [ "$PAGES_CONTRACT_CHANGED" -eq 0 ]; then
  skip "pages-contract（未触及契约文件）"
else
  if node "$ROOT_DIR/scripts/check-pages-contract.mjs" 2>&1; then
    pass "pages-contract（/convert pages 字段两端同现）"
  else
    fail "pages-contract — /convert pages 字段契约漂移，运行 node scripts/check-pages-contract.mjs 查看缺失端"
  fi
fi

# ── scoreparse 文本证据必须归属评分方案 ───────────────────
echo "── scoreparse 文本评分范围 ──"
if node "$ROOT_DIR/scripts/check-scoreparse-text-scope.mjs" 2>&1; then
  pass "scoreparse 文本证据评分范围守卫"
else
  fail "scoreparse 文本证据评分范围守卫 — 纯文本评分事实必须由当前评分方案标题范围证明"
fi

# ── 5.7. 仓库卫生：根目录产物文件拦截（PR !2399 教训） ────
# implementation-notes.html / ui_mvp_preview.html 等产物/笔记文件不应持续入库。
# 历史文件允许继续存在，仅拦截本次推送对它们的新增/修改。
echo "── 仓库卫生 ──"
HYGIENE_OFFENDERS=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -E '^(implementation-notes\.html|ui_mvp_preview\.html)$' || true)
if [ -z "$HYGIENE_OFFENDERS" ]; then
  pass "仓库卫生（无产物文件改动）"
elif git log -1 --format='%s %b' 2>/dev/null | grep -q '\[skip repo-hygiene\]'; then
  skip "仓库卫生：产物文件被改动（已标记 [skip repo-hygiene]）"
else
  fail "仓库卫生 — 产物/笔记文件被改动入库: $(echo "$HYGIENE_OFFENDERS" | tr '\n' ' ')。应迁移至 docs/ 或加入 .gitignore（commit message 加 [skip repo-hygiene] 可跳过）"
fi

# ── 6. 泄露检查 ────────────────────────────────────────
echo "── 文件泄露检查 ──"
UNTRACKED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | wc -l | tr -d ' ')
STAGED_FILES=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | head -20)
if [ "$UNTRACKED" -gt 20 ]; then
  skip "本次推送 $UNTRACKED 个文件 (>20)"
  echo "$STAGED_FILES"
else
  pass "本次推送 $UNTRACKED 个文件，数量合理"
fi

# ── 7. E2E 选择器安全检查 ────────────────────────────────
echo "── E2E 选择器 ──"
CHANGED_E2E=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep 'e2e/.*\.spec\.js$' || true)
if [ -n "$CHANGED_E2E" ]; then
  BREADCRUMB_RISK=$(grep -n "getByText(" $CHANGED_E2E 2>/dev/null | grep -v '.first()' | grep -v '// breadcrumb-ok' || true)
  if [ -n "$BREADCRUMB_RISK" ]; then
    skip "E2E 测试中使用了 getByText()"
  else
    pass "E2E 选择器无面包屑碰撞风险"
  fi
else
  pass "无新增 E2E 文件"
fi

# ── 8. agent-locks 完整检查 ──────────────────────────────
echo "── agent-locks ──"
if [ -f "$ROOT_DIR/package.json" ]; then
  if node "$ROOT_DIR/scripts/check-agent-locks.mjs" --base "$GATE_BASE" 2>/dev/null; then
    pass "agent-locks 无冲突"
  else
    fail "agent-locks — 有锁冲突。运行 npm run agent:lock-check 查看详情"
  fi
fi

# ── 9. FP-Java 架构门禁 ────────────────────────────────
echo "── FP-Java 架构 ──"
if [ ! -d "$ROOT_DIR/backend" ]; then
  skip "非 Java 项目"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "ResponsibilityArchitectureTest（无 backend/ 变更）"
else
  backend_mvn_run
  if surefire_failed ResponsibilityArchitectureTest; then
    fail "ResponsibilityArchitectureTest — 违反 FP-Java 规则：core 依赖框架/超 300 行/超 2 类职责"
  else
    pass "ResponsibilityArchitectureTest"
  fi
fi

# ── 9.5. CO-373 直调拦截（User.getRoleCode） ──────────────
# CO-373 根因：OSS 同步用户 role_id=NULL 时，User.getRoleCode() 实体 fallback 返回
# "manager"，导致下游业务权限误判。新增直调必须改为 EffectiveRoleResolver / DataScopeConfigService，
# 或在上方加 // SAFE: 注释（仅限已记录豁免场景）。
echo "── CO-373 直调拦截 ──"
if [ ! -d "$ROOT_DIR/backend/src/main" ]; then
  skip "无 Java 源码"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "RoleCode 直调拦截（无 backend/ 变更）"
else
  if node "$ROOT_DIR/scripts/check-rolecode-direct-calls.mjs" 2>&1; then
    pass "RoleCode 直调拦截"
  else
    fail "RoleCode 直调拦截 — 新增 User.getRoleCode() 直调。迁移到 EffectiveRoleResolver.resolveRoleCode(user) 或加 // SAFE: 注释。详见 scripts/check-rolecode-direct-calls.mjs"
  fi
fi

# ── 9.6. toMap 无 merge function 拦截（Constitution v2.0.0 Principle VII） ──
# 2-arg Collectors.toMap(k, v) 在重复 key 时抛 IllegalStateException。
# 新代码必须用 3-arg toMap(k, v, (a, b) -> a)。遗留豁免见 scripts/tomap-exemptions.json。
echo "── toMap merge function 拦截 ──"
if [ ! -d "$ROOT_DIR/backend/src/main" ]; then
  skip "无 Java 源码"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "toMap 拦截（无 backend/ 变更）"
else
  if node "$ROOT_DIR/scripts/check-tomap-no-merge-function.mjs" 2>&1; then
    pass "toMap merge function 拦截"
  else
    fail "toMap 无 merge function — Collectors.toMap 2-arg 调用会抛 IllegalStateException。加 (a, b) -> a 第 3 参数。详见 scripts/check-tomap-no-merge-function.mjs"
  fi
fi

# ── 9.7. JSON 字段序列化检查（CO-469 第八轮 P1 防复发） ──
# 工程背景（2026-07-06 CO-469 第八轮 P0 事故）：
#   PersonnelImportTaskRepositoryAdapter.serializeErrorDetails 用 List.toString()
#   写入 MySQL JSON 字段，触发 DataIntegrityViolationException，
#   被 SimpleAsyncUncaughtExceptionHandler 吞掉，任务永卡 PROCESSING/5%。
# P1 全仓审计发现 2 个同类未爆雷：
#   TenderSourceConfig.toJsonArray 手写拼接未转义控制字符
#   ApprovalCommandService 用 Collectors.joining 写 CSV 到 JSON 字段
# 本门禁在 pre-push 阶段拦截新增的 List/Map/Set.toString() 写 JSON 字段模式。
# Pattern A（高置信度，阻断）：List/Map/Set.toString() 调用
# Pattern B/C（中置信度，仅警告不阻断）：手写拼接 / Collectors.joining
# 逃生阀：JSON_FIELD_SERIALIZATION_SKIP=1（仅限已记录豁免场景，需在 PR 描述说明理由）
echo "── JSON 字段序列化 ──"
if [ ! -d "$ROOT_DIR/backend/src/main" ]; then
  skip "无 Java 源码"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "JSON 字段序列化检查（无 backend/ 变更）"
elif [ "${JSON_FIELD_SERIALIZATION_SKIP:-0}" = "1" ]; then
  skip "JSON 字段序列化检查（JSON_FIELD_SERIALIZATION_SKIP=1 逃生阀）"
else
  # 使用 --cached 增量模式，只扫描暂存区变更的文件（pre-push 场景）
  # 增量比全量快 10-100 倍，且变更才是风险点
  if bash "$ROOT_DIR/scripts/check-json-field-serialization.sh" --cached 2>&1; then
    pass "JSON 字段序列化（无高风险 .toString 模式）"
  else
    fail "JSON 字段序列化 — 检测到疑似集合 .toString()（Pattern A 阻断）。改为 Jackson ObjectMapper.writeValueAsString()。逃生阀：JSON_FIELD_SERIALIZATION_SKIP=1"
  fi
fi

# ── 9.8. 父权限兜底拦截（账户/CA 页面 403 反复修复防复发） ──
# 工程背景（2026-07-10 账户管理/CA 信息管理页面 403）：
#   PlatformAccountController / CaCertificateController 类级使用 @PreAuthorize("hasAuthority('resource')")，
#   但 OSS 端对 bid-projectLeader 只下发 resource-account / resource-ca 子菜单，未下发 resource 父菜单，
#   导致 403。修复方式是在 UserDetailsServiceImpl 中兜底：持有任意 resource-* 子权限时自动补 resource。
# 本门禁拦截新增/变更的父权限 @PreAuthorize 缺少子权限 → 父权限兜底的情况。
# 逃生阀：PARENT_PERMISSION_FALLBACK_SKIP=1（仅限已记录豁免场景，需在 PR 描述说明理由）
echo "── 父权限兜底拦截 ──"
if [ ! -d "$ROOT_DIR/backend/src/main" ]; then
  skip "无 Java 源码"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "父权限兜底拦截（无 backend/ 变更）"
elif [ "${PARENT_PERMISSION_FALLBACK_SKIP:-0}" = "1" ]; then
  skip "父权限兜底拦截（PARENT_PERMISSION_FALLBACK_SKIP=1 逃生阀）"
else
  if node "$ROOT_DIR/scripts/check-parent-permission-fallback.mjs" 2>&1; then
    pass "父权限兜底拦截"
  else
    fail "父权限兜底拦截 — @PreAuthorize 使用父权限，但 UserDetailsServiceImpl 缺少子权限兜底。参考 scripts/check-parent-permission-fallback.mjs 修复。逃生阀：PARENT_PERMISSION_FALLBACK_SKIP=1"
  fi
fi

# ── 9.9. 文件路径默认值拦截（macOS SSV 只读 + 绝对路径防复发） ──
# 工程背景（2026-07-17 仓库 Word 合订本附件内容丢失）：
#   @Value("${warehouse.attachment.root:/data/attachments/warehouse}") 默认值是绝对路径，
#   macOS Catalina+ SSV 保护使根目录 / 只读，mkdir /data 失败（Read-only file system）。
#   上传时 Files.createDirectories 抛 IOException → HTTP 400，用户用历史数据未感知；
#   Word 生成时 Files.exists 返回 false → 所有三级标题下显示"（文件缺失）"。
#   全局收敛发现 6 处同类（warehouse 4 + performance 2）。
# 本门禁拦截新增的 @Value 文件路径默认值以 "/" 开头（排除 http://, https:// 等 URL）。
echo "── 文件路径默认值拦截 ──"
if [ ! -d "$ROOT_DIR/backend/src/main" ]; then
  skip "无 Java 源码"
elif [ "${BACKEND_CHANGED:-0}" -eq 0 ]; then
  skip "文件路径默认值拦截（无 backend/ 变更）"
else
  if node "$ROOT_DIR/scripts/check-attachment-root-path.mjs" "$GATE_BASE" 2>&1; then
    pass "文件路径默认值拦截"
  else
    fail "文件路径默认值拦截 — 新增 @Value 默认值以 '/' 开头（macOS SSV 只读）。改为相对路径如 'data/warehouse-attachments'。详见 scripts/check-attachment-root-path.mjs"
  fi
fi

# ── 9.10. lessons-learned.md 章节编号冲突拦截（并行归档防撞号） ──
# 背景：多 agent 并行做"每周知识归档"时，各自从旧 base 拉分支取编号，
# 先合入的占号，后开的 PR 撞号或内容重复（PR !2130 vs !2131/!2132 真实事故）。
# 逃生阀：LESSONS_SECTION_CONFLICT_SKIP=1（仅限已记录豁免场景，需在 PR 描述说明理由）
echo "── lessons-learned 章节冲突 ──"
LESSONS_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -c '^docs/lessons/lessons-learned\.md$' || true)
if [ "$LESSONS_CHANGED" -eq 0 ]; then
  skip "lessons-learned 章节冲突（无 docs/lessons/lessons-learned.md 变更）"
elif [ "${LESSONS_SECTION_CONFLICT_SKIP:-0}" = "1" ]; then
  skip "lessons-learned 章节冲突（LESSONS_SECTION_CONFLICT_SKIP=1 逃生阀）"
else
  if node "$ROOT_DIR/scripts/check-lessons-section-conflict.mjs" "$GATE_BASE" 2>&1; then
    pass "lessons-learned 章节冲突"
  else
    fail "lessons-learned 章节冲突 — 新增 ## N. 编号与 origin/main 冲突（撞号或回退）。先 git fetch origin main && git rebase origin/main，再重新取下一个可用编号。逃生阀：LESSONS_SECTION_CONFLICT_SKIP=1"
  fi
fi

# ── 9.11. scoreparse 版式修复金标门禁（Stage A Task1，2026-09-12） ──
# 工程背景：
#   项目 1374 曾因冗余价格公式行未去重 + “评标基准价为”独立成项导致权重 101 膨胀
#   （fix 1919ecb41）；3 列内嵌分值表曾因维度名 <br> 未平滑导致维度带空格
#   （fix a1b3b7c55）。两轮修复均靠 GoldenCasesReplayTest 金标夹具锁定回归面。
# 本门禁：commit 范围触及 scoreparse 解析主代码（domain/application/infrastructure），
#   必须同步新增或更新 scoreparse 金标夹具或回放/提取测试，否则 fail。
#   新版式修复无 fixture 即 fail（等效说明见 scripts/eval-scoreparse/README.md §门禁）。
# 逃生阀：SCOREPARSE_GATE_SKIP=1（仅限非版式逻辑变更，需在 PR 描述说明理由）
echo "── scoreparse 金标门禁 ──"
SP_PROD_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE '^backend/src/main/java/.*/scoreparse/(domain|application|infrastructure)/' || true)
SP_FIXTURE_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE 'scoreparse-golden/|scoreparse/source-evidence-real-excerpts\.json|scoreparse/docx-continuity/|scoreparse/project-1382/|RealSourceExcerptReplayTest\.java|CrossFormatDocumentSourceReplayTest\.java|GoldenCasesReplayTest\.java|LiveScorePublicationGateTest\.java|Project1382FullPipelineIntegrationTest\.java|CellAnchoredScoreExtractorTest\.java|StructuredTableCandidateExtractorTest\.java|ScoreTableRecallLaneTest\.java|ScoreItemMergePolicyTest\.java|ScoreGlobalMergeOrchestratorTest\.java' || true)
if [ ! -d "$ROOT_DIR/backend/src/main" ]; then
  skip "scoreparse 金标门禁（无 Java 源码）"
elif [ "${SP_PROD_CHANGED:-0}" -eq 0 ]; then
  skip "scoreparse 金标门禁（未触及 scoreparse 解析主代码）"
elif [ "${SCOREPARSE_GATE_SKIP:-0}" = "1" ]; then
  skip "scoreparse 金标门禁（SCOREPARSE_GATE_SKIP=1 逃生阀）"
elif [ "${SP_FIXTURE_CHANGED:-0}" -eq 0 ]; then
  fail "scoreparse 金标门禁 — 本次推送改了 scoreparse 解析主代码（${SP_PROD_CHANGED} 个文件）但无金标夹具/回放测试同步变更。新版式修复无 fixture 即 fail：在 backend/src/test/resources/scoreparse-golden/ 新增 <case>.md + <case>.expected.json 并在 GoldenCasesReplayTest#cases() 注册（指南见 scripts/eval-scoreparse/README.md §如何新增 case）。逃生阀：SCOREPARSE_GATE_SKIP=1"
else
  pass "scoreparse 金标门禁（解析变更 + 夹具/测试同步）"
fi

# ── 10. 路由-E2E 兼容性检查 ────────────────────────────
echo "── 路由-E2E 兼容 ──"
STAGED_ROUTES=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE '^src/(router|views)/' || true)
if [ "$STAGED_ROUTES" -gt 0 ]; then
  if node "$ROOT_DIR/scripts/check-route-e2e-compat.mjs" 2>/dev/null; then
    pass "route-e2e-compat"
  else
    fail "route-e2e-compat — 路由/E2E 播种不兼容"
  fi
else
  pass "route-e2e-compat (无路由/视图变更)"
fi

# ── 11. E2E-UI 联动检查 ────────────────────────────────
echo "── E2E-UI 联动 ──"
if [ "$SKIP_E2E_CHECK" = "true" ]; then
  skip "E2E-UI 联动 (--skip-e2e-check)"
else
  UI_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE '^src/(router|views)/' || true)
  E2E_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE '^e2e/' || true)
  if [ "$UI_CHANGED" -gt 0 ] && [ "$E2E_CHANGED" -eq 0 ]; then
    HEADER=$(git log -1 --format='%s %b' 2>/dev/null || true)
    if echo "$HEADER" | grep -q '\[skip e2e-scope\]'; then
      skip "E2E-UI: UI 变更但 E2E 未更新 (已标记 [skip e2e-scope])"
    else
      fail "E2E-UI: UI 有变更但 e2e/ 无对应更新。提交message加 [skip e2e-scope] 可跳过"
    fi
  else
    pass "E2E-UI 联动"
  fi
fi

# Bounded executable source regressions complement the changed-fixture check above.
# Agent frontend shortcuts never silently suppress these backend/governance regressions.
if [ "$EXPLICIT_SKIP_TESTS" = "true" ]; then
  skip "scoreparse 根因回归 / token CLI 回归 (--skip-tests)"
else
  if node --test "$ROOT_DIR/scripts/check-token-coverage.test.mjs" "$ROOT_DIR/scripts/check-pre-push-dispatch.test.mjs"; then
    pass "token CLI 回归"
  else
    fail "token CLI 回归"
  fi
  if bash "$ROOT_DIR/scripts/check-worktree-protection.test.sh"; then
    pass "共享 gitdir CLI 回归"
  else
    fail "共享 gitdir CLI 回归"
  fi
  if [ "$SP_PROD_CHANGED" -gt 0 ] && [ "${SCOREPARSE_GATE_SKIP:-0}" != "1" ]; then
    if bash "$ROOT_DIR/scripts/check-scoreparse-root-cause.sh"; then
      pass "scoreparse 可执行根因回归"
    else
      fail "scoreparse 可执行根因回归"
    fi
  else
    skip "scoreparse 可执行根因回归（未变更解析主代码或显式 SCOREPARSE_GATE_SKIP）"
  fi
fi

# ── 12. 前端单元测试 ───────────────────────────────────
echo "── 前端单元测试 ──"
if [ "$SKIP_TESTS" = "true" ]; then
  skip "test:unit (--skip-tests)"
else
  if npm run test:unit --silent 2>/dev/null; then
    pass "test:unit"
  else
    fail "test:unit — 前端单元测试失败"
  fi
fi

# ── 13. Lint 检查 ──────────────────────────────────────
echo "── Lint 检查 ──"
if npm run lint --silent 2>/dev/null; then
  pass "eslint"
else
  fail "eslint — 有 lint 错误，运行 npm run lint:fix 修复"
fi

# ── 14. 前端构建 ────────────────────────────────────────
echo "── 前端构建 ──"
if [ "${FRONTEND_CHANGED:-0}" -eq 0 ]; then
  skip "build:api（无 src/ 变更）"
elif [ "$EXPLICIT_SKIP_TESTS" = "true" ]; then
  skip "build:api (--skip-tests 显式跳过)"
else
  # 即使是 agent/* 分支，只要触及 src/ 源码，就必须执行构建编译门禁，严防 Vue SFC 模板宏与语法错误
  if npm run build:api --silent 2>/dev/null; then
    pass "build:api"
  else
    fail "build:api — 前端构建失败。请本地运行 npm run build:api 查看详细编译错误。"
  fi
fi

# ── 15. Agent 多任务分支软性检查 ─────────────────────────
echo "── Agent 任务分支集中度 ──"
if [[ "$branch" == agent/* ]]; then
  agent_name="${branch#agent/}"
  agent_name="${agent_name%%/*}"
  agent_task_branches=$(git branch --list "agent/${agent_name}/*" 2>/dev/null | sed 's/^[ *+] //' | grep -v "^agent/${agent_name}-init$" || true)
  agent_task_count=$(echo "$agent_task_branches" | grep -cE '^agent/' || true)
  if [[ "${agent_task_count:-0}" -gt 1 ]]; then
    echo -e "${YELLOW}⚠${NC} Agent '$agent_name' 当前有 ${agent_task_count} 个活跃任务分支（不含 -init 锚点）"
    echo "$agent_task_branches" | sed 's/^/    - /'
    echo "   建议：任务完成后及时清理本地分支，避免并行任务冲突。"
    SKIPD=$((SKIPD + 1))
  else
    pass "Agent 任务分支集中度良好"
  fi
else
  skip "非 agent 分支"
fi

# ── 16. Wiki 健康检查（Agent Wiki 运行规范） ─────────────
# 工程背景（2026-07-23）:
#   .wiki/ 体系已存在 60+ 页面，但维护停在 2026-06-20。CO-361 反复修复 5 次、
#   OSS 角色问题反复 10+ 轮——Wiki 一条都没记。根因：架构搭好但纪律未建立。
#   本门禁确保 Agent 在 push 时检查是否同步更新了相关 wiki 页面。
#   详见 .wiki/WIKI.md §2 触发点 4。
# 模式切换:
#   WIKI_CHECK_MODE=warning  仅警告不阻塞（默认，2 周过渡期至 2026-08-06）
#   WIKI_CHECK_MODE=error    阻塞 push（2026-08-06 后默认）
#   WIKI_CHECK_SKIP=1        完全跳过（仅限紧急修复）
# 过渡期：2026-07-23 ~ 2026-08-06（2 周），仅 warning
echo "── Wiki 健康检查 ──"
if [ "${WIKI_CHECK_SKIP:-0}" = "1" ]; then
  skip "Wiki 健康检查（WIKI_CHECK_SKIP=1 逃生阀）"
elif [ ! -d "$ROOT_DIR/.wiki/pages" ]; then
  skip "Wiki 健康检查（无 .wiki/pages 目录）"
else
  WIKI_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -c '^\.wiki/' || true)
  CODE_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep -cE '^(src/|backend/src/)' || true)

  # 默认模式：2 周过渡期内 warning，之后 error
  TODAY_NUM=$(date +%Y%m%d)
  HARD_DATE_NUM=20260806
  if [ "$TODAY_NUM" -ge "$HARD_DATE_NUM" ]; then
    WIKI_DEFAULT_MODE="error"
  else
    WIKI_DEFAULT_MODE="warning"
  fi
  WIKI_CHECK_MODE="${WIKI_CHECK_MODE:-$WIKI_DEFAULT_MODE}"

  if [ "$WIKI_CHANGED" -eq 0 ] && [ "$CODE_CHANGED" -gt 0 ]; then
    # 代码变更但无 wiki 变更 → 提醒回填
    WIKI_MSG="Wiki 健康检查 — 检测到代码变更（$CODE_CHANGED 个文件）但无 .wiki/ 变更。请确认是否需要回填新知识到 .wiki/pages/。详见 .wiki/WIKI.md §2 触发点 1/3"
    if [ "$WIKI_CHECK_MODE" = "error" ]; then
      fail "$WIKI_MSG"
    else
      echo -e "${YELLOW}⚠${NC} $WIKI_MSG"
      echo "         (warning 模式，过渡期至 2026-08-06 后转 error)"
      SKIPD=$((SKIPD + 1))
    fi
  elif [ "$WIKI_CHANGED" -gt 0 ]; then
    # 有 wiki 变更，检查 frontmatter 合规性
    WIKI_PAGES_CHANGED=$(git diff --name-only "$GATE_BASE"..HEAD 2>/dev/null | grep '^\.wiki/pages/.*\.md$' || true)
    WIKI_VIOLATIONS=0
    if [ -n "$WIKI_PAGES_CHANGED" ]; then
      while IFS= read -r wiki_file; do
        if [ -f "$ROOT_DIR/$wiki_file" ]; then
          if ! head -60 "$ROOT_DIR/$wiki_file" | grep -q 'health_checked:'; then
            echo "    ⚠ $wiki_file 缺少 health_checked 字段"
            WIKI_VIOLATIONS=$((WIKI_VIOLATIONS + 1))
          fi
          if ! head -60 "$ROOT_DIR/$wiki_file" | grep -q 'updated:'; then
            echo "    ⚠ $wiki_file 缺少 updated 字段"
            WIKI_VIOLATIONS=$((WIKI_VIOLATIONS + 1))
          fi
        fi
      done <<< "$WIKI_PAGES_CHANGED"
    fi
    if [ "$WIKI_VIOLATIONS" -gt 0 ]; then
      WIKI_MSG="Wiki 健康检查 — $WIKI_VIOLATIONS 个页面缺 frontmatter 字段（health_checked/updated）"
      if [ "$WIKI_CHECK_MODE" = "error" ]; then
        fail "$WIKI_MSG"
      else
        echo -e "${YELLOW}⚠${NC} $WIKI_MSG"
        echo "         (warning 模式，过渡期至 2026-08-06 后转 error)"
        SKIPD=$((SKIPD + 1))
      fi
    else
      pass "Wiki 健康检查（已同步更新 $WIKI_CHANGED 个 wiki 文件）"
    fi
  else
    pass "Wiki 健康检查（无代码/wiki 变更）"
  fi
fi

# ── §32: Unified Dev Flow S5/S6 物理硬门禁 ───────────────────────
# 针对任务开发分支（agent/*, feat/*, fix/* 等非主干分支），强制核验 S5 验证与 S6 Code Review 工件
GATE_BRANCH="$(git branch --show-current 2>/dev/null || true)"
if [[ "$GATE_BRANCH" =~ ^(agent/|feat/|fix/) ]]; then
  CHECKER="${HOME}/.claude/scripts/entry-gate-check.py"
  if [[ -f "$CHECKER" ]]; then
    echo "🔍 检查 S5 验证与 S6 Code Review 工件..."
    if ! python3 "$CHECKER" --project-dir "$ROOT_DIR" --guard-pr; then
      fail "Unified Dev Flow 门禁未通过：缺少合规的 S5 验证工件 (.omc/verify-*.md) 或 S6 审查工件 (.omc/review-*.md 且 verdict 须为 MERGE_READY)"
    else
      pass "Unified Dev Flow 门禁（S5 验证与 S6 Code Review: MERGE_READY）"
    fi
  else
    echo "⚠️  entry-gate-check.py 未找到，跳过 S5/S6 门禁"
    SKIPD=$((SKIPD + 1))
  fi
else
  pass "Unified Dev Flow 门禁（非任务分支，跳过 S5/S6 检查）"
fi

# ── 汇总 ────────────────────────────────────────────────
echo ""
echo "─────────────────────────"
echo -e "通过: ${GREEN}${PASS}${NC}  失败: ${RED}${FAIL}${NC}  跳过: ${YELLOW}${SKIPD}${NC}"
echo "─────────────────────────"

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo -e "${RED}门禁未通过。修复以上 ${FAIL} 项后重试。${NC}"
  echo ""
  echo "提示："
  echo "  --skip-tests      跳过 test:unit + build:api（节省 30-60s）"
  echo "  --skip-e2e-check  跳过 E2E-UI 联动检查"
  echo "  PRE_PUSH_GATE=0  完全跳过门禁（紧急情况使用）"
  exit 1
else
  echo -e "${GREEN}门禁通过，可以推送。${NC}"
  exit 0
fi
