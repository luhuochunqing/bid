#!/usr/bin/env bash
# Input: uploaded release archive path, remote host deployment paths, service restart configuration, backend.env AI_PROVIDER guard, optional DB_BACKUP_COMMAND (pre-activation DB backup), and optional FLYWAY_REPAIR_RUNNER (defaults to $APP_ROOT/bin/flyway-repair-runner.sh, the authoritative runner location)
# Output: pre-activation DB backup + Flyway validate + atomically activated release files, restarted backend service, and remote health verification
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

required_env=(RELEASE_ARCHIVE APP_ROOT FRONTEND_PUBLIC_DIR BACKEND_SERVICE_NAME HEALTHCHECK_URL)
for name in "${required_env[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Missing required env: %s\n' "$name" >&2
    exit 1
  fi
done

if [[ ! -f "$RELEASE_ARCHIVE" ]]; then
  printf 'Release archive not found: %s\n' "$RELEASE_ARCHIVE" >&2
  exit 1
fi

RELEASE_ID="${RELEASE_ID:-$(date +%Y%m%d-%H%M%S)}"
RELEASES_DIR="${RELEASES_DIR:-$APP_ROOT/releases}"
BACKEND_RUNTIME_DIR="${BACKEND_RUNTIME_DIR:-$APP_ROOT/shared/backend}"
BACKEND_JAR_PATH="${BACKEND_JAR_PATH:-$BACKEND_RUNTIME_DIR/app.jar}"
DEPLOYED_RELEASE_RECORD="${DEPLOYED_RELEASE_RECORD:-$APP_ROOT/deployed-release.json}"
SYSTEMCTL_BIN="${SYSTEMCTL_BIN:-systemctl}"
# 默认 true：jetty 用户已配置 NOPASSWD sudo（第 15 次部署事故后调整）
# 如需禁用 sudo，显式传 SYSTEMCTL_SUDO=false
SYSTEMCTL_SUDO="${SYSTEMCTL_SUDO:-true}"
POST_DEPLOY_COMMAND="${POST_DEPLOY_COMMAND:-}"
DB_BACKUP_COMMAND="${DB_BACKUP_COMMAND:-}"
PENDING_FRONTEND_DIR="${FRONTEND_PUBLIC_DIR}.pending"
RELEASE_DIR="$RELEASES_DIR/$RELEASE_ID"

