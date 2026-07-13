#!/usr/bin/env bash
# Input: repository source tree, installed toolchains, and release build environment variables
# Output: production deployment executed on 172.16.10.149
# Pos: scripts/release/ - Production deployment automation
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# deploy-prod.sh — 生产环境一键部署脚本
#
# 功能：本地打包（注入 OBS + Sentry + 同源 API）→ scp 上传 → ssh 远程执行 remote-deploy.sh
# 用法：
#   bash scripts/release/deploy-prod.sh                    # 使用当前 HEAD 作为 RELEASE_ID
#   bash scripts/release/deploy-prod.sh <release-id>        # 指定 RELEASE_ID
#   SKIP_FLYWAY_VALIDATE=1 bash scripts/release/deploy-prod.sh  # 紧急跳过 Flyway 预检
#
# 环境门禁：脚本启动时必须显式声明 ENV=prod，否则拒绝执行。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/dev-env.sh" 2>/dev/null || true

# ── 环境门禁 ──
printf '═══════════════════════════════════════════════════════════\n'
printf '  ⚠️  生产环境部署脚本\n'
printf '  目标服务器：172.16.10.149（winbid-01.prod）\n'
printf '  域名：https://winbid.ehsy.com/\n'
printf '  数据库：winbid-01.prod.rds.ehsy.com（生产库，Flyway 增量迁移）\n'
printf '═══════════════════════════════════════════════════════════\n\n'

if [[ "${ENV:-}" != "prod" ]]; then
  printf '❌ 环境门禁未通过\n\n'
  printf '   必须显式声明环境：\n'
  printf '     ENV=prod bash scripts/release/deploy-prod.sh\n\n'
  printf '   当前 ENV="%s"\n' "${ENV:-（未设置）}"
  exit 1
fi

printf '✅ 环境门禁通过：ENV=prod\n\n'

# ── 配置 ──
PROD_HOST="jetty@172.16.10.149"
PROD_APP_ROOT="/opt/xiyu-bid"
PROD_FRONTEND_DIR="/srv/www/xiyu-bid"
PROD_SERVICE_NAME="xiyu-bid-backend"
PROD_HEALTHCHECK_URL="http://127.0.0.1:18080/actuator/health"

# 前端构建参数（生产专用）
# VITE_API_BASE_URL= → 同源构建（前端和后端同 origin，通过 Nginx 反代）
# VITE_OBS_ENABLED=true → 启用华为云 OBS 大文件直传
# VITE_SENTRY_DSN → 前端 Sentry 错误追踪
export VITE_API_BASE_URL=""
export VITE_OBS_ENABLED="true"
export VITE_SENTRY_DSN="https://afe598346bea591afeabcefe91562d9b@o4511652658937856.ingest.us.sentry.io/4511652674076672"
export VITE_SENTRY_ENVIRONMENT="production"
export VITE_SENTRY_TRACES_SAMPLE_RATE="0.1"

# Release ID
RELEASE_ID="${1:-$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || date +%Y%m%d-%H%M%S)}"
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

if [[ "$CURRENT_BRANCH" != "main" ]]; then
  printf '\n⚠️  当前不在 main 分支（%s）\n' "$CURRENT_BRANCH"
  printf '   生产部署建议从 main 分支进行。继续？(y/N) '
  read -r CONFIRM
  [[ "$CONFIRM" == "y" || "$CONFIRM" == "Y" ]] || exit 1
fi
printf '✅ 基线确认\n\n'

# ── Step 2: 服务器现状检查 ──
printf '==> Step 2: 服务器现状检查\n'
printf '  SSH 连接测试...'
if ! ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$PROD_HOST" 'hostname; uptime' >/dev/null 2>&1; then
  printf ' ❌\n'
  printf '  无法连接 %s\n' "$PROD_HOST"
  exit 1
fi
printf ' ✅\n'

