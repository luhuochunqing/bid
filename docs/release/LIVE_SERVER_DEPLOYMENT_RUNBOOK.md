# 客户测试服务器部署流程

本文记录西域数智化投标管理平台当前客户测试服务器的完整部署流程。该流程基于 2026-05-27 实际部署复盘整理，适用于 `172.16.38.78` 这台 `winbid-01.test` 服务器。

> 不要把 Jumpserver、数据库、smoke 账号密码写入本文或提交到仓库。密码只允许从安全渠道获取，并通过交互输入或临时环境变量使用。

## 1. 环境事实

| 项 | 值 |
|---|---|
| 前端访问入口 | `http://172.16.38.78:8080/` |
| Jumpserver 入口 | `tops-login.ehsy.com` |
| Jumpserver 目标资产 | `winbid-01.test` / `172.16.38.78` |
| 服务器用户 | `jetty` |
| 应用根目录 | `/opt/xiyu-bid` |
| 前端生效目录 | `/srv/www/xiyu-bid` |
| 后端 jar | `/opt/xiyu-bid/shared/backend/app.jar` |
| 部署记录 | `/opt/xiyu-bid/deployed-release.json` |
| 后端 systemd 服务 | `xiyu-bid-backend` |
| 后端环境变量文件 | `/etc/xiyu-bid/backend.env` |
| 后端内部端口 | `127.0.0.1:18080` |
| Nginx 对外端口 | `80` / `8080` |
| 对外健康检查 | `http://172.16.38.78:8080/actuator/health` |
| 内部健康检查 | `http://127.0.0.1:8080/actuator/health`、`http://127.0.0.1:18080/actuator/health` |
| MySQL Host | `winbid-01.test.rds.ehsy.com` |
| MySQL DB | `winbid` |
| MySQL User | `ea_bid` |
| Redis Host | `winbid-01.test.redis.ehsy.com` |

服务器只负责运行包，不负责构建。不要在服务器上执行 `npm install`、`mvn package` 或 Git 拉代码发布。

## 2. 发布原则

1. 只从已通过 CI 和 `Main Release Pipeline` 的 `main` 提交发布。
2. 前端生产包必须显式注入 `http://172.16.38.78:8080`，不能发布指向 `127.0.0.1` 或漏掉 `:8080` 的包。
3. 发布前必须完成数据库备份，并保存 `sha256`、备份元信息和 `flyway_schema_history`。
4. 线上真实版本以 `/opt/xiyu-bid/deployed-release.json` 为准。
5. 临时 SSH key、临时下载链接、GitHub 临时 release 必须在发布后清理。

## 3. 本地选择发布提交

在独立 worktree 中执行，避免污染主工作区：

```bash
cd /Users/user/xiyu/worktrees/<deploy-worktree>
git fetch origin main --prune
git reset --hard origin/main
git log --oneline --max-count=5 origin/main
```

确认 GitHub Actions：

```bash
gh run list --repo ericforai/bidding --branch main --limit 8 \
  --json databaseId,headSha,status,conclusion,name,url
```

要求：

- 目标提交的 `CI` 为 `success`
- 目标提交的 `Main Release Pipeline` 为 `success`
- 如果最新提交只改 `.agent-locks/` 等协作元数据，可记录为“不影响运行包”，无需重启线上服务

## 4. 本地打包

```bash
cd /Users/user/xiyu/worktrees/<deploy-worktree>

export RELEASE_SHA="$(git rev-parse --short=8 HEAD)"
export RELEASE_ID="${RELEASE_SHA}-api8080"
export PRODUCTION_API_BASE_URL="http://172.16.38.78:8080"
export VITE_API_BASE_URL="http://172.16.38.78:8080"

npm ci
bash scripts/release/package-release.sh

sha256sum ".release/xiyu-bid-release-${RELEASE_ID}.tar.gz"
sha256sum ".release/${RELEASE_ID}/backend/app.jar"
```

检查前端包的 API 地址：

