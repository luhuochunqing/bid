#!/usr/bin/env bash
# Input: none
# Output: 生产部署数据库预备份 fail-closed 契约验证结果（失败即非零退出并打印明细）
# Pos: scripts/test/ - 发布脚本回归契约
# 维护声明: 重放第 39 次生产部署（2026-11-07）事故：生产账号 ea_bid_pro 无 LOCK TABLES 权限，
#           `mysqldump | gzip > file` 吞掉 mysqldump 退出码，产出 371 字节假备份且部署继续。
#           本契约保证：备份失败必非零退出、必删除半成品、remote-deploy.sh 必中止部署；反向变异必须被拒绝。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKUP_SCRIPT="$REPO_ROOT/scripts/release/backup-db-prod.sh"
DEPLOY_SCRIPT="$REPO_ROOT/scripts/release/deploy-prod.sh"
REMOTE_DEPLOY="$REPO_ROOT/scripts/release/remote-deploy.sh"

PASS_COUNT=0

assert_true() {
  local condition="$1" description="$2"
  if [[ "$condition" == "1" ]]; then
    echo "[PASS] $description"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "[FAIL] $description" >&2
    exit 1
  fi
}

assert_contains() {
  local haystack="$1" needle="$2" description="$3"
  if [[ "$haystack" != *"$needle"* ]]; then
    echo "[FAIL] missing: $description" >&2
    echo "        expected snippet: $needle" >&2
    exit 1
  fi
  echo "[PASS] $description"
  PASS_COUNT=$((PASS_COUNT + 1))
}

assert_not_contains() {
  local haystack="$1" needle="$2" description="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    echo "[FAIL] unexpected snippet: $description" >&2
    echo "        found: $needle" >&2
    exit 1
  fi
  echo "[PASS] $description"
  PASS_COUNT=$((PASS_COUNT + 1))
}

assert_no_backup_file() {
  local dir="$1" description="$2"
  local found
  found="$(find "$dir" -maxdepth 1 -name '*.sql.gz' 2>/dev/null | wc -l | tr -d ' ')"
  assert_true "$([[ "$found" == "0" ]] && echo 1 || echo 0)" "$description"
}

assert_file_exists() {
  local path="$1" description="$2"
  assert_true "$([[ -f "$path" ]] && echo 1 || echo 0)" "$description"
}

assert_file_absent() {
  local path="$1" description="$2"
  assert_true "$([[ ! -f "$path" ]] && echo 1 || echo 0)" "$description"
}

# --- 场景 0：脚本语法有效 ---
bash -n "$BACKUP_SCRIPT"
echo "[PASS] backup-db-prod.sh bash 语法有效"
PASS_COUNT=$((PASS_COUNT + 1))

bash -n "$DEPLOY_SCRIPT"
echo "[PASS] deploy-prod.sh bash 语法有效"
PASS_COUNT=$((PASS_COUNT + 1))

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

FAKE_BIN="$WORK/fake-bin"
mkdir -p "$FAKE_BIN"
ENV_FILE="$WORK/backend.env"
ARG_LOG="$WORK/mysqldump-args.log"
: >"$ARG_LOG"

cat >"$ENV_FILE" <<'EOF'
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=ea_bid_pro
DB_NAME=xiyu_bid_main
DB_PASSWORD=secret
EOF

# 假 mysqldump：按 FAKE_MYSQLDUMP_MODE 重放三种真实失败形态
cat >"$FAKE_BIN/mysqldump" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_ARGS_LOG"
case "${FAKE_MYSQLDUMP_MODE:-success}" in
  access_denied)
    printf 'mysqldump: Got error: 1044: Access denied for user when using LOCK TABLES\n' >&2
    exit 2
    ;;
  truncated)
    printf -- '-- MySQL dump\n/* partial data, mysqldump died mid-way */\n'
    exit 0
    ;;
  success)
    printf -- '-- MySQL dump 10.13\nCREATE TABLE demo (id int);\nINSERT INTO demo VALUES (1);\n'
    printf -- '-- Dump completed on 2026-11-08  1:00:00\n'
    exit 0
    ;;
  *)
    printf 'unknown FAKE_MYSQLDUMP_MODE\n' >&2
    exit 9
    ;;
esac
EOF
chmod +x "$FAKE_BIN/mysqldump"

