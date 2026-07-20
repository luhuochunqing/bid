# 第 101 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `d229bd6` |
| 上一版本 | `4b0e9ea`（第 100 次，2026-07-20 09:38） |
| 部署时间 | 2026-07-20 15:38 CST |
| 增量 | 1 PR（v3.10 标讯接口新增项目负责人工号字段） |
| 新增迁移 | 无（纯 Java 代码 + 测试 + 文档变更） |
| 部署结果 | ✅ 成功（健康检查脚本 120 次失败，但服务实际 4 分 39 秒后 readinessState 恢复 UP，属 Kafka SDK readiness 延迟已知行为） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用 |

## 部署原因

本次部署合入 1 个标讯集成接口字段增强：

1. **v3.10 标讯接口新增项目负责人工号字段**（PR !2153，CO-333 增强）：
   - `TenderPushRequest` / `TenderUpdateRequest` 新增 `projectManagerEmployeeId` 字段，与 `projectManagerName` 配套使用
   - 解决原有"仅按姓名反查 user_id"在重名场景下绑定失败的问题
   - `ProjectManagerIdResolver` 新增 `applyTo` 公共入口，统一四级解析策略：**工号优先 → 姓名作校验 → username 回落 → fullName 兜底**
   - 注入 `UserEnabledStatusService` 与 `TenderAutoAssignmentService` 行为对齐
   - 工号命中后姓名不符仅 `warn` 不阻断（与产品方案一致）
   - 重构：将 `applyProjectManager` 从 `TenderIntegrationMapper` 抽取到 Resolver，mapper 行数从 318 降至 287（300 硬上限以下）
   - 测试：186 passed / 0 failed / 2 skipped

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae/deploy-report-99-100（任务分支，HEAD = origin/main + 部署报告文档 commit） |
| HEAD commit | `d229bd6b7`（其中 `0bc90f7f0` = origin/main 最新） |
| origin/main | `0bc90f7f0`（父 commit，!2153 merge commit） |
| GitHub 镜像 | ⚠️ 落后 41 个 commit（部署前未同步，建议部署后执行 sync-to-github.sh） |
| git wrapper | ✅ 生效（scripts/git） |
| Flyway validate | ✅ 通过（234 migrations, all checksums match） |
| DB 已应用最新版本 | V1171（第 100 次部署应用） |
| 源码最新迁移版本 | V1171（无新增迁移） |

## 增量 PR 列表

| PR | 类型 | 标题 |
|---|---|---|
| !2153 | feat(integration) | v3.10 标讯接口新增项目负责人工号字段（CO-333 增强） |

## 改动范围

### v3.10 标讯接口新增项目负责人工号字段（!2153）

**后端 Java 代码（6 个文件）**：
- `backend/src/main/java/com/xiyu/bid/integration/external/ProjectManagerIdResolver.java`（+151 行，新增 `applyTo` 公共入口与工号优先解析策略）
- `backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationCommandService.java`（调用点调整）
- `backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationMapper.java`（-31 行，抽取 `applyProjectManager` 到 Resolver，行数 318→287）
- `backend/src/main/java/com/xiyu/bid/integration/external/TenderPushRequest.java`（+9 行，新增 `projectManagerEmployeeId` 字段）
- `backend/src/main/java/com/xiyu/bid/integration/external/TenderUpdateRequest.java`（+9 行，新增 `projectManagerEmployeeId` 字段）

**后端测试代码（6 个文件）**：
- `ProjectManagerIdResolverTest.java`（+312 行，覆盖工号优先/姓名校验/username 回落/fullName 兜底四级路径）
- `TenderIntegrationCommandServiceCrmDuplicateTest.java` / `TenderIntegrationCommandServiceDedupProjectTypeTest.java` / `TenderIntegrationCommandServiceEventTest.java` / `TenderIntegrationServiceMapToEntityTest.java` / `TenderIntegrationServicePushEvaluationTest.java` / `TenderIntegrationServiceUpdateCrmLinkTest.java`（mock 从 `resolveByFullName` 改为 `applyTo` 副作用）

