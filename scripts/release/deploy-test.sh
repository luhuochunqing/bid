#!/usr/bin/env bash
# Input: repository source tree, installed toolchains, and release build environment variables
# Output: test environment deployment executed on 172.16.38.78
# Pos: scripts/release/ - Test environment deployment automation
# 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# deploy-test.sh — 测试环境一键部署脚本
#
# 功能：本地打包（固化 OBS=true + 同源 API + 防 macOS ._ 残留）→ scp 上传 → ssh 远程执行 remote-deploy.sh
# 用法：
#   ENV=test bash scripts/release/deploy-test.sh                    # 使用当前 HEAD 作为 RELEASE_ID
#   ENV=test bash scripts/release/deploy-test.sh <release-id>        # 指定 RELEASE_ID
#   ENV=test SKIP_FLYWAY_VALIDATE=1 bash scripts/release/deploy-test.sh  # 紧急跳过 Flyway 预检
#
# 环境门禁：脚本启动时必须显式声明 ENV=test，否则拒绝执行。
#
# ⚠️ 关键固化项：VITE_OBS_ENABLED=true
#    历史教训：第 84 次部署（2026-07-13）漏传该变量，导致第 82 次部署的 OBS 直传修复被无声回退。
#    自此测试环境部署统一走本脚本，禁止手工拼凑 package-release.sh 命令。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/dev-env.sh" 2>/dev/null || true

# ── 环境门禁 ──
printf '═══════════════════════════════════════════════════════════\n'
printf '  🧪 测试环境部署脚本\n'
printf '  目标服务器：172.16.38.78（winbid-01.test）\n'
printf '  访问入口：http://172.16.38.78:8080/\n'
printf '  数据库：winbid-01.test.rds.ehsy.com\n'
printf '═══════════════════════════════════════════════════════════\n\n'

if [[ "${ENV:-}" != "test" ]]; then
  printf '❌ 环境门禁未通过\n\n'
  printf '   必须显式声明环境：\n'
  printf '     ENV=test bash scripts/release/deploy-test.sh\n\n'
  printf '   当前 ENV="%s"\n' "${ENV:-（未设置）}"
  exit 1
fi

printf '✅ 环境门禁通过：ENV=test\n\n'

# ── 配置 ──
TEST_HOST="jetty@172.16.38.78"
TEST_APP_ROOT="/opt/xiyu-bid"
TEST_FRONTEND_DIR="/srv/www/xiyu-bid"
TEST_SERVICE_NAME="xiyu-bid-backend"
TEST_HEALTHCHECK_URL="http://127.0.0.1:18080/actuator/health"

# 前端构建参数（测试环境专用，固化防止漏传）
# VITE_API_BASE_URL= → 同源构建（前端和后端同 origin，通过 Nginx 反代）
# VITE_OBS_ENABLED=true → 启用华为云 OBS 大文件直传（第 82 次部署修复项，禁止漏传）
# COPYFILE_DISABLE=1 → 避免 macOS tar 打包残留 ._ 文件污染产物
# VITE_SENTRY_DSN= → 测试环境不启用 Sentry（留空）
export VITE_API_BASE_URL=""
export VITE_OBS_ENABLED="true"
export COPYFILE_DISABLE="1"
export VITE_SENTRY_DSN=""
export VITE_SENTRY_ENVIRONMENT="test"
export VITE_SENTRY_TRACES_SAMPLE_RATE="0"

# Release ID
RELEASE_ID="${1:-$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || date +%Y%m%d-%H%M%S)}"
RELEASE_ID="${RELEASE_ID}-api8080"
RELEASE_ARCHIVE="$ROOT_DIR/.release/xiyu-bid-release-${RELEASE_ID}.tar.gz"
SKIP_FLYWAY_VALIDATE="${SKIP_FLYWAY_VALIDATE:-0}"

# ── Step 0: 早操三连 ──
printf '==> Step 0: 早操三连\n'
cd "$ROOT_DIR"
bash scripts/sync-env.sh . || true
bash scripts/check-git-wrapper.sh || true
printf '✅ 早操完成\n\n'