assert_not_nested_under() {
  local label="$1"
  local candidate="$2"
  local forbidden_root="$3"

  if [[ "$candidate" == "$forbidden_root" || "$candidate" == "$forbidden_root"/* ]]; then
    printf '%s must not live under %s: %s\n' "$label" "$forbidden_root" "$candidate" >&2
    exit 1
  fi
}

write_deployed_release_record() {
  local metadata_source="$RELEASE_DIR/release-metadata.json"
  local activated_at
  activated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

  if [[ -f "$metadata_source" ]]; then
    {
      printf '{\n'
      printf '  "releaseId": "%s",\n' "$RELEASE_ID"
      printf '  "activatedAt": "%s",\n' "$activated_at"
      printf '  "releaseDir": "%s",\n' "$RELEASE_DIR"
      printf '  "frontendPublicDir": "%s",\n' "$FRONTEND_PUBLIC_DIR"
      printf '  "backendJarPath": "%s",\n' "$BACKEND_JAR_PATH"
      printf '  "backendServiceName": "%s",\n' "$BACKEND_SERVICE_NAME"
      printf '  "healthcheckUrl": "%s",\n' "$HEALTHCHECK_URL"
      printf '  "packageMetadata": '
      sed 's/^/  /' "$metadata_source"
      printf '\n}\n'
    } > "$DEPLOYED_RELEASE_RECORD"
  else
    cat > "$DEPLOYED_RELEASE_RECORD" <<EOF
{
  "releaseId": "$RELEASE_ID",
  "activatedAt": "$activated_at",
  "releaseDir": "$RELEASE_DIR",
  "frontendPublicDir": "$FRONTEND_PUBLIC_DIR",
  "backendJarPath": "$BACKEND_JAR_PATH",
  "backendServiceName": "$BACKEND_SERVICE_NAME",
  "healthcheckUrl": "$HEALTHCHECK_URL"
}
EOF
  fi
}

run_systemctl() {
  if [[ "$SYSTEMCTL_SUDO" == "true" ]]; then
    sudo "$SYSTEMCTL_BIN" "$@"
  else
    "$SYSTEMCTL_BIN" "$@"
  fi
}

printf '==> Preparing remote release directories\n'
mkdir -p "$RELEASES_DIR" "$BACKEND_RUNTIME_DIR" "$FRONTEND_PUBLIC_DIR"
assert_not_nested_under "FRONTEND_PUBLIC_DIR" "$FRONTEND_PUBLIC_DIR" "$RELEASES_DIR"
assert_not_nested_under "BACKEND_RUNTIME_DIR" "$BACKEND_RUNTIME_DIR" "$RELEASES_DIR"
assert_not_nested_under "BACKEND_JAR_PATH" "$BACKEND_JAR_PATH" "$RELEASES_DIR"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

printf '==> Extracting %s\n' "$RELEASE_ARCHIVE"
tar -xzf "$RELEASE_ARCHIVE" -C "$RELEASE_DIR"

if [[ -n "$DB_BACKUP_COMMAND" ]]; then
  printf '==> Running backup command\n'
  bash -lc "$DB_BACKUP_COMMAND"
fi

# ── Sidecar 容器增量自动升级（闭环自动化，消除技术债）──
SIDECAR_ARCHIVE="$RELEASE_DIR/sidecar/sidecar.tar.gz"
if [[ -f "$SIDECAR_ARCHIVE" ]]; then
  SIDECAR_DIR="$APP_ROOT/sidecar"
  mkdir -p "$SIDECAR_DIR"
  # 内容指纹优先：package-release.sh 已把"文件内容哈希"写入 release-metadata.json（同一算法
  # 单一来源，避免两处重复实现）。2026-10-22 第 228 次事故：归档字节哈希受 gzip 头时间戳
  # 影响，sidecar 源码零变更也会漂移，误触发 docker 重建；历史包无该字段时回退归档字节哈希。
  # 必须先判存在：metadata 缺失时 sed 退出码 2，在 set -euo pipefail 下会直接中断部署，
  # 使下面的回退分支永远执行不到。
  NEW_SIDECAR_HASH=""
  if [[ -f "$RELEASE_DIR/release-metadata.json" ]]; then
    NEW_SIDECAR_HASH="$(sed -n 's/.*"sidecarSha256"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$RELEASE_DIR/release-metadata.json" | head -n 1)"
  fi
  if [[ -z "$NEW_SIDECAR_HASH" ]]; then
    NEW_SIDECAR_HASH="$(sha256sum "$SIDECAR_ARCHIVE" 2>/dev/null | cut -d' ' -f1 || shasum -a 256 "$SIDECAR_ARCHIVE" | cut -d' ' -f1)"
  fi
  OLD_SIDECAR_HASH="$(cat "$SIDECAR_DIR/.deployed_sha256" 2>/dev/null || echo "")"
  if [[ "$NEW_SIDECAR_HASH" != "$OLD_SIDECAR_HASH" ]]; then
    printf '==> Sidecar 源码有变动（%s → %s），更新并重启容器\n' "${OLD_SIDECAR_HASH:-初始}" "$NEW_SIDECAR_HASH"
    tar -xzf "$SIDECAR_ARCHIVE" -C "$SIDECAR_DIR"
    find "$SIDECAR_DIR" -name "._*" -delete 2>/dev/null || true
    DOCKER_BIN="docker"
    if ! docker ps >/dev/null 2>&1 && sudo docker ps >/dev/null 2>&1; then
      DOCKER_BIN="sudo docker"
    fi
    if $DOCKER_BIN info >/dev/null 2>&1; then
      printf '==> 重新构建 document-converter-sidecar 镜像...\n'
      $DOCKER_BIN build --progress=plain -f "$SIDECAR_DIR/Dockerfile.cn" -t document-converter-sidecar:latest "$SIDECAR_DIR"
      printf '==> 重新启动 xiyu-sidecar 容器...\n'
      $DOCKER_BIN rm -f xiyu-sidecar 2>/dev/null || true
      # 内存 4g（1373 根治：428MB PDF 全量解析曾以 ~2.98GB RSS 撞 3g 上限被 OOM-kill；
      # 测试服 7.4GB/可用 4.5GB、生产 15GB/可用 11GB，4g 双环境均安全；5g+ 无 swap 下有宿主 OOM 风险）
      # 鉴权：SIDECAR_SHARED_KEY 由部署调用方 source /etc/xiyu-bid/backend.env 提供；
      # 设置后 sidecar 校验 X-Sidecar-Key（后端适配器以同一 key 发送头），未设置则保持开发态不启用。
      # ⚠️ 禁止用 "${SIDECAR_SHARED_KEY:-}" 无条件下发：空字符串会使 sidecar 端
      # _SHARED_KEY 非 None，误启用鉴权导致 401（2026-09-12 解析降级根因）。
      SIDECAR_RUN_ARGS=(run -d --name xiyu-sidecar --restart=always -p 8000:8000 \
        --memory=4g --memory-swap=4g \
        -e SIDECAR_HOST=0.0.0.0 -e SIDECAR_PORT=8000 -e SIDECAR_MAX_UPLOAD_MB=3072)
      if [[ -n "${SIDECAR_SHARED_KEY:-}" ]]; then
        SIDECAR_RUN_ARGS+=(-e "SIDECAR_SHARED_KEY=${SIDECAR_SHARED_KEY}")
      fi
      $DOCKER_BIN "${SIDECAR_RUN_ARGS[@]}" document-converter-sidecar:latest
      printf '%s\n' "$NEW_SIDECAR_HASH" > "$SIDECAR_DIR/.deployed_sha256"
      printf '✅ Sidecar 容器升级完成\n'
      # ── Sidecar 健康门禁（发布闭环 Stage D：后端停止前拦截，失败零影响）──
      # 重建后必须先证明新容器存活 + 版本指纹可读，否则直接退出；
      # 此时旧 jar 仍在运行、前端未切换，服务未中断。
      DEPLOYED_SIDECAR_SCHEMA="$(grep -E '^SCHEMA_VERSION' "$SIDECAR_DIR/table_extractor.py" 2>/dev/null | cut -d'"' -f2 | tr -d "'" || true)"
      printf '==> Sidecar 表格契约版本：schemaVersion=%s（Java v1 期望 1.0 / v2 期望 2.0）\n' "${DEPLOYED_SIDECAR_SCHEMA:-未知}"
      SIDECAR_HEALTH_URL="${SIDECAR_HEALTH_URL:-http://127.0.0.1:8000/health}"
      SIDECAR_HEALTH_OK=0
      for _i in $(seq 1 30); do
        if curl -fsS --connect-timeout 3 "$SIDECAR_HEALTH_URL" 2>/dev/null | grep -q '"up"'; then
          SIDECAR_HEALTH_OK=1
          break
        fi
        sleep 2
      done
      if [[ "$SIDECAR_HEALTH_OK" != "1" ]]; then
        printf '\n❌ Sidecar 健康检查失败（%s，30 次重试）——新容器未存活，停止 rollout。\n' "$SIDECAR_HEALTH_URL" >&2
        printf '   旧版本仍在运行（后端未停止、前端未切换），服务未中断。处置：\n' >&2
        printf '     docker logs --tail 50 xiyu-sidecar\n\n' >&2
        exit 1
      fi
      printf '✅ Sidecar 健康检查通过（%s）\n' "$SIDECAR_HEALTH_URL"
    else
      printf '⚠️  未检测到可用 Docker 守护进程，跳过容器自动构建\n' >&2
    fi
  else
    printf '==> Sidecar 源码无变动（SHA-256: %s），跳过容器构建\n' "$NEW_SIDECAR_HASH"
    DEPLOYED_SIDECAR_SCHEMA="$(grep -E '^SCHEMA_VERSION' "$SIDECAR_DIR/table_extractor.py" 2>/dev/null | cut -d'"' -f2 | tr -d "'" || true)"
    printf '    表格契约版本：schemaVersion=%s\n' "${DEPLOYED_SIDECAR_SCHEMA:-未知}"
fi
fi

# ── AI Provider 守卫（任何服务变更之前，失败零影响）──
# 工程背景（PR !2396 生产风险审计）：ai.provider=mock 时全部 AI 接口静默回落 MockAiProvider
# 返回假数据；生产必须显式配置真实 AI_PROVIDER（首产记录 AI_PROVIDER=custom）。
# backend.env 丢失或重配漏项时在这里拦截，禁止"静默假数据"形态上线。
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/xiyu-bid/backend.env}"
if [[ -f "$BACKEND_ENV_FILE" ]]; then
  ENV_CONTENT="$(cat "$BACKEND_ENV_FILE" 2>/dev/null || sudo cat "$BACKEND_ENV_FILE" 2>/dev/null || true)"
  if [[ -n "$ENV_CONTENT" ]]; then
    DEPLOY_PROFILES="$(printf '%s\n' "$ENV_CONTENT" | sed -n 's/^SPRING_PROFILES_ACTIVE=//p' | tail -n 1)"
    DEPLOY_AI_PROVIDER="$(printf '%s\n' "$ENV_CONTENT" | sed -n 's/^AI_PROVIDER=//p' | tail -n 1)"
    if [[ ",$DEPLOY_PROFILES," == *",prod,"* ]]; then
      if [[ -z "$DEPLOY_AI_PROVIDER" ]]; then
        printf '\n❌ backend.env 缺少 AI_PROVIDER：prod 下 AI 功能会直接报错。\n' >&2
        printf '   处置：在 %s 增加 AI_PROVIDER=custom（见 docs/release/interface-integration-record-2026-07-09-1st-prod.md）。\n' "$BACKEND_ENV_FILE" >&2
        printf '   当前服务未受任何影响，修正后重跑部署。\n\n' >&2
        exit 1
      fi
      if [[ "$DEPLOY_AI_PROVIDER" == "mock" ]]; then
        printf '\n❌ prod 禁止 AI_PROVIDER=mock：全部 AI 接口会静默返回假数据。\n' >&2
        printf '   处置：将 %s 中 AI_PROVIDER 改回真实厂商（如 custom）。\n\n' "$BACKEND_ENV_FILE" >&2
        exit 1
      fi
      printf '✅ AI Provider 守卫通过：AI_PROVIDER=%s\n' "$DEPLOY_AI_PROVIDER"
    fi
  else
    printf '⚠️  无法读取 %s（权限受限），跳过 AI Provider 守卫。\n' "$BACKEND_ENV_FILE"
  fi
else
  printf '⚠️  未找到 %s，跳过 AI Provider 守卫。\n' "$BACKEND_ENV_FILE"
fi

# ── Flyway validate 预检（restart 前置，避免 checksum mismatch 导致启动失败）──
# 工程背景（2026-06-26）：新 jar 启动时若生产 flyway_schema_history 与代码迁移文件 checksum 不一致，
# Spring Boot validateOnMigrate 会让后端拒绝启动，remote-deploy 会卡满 8 分钟 health 超时才失败。
# 本预检在"任何版本切换"之前用当前运行中的 jar 自带 Flyway 跑 validate，失败则停止 rollout，
# 此时旧 jar 仍在运行、前端也未切换（前后端版本一致），操作者可安全用 flyway-repair-runner.sh 处置。
# 依赖：flyway-repair-runner.sh 权威位置为 $APP_ROOT/bin/（deploy-test.sh / deploy-prod.sh 每次部署
# 都会 scp 最新版至此，与 remote-deploy.sh 同步演进）。禁止从 $(dirname $0) 解析：incoming/ 会残留
# 历史旧版 runner（第 155 次部署事故：旧版缺 ignoreMigrationPatterns("*:pending")，把 pending 新迁移
# 误报为 validate 失败）；CI 经 bash -s 执行时 dirname 更不可预测。
# 跳过：SKIP_FLYWAY_VALIDATE=1（紧急/离线场景）
FLYWAY_REPAIR_RUNNER="${FLYWAY_REPAIR_RUNNER:-$APP_ROOT/bin/flyway-repair-runner.sh}"
if [[ "${SKIP_FLYWAY_VALIDATE:-0}" != "1" && -x "$FLYWAY_REPAIR_RUNNER" ]]; then
  printf '==> Flyway validate pre-check (before any version activation)\n'
  # validate 失败时阻止 rollout；此时旧 jar 未被覆盖、前端未切换，前后端版本一致
  if ! bash "$FLYWAY_REPAIR_RUNNER" validate; then
    printf '\n❌ Flyway validate 失败 — 检测到 checksum mismatch 或配置问题。\n' >&2
    printf '   旧版本仍在运行（前后端未切换），服务未中断。处置方案：\n' >&2
    printf '   1. 查看失败详情（上面输出），确认哪些版本 mismatch\n' >&2
    printf '   2. 确认 mismatch 是良性的（仅历史迁移被改），执行 repair：\n' >&2
    printf '        bash %s repair\n' "$FLYWAY_REPAIR_RUNNER" >&2
    printf '   3. repair 后重跑 remote-deploy.sh\n' >&2
    printf '   4. 紧急跳过本预检（不推荐，会让后端启动失败）：SKIP_FLYWAY_VALIDATE=1\n' >&2
    printf '   详见 docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md §13.5\n\n' >&2
    exit 1
  fi
  printf '✅ Flyway validate 通过（仅 pending 新迁移为预期状态）\n\n'
elif [[ "${SKIP_FLYWAY_VALIDATE:-0}" != "1" ]]; then
  printf '⚠️  Flyway validate 预检跳过：flyway-repair-runner.sh 不可用 (%s)\n' "$FLYWAY_REPAIR_RUNNER" >&2
  printf '   权威位置为 %s/bin/（deploy-test.sh / deploy-prod.sh 会自动上传最新版），补齐后重跑部署即可获得 checksum mismatch 前置拦截。\n\n' "$APP_ROOT" >&2
fi

printf '==> Activating frontend assets\n'

# ── 前端目录全层级属主校验（rm 前置，避免无权删除导致部署失败）──
# 工程背景（2026-08-24 第 141 次测试部署）：/srv/www/xiyu-bid/assets/ 子目录属主曾为 nginx:nginx，
# jetty 用户 rm -rf "$FRONTEND_PUBLIC_DIR" 时 Permission denied，部署中断。
# 本校验递归检测目录树属主，若存在非当前用户的条目，则 sudo chown -R 修复后再删除激活。
# SIGPIPE 防护（2026-08-28 第 148 次测试部署）：本脚本 set -euo pipefail，当非属主条目超过 5 个
# （如上一版部署后 sudo cp -rn 保留旧 assets 引入数百个 root 属主文件）时 head 提前退出，
# find 收到 SIGPIPE(exit 141) → pipefail → set -e 静默杀死脚本，chown 修复分支永远执行不到。
# 故 find 侧加 || true 吞掉 SIGPIPE/遍历失败，让下方修复分支正常接手。
DEPLOY_USER="$(id -un)"
if [[ -d "$FRONTEND_PUBLIC_DIR" ]]; then
  NON_OWNED="$( { find "$FRONTEND_PUBLIC_DIR" ! -user "$DEPLOY_USER" -print 2>/dev/null || true; } | head -n 5)"
  if [[ -n "$NON_OWNED" ]]; then
    printf '⚠️  前端目录存在非 %s 属主的条目，执行 sudo chown -R 修复：\n' "$DEPLOY_USER"
    printf '    %s\n' "$NON_OWNED"
    sudo chown -R "$DEPLOY_USER" "$FRONTEND_PUBLIC_DIR"
    printf '✅ 前端目录属主已统一为 %s，可安全删除并激活\n' "$DEPLOY_USER"
  fi
fi

rm -rf "$PENDING_FRONTEND_DIR"
mkdir -p "$PENDING_FRONTEND_DIR"
cp -R "$RELEASE_DIR/frontend/." "$PENDING_FRONTEND_DIR/"
rm -rf "$FRONTEND_PUBLIC_DIR"
mv "$PENDING_FRONTEND_DIR" "$FRONTEND_PUBLIC_DIR"

# 必须先停服务再替换 jar，再启动服务。
# 反例：先 cp 替换 jar 再 systemctl restart 会导致旧进程 ShutdownHook
# 执行时引用已被替换的 jar，触发 NoClassDefFoundError / ClassNotFoundException
# 并卡住 90s 直到 systemd SIGKILL，部署窗口内请求会异常。
# 详见 docs/lessons/lessons-learned.md §5。
printf '==> Stopping backend service %s\n' "$BACKEND_SERVICE_NAME"
run_systemctl stop "$BACKEND_SERVICE_NAME"

# ── 原子 jar 替换（P2 风险修复）──
# 原实现直接 cp 覆盖 app.jar：cp 中途失败（磁盘满/权限）会留下半截 jar，
# set -e 下脚本直接退出，服务停在"已停止"状态且无自动恢复。
# 修复：先 cp 到同目录 .new 临时文件（同文件系统），再 mv 原子替换；
#       任一失败则删除 .new、重启旧服务止损，并以非零码退出让调用方感知。
printf '==> Updating backend artifact (atomic)\n'
if ! cp "$RELEASE_DIR/backend/app.jar" "$BACKEND_JAR_PATH.new"; then
  printf '\n❌ 复制新 jar 失败：%s\n' "$BACKEND_JAR_PATH.new" >&2
  rm -f "$BACKEND_JAR_PATH.new"
  printf '   回滚：重启旧服务 %s\n' "$BACKEND_SERVICE_NAME" >&2
  run_systemctl start "$BACKEND_SERVICE_NAME" || true
  exit 1
fi
if ! mv "$BACKEND_JAR_PATH.new" "$BACKEND_JAR_PATH"; then
  printf '\n❌ 原子替换 jar 失败：%s.new → %s\n' "$BACKEND_JAR_PATH" "$BACKEND_JAR_PATH" >&2
  rm -f "$BACKEND_JAR_PATH.new"
  printf '   回滚：重启旧服务 %s\n' "$BACKEND_SERVICE_NAME" >&2
  run_systemctl start "$BACKEND_SERVICE_NAME" || true
  exit 1
fi

printf '==> Writing deployed release record %s\n' "$DEPLOYED_RELEASE_RECORD"
write_deployed_release_record

printf '==> Starting backend service %s\n' "$BACKEND_SERVICE_NAME"
run_systemctl daemon-reload || true
run_systemctl start "$BACKEND_SERVICE_NAME"
run_systemctl --no-pager --full status "$BACKEND_SERVICE_NAME" || true

printf '==> Waiting for health check %s\n' "$HEALTHCHECK_URL"
# 部分环境 ApplicationReadyEvent（如 OrganizationEvent SDK 注册/Kafka 启动）耗时超过 2 分钟。
# 等待上限默认 240 次（8 分钟）：第 147 次测试部署实测 Kafka SDK readiness 最长约 6 分钟才自恢复，
# 第 152 次约 5 分钟；第 62/69/102/139 次部署均曾撞满 120 次（4 分钟）窗口误报。
# 240 次 = 6 分钟实测 + 2 分钟余量，避免后端已正常启动但脚本提前失败。
#
# 防复发（第 34 次事故）：crash-loop 场景下 systemd 在后端崩溃后立即 restart，
# restart 间隙后端可能短暂响应 /actuator/health 返回 200（Spring Boot 在 logback
# 加载失败前可能短暂启动），单次 curl 成功不能证明服务稳定。
# 修复：必须连续 N 次成功（每次间隔 2 秒）才算健康，避免"启动即崩溃"的误判。
HEALTH_CHECK_CONSECUTIVE_REQUIRED="${HEALTH_CHECK_CONSECUTIVE_REQUIRED:-3}"
HEALTH_CHECK_CONSECUTIVE_SUCCESSES=0
HEALTH_CHECK_TOTAL_ATTEMPTS=0
HEALTH_CHECK_MAX_ATTEMPTS="${HEALTH_CHECK_MAX_ATTEMPTS:-240}"

while [[ $HEALTH_CHECK_TOTAL_ATTEMPTS -lt $HEALTH_CHECK_MAX_ATTEMPTS ]]; do
  HEALTH_CHECK_TOTAL_ATTEMPTS=$((HEALTH_CHECK_TOTAL_ATTEMPTS + 1))
  if curl -fsS "$HEALTHCHECK_URL" >/dev/null 2>&1; then
    HEALTH_CHECK_CONSECUTIVE_SUCCESSES=$((HEALTH_CHECK_CONSECUTIVE_SUCCESSES + 1))
    if [[ $HEALTH_CHECK_CONSECUTIVE_SUCCESSES -ge $HEALTH_CHECK_CONSECUTIVE_REQUIRED ]]; then
      break
    fi
  else
    HEALTH_CHECK_CONSECUTIVE_SUCCESSES=0
  fi
  sleep 2
done

# 循环结束后最终验证（连续成功后再次确认，防 break 后服务崩溃）
if ! curl -fsS "$HEALTHCHECK_URL" >/dev/null 2>&1; then
  printf '\n❌ Health check failed after %d attempts (consecutive successes: %d/%d)\n' \
    "$HEALTH_CHECK_TOTAL_ATTEMPTS" "$HEALTH_CHECK_CONSECUTIVE_SUCCESSES" "$HEALTH_CHECK_CONSECUTIVE_REQUIRED" >&2
  printf '   Service may be in crash-loop. Diagnostic commands:\n' >&2
  printf '     sudo systemctl status %s\n' "$BACKEND_SERVICE_NAME" >&2
  printf '     sudo journalctl -u %s --no-pager -n 50\n' "$BACKEND_SERVICE_NAME" >&2
  printf '\n   Rollback: restore previous jar from %s/releases/<prev-release-id>/backend/app.jar\n' "$APP_ROOT" >&2
  printf '   Then: sudo systemctl restart %s\n\n' "$BACKEND_SERVICE_NAME" >&2
  exit 1
fi

# 检查 systemd 服务状态（必须 active，排除 activating/reloading/restarting/failed）
# 注意：systemd 219（CentOS 7）不支持 --value 参数（systemd 230+ 才引入），
# 用 cut 提取值以兼容旧版 systemd。
SERVICE_ACTIVE_STATE="$(run_systemctl show -p ActiveState "$BACKEND_SERVICE_NAME" 2>/dev/null | cut -d= -f2 || echo unknown)"
SERVICE_SUB_STATE="$(run_systemctl show -p SubState "$BACKEND_SERVICE_NAME" 2>/dev/null | cut -d= -f2 || echo unknown)"
if [[ "$SERVICE_ACTIVE_STATE" != "active" || "$SERVICE_SUB_STATE" == "reloading" || "$SERVICE_SUB_STATE" == "restarting" ]]; then
  printf '\n❌ Service not stable: ActiveState=%s, SubState=%s\n' \
    "$SERVICE_ACTIVE_STATE" "$SERVICE_SUB_STATE" >&2
  printf '   Service may be crash-looping. Check journalctl for details.\n' >&2
  printf '   Rollback: restore previous jar from %s/releases/<prev-release-id>/backend/app.jar\n\n' "$APP_ROOT" >&2
  exit 1
fi
printf '✅ Health check passed (consecutive %d/%d, total attempts: %d, service: %s/%s)\n' \
  "$HEALTH_CHECK_CONSECUTIVE_SUCCESSES" "$HEALTH_CHECK_CONSECUTIVE_REQUIRED" \
  "$HEALTH_CHECK_TOTAL_ATTEMPTS" "$SERVICE_ACTIVE_STATE" "$SERVICE_SUB_STATE"

# ── 前端一致性校验：确保 nginx 根目录与发布包一致 ──
printf '==> Verifying frontend consistency\n'
RELEASE_INDEX_HTML="$RELEASE_DIR/frontend/index.html"
PUBLIC_INDEX_HTML="$FRONTEND_PUBLIC_DIR/index.html"
if [[ -f "$RELEASE_INDEX_HTML" && -f "$PUBLIC_INDEX_HTML" ]]; then
  RELEASE_ENTRY=$(grep -oP 'src="/assets/[^"]+' "$RELEASE_INDEX_HTML" | head -1)
  PUBLIC_ENTRY=$(grep -oP 'src="/assets/[^"]+' "$PUBLIC_INDEX_HTML" | head -1)
  if [[ "$RELEASE_ENTRY" != "$PUBLIC_ENTRY" ]]; then
    printf '⚠️  Frontend mismatch detected! Entry: %s (release) vs %s (public)\n' "$RELEASE_ENTRY" "$PUBLIC_ENTRY"
    printf '==> Auto-fixing frontend: re-syncing from release archive\n'
    rm -rf "$PENDING_FRONTEND_DIR"
    mkdir -p "$PENDING_FRONTEND_DIR"
    cp -R "$RELEASE_DIR/frontend/." "$PENDING_FRONTEND_DIR/"
    rm -rf "$FRONTEND_PUBLIC_DIR"
    mv "$PENDING_FRONTEND_DIR" "$FRONTEND_PUBLIC_DIR"
    printf '✅ Frontend re-synced successfully\n'
  else
    printf '✅ Frontend matches release (%s)\n' "$RELEASE_ENTRY"
  fi
else
  if [[ ! -f "$RELEASE_INDEX_HTML" ]]; then
    printf '⚠️  Release frontend index.html not found — skipping frontend verification\n'
  fi
  if [[ ! -f "$PUBLIC_INDEX_HTML" ]]; then
    printf '⚠️  Public frontend index.html not found at %s — re-syncing\n' "$PUBLIC_INDEX_HTML"
    rm -rf "$PENDING_FRONTEND_DIR"
    mkdir -p "$PENDING_FRONTEND_DIR"
    cp -R "$RELEASE_DIR/frontend/." "$PENDING_FRONTEND_DIR/"
    rm -rf "$FRONTEND_PUBLIC_DIR"
    mv "$PENDING_FRONTEND_DIR" "$FRONTEND_PUBLIC_DIR"
  fi
fi

if [[ -n "$POST_DEPLOY_COMMAND" ]]; then
  printf '==> Running post deploy command\n'
  bash -lc "$POST_DEPLOY_COMMAND"
fi

printf 'Remote deployment completed for release %s\n' "$RELEASE_ID"
