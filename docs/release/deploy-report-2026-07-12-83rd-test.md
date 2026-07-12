# 西域数智化投标管理平台 — 第 83 次部署报告（测试环境）

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 部署环境 | 测试环境（test） |
| 目标主机 | `winbid-01` / `172.16.38.78` |
| Release ID | `c3f2fe8ca-api8080` |
| 部署 Commit | `c3f2fe8ca` |
| 上一 Release | `66c245218-api8080` |
| 部署时间 | 2026-07-12 14:43 CST |
| 打包时间 | 2026-07-12 14:42 CST |
| 部署人员 | trae Agent |
| 回滚状态 | 无需回滚（服务已恢复健康） |

## 2. 基线信息

- **本地分支**：`agent/trae-init`
- **HEAD = origin/main**：`c3f2fe8ca !2043 fix(tender): 移除 CRM 商机占用提示中不存在的"解除原关联"引导`
- **GitHub 镜像**：部署前落后 2 commit，部署后已同步至 `c3f2fe8ca`
- **Git 工作区**：干净（`git status --short` 无输出）

## 3. PR / 改动范围

本次部署包含 25 个 commit，从 `66c245218` 到 `c3f2fe8ca`。按主题归类：

### 3.1 CO-571 Webhook Delivery Username 修复（4 commit）

| Commit | 说明 |
|--------|------|
| `c0d25f2c5` | fix(webhook): CO-571 补全 operatorName 传播，修复 CRM 回调因 username=null 落入死信 |
| `276ed4f3c` | !2031 fix(webhook): CO-571 补全 operatorName 传播 + 修复 CRM 推送半关联断言 |
| `965c65976` | fix(webhook): CO-571 Phase B 入队解析可用 OSS 用户，禁止空 username 静默死信 |
| `7ff8093d0` | !2037 fix(webhook): CO-571 Phase B 入队解析可用 OSS 用户，禁止空 username 静默死信 |

### 3.2 spec-035 Account 页面 N+1 查询 429 降级（5 commit）

| Commit | 说明 |
|--------|------|
| `b50ec2642` | fix(rate-limit): Account 页面详情请求 429 降级 + 串行化 |
| `8a32fe8b3` | !2036 fix(rate-limit): Account 页面详情请求 429 降级 + 串行化 |
| `ba419eb22` | fix(account): 根治 Account 页面 N+1 查询导致 429（spec 035 Phase B）(!2040) |
| `79b82eabe` | !2038 feat(tooling+wiki): spec-035 root-account-429 沉淀 — 3 个 pre-push 脚本 + .wiki 案例库更新 |
| `951533553` | feat(tooling+wiki): spec-035 root-account-429 沉淀 |

### 3.3 Rate-limit 429 友好提示修复（1 commit）

| Commit | 说明 |
|--------|------|
| `7e840785e` | !2035 fix(rate-limit): 修复业务层 catch 覆盖全局 429 友好提示 |

### 3.4 CRM 商机推送修复（7 commit）

| Commit | 说明 |
|--------|------|
| `fa02d3bc5` | fix: CRM 推送标讯时商机未关联 — 传入 username 让反查能拿到 CRM token |
| `1f99ed2a0` | fix: 优先用项目负责人 username 获取 CRM token（admin 无 OSS token） |
| `df9adabad` | !2041 fix(integration): CRM 推送标讯时商机未关联 — 传入 username 让反查能拿到 CRM token |
| `9f0ecf598` | fix(tender): 移除 CRM 商机占用提示中不存在的"解除原关联"引导 |
| `c3f2fe8ca` | !2043 fix(tender): 移除 CRM 商机占用提示中不存在的"解除原关联"引导 |
| `6c831ed37` | test(crm): 适配 integration 构造参数并修复半关联断言 + 补充 CRM 缓存边界测试 |
| `2c4fa9274` | docs(lessons): 沉淀 CRM 商机未关联第 6 次复发案例（PR !2041） |

### 3.5 文档 / 部署报告 / 教训沉淀（8 commit）