```bash
rg "172\\.16\\.38\\.78:8080" ".release/${RELEASE_ID}/frontend/assets"/*.js
```

注意：当前前端配置代码中可能仍包含 `127.0.0.1` 作为兜底常量。判断是否可发的关键是构建产物中必须出现实际注入值 `VITE_API_BASE_URL:"http://172.16.38.78:8080"` 或等价内容。

## 5. 进入服务器

优先走 Jumpserver 菜单：

```bash
ssh -o HostKeyAlgorithms=+ssh-rsa \
  -o PubkeyAcceptedAlgorithms=+ssh-rsa \
  winbid@tops-login.ehsy.com
```

进入菜单后搜索或选择 `172.16.38.78` / `winbid-01.test`，进入 `jetty@172.16.38.78` shell。

文件传输建议使用一次性直连 key：

```bash
ssh-keygen -t ed25519 \
  -f "/tmp/xiyu-prod-deploy-${RELEASE_ID}" \
  -N "" \
  -C "xiyu-prod-deploy-${RELEASE_ID}"

cat "/tmp/xiyu-prod-deploy-${RELEASE_ID}.pub"
```

在 Jumpserver 进入的服务器 shell 中追加公钥：

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo '<一次性公钥内容>' >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

本地验证直连：

```bash
ssh -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" \
  -o IdentitiesOnly=yes \
  -o StrictHostKeyChecking=accept-new \
  jetty@172.16.38.78 'hostname; date; whoami'
```

## 6. 服务器预检

```bash
ssh -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" jetty@172.16.38.78 '
  set -e
  systemctl is-active nginx xiyu-bid-backend
  curl -fsS http://127.0.0.1:8080/actuator/health
  curl -fsS http://127.0.0.1:18080/actuator/health
  sha256sum /opt/xiyu-bid/shared/backend/app.jar
  test -d /opt/xiyu-bid
  test -d /srv/www/xiyu-bid
  test -f /etc/xiyu-bid/backend.env
'
```

确认服务器环境变量，输出时不要显示密码：

```bash
ssh -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" jetty@172.16.38.78 '
  sudo grep -E "^(SPRING_PROFILES_ACTIVE|DB_HOST|DB_PORT|DB_NAME|DB_USERNAME|DB_USER|REDIS_HOST|REDIS_PORT|SERVER_PORT|SERVER_ADDRESS|CORS_ALLOWED_ORIGINS)=" /etc/xiyu-bid/backend.env
'
```

期望至少包含：

- `SPRING_PROFILES_ACTIVE=prod,mysql`
- `DB_HOST=winbid-01.test.rds.ehsy.com`
- `DB_NAME=winbid`
- `DB_USERNAME=ea_bid`
- `REDIS_HOST=winbid-01.test.redis.ehsy.com`
- `SERVER_PORT=18080`
- `CORS_ALLOWED_ORIGINS` 包含 `http://172.16.38.78:8080`
- `XIYU_ORG_SYNC_ENABLED=true`
- `XIYU_ORG_EVENT_SDK_ENABLED=true`
- `XIYU_ORG_EVENT_SERVER_REGISTER_URL=http://event-busserver-test.ehsy.com`
- `XIYU_ORG_EVENT_SERVICE_NAME=BidSystemOrgConsumer`
- `XIYU_ORG_DIRECTORY_BASE_URL=https://base-oss-test.ehsy.com`

## 7. 数据库备份

发布前在服务器上执行：