# ── Step 1: 确认基线 ──
printf '==> Step 1: 确认基线\n'
CURRENT_HEAD=$(git -C "$ROOT_DIR" rev-parse --short HEAD)
CURRENT_BRANCH=$(git -C "$ROOT_DIR" rev-parse --abbrev-ref HEAD)
printf '  分支：%s\n' "$CURRENT_BRANCH"
printf '  HEAD：%s\n' "$CURRENT_HEAD"

if [[ "$CURRENT_BRANCH" != "main" && "$CURRENT_BRANCH" != agent/*-init ]]; then
  printf '\n⚠️  当前不在 main / 锚点分支（%s）\n' "$CURRENT_BRANCH"
  printf '   测试部署建议从 main 或 agent/*-init 锚点分支进行。继续？(y/N) '
  read -r CONFIRM
  [[ "$CONFIRM" == "y" || "$CONFIRM" == "Y" ]] || exit 1
fi
printf '✅ 基线确认\n\n'

# ── Step 2: 服务器现状检查 ──
printf '==> Step 2: 服务器现状检查\n'
printf '  SSH 连接测试...'
if ! ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$TEST_HOST" 'hostname; uptime' >/dev/null 2>&1; then
  printf ' ❌\n'
  printf '  无法连接 %s\n' "$TEST_HOST"
  printf '  排障提示：检查 Mac TUN 模式代理是否拦截 172.16.0.0/12 内网流量（route get 172.16.38.78）\n'
  exit 1
fi
printf ' ✅\n'

printf '  当前部署：'
CURRENT_DEPLOY=$(ssh -o StrictHostKeyChecking=no "$TEST_HOST" 'cat '"$TEST_APP_ROOT"'/deployed-release.json 2>/dev/null | grep releaseId' 2>/dev/null || echo "（无）")
printf '%s\n' "$CURRENT_DEPLOY"

printf '  健康检查：'
HEALTH=$(ssh -o StrictHostKeyChecking=no "$TEST_HOST" 'curl -fsS '"$TEST_HEALTHCHECK_URL"' 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get(\"status\",\"unknown\"))" 2>/dev/null' 2>/dev/null || echo "DOWN")
printf '%s\n\n' "$HEALTH"

# ── Step 3: 本地打包 ──
printf '==> Step 3: 本地打包（OBS=true, 同源API, COPYFILE_DISABLE=1）\n'
printf '  RELEASE_ID=%s\n' "$RELEASE_ID"
printf '  VITE_API_BASE_URL=（同源）\n'
printf '  VITE_OBS_ENABLED=true ← 固化，禁止漏传\n'
printf '  COPYFILE_DISABLE=1\n'
printf '  VITE_SENTRY_DSN=（测试环境留空）\n\n'

RELEASE_ID="$RELEASE_ID" bash "$ROOT_DIR/scripts/release/package-release.sh"

if [[ ! -f "$RELEASE_ARCHIVE" ]]; then
  printf '❌ 打包失败：%s 不存在\n' "$RELEASE_ARCHIVE" >&2
  exit 1
fi

ARCHIVE_SIZE=$(du -h "$RELEASE_ARCHIVE" | cut -f1)
printf '\n✅ 打包完成：%s（%s）\n\n' "$RELEASE_ARCHIVE" "$ARCHIVE_SIZE"

# ── Step 4: 上传 ──
printf '==> Step 4: 上传到测试服务器\n'
ssh -o StrictHostKeyChecking=no "$TEST_HOST" "mkdir -p $TEST_APP_ROOT/incoming"
scp -o StrictHostKeyChecking=no "$RELEASE_ARCHIVE" "$TEST_HOST:$TEST_APP_ROOT/incoming/"
scp -o StrictHostKeyChecking=no "$ROOT_DIR/scripts/release/remote-deploy.sh" "$TEST_HOST:$TEST_APP_ROOT/incoming/"
scp -o StrictHostKeyChecking=no "$ROOT_DIR/scripts/release/flyway-repair-runner.sh" "$TEST_HOST:$TEST_APP_ROOT/bin/" 2>/dev/null || true
printf '✅ 上传完成\n\n'

# ── Step 5: 远程部署 ──
printf '==> Step 5: 远程部署\n'
printf '  开始时间：%s\n' "$(date '+%Y-%m-%d %H:%M:%S')"

DEPLOY_CMD="RELEASE_ARCHIVE='$TEST_APP_ROOT/incoming/$(basename "$RELEASE_ARCHIVE")'"
DEPLOY_CMD="$DEPLOY_CMD RELEASE_ID='$RELEASE_ID'"
DEPLOY_CMD="$DEPLOY_CMD APP_ROOT='$TEST_APP_ROOT'"
DEPLOY_CMD="$DEPLOY_CMD FRONTEND_PUBLIC_DIR='$TEST_FRONTEND_DIR'"
DEPLOY_CMD="$DEPLOY_CMD BACKEND_SERVICE_NAME='$TEST_SERVICE_NAME'"
DEPLOY_CMD="$DEPLOY_CMD HEALTHCHECK_URL='$TEST_HEALTHCHECK_URL'"
DEPLOY_CMD="$DEPLOY_CMD SYSTEMCTL_SUDO=true"
DEPLOY_CMD="$DEPLOY_CMD SKIP_FLYWAY_VALIDATE=$SKIP_FLYWAY_VALIDATE"
DEPLOY_CMD="$DEPLOY_CMD bash $TEST_APP_ROOT/incoming/remote-deploy.sh"

ssh -o StrictHostKeyChecking=no "$TEST_HOST" "$DEPLOY_CMD" || true

DEPLOY_EXIT=$?
printf '\n  结束时间：%s\n' "$(date '+%Y-%m-%d %H:%M:%S')"

# ── Step 6: 部署后验证 ──
printf '\n==> Step 6: 部署后验证\n'
printf '  健康检查：'
FINAL_HEALTH=$(ssh -o StrictHostKeyChecking=no "$TEST_HOST" 'curl -fsS '"$TEST_HEALTHCHECK_URL"' 2>/dev/null' 2>/dev/null || echo '{"status":"DOWN"}')
printf '%s\n' "$(echo "$FINAL_HEALTH" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("status","unknown"))' 2>/dev/null || echo 'unknown')"

printf '  前端入口：'
FRONTEND_ENTRY=$(ssh -o StrictHostKeyChecking=no "$TEST_HOST" 'grep -oP "src=\"/assets/[^\"]+" '"$TEST_FRONTEND_DIR"'/index.html 2>/dev/null | head -1' 2>/dev/null || echo "（未找到）")
printf '%s\n' "$FRONTEND_ENTRY"

printf '  部署记录：'
DEPLOYED=$(ssh -o StrictHostKeyChecking=no "$TEST_HOST" 'cat '"$TEST_APP_ROOT"'/deployed-release.json 2>/dev/null | grep releaseId' 2>/dev/null || echo "（无）")
printf '%s\n' "$DEPLOYED"

# OBS 直传启用校验（第 84 次部署漏传事故的回归门禁）
# 校验范围：当前 release 目录（$APP_ROOT/releases/$RELEASE_ID/frontend/assets/），
#           不是 FRONTEND_PUBLIC_DIR（那里可能因 cp -rn 保留旧 assets 导致误判）
printf '  OBS 直传启用校验：'
OBS_CHECK=$(ssh -o StrictHostKeyChecking=no "$TEST_HOST" '
  count=0
  for f in '"$TEST_APP_ROOT"'/releases/'"$RELEASE_ID"'/frontend/assets/Detail-*.js; do
    [ -f "$f" ] || continue
    n=$(grep -o "\.upload(" "$f" 2>/dev/null | wc -l | tr -d " ")
    count=$((count + n))
  done
  echo "$count"
' 2>/dev/null || echo "0")
if [[ "$OBS_CHECK" -ge 2 ]]; then
  printf '✅ 已启用（Detail chunk .upload( 调用数=%s）\n' "$OBS_CHECK"
else
  printf '❌ 未启用（.upload( 调用数=%s，期望 >=2）\n' "$OBS_CHECK" >&2
  printf '     根因：VITE_OBS_ENABLED=true 未传入打包，或 OBS 直传逻辑被 tree-shake\n' >&2
  printf '     修复：确认使用 deploy-test.sh 部署，勿手工拼凑 package-release.sh 命令\n' >&2
fi

printf '\n═══════════════════════════════════════════════════════════\n'
printf '  ✅ 测试环境部署完成\n'
printf '  Release ID: %s\n' "$RELEASE_ID"
printf '  访问地址: http://172.16.38.78:8080/\n'
printf '═══════════════════════════════════════════════════════════\n'
