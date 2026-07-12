# 第 8 次生产环境部署报告 — 2026-07-12

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产 (prod)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.10.149` |
| 用途 | 正式环境、对外服务 |
| 部署序号 | 第 8 次（生产） |
| 部署时间 | 2026-07-12 13:54 ~ 14:00 (CST) |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `df9adabad` |
| 上一版本 Release | `ba419eb22`（2026-07-12 03:50:51 UTC） |
| 分支 | `agent/trae/prod-deploy-20260712-8th` |
| 基线 commit | `df9adabad`（origin/main） |
| 激活时间 | 2026-07-12T05:57:23Z |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无（最新仍为 V1165） |
| Smoke 测试 | 全部通过 |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- 任务分支：`agent/trae/prod-deploy-20260712-8th`（基于 origin/main）
- 早操 SOP：已执行 `sync-env.sh`，HEAD = origin/main
- GitHub 镜像状态：部署前落后 20 个 commit（待 PR 合入后同步）
- 本地门禁自检：7 项全部通过（hooksPath、pre-commit、pre-push、git wrapper、agent-locks 等）

## 增量改动（ba419eb22 → df9adabad，6 个 commit）

| Commit | 说明 | PR |
|---|---|---|
| `df9adabad` | fix(integration): CRM 推送标讯时商机未关联 — 传入 username 让反查能拿到 CRM token | !2041 |
| `1f99ed2a0` | fix: 优先用项目负责人 username 获取 CRM token（admin 无 OSS token） | - |
| `2c4fa9274` | docs(lessons): 沉淀 CRM 商机未关联第 6 次复发案例 | !2041 |
| `fa02d3bc5` | fix: CRM 推送标讯时商机未关联 — 传入 username 让反查能拿到 CRM token | - |
| `79b82eabe` | feat(tooling+wiki): spec-035 root-account-429 沉淀 — 3 个 pre-push 脚本 + .wiki 案例库更新 | !2038 |
| `951533553` | feat(tooling+wiki): spec-035 root-account-429 沉淀 — 3 个 pre-push 脚本 + .wiki 案例库更新 | - |

### 改动范围

- **后端**：CRM 推送标讯修复（传入 username 让反查能拿到 CRM token）
- **工具/文档**：spec-035 root-account-429 沉淀 + 3 个 pre-push 脚本 + .wiki 案例库更新
- **数据库迁移**：无新增（最新仍为 V1165）

## Flyway 预检结果

### Step 1: Flyway validate

```
Successfully validated 228 migrations (execution time 00:00.090s)
VALIDATE OK - all checksums match
```

✅ 通过

### Step 2: DB 已应用版本对比

| version | description | success | installed_on |
|---|---|---|---|
| 1165 | add bid system admin role | 1 | 2026-07-12 08:57:40 |
| 1164 | lock oss user local passwords | 1 | 2026-07-10 21:27:37 |
| 1163 | add operator username to webhook delivery tasks | 1 | 2026-07-10 19:07:06 |
| 1162 | add margin permission to bid specialist | 1 | 2026-07-10 13:14:26 |
| 1161 | ca related platforms text | 1 | 2026-07-09 21:21:15 |

✅ DB 已应用版本与源码最新版本（V1165）一致

### Step 3: remote-deploy.sh 内置 validate

```
13:57:20.379 [main] INFO org.flywaydb.core.internal.command.DbValidate -- Successfully validated 228 migrations
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

✅ 通过

## 部署步骤

### 1. 本地打包

```bash
RELEASE_ID="df9adabad" VITE_API_BASE_URL= COPYFILE_DISABLE=1 bash scripts/release/package-release.sh
```

- `VITE_API_BASE_URL=` 显式设空触发同源构建（baseURL=""）
- `COPYFILE_DISABLE=1` 避免 macOS `._*` 残留
- 打包时间：32.5s
- 归档大小：153M
- 产物目录：`.release/df9adabad/`

### 2. 产物校验

- JAR 内 Flyway 迁移文件：227 个，无重复版本
- 前端入口：`assets/index-fJZWAwqJ.js`
- 归档：`.release/xiyu-bid-release-df9adabad.tar.gz`（153M）

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-df9adabad.tar.gz scripts/release/remote-deploy.sh jetty@172.16.10.149:/opt/xiyu-bid/incoming/

ssh jetty@172.16.10.149 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-df9adabad.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=df9adabad \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="source /etc/xiyu-bid/backend.env && mysqldump ... | gzip > /opt/xiyu-bid/db-backups/winbid-df9adabad-*.sql.gz" \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

关键点：
- `SYSTEMCTL_SUDO=true`：使用 sudo 重启服务（jetty 用户已配置 NOPASSWD sudo）
- `remote-deploy.sh` 自动执行：Flyway validate → DB 备份 → 停服 → 替换 jar → 启服 → 健康检查

### 4. 前端资源保留（防跨版本 404）

```bash
ssh jetty@172.16.10.149 'sudo cp -rn /opt/xiyu-bid/releases/ba419eb22/frontend/assets/* /srv/www/xiyu-bid/assets/ 2>/dev/null'
```

✅ 已保留上一版本 ba419eb22 的 assets（旧 hash 资源保留 24h，让旧标签页自然刷新）