```bash
ssh -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" jetty@172.16.38.78 '
  set -euo pipefail
  ts=$(date +%Y%m%d%H%M%S)
  backup="/opt/xiyu-bid/db-backups/winbid-${RELEASE_ID}-${ts}.sql.gz"

  sudo BACKUP_PATH="$backup" bash -lc '"'"'
    set -euo pipefail
    set -a
    . /etc/xiyu-bid/backend.env
    set +a

    mkdir -p "$(dirname "$BACKUP_PATH")"
    db_user="${DB_USERNAME:-${DB_USER:-}}"

    mysqldump \
      -h "$DB_HOST" \
      -P "${DB_PORT:-3306}" \
      -u "$db_user" \
      -p"$DB_PASSWORD" \
      --single-transaction \
      --routines \
      --triggers \
      "$DB_NAME" | gzip -c > "$BACKUP_PATH"

    sha256sum "$BACKUP_PATH" > "$BACKUP_PATH.sha256"
    mysql \
      -h "$DB_HOST" \
      -P "${DB_PORT:-3306}" \
      -u "$db_user" \
      -p"$DB_PASSWORD" \
      "$DB_NAME" \
      -e "select installed_rank,version,description,type,script,checksum,installed_on,success from flyway_schema_history order by installed_rank" \
      > "$BACKUP_PATH.flyway.tsv"

    flyway_rows=$(($(wc -l < "$BACKUP_PATH.flyway.tsv") - 1))
    {
      printf "backup_path=%s\n" "$BACKUP_PATH"
      printf "created_at=%s\n" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
      printf "db_host=%s\n" "$DB_HOST"
      printf "db_name=%s\n" "$DB_NAME"
      printf "flyway_rows=%s\n" "$flyway_rows"
    } > "$BACKUP_PATH.metadata"

    ls -lh "$BACKUP_PATH"
    cat "$BACKUP_PATH.sha256"
    cat "$BACKUP_PATH.metadata"
  '"'"'
'
```

备份完成后，把路径、大小、SHA256 和 Flyway 行数记录到发布记录中。

## 8. 上传发布包

主路径：上传全量 release archive。

```bash
scp -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" \
  ".release/xiyu-bid-release-${RELEASE_ID}.tar.gz" \
  jetty@172.16.38.78:"/opt/xiyu-bid/incoming/xiyu-bid-release-${RELEASE_ID}.tar.gz"

scp -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" \
  scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:"/opt/xiyu-bid/incoming/remote-deploy-${RELEASE_ID}.sh"
```

如果直连网络抖动，优先尝试：

```bash
rsync -avP \
  -e "ssh -i /tmp/xiyu-prod-deploy-${RELEASE_ID} -o IdentitiesOnly=yes" \
  ".release/xiyu-bid-release-${RELEASE_ID}.tar.gz" \
  jetty@172.16.38.78:"/opt/xiyu-bid/incoming/"
```

如仍无法稳定传输，见本文附录“慢链路应急小包路径”。

## 9. 激活发布

在服务器上执行远端激活脚本：

```bash
ssh -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" jetty@172.16.38.78 "
  set -euo pipefail
  chmod +x /opt/xiyu-bid/incoming/remote-deploy-${RELEASE_ID}.sh

  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-${RELEASE_ID}.tar.gz \
  RELEASE_ID=${RELEASE_ID} \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  BACKEND_RUNTIME_DIR=/opt/xiyu-bid/shared/backend \
  BACKEND_JAR_PATH=/opt/xiyu-bid/shared/backend/app.jar \
  DEPLOYED_RELEASE_RECORD=/opt/xiyu-bid/deployed-release.json \
  HEALTHCHECK_URL=http://127.0.0.1:8080/actuator/health \
  SYSTEMCTL_SUDO=true \
  bash /opt/xiyu-bid/incoming/remote-deploy-${RELEASE_ID}.sh
"
```

脚本会完成：

- 解压发布包到 `/opt/xiyu-bid/releases/<release-id>`
- **Flyway validate 预检**（自 2026-06-26）：覆盖后端 jar 之前，用当前运行 jar 的 Flyway 对生产 DB 跑 validate，失败则停止 rollout。详见 §13.5
- 切换 `/srv/www/xiyu-bid`
- 更新 `/opt/xiyu-bid/shared/backend/app.jar`
- 写入 `/opt/xiyu-bid/deployed-release.json`
- 重启 `xiyu-bid-backend`
- 等待健康检查通过