| Commit | 说明 |
|--------|------|
| `527a0c940` | docs(release): 第 82 次测试环境部署报告 |
| `c6294a4dc` | !2033 docs(release): 第 82 次测试环境部署报告 |
| `d704d1e4a` | docs(release): 第 82 次生产环境部署报告 |
| `0db289c87` | fix(release): 修正生产环境部署报告序号为第 7 次 |
| `8108a218c` | fix(release): 在报告中注明分支名为历史命名 |
| `561fd2436` | !2034 docs(release): 第 7 次生产环境部署报告 |
| `04f1ec0c4` | !2042 docs(release): 第 8 次生产环境部署报告 (prod) |
| `f0a052377` | docs(lessons): §55 更新 5/6 参 factory @Deprecated 状态为已完成 |

**新增 Flyway 迁移**：无（DB 已应用最新版本仍为 V1165）

## 4. Flyway 预检结果

| 步骤 | 结果 |
|------|------|
| 服务器 `flyway-repair-runner.sh validate` | ✅ 通过（228 migrations，all checksums match） |
| DB 已应用最新版本 | V1165 `add bid system admin role`（2026-07-11 16:43:52） |
| JAR 内迁移版本重复校验 | ✅ 无重复（227 个 SQL 文件） |
| 部署中内置 validate | ✅ 通过 |

## 5. 部署步骤

```bash
# 1. 本地打包（关键：VITE_API_BASE_URL= 显式空触发同源构建）
RELEASE_ID="c3f2fe8ca-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh

# 2. 上传产物与部署脚本
scp .release/xiyu-bid-release-c3f2fe8ca-api8080.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

# 3. 执行远程部署（SYSTEMCTL_SUDO=true，HEALTHCHECK_URL 指向后端内部端口 18080）
RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-c3f2fe8ca-api8080.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=c3f2fe8ca-api8080 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="source /etc/xiyu-bid/backend.env && mysqldump ... | gzip > /opt/xiyu-bid/db-backups/winbid-c3f2fe8ca-api8080-$(date +%Y%m%d%H%M%S).sql.gz" \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh
```

**部署脚本健康检查**：remote-deploy.sh 在 120 次健康检查尝试后报告失败（503），但服务在约 4 分钟后自行恢复为 UP。此为已知行为，详见第 7 节。

## 6. 验证结果

| 检查项 | 命令/路径 | 结果 |
|--------|-----------|------|
| 后端 health | `GET /actuator/health`（经 Nginx 8080） | ✅ 200 UP |
| 后端 readiness | `GET /actuator/health/readiness` | ✅ 200 UP |
| DB 组件 | health details | ✅ UP（MySQL，isValid()） |
| Redis 组件 | health details | ✅ UP（6.2.19） |
| Sidecar 组件 | health details | ✅ UP（http://localhost:8000 reachable） |
| AI Provider | health details | ✅ UP（qwen3.7-max，apiKeyConfigured） |
| JWT 组件 | health details | ✅ UP（HMAC-SHA256，secretLength=64，STRONG） |
| 登录路由（空密码） | `POST /api/auth/login {}` | ✅ 400（预期，验证错误） |
| 项目列表（未认证） | `GET /api/projects` | ✅ 403（预期，需认证） |
| CRM 健康（未认证） | `GET /api/integration/crm/health` | ✅ 401（预期，需认证） |
| 前端入口 | `GET /` | ✅ 200 |
| 前端登录页 | `GET /login` | ✅ 200 |
| 前端入口 hash | `assets/index-fJZWAwqJ.js` | ✅ 与 release 包一致 |
| 已部署 release 记录 | `/opt/xiyu-bid/deployed-release.json` | ✅ `c3f2fe8ca-api8080`（2026-07-12T06:43:20Z） |
| 后端 MainPID | systemctl | `11334`（active running） |

## 7. 问题与经验沉淀

### 7.1 remote-deploy.sh 健康检查误报（Kafka SDK readiness 延迟）

**现象**：remote-deploy.sh 在重启服务后 120 次健康检查（约 4 分钟）均收到 503，脚本判定部署失败。但手动检查 `/actuator/health` 返回 200 UP，业务接口（如 `/api/notifications/unread-count`）正常返回 200，用户可正常登录使用。