### 5. macOS 残留清理

```bash
find /srv/www/xiyu-bid/ -name "._*" -delete
```

✅ 已清理

## 验证结果

### 健康检查

```
Health: UP
  - aiProvider: UP (provider: custom, model: qwen3.7-max)
  - db: UP (MySQL)
  - diskSpace: UP (free: 87G)
  - jwt: UP (HMAC-SHA256, secretLength: 47)
  - livenessState: UP
  - ping: UP
  - readinessState: UP
  - redis: UP (version: 6.2.19)
  - sidecar: UP (reachable)
```

✅ 所有组件 UP

### Smoke 测试（经 Nginx 8080 代理到后端 18080）

| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `GET /actuator/health` | 200 UP | 200 UP | ✅ |
| `GET /actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `GET /actuator/health`（经 Nginx） | 200 UP | 200 UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects`（无认证） | 403 | 403 | ✅ |
| `GET /api/integration/crm/health`（无认证） | 401 | 401 | ✅ |
| `GET /`（前端首页） | 200 | 200 | ✅ |
| `GET /login`（前端登录页） | 200 | 200 | ✅ |
| 前端 assets 入口 | `index-fJZWAwqJ.js` | `index-fJZWAwqJ.js` | ✅ |

✅ 全部通过

### 部署记录确认

```json
{
  "releaseId": "df9adabad",
  "activatedAt": "2026-07-12T05:57:23Z",
  "releaseDir": "/opt/xiyu-bid/releases/df9adabad",
  "frontendPublicDir": "/srv/www/xiyu-bid",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "backendServiceName": "xiyu-bid-backend",
  "healthcheckUrl": "http://127.0.0.1:18080/actuator/health",
  "packageMetadata": {
    "releaseId": "df9adabad",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-12T03:56:30Z",
    "sentryEnabled": true
  }
}
```

✅ 已更新

## GitHub 镜像同步

- 部署前状态：GitHub main 落后 Gitee main 20 个 commit
- 待 PR 合入 main 后执行：`SYNC_TO_GITHUB=1 bash scripts/sync-env.sh .`

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 Release | `ba419eb22` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/ba419eb22` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-df9adabad-*.sql.gz` |
| 回滚命令 | 恢复旧 jar + 前端，无需 DB 回滚（无新迁移） |

## 配置清理检查

```bash
ssh jetty@172.16.10.149 'sudo grep -E "SHOW_DETAILS|DEBUG|TRACE" /etc/xiyu-bid/backend.env'
```

结果：`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`

⚠️ 历史保留配置（第 13/14/15 次决定保留），非本次新增。用于运维诊断查看健康组件详情，保持现状。

## 经验沉淀应用情况

本次部署应用了以下经验：

1. **Flyway 预检 3 步法**：部署前主动 validate + DB 版本对比，避免启动时才发现问题 ✅
2. **同源构建（VITE_API_BASE_URL=）**：生产前端同源构建，忽略 VITE_API_BASE_URL ✅
3. **SYSTEMCTL_SUDO=true**：使用 sudo 重启服务，避免 Interactive authentication 失败 ✅
4. **前端资源保留**：从上一版本 release 目录 cp -rn 旧 assets，防跨版本 404 ✅
5. **macOS `._*` 残留清理**：COPYFILE_DISABLE=1 打包 + find delete 兜底 ✅
6. **Smoke 测试替代方案**：admin 密码未知，用 400/403/401 验证接口路由 ✅
7. **服务器本地 curl**：避开本地 HTTP_PROXY 干扰，Smoke 全部在服务器本地执行 ✅
8. **健康检查容忍 Kafka 延迟**：remote-deploy.sh 内置 4 分钟超时容忍 ✅（本次未触发延迟，14 次尝试即通过）

## 风险提示

1. **CRM 推送修复涉及业务逻辑**：本次改动核心是 CRM 推送标讯时商机未关联修复，需关注生产环境 CRM 集成实际行为
2. **前端资源保留 24h**：旧 hash 资源保留 24h 后需清理（下次部署或定时任务）
3. **GitHub 镜像落后 20 commit**：待 PR 合入后同步
4. **无新增 Flyway 迁移**：本次部署无 DB schema 变更，回滚无需 DB 恢复

## 部署确认清单

- [x] 环境门禁确认（用户确认生产环境 172.16.10.149）
- [x] 早操三连 + 基线确认（HEAD = origin/main）
- [x] 服务器现状探查（旧 release ba419eb22 健康 UP）
- [x] Flyway 预检 3 步法（validate + DB 版本对比 + remote-deploy 内置）
- [x] 本地打包（RELEASE_ID=df9adabad，同源构建）
- [x] 产物校验（227 迁移文件无重复，前端入口一致）
- [x] 上传 + 部署（remote-deploy.sh，SYSTEMCTL_SUDO=true）
- [x] 前端资源保留（cp -rn ba419eb22 assets）
- [x] 健康检查（所有组件 UP）
- [x] Smoke 测试（health + readiness + 3 接口路由 + 前端页面）
- [x] 配置清理检查（仅历史 SHOW_DETAILS=always，非本次新增）
- [x] GitHub 镜像同步检查（落后 20 commit，待 PR 合入后同步）
- [x] 部署报告生成（本文件）