> **预检依赖**：validate 预检需要 `flyway-repair-runner.sh` 与 `remote-deploy.sh` 一同上传到服务器。`SKIP_FLYWAY_VALIDATE=1` 可跳过（紧急/离线），但会让 checksum mismatch 拖到后端启动阶段才暴露。

## 10. 发布后验证

服务器内部验证：

```bash
ssh -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" jetty@172.16.38.78 '
  set -euo pipefail
  systemctl is-active nginx xiyu-bid-backend
  curl -fsS http://127.0.0.1:18080/actuator/health
  curl -fsS http://127.0.0.1:8080/actuator/health
  curl -fsSI http://127.0.0.1:8080/ | sed -n "1,12p"

  curl -sS -i -X OPTIONS http://127.0.0.1:8080/api/auth/login \
    -H "Origin: http://172.16.38.78:8080" \
    -H "Access-Control-Request-Method: POST" \
    -H "Access-Control-Request-Headers: content-type,authorization" \
    | sed -n "1,18p"
'
```

本机外部入口验证：

```bash
curl -m 15 -fsSI http://172.16.38.78:8080/
curl -m 15 -fsS http://172.16.38.78:8080/actuator/health
```

运行生产 smoke：

```bash
mkdir -p "/tmp/xiyu-prod-smoke-${RELEASE_ID}"

PRODUCTION_API_BASE_URL=http://172.16.38.78:8080 \
PRODUCTION_WEB_BASE_URL=http://172.16.38.78:8080 \
PROD_SMOKE_USERNAME='<smoke-username>' \
PROD_SMOKE_PASSWORD='<smoke-password>' \
PROMETHEUS_MODE=skip \
CRM_SMOKE_MODE=required \
CRM_SMOKE_PAGE_SIZE=1 \
REPORT_DIR="/tmp/xiyu-prod-smoke-${RELEASE_ID}" \
node scripts/release/run-prod-smoke.mjs

cat "/tmp/xiyu-prod-smoke-${RELEASE_ID}"/*.md
```

Go / No-Go：

- smoke 报告 `Verdict: GO`
- `Failed Checks: 0`
- 登录可拿到 token
- CRM page-list smoke 通过；客户测试服务器应使用 `CRM_SMOKE_MODE=required`，空商机或 409 都是 NO-GO
- `nginx` 和 `xiyu-bid-backend` 都是 `active`

## 11. 清理

先清服务器临时文件，再移除临时 key。不要反过来做，否则会失去直连清理能力。

```bash
ssh -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" jetty@172.16.38.78 "
  rm -f /opt/xiyu-bid/incoming/*.tmp
  rm -f /opt/xiyu-bid/incoming/*.https.tmp
  find /opt/xiyu-bid/incoming -maxdepth 1 -type d -name 'deploy-*' -print -exec rm -rf {} +
"
```

移除服务器授权公钥：

```bash
PUB_B64="$(base64 < "/tmp/xiyu-prod-deploy-${RELEASE_ID}.pub" | tr -d '\n')"

ssh -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" jetty@172.16.38.78 \
  "python3 - '$PUB_B64'" <<'PY'
import base64
import pathlib
import sys

pub = base64.b64decode(sys.argv[1]).decode('utf-8').strip()
path = pathlib.Path.home() / '.ssh' / 'authorized_keys'
lines = path.read_text(encoding='utf-8').splitlines()
new_lines = [line for line in lines if line.strip() != pub]
path.write_text('\n'.join(new_lines) + ('\n' if new_lines else ''), encoding='utf-8')
print('authorized_key_removed=%s' % (len(lines) - len(new_lines)))
PY

rm -f "/tmp/xiyu-prod-deploy-${RELEASE_ID}" "/tmp/xiyu-prod-deploy-${RELEASE_ID}.pub"
```

如果使用了临时 GitHub release 或临时 HTTPS 文件中转，发布后必须删除对应资产。

## 12. 回滚

应用回滚优先使用上线前保存的 jar、前端目录和部署记录：