**集成文档**：
- `docs/integration/标讯集成接口文档-v3.8.md`（接口文档更新，含 v3.10 字段说明）

## Flyway 预检 3 步法

| 步骤 | 结果 | 说明 |
|---|---|---|
| Step 1: 服务器 validate | ✅ | `VALIDATE OK - all checksums match`（234 migrations validated） |
| Step 2: DB 版本对比 | ✅ | DB 已应用最新版本 V1171，源码最新版本 V1171，无 pending 迁移 |
| Step 3: remote-deploy 内置 | ✅ | `remote-deploy.sh` 在激活新 jar 前自动 validate，通过 |

## 部署步骤

### Step 5: 本地打包
```bash
RELEASE_ID="d229bd6" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

打包结果：
- ✅ 前端构建（`npm run build:api`）+ `check:frontend-api-base` 通过
- ✅ 后端打包（`mvn clean -DskipTests package`）成功，jar = `bid-platform-1.0.3.jar`
- ✅ jar 内 Flyway 迁移版本无重复（233 files）
- ✅ OBS 直传已启用（Detail chunk `.upload(` 调用数=2）
- ✅ `release-metadata.json` 中 `obsEnabled=true`、`apiBaseUrl=""`（同源构建）

### Step 6: 产物校验
- Release archive: `.release/xiyu-bid-release-d229bd6.tar.gz`（153M）
- 前端入口 chunk: `assets/index-XcC1Psz3.js`（与服务器部署后一致）

### Step 7: 上传 + 部署
```bash
scp .release/xiyu-bid-release-d229bd6.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-d229bd6.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=d229bd6 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="..." \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

部署日志关键节点：
- `15:38:54` Flyway validate 通过（234 migrations, all checksums match）
- `15:38:57` 后端服务启动（PID 26040）
- `15:38:57 - 15:43:15` 健康检查脚本 120 次失败（约 4 分钟）
- `15:43:02` 后端已开始处理业务请求（日志显示 `GET /api/notifications/unread-count 200`，`userId=1 roleCode=admin`）
- `15:43:15` `OrganizationEventSdkKafkaStarter` 开始 Kafka SDK 初始化（readiness 延迟根因）
- `15:43:35`（约）health 全面恢复 UP（ readinessState UP）

### Step 7.5: 前端资源保留（防跨版本 404）
```bash
ssh jetty@172.16.38.78 'sudo cp -rn /opt/xiyu-bid/releases/4b0e9ea/frontend/assets/* \
  /srv/www/xiyu-bid/assets/ 2>/dev/null && echo "✅ 已保留上一版本(4b0e9ea) assets"'
```
> 注意：`deployed-release.json` 此时已更新为 `d229bd6`，需手动指定上一版本目录 `4b0e9ea`，不能从 json 读取。

## 验证结果

### 健康检查（部署后 4 分 39 秒恢复）

```json
{
  "status": "UP",
  "components": {
    "aiProvider": {"status": "UP", "provider": "custom", "model": "qwen3.7-max"},
    "db": {"status": "UP", "database": "MySQL"},
    "diskSpace": {"status": "UP", "free": "15083401216"},
    "jwt": {"status": "UP", "strength": "STRONG", "secretLength": 64},
    "livenessState": {"status": "UP"},
    "ping": {"status": "UP"},
    "readinessState": {"status": "UP"},
    "redis": {"status": "UP", "version": "6.2.19"},
    "sidecar": {"status": "UP", "url": "http://localhost:8000"}
  }
}
```

### Smoke 测试（经 Nginx 8080 代理）

| # | 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | 200 UP | HTTP 200 | ✅ |
| 2 | `GET /actuator/health/readiness` | 200 UP | HTTP 200 | ✅ |
| 3 | `POST /api/auth/login`（空 body） | 400 | HTTP 400 | ✅ |
| 4 | `GET /api/projects`（无认证） | 403 | HTTP 403 | ✅ |
| 5 | `GET /api/integration/crm/health`（无认证） | 401 | HTTP 401 | ✅ |
| 6 | `GET /` | 200 | HTTP 200 | ✅ |
| 7 | `GET /login` | 200 | HTTP 200 | ✅ |
| 8 | 前端入口 chunk | 与 release 一致 | `assets/index-XcC1Psz3.js` | ✅ |

### 迁移应用验证
- 无新增迁移（DB 仍为 V1171，与第 100 次部署一致）

## GitHub 镜像同步

| 检查项 | 结果 |
|---|---|
| 部署前 Gitee vs GitHub | GitHub 落后 39 个 commit |
| 部署后 Gitee vs GitHub | GitHub 落后 41 个 commit（本次新增 2 个 commit：!2153 + 部署报告） |
| 同步操作 | ⚠️ 未同步，建议执行 `bash scripts/sync-to-github.sh` |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | ⚠️ 保留 | 历史决定保留（第 13/14/15 次用户决定），非临时配置 |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` 其他 | ✅ 无 | 无其他临时调试配置 |

## 经验沉淀应用情况

| 经验 | 是否应用 | 说明 |
|---|---|---|
| Flyway 预检 3 步法 | ✅ | 部署前主动执行 validate + DB 版本对比 |
| Kafka SDK readiness 延迟 | ✅ | 第 8/9/10/13/15/100 次后再次出现，4 分 39 秒恢复，属已知行为，未回滚 |
| 同源构建（`VITE_API_BASE_URL=`） | ✅ | 显式设空触发同源构建 |
| OBS 直传双保险（`VITE_OBS_ENABLED=true`） | ✅ | 显式传入 + 产物校验 `obsEnabled=true` |
| macOS `._*` 残留文件 | ✅ | `COPYFILE_DISABLE=1` 预防 |
| `SYSTEMCTL_SUDO=true` | ✅ | jetty 用户已配置 NOPASSWD sudo |
| 前端 hash 资源跨版本 404 | ✅ | 从上一版本 `4b0e9ea/frontend/assets/` 拷贝旧 hash 文件保留 24h |
| `--noproxy '*'` 绕过 Mac HTTP_PROXY | ✅ | 所有 curl 命令统一加 `--noproxy '*'` |
| jar 内 Flyway 迁移版本无重复校验 | ✅ | package-release.sh 内置门禁通过 |

## 风险提示

1. **GitHub 镜像落后 41 个 commit**：建议尽快执行 `bash scripts/sync-to-github.sh` 同步镜像，保持双远程一致性
2. **Kafka SDK readiness 延迟**：本次部署再次出现（4 分 39 秒恢复），虽属已知行为但用户体验不佳。建议后续考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池执行，避免阻塞主线程
3. **当前任务分支未合入 main**：`agent/trae/deploy-report-99-100` 分支包含本报告文档 commit，建议提 PR 合入 main

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操同步（sync-env.sh + rebase origin/main）
- [x] Flyway 预检 3 步法全部通过
- [x] 本地打包 + 产物校验（OBS 启用、同源构建、无重复迁移）
- [x] 上传 + remote-deploy.sh 部署（含 DB 备份）
- [x] 前端资源保留（4b0e9ea assets 拷贝）
- [x] 健康检查通过（4 分 39 秒恢复，readinessState UP）
- [x] Smoke 测试 8 项全绿
- [x] 配置清理检查（仅保留用户决定项）
- [x] 部署报告生成
- [ ] GitHub 镜像同步（待执行）
- [ ] 当前分支提 PR 合入 main（待执行）

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚姿态 | 未需要 |
| 上一版本 release | `/opt/xiyu-bid/releases/4b0e9ea/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/4b0e9ea/backend/app.jar` |
| 上一版本前端 | `/opt/xiyu-bid/releases/4b0e9ea/frontend/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-d229bd6-<timestamp>.sql.gz` |
| 回滚命令 | `scp /opt/xiyu-bid/releases/4b0e9ea/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