run_backup() {
  local mode="$1" dir="$2"
  mkdir -p "$dir"
  env \
    PATH="$FAKE_BIN:$PATH" \
    BACKEND_ENV_FILE="$ENV_FILE" \
    BACKUP_DIR="$dir" \
    DB_BACKUP_MIN_BYTES=1 \
    FAKE_MYSQLDUMP_MODE="$mode" \
    FAKE_ARGS_LOG="$ARG_LOG" \
    bash "$BACKUP_SCRIPT" contract-release \
    >"$WORK/stdout.log" 2>"$WORK/stderr.log"
}

# --- 场景 1（事故重放）：无 LOCK TABLES 权限 → 非零退出 + 无假备份残留 ---
DIR1="$WORK/backups-denied"
RC1=0
run_backup access_denied "$DIR1" || RC1=$?
STDERR1="$(cat "$WORK/stderr.log")"
assert_true "$([[ $RC1 -ne 0 ]] && echo 1 || echo 0)" "场景1: 备份失败必须非零退出（rc=${RC1}）"
assert_no_backup_file "$DIR1" "场景1: 不残留假备份文件（第 39 次事故不复发）"
assert_contains "$STDERR1" "DB backup failed" "场景1: 明确报错"
assert_contains "$STDERR1" "partial file removed" "场景1: 半成品已删除"

# --- 场景 2：mysqldump 退出 0 但 dump 不完整（gzip 完整的截断内容）→ 必须被拦下 ---
DIR2="$WORK/backups-truncated"
RC2=0
run_backup truncated "$DIR2" || RC2=$?
STDERR2="$(cat "$WORK/stderr.log")"
assert_true "$([[ $RC2 -ne 0 ]] && echo 1 || echo 0)" "场景2: 缺 Dump completed 标记必须非零退出（rc=${RC2}）"
assert_no_backup_file "$DIR2" "场景2: 不残留截断备份文件"
assert_contains "$STDERR2" "dump incomplete" "场景2: 报错指明 dump 不完整"

# --- 场景 3：正常备份 → 成功产出 + --single-transaction + 完整性校验 ---
DIR3="$WORK/backups-ok"
RC3=0
run_backup success "$DIR3" || RC3=$?
assert_true "$([[ $RC3 -eq 0 ]] && echo 1 || echo 0)" "场景3: 正常备份退出码为 0（rc=${RC3}）"
OUT3="$(find "$DIR3" -maxdepth 1 -name '*.sql.gz' | head -n 1)"
assert_file_exists "$OUT3" "场景3: 备份文件已生成"
gzip -t "$OUT3"
echo "[PASS] 场景3: gzip 完整性校验通过"
PASS_COUNT=$((PASS_COUNT + 1))
TAIL3="$(gzip -dc "$OUT3" | tail -n 1)"
assert_contains "$TAIL3" "Dump completed" "场景3: 尾部完成标记存在"
assert_contains "$(cat "$ARG_LOG")" "--single-transaction" "场景3: mysqldump 使用 --single-transaction（不再依赖 LOCK TABLES）"
assert_contains "$(cat "$WORK/stdout.log")" "DB backup completed" "场景3: 成功日志含完成信息"

# --- 场景 4：备份命令失败时 remote-deploy.sh 必须中止部署（fail closed）---
APP_ROOT="$WORK/app-root"
FRONTEND_DIR="$WORK/frontend"
mkdir -p "$APP_ROOT" "$FRONTEND_DIR" "$WORK/stage/backend" "$WORK/stage/frontend"
echo "dummy-jar" >"$WORK/stage/backend/app.jar"
echo "<html>dummy</html>" >"$WORK/stage/frontend/index.html"
tar -czf "$WORK/release.tar.gz" -C "$WORK/stage" .

# 预置旧版本，不能只靠成功后才写入的部署记录判断是否已激活。
mkdir -p "$APP_ROOT/shared/backend"
printf 'old-backend\n' >"$WORK/old-app.jar"
printf 'old-frontend\n' >"$WORK/old-index.html"
cp "$WORK/old-app.jar" "$APP_ROOT/shared/backend/app.jar"
cp "$WORK/old-index.html" "$FRONTEND_DIR/index.html"

# 备份后的 Flyway 阶段设可观测哨兵；若越过备份边界，立即退出，避免服务/网络操作。
cat >"$WORK/flyway-sentinel.sh" <<'EOF'
#!/usr/bin/env bash
printf 'entered\n' >"$AFTER_BACKUP_MARKER"
exit 23
EOF
chmod +x "$WORK/flyway-sentinel.sh"