```bash
ssh -i "/tmp/xiyu-prod-deploy-${RELEASE_ID}" jetty@172.16.38.78 '
  set -euo pipefail
  backup_dir="/opt/xiyu-bid/backups/<release-id>-<timestamp>"

  rm -rf /srv/www/xiyu-bid
  cp -a "$backup_dir/frontend" /srv/www/xiyu-bid
  cp "$backup_dir/app.jar" /opt/xiyu-bid/shared/backend/app.jar
  cp "$backup_dir/deployed-release.json" /opt/xiyu-bid/deployed-release.json

  sudo systemctl restart xiyu-bid-backend
  curl -fsS http://127.0.0.1:8080/actuator/health
'
```

数据库回滚只有在迁移破坏数据或 schema 时执行。优先联系 DBA，用发布前备份恢复：

```bash
gunzip -c /opt/xiyu-bid/db-backups/<backup>.sql.gz \
  | mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USERNAME" -p "$DB_NAME"
```

不要在生产执行 `flyway clean`。

## 13. 常见问题

### 13.1 页面能打开但登录失败

优先检查前端包中的 API base：

```bash
rg "172\\.16\\.38\\.78:8080|172\\.16\\.38\\.78[^:]" /srv/www/xiyu-bid/assets/*.js
```

曾经出现过错误包把 API base 打成 `http://172.16.38.78`，导致浏览器从 `:8080` 页面请求 `:80` API。

### 13.2 本机访问 `172.16.38.78` 抖动

先在服务器内测：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:18080/actuator/health
```

如果服务器内部正常、本机偶发 `Empty reply` 或 timeout，优先判断为本机到客户网段链路问题。

### 13.3 重启后 18080 短时间拒绝连接

后端刚重启时 `127.0.0.1:18080` 可能短时间 `Connection refused` 或经 Nginx 返回 `503`。以 60 次、每 2 秒重试为准，最终必须返回 `UP`。

### 13.4 日志里出现旧进程 shutdown 异常

本次部署中旧进程停止阶段曾出现 `NoClassDefFoundError`，但新进程启动后健康检查和 smoke 均通过。判断时不要只看旧 PID 的 shutdown 栈，要同时检查：

```bash
systemctl is-active xiyu-bid-backend
curl -fsS http://127.0.0.1:8080/actuator/health
sudo journalctl -u xiyu-bid-backend --since "10 min ago" --no-pager
```

### 13.5 Flyway checksum mismatch / 后端启动失败

**症状**：部署后 `remote-deploy.sh` 卡在 `Waiting for health check`，4 分钟后超时退出；服务器日志报：

```text
Migration checksum mismatch for migration version 1096
Apply Byte DESC: ... // 或 Description 不匹配
=> Found more than one migration with version XXXX（重复版本号场景）
```

**根因**：origin/main 上已发布的迁移文件（`V*.sql`）被修改了字节或重命名。Flyway 在后端启动时 `validateOnMigrate=true` 会比对生产 `flyway_schema_history` 里记录的 checksum 与当前 jar 内迁移文件的 checksum，不一致就拒绝启动。常见触发动作：
- `git mv V1096__old.sql V1096__new.sql`（同版本号改描述）
- 直接编辑已发布 `V*.sql` 的内容（哪怕只改注释）
- 历史事故：commit `407587394` 把 `V1096__add_users_full_name_pinyin.sql` 重命名为 `V1096__add_users_employee_number_pinyin.sql`，导致 9 个版本 checksum mismatch。

**诊断（只读，安全）**：

```bash
# 在服务器上跑（用当前运行 jar 自带的 Flyway，与启动时算法一致）
bash scripts/release/flyway-repair-runner.sh validate
# 输出会列出所有 mismatch 的版本号 + pending 的新迁移
```

**修复（repair）**：

```bash
# 1. repair 会自动前置备份 flyway_schema_history（无需手动备份）
bash scripts/release/flyway-repair-runner.sh repair
#    repair 只改 flyway_schema_history 的元数据（checksum/description），
#    不动任何业务表。repair 后会自动跑 validate 确认。