printf '  当前部署：'
CURRENT_DEPLOY=$(ssh -o StrictHostKeyChecking=no "$PROD_HOST" 'cat '"$PROD_APP_ROOT"'/deployed-release.json 2>/dev/null | grep releaseId' 2>/dev/null || echo "（无）")
printf '%s\n' "$CURRENT_DEPLOY"

printf '  健康检查：'
HEALTH=$(ssh -o StrictHostKeyChecking=no "$PROD_HOST" 'curl -fsS '"$PROD_HEALTHCHECK_URL"' 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get(\"status\",\"unknown\"))" 2>/dev/null' 2>/dev/null || echo "DOWN")
printf '%s\n\n' "$HEALTH"

# ── Step 3: 本地打包 ──
printf '==> Step 3: 本地打包（OBS=true, Sentry=true, 同源API）\n'
printf '  RELEASE_ID=%s\n' "$RELEASE_ID"
printf '  VITE_API_BASE_URL=（同源）\n'
printf '  VITE_OBS_ENABLED=true\n'
printf '  VITE_SENTRY_DSN=（已配置）\n\n'

RELEASE_ID="$RELEASE_ID" bash "$ROOT_DIR/scripts/release/package-release.sh"

if [[ ! -f "$RELEASE_ARCHIVE" ]]; then
  printf '❌ 打包失败：%s 不存在\n' "$RELEASE_ARCHIVE" >&2
  exit 1
fi

ARCHIVE_SIZE=$(du -h "$RELEASE_ARCHIVE" | cut -f1)
printf '\n✅ 打包完成：%s（%s）\n\n' "$RELEASE_ARCHIVE" "$ARCHIVE_SIZE"

# ── Step 4: 上传 ──
printf '==> Step 4: 上传到生产服务器\n'
ssh -o StrictHostKeyChecking=no "$PROD_HOST" "mkdir -p $PROD_APP_ROOT/incoming"
scp -o StrictHostKeyChecking=no "$RELEASE_ARCHIVE" "$PROD_HOST:$PROD_APP_ROOT/incoming/"
scp -o StrictHostKeyChecking=no "$ROOT_DIR/scripts/release/remote-deploy.sh" "$PROD_HOST:$PROD_APP_ROOT/incoming/"
scp -o StrictHostKeyChecking=no "$ROOT_DIR/scripts/release/flyway-repair-runner.sh" "$PROD_HOST:$PROD_APP_ROOT/bin/" 2>/dev/null || true
printf '✅ 上传完成\n\n'

# ── Step 5: 远程部署 ──
printf '==> Step 5: 远程部署\n'
printf '  开始时间：%s\n' "$(date '+%Y-%m-%d %H:%M:%S')"

DEPLOY_CMD="RELEASE_ARCHIVE='$PROD_APP_ROOT/incoming/$(basename "$RELEASE_ARCHIVE")'"
DEPLOY_CMD="$DEPLOY_CMD RELEASE_ID='$RELEASE_ID'"
DEPLOY_CMD="$DEPLOY_CMD APP_ROOT='$PROD_APP_ROOT'"
DEPLOY_CMD="$DEPLOY_CMD FRONTEND_PUBLIC_DIR='$PROD_FRONTEND_DIR'"
DEPLOY_CMD="$DEPLOY_CMD BACKEND_SERVICE_NAME='$PROD_SERVICE_NAME'"
DEPLOY_CMD="$DEPLOY_CMD HEALTHCHECK_URL='$PROD_HEALTHCHECK_URL'"
DEPLOY_CMD="$DEPLOY_CMD SYSTEMCTL_SUDO=true"
DEPLOY_CMD="$DEPLOY_CMD SKIP_FLYWAY_VALIDATE=$SKIP_FLYWAY_VALIDATE"
DEPLOY_CMD="$DEPLOY_CMD bash $PROD_APP_ROOT/incoming/remote-deploy.sh"