run_remote_backup_failure() {
  local script="$1"
  env \
    RELEASE_ARCHIVE="$WORK/release.tar.gz" \
    RELEASE_ID="contract-test" \
    APP_ROOT="$APP_ROOT" \
    FRONTEND_PUBLIC_DIR="$FRONTEND_DIR" \
    BACKEND_SERVICE_NAME="dummy-service" \
    HEALTHCHECK_URL="http://127.0.0.1:1/actuator/health" \
    BACKEND_ENV_FILE="$ENV_FILE" \
    SYSTEMCTL_BIN="/usr/bin/false" \
    SYSTEMCTL_SUDO="false" \
    SKIP_FLYWAY_VALIDATE=0 \
    FLYWAY_REPAIR_RUNNER="$WORK/flyway-sentinel.sh" \
    AFTER_BACKUP_MARKER="$WORK/after-backup.marker" \
    DB_BACKUP_COMMAND="exit 7" \
    bash "$script" >"$WORK/remote-stdout.log" 2>"$WORK/remote-stderr.log"
}

assert_backup_boundary() {
  local rc="$1"
  assert_true "$([[ $rc -eq 7 ]] && echo 1 || echo 0)" "场景4: 保留备份退出码7（rc=${rc}）"
  assert_file_absent "$WORK/after-backup.marker" "场景4: 未进入备份后续阶段"
  assert_not_contains "$(cat "$WORK/remote-stdout.log")" "Activating frontend assets" "场景4: 未进入前端激活"
  assert_true "$(cmp -s "$WORK/old-app.jar" "$APP_ROOT/shared/backend/app.jar" && echo 1 || echo 0)" "场景4: 已有后端内容未变化"
  assert_true "$(cmp -s "$WORK/old-index.html" "$FRONTEND_DIR/index.html" && echo 1 || echo 0)" "场景4: 已有前端内容未变化"
  assert_file_absent "$APP_ROOT/deployed-release.json" "场景4: 未写入部署记录"
}

RC4=0
run_remote_backup_failure "$REMOTE_DEPLOY" || RC4=$?
assert_backup_boundary "$RC4"

# 仅修改临时副本，模拟忽略备份失败；同一组边界断言必须失败。
MUTANT="$WORK/remote-deploy-ignore-backup.sh"
python3 - "$REMOTE_DEPLOY" "$MUTANT" <<'PYTHON'
import pathlib
import sys
source = pathlib.Path(sys.argv[1]).read_text()
needle = '  bash -lc "$DB_BACKUP_COMMAND"\n'
if source.count(needle) != 1:
    raise SystemExit("备份调用必须唯一，无法构造反向验证")
pathlib.Path(sys.argv[2]).write_text(source.replace(needle, needle.rstrip('\n') + ' || true\n'))
PYTHON
MUTANT_RC=0
run_remote_backup_failure "$MUTANT" || MUTANT_RC=$?
assert_file_exists "$WORK/after-backup.marker" "场景4反向验证: 忽略备份失败确实进入后续阶段"
BOUNDARY_RC=0
(assert_backup_boundary "$MUTANT_RC") >"$WORK/mutant-boundary.log" 2>&1 || BOUNDARY_RC=$?
assert_true "$([[ $BOUNDARY_RC -ne 0 ]] && echo 1 || echo 0)" "场景4反向验证: 同一组边界断言拒绝忽略备份失败"
assert_contains "$(cat "$WORK/mutant-boundary.log")" "[FAIL] 场景4: 保留备份退出码7" "场景4反向验证: 失败原因是备份退出码丢失"

# --- 场景 5：静态回归断言（防止内联管道回潮）---
DEPLOY_CONTENT="$(cat "$DEPLOY_SCRIPT")"
BACKUP_CONTENT="$(cat "$BACKUP_SCRIPT")"
assert_contains "$DEPLOY_CONTENT" "backup-db-prod.sh" "场景5: deploy-prod.sh 调用独立备份脚本"
assert_contains "$DEPLOY_CONTENT" 'backup-db-prod.sh" "$PROD_HOST:$PROD_APP_ROOT/bin/"' "场景5: 备份脚本上传到 bin/ 权威位置"
assert_not_contains "$DEPLOY_CONTENT" 'mysqldump -h"${DB_HOST' "场景5: deploy-prod.sh 不再内联 mysqldump 管道"
assert_contains "$BACKUP_CONTENT" "--single-transaction" "场景5: 备份脚本使用 --single-transaction"
assert_contains "$BACKUP_CONTENT" "Dump completed" "场景5: 备份脚本校验 Dump completed"

echo
echo "prod-db-backup fail-closed contract: ${PASS_COUNT} assertions passed"