# 2. validate 应只剩 "Detected resolved migration not applied to database: <新版本>"
#    这是预期状态——新迁移会由新 jar 启动时自动 migrate 执行。

# 3. 重新跑 remote-deploy.sh 激活新 jar（这次 validate 预检会通过）
```

**回滚（如 repair 误操作）**：

repair 前自动备份在 `/opt/xiyu-bid/backups/flyway-history/flyway_schema_history-<timestamp>.sql`，整表恢复：

```bash
# 恢复 flyway_schema_history 到 repair 前状态
mysql -h <DB_HOST> -u <DB_USER> -p <DB_NAME> < /opt/xiyu-bid/backups/flyway-history/flyway_schema_history-<timestamp>.sql
```

如需回滚 jar 到上一版本（因为新 jar 已被 restart 过）：

```bash
cp /opt/xiyu-bid/releases/<旧release-id>/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar
sudo systemctl restart xiyu-bid-backend
```

**预防**：本地提交时 `check-flyway-immutable.sh` pre-commit 门禁会拦截"修改 origin/main 已存在的 V*.sql"。如果确实需要紧急修复已发布迁移（极少见），用 `FLYWAY_ALLOW_IMMUTABLE_EDIT=1 git commit`，但必须在部署前跑 repair，并在 PR 描述里说明理由。

**注意**：自 2026-06-26 起，`remote-deploy.sh` 在覆盖后端 jar 之前会自动跑 validate 预检，失败则停止 rollout（旧 jar 仍在运行，服务不中断）。所以 checksum mismatch 通常在部署阶段就被拦下，不会进入"health check 4 分钟超时"的现场。本章节描述的是预检被 `SKIP_FLYWAY_VALIDATE=1` 跳过、或首次启用预检前已部署的存量场景。

## 附录：慢链路应急小包路径

当全量包约 120 MB 且直连 SSH/SCP 极慢时，可以使用小包应急路径。该路径只建议由熟悉 jar 结构的发布负责人执行，常规部署仍应优先使用全量 release archive。

适用条件：

- 服务器当前 `/opt/xiyu-bid/shared/backend/app.jar` 的 SHA256 能和本地某个旧 release jar 对上
- 本地有旧 jar 和新 jar
- 新 jar 可以通过“复制旧 jar 未变条目 + 覆盖变更条目 + 删除移除条目”重建
- 重建后必须逐 entry 校验 CRC 和 size，并跑完整 smoke

本次 2026-05-27 实际采用的小包内容：

- `backend-patch.zip`：新 jar 中新增或变化的条目
- `backend-delete-list.txt`：旧 jar 中新版本已删除的条目
- `backend-entry-manifest.tsv`：新 jar 全量条目 manifest，包含 CRC、size、path
- `frontend.tar.gz`：完整前端静态资源
- `release-metadata.json`：release id、commit、base jar sha、新 jar sha

上传后在服务器执行：

1. 校验小包 SHA256
2. 备份当前 jar、前端目录、`deployed-release.json`
3. 用 Python `zipfile` 按 manifest 重建 jar
4. 替换 `/srv/www/xiyu-bid` 和 `/opt/xiyu-bid/shared/backend/app.jar`
5. 写入 `/opt/xiyu-bid/deployed-release.json`
6. 重启 `xiyu-bid-backend`
7. 执行本文第 10 节全部验证

如果第 4-7 步任一步失败，立即恢复备份目录中的旧 jar、旧前端和旧部署记录。

## 14. Markitdown Sidecar 部署（2026-06-21 首次部署）

### 14.1 背景

后端 `MarkItDownSidecarExtractor` 默认调用 `http://localhost:8000/convert` 提取 PDF/DOCX/PPTX/XLSX 等富文本文档。在 sidecar 未部署时，后端走 fallback 纯文本提取模式，丢失标题层级、表格结构、OCR 等能力，导致 LLM 智能解析质量差。