# set -euo pipefail 下 ssh 失败会立即退出脚本，导致 Step 6 / L3 OBS 校验无法执行。
# 用 `|| DEPLOY_EXIT=$?` 捕获退出码（既不让脚本立即退出，也不丢失状态）。
# 对比 deploy-test.sh L148 的 `|| true`：那里 DEPLOY_EXIT 永远为 0（丢失状态），
# 但 deploy-test.sh 无 if 判断所以不影响；deploy-prod.sh 有失败诊断分支，必须保留真实退出码。
DEPLOY_EXIT=0
ssh -o StrictHostKeyChecking=no "$PROD_HOST" "$DEPLOY_CMD" || DEPLOY_EXIT=$?
printf '\n  结束时间：%s\n' "$(date '+%Y-%m-%d %H:%M:%S')"

if [[ $DEPLOY_EXIT -ne 0 ]]; then
  printf '\n❌ 部署脚本退出码 %d\n' "$DEPLOY_EXIT" >&2
  printf '   可能原因：\n' >&2
  printf '   1. Flyway validate 失败 → 用 SKIP_FLYWAY_VALIDATE=1 重试（不推荐）\n' >&2
  printf '   2. 健康检查超时 → 检查服务状态：ssh %s "sudo systemctl status %s"\n' "$PROD_HOST" "$PROD_SERVICE_NAME" >&2
  printf '   3. Kafka SDK 启动延迟 → 等待 4 分钟后手动检查健康状态\n' >&2
  printf '\n   ⚠️  部署失败，继续执行 Step 6 校验以收集 OBS 诊断信息\n' >&2
fi

# ── Step 6: 验证 ──
printf '\n==> Step 6: 部署后验证\n'
printf '  健康检查：'
FINAL_HEALTH=$(ssh -o StrictHostKeyChecking=no "$PROD_HOST" 'curl -fsS '"$PROD_HEALTHCHECK_URL"' 2>/dev/null' 2>/dev/null || echo '{"status":"DOWN"}')
printf '%s\n' "$(echo "$FINAL_HEALTH" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("status","unknown"))' 2>/dev/null || echo 'unknown')"

printf '  前端入口：'
FRONTEND_ENTRY=$(ssh -o StrictHostKeyChecking=no "$PROD_HOST" 'grep -oP "src=\"/assets/[^\"]+" '"$PROD_FRONTEND_DIR"'/index.html 2>/dev/null | head -1' 2>/dev/null || echo "（未找到）")
printf '%s\n' "$FRONTEND_ENTRY"

printf '  部署记录：'
DEPLOYED=$(ssh -o StrictHostKeyChecking=no "$PROD_HOST" 'cat '"$PROD_APP_ROOT"'/deployed-release.json 2>/dev/null | grep releaseId' 2>/dev/null || echo "（无）")
printf '%s\n' "$DEPLOYED"

# OBS 直传启用校验（第 8 次生产部署漏传 VITE_OBS_ENABLED=true 事故的回归门禁）
# 作用：即使打包命令漏传，部署后也能立即发现并报警
printf '  OBS 直传启用校验：'
OBS_CHECK=$(ssh -o StrictHostKeyChecking=no "$PROD_HOST" '
  count=0
  for f in '"$PROD_FRONTEND_DIR"'/assets/Detail-*.js; do
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
  printf '     修复：必须使用 ENV=prod bash scripts/release/deploy-prod.sh 部署，禁止手工拼凑 package-release.sh 命令\n' >&2
  printf '     历史：第 8 次生产部署（df9adabad, 2026-07-12）漏传导致 OBS 直传失效\n' >&2
fi

# 部署失败时退出（放在 L3 OBS 校验之后，确保诊断信息已收集后再退出）
if [[ $DEPLOY_EXIT -ne 0 ]]; then
  exit $DEPLOY_EXIT
fi

printf '\n═══════════════════════════════════════════════════════════\n'
printf '  ✅ 生产环境部署完成\n'
printf '  Release ID: %s\n' "$RELEASE_ID"
printf '  访问地址: https://winbid.ehsy.com/\n'
printf '═══════════════════════════════════════════════════════════\n'