**根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`，Kafka 初始化阻塞主线程时延迟 `AvailabilityChangeEvent` 处理，导致 readiness 长时间处于 `OUT_OF_SERVICE`。此问题已在第 8、9、10、13、15、81、82 次部署中反复出现，是本项目的已知行为。

**处置**：未执行回滚。等待约 4 分钟后服务自行恢复（14:47:44 第 1 次手动检查即通过），所有 smoke 验证通过。

**建议**（延续第 82 次报告）：后续考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池执行，避免阻塞主线程。同时 remote-deploy.sh 的健康检查可加入 readiness 延迟容忍或单独检查 `/actuator/health/readiness` 状态。

### 7.2 上一版本前端资源保留

部署完成后，手动从上一版本 release 目录 `/opt/xiyu-bid/releases/66c245218-api8080/frontend/assets/` 拷贝旧 hash 化静态资源到 `/srv/www/xiyu-bid/assets/`（`cp -rn`，不覆盖新文件），保留 24h 让旧标签页自然刷新，避免 `Unable to preload CSS` 跨版本 404 噪声（第 18 条经验）。

> 注：remote-deploy.sh 在写入新 deployed-release.json 后，原脚本中的"自动保留上一版本 assets"逻辑因 deployed-release.json 已被覆盖而失效（PREV 变量指向当前版本）。本次手动指定旧版本号 66c245218 完成保留。

### 7.3 临时调试配置检查

`/etc/xiyu-bid/backend.env` 中保留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`。此为第 13、14、15、81、82 次延续的决定，便于 `/actuator/health` 显示完整组件详情用于诊断。非临时调试配置，无需清理。

## 8. GitHub 镜像同步

```bash
bash scripts/sync-to-github.sh
```

- Gitee main: `c3f2fe8ca41f26706c6323c40938885e0c91d74c`
- GitHub main: `c3f2fe8ca41f26706c6323c40938885e0c91d74c`
- 状态：完全一致 ✅

## 9. 回滚信息

| 项目 | 值 |
|------|-----|
| 回滚触发 | 未触发 |
| 上一可用 release | `66c245218-api8080` |
| 上一 release 目录 | `/opt/xiyu-bid/releases/66c245218-api8080` |
| 回滚命令 | `sudo cp /opt/xiyu-bid/releases/66c245218-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| DB 备份 | 部署前已自动备份至 `/opt/xiyu-bid/db-backups/winbid-c3f2fe8ca-api8080-*.sql.gz` |

## 10. 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连 + 基线确认（agent/trae-init ff-only 同步到 origin/main）
- [x] 服务器现状检查（66c245218-api8080，active，health UP）
- [x] Flyway 预检 3 步（VALIDATE OK，DB V1165，无新增迁移）
- [x] 本地打包（VITE_API_BASE_URL= 同源构建，33 秒 BUILD SUCCESS）
- [x] 产物校验（227 迁移文件无重复，前端入口 index-fJZWAwqJ.js）
- [x] 上传 + 部署到测试服务器（SYSTEMCTL_SUDO=true）
- [x] 健康检查 + Smoke 测试（health 200 UP, readiness 200 UP, 全部 smoke 通过）
- [x] 上一版本前端资源保留（cp -rn 保留 24h）
- [x] GitHub 镜像同步（Gitee = GitHub = c3f2fe8ca）
- [x] 临时调试配置检查（仅 SHOW_DETAILS=always，已知保留）

## 11. 风险提示

1. **Kafka SDK readiness 延迟**：本次 4 分钟延迟属已知行为，但若未来 Kafka broker 不可达时间延长，可能影响生产环境对外服务。建议后续考虑 `@Async` 改造。
2. **remote-deploy.sh 旧 assets 自动保留逻辑失效**：脚本在新 deployed-release.json 写入后才执行保留逻辑，导致 PREV 变量指向当前版本。本次手动指定旧版本号兜底。建议后续修复脚本顺序：先备份 PREV 再写入新 release.json。
3. **本次部署包含生产环境报告文档**：04f1ec0c4 (!2042) 是生产环境第 8 次部署报告，已随本次测试环境部署一并合入 main，不会单独再部署到生产。