本次部署前，服务器从未运行过 sidecar（systemd/docker/进程均无痕迹），所有 doc-insight 调用都走 fallback。6/16-6/17 日志显示典型模式：3 秒内重试 3 次 sidecar 连接被拒后立即 fallback。

### 14.2 部署架构

| 项 | 值 |
|---|---|
| 部署方式 | Docker 容器（服务器有 Docker 26.1.4，无 Python 3.9+） |
| 容器名 | `xiyu-sidecar` |
| 镜像 | `document-converter-sidecar:latest`（583MB） |
| 端口映射 | `0.0.0.0:8000->8000/tcp` |
| 重启策略 | `--restart=always`（异常自动重启 + 开机自启） |
| 内存占用 | ~122MB / 7.4GB |
| 源码位置 | `/opt/xiyu-bid/sidecar/`（含 `app.py`、`converter.py`、`requirements.txt`、`Dockerfile`、`Dockerfile.cn`） |

### 14.3 Dockerfile.cn（国内镜像源加速版）

服务器无法直连 Docker Hub（超时），且默认 debian 源 `deb.debian.org` 下载 9MB Packages 文件卡 7 分钟无响应。`Dockerfile.cn` 是国内镜像源加速版：

- **debian 源**：替换为 `mirrors.aliyun.com`（阿里云）
- **PyPI 源**：`https://pypi.tuna.tsinghua.edu.cn/simple`（清华）
- **额外依赖**：`libmagic1`（file type detection）、`libpoppler-cpp-dev`（pdfminer）、`tesseract-ocr`（OCR）
- **markitdown 安装**：显式 `pip install markitdown[all]`（requirements.txt 注释说"NOT on PyPI"是错的，实际在 PyPI 上）

构建命令：

```bash
cd /opt/xiyu-bid/sidecar
sudo docker build --progress=plain -f Dockerfile.cn -t document-converter-sidecar:latest .
```

构建耗时约 2 分钟（apt + pip 都走国内源）。

### 14.4 首次部署步骤

```bash
# 1. 本地打包 sidecar 源码
cd /Users/user/xiyu/worktrees/trae/document-converter-sidecar
tar czf /tmp/document-converter-sidecar.tar.gz .

# 2. 上传到服务器
scp /tmp/document-converter-sidecar.tar.gz jetty@172.16.38.78:/tmp/

# 3. 服务器上解压 + 清理 macOS 元数据
ssh jetty@172.16.38.78 '
  sudo mkdir -p /opt/xiyu-bid/sidecar
  sudo chown jetty:jetty /opt/xiyu-bid/sidecar
  cd /opt/xiyu-bid/sidecar
  tar xzf /tmp/document-converter-sidecar.tar.gz
  find . -name "._*" -delete
'

# 4. 创建 Dockerfile.cn（见 14.3 节内容，或从仓库 document-converter-sidecar/Dockerfile.cn 同步）

# 5. 配置 docker 国内镜像源（拉 python:3.11-slim 用）
ssh jetty@172.16.38.78 '
  echo "{\"registry-mirrors\": [\"https://docker.m.daocloud.io\", \"https://docker.mirrors.ustc.edu.cn\", \"https://hub-mirror.c.163.com\"]}" | sudo tee /etc/docker/daemon.json
  sudo systemctl restart docker
  sudo docker pull python:3.11-slim
'

# 6. 构建镜像
ssh jetty@172.16.38.78 'cd /opt/xiyu-bid/sidecar && sudo docker build --progress=plain -f Dockerfile.cn -t document-converter-sidecar:latest .'

# 7. 启动容器
ssh jetty@172.16.38.78 'sudo docker run -d --name xiyu-sidecar --restart=always -p 8000:8000 document-converter-sidecar:latest'

# 8. 验证（见 14.5 节）
```

### 14.5 验证清单

```bash
# 容器状态
sudo docker ps --filter 'name=xiyu-sidecar'

# 健康检查
curl -s http://127.0.0.1:8000/health
# 期望: {"status":"up"}

# 文档转换测试
echo '# 测试标题\n\n正文内容' > /tmp/test.md
curl -s -X POST http://127.0.0.1:8000/convert -F 'file=@/tmp/test.md' | head -c 500
# 期望: 返回 JSON，含 markdown、sections、contentHash 字段

# 容器日志（确认无错误）
sudo docker logs xiyu-sidecar 2>&1 | tail -20
# 期望: "Uvicorn running on http://0.0.0.0:8000"，无 ModuleNotFoundError

# 资源占用
sudo docker stats xiyu-sidecar --no-stream
# 期望: MEM < 200MB
```

### 14.6 日常维护命令

```bash
# 查看实时日志
sudo docker logs -f xiyu-sidecar

# 重启容器
sudo docker restart xiyu-sidecar

# 停止/启动
sudo docker stop xiyu-sidecar
sudo docker start xiyu-sidecar

# 查看资源占用
sudo docker stats xiyu-sidecar --no-stream

# 进入容器调试
sudo docker exec -it xiyu-sidecar bash
```

### 14.7 升级 sidecar 代码

当 `document-converter-sidecar/app.py` 或 `converter.py` 有更新时：

```bash
# 1. 本地重新打包上传
cd /Users/user/xiyu/worktrees/trae/document-converter-sidecar
tar czf /tmp/document-converter-sidecar.tar.gz .
scp /tmp/document-converter-sidecar.tar.gz jetty@172.16.38.78:/tmp/

# 2. 服务器上解压覆盖
ssh jetty@172.16.38.78 '
  cd /opt/xiyu-bid/sidecar
  tar xzf /tmp/document-converter-sidecar.tar.gz
  find . -name "._*" -delete
'

# 3. 重新构建镜像 + 重启容器
ssh jetty@172.16.38.78 '
  cd /opt/xiyu-bid/sidecar
  sudo docker build --progress=plain -f Dockerfile.cn -t document-converter-sidecar:latest .
  sudo docker restart xiyu-sidecar
'

# 4. 验证（见 14.5 节）
```

### 14.8 已知限制

1. **SIDECAR_SHARED_KEY 未设置**：容器启动时日志会打印 `WARNING app: SIDECAR_SHARED_KEY is not set — authentication is disabled`。当前后端也未设置该密钥（backend.env 无 `SIDECAR_SHARED_KEY`），双方都走无认证模式。如需加固，需同时在后端 env 和容器 env 注入相同密钥。

2. **ffmpeg 未安装**：日志会打印 `Couldn't find ffmpeg or avconv`。影响音频/视频转写（markitdown transcription 功能），不影响 PDF/DOCX/XLSX 提取。如需音频转写，在 Dockerfile.cn 中加 `ffmpeg` 包。

3. **`.doc` 格式支持有限**：markitdown 在 Linux 上无法用 macOS `textutil` 预转换 `.doc`，需额外安装 `libreoffice`（体积大，未默认安装）。

4. **LLM 余额不足仍会 500**：sidecar 只解决文档提取质量问题。doc-insight 完整链路是 `sidecar 提取 → LLM 智能解析`，LLM 余额不足时仍返回 500。需在系统设置页充值或更换 API Key。

### 14.9 故障排查

| 现象 | 排查 |
|---|---|
| 容器 `Restarting (1)` | `sudo docker logs xiyu-sidecar` 看启动错误，常见为 `ModuleNotFoundError`（markitdown 未装） |
| 后端日志 `Connection refused localhost:8000` | `sudo docker ps` 确认容器在运行；`curl http://127.0.0.1:8000/health` 测连通性 |
| 后端仍走 fallback | 后端默认 sidecar URL 是 `http://localhost:8000`，容器已映射到宿主机 8000，无需额外配置 |
| 文档转换返回 500 | `sudo docker logs xiyu-sidecar 2>&1 \| tail -50` 看转换异常，常见为 PDF 加密、文件损坏 |
| 内存持续增长 | markitdown 处理大文件可能内存泄漏，`sudo docker restart xiyu-sidecar` 重启释放 |
