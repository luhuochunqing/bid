# 西域数智化投标管理平台 — 第 81 次部署报告（测试环境）

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 部署环境 | 测试环境（test） |
| 目标主机 | `winbid-01` / `172.16.38.78` |
| Release ID | `908002217-api8080` |
| 部署 Commit | `908002217` |
| 上一 Release | `95dca45c1-api8080` |
| 部署时间 | 2026-07-11 20:16 CST |
| 打包时间 | 2026-07-11 20:15 CST |
| 部署人员 | trae Agent |
| 回滚状态 | 无需回滚（服务已恢复健康） |

## 2. 基线信息

- **本地分支**：`agent/trae/serve-latest`
- **HEAD = origin/main**：`908002217 !2022 fix: 06234 OSS 角色解析回归 — sysRoleList roleName 不再使用 positionToRoleMapper`
- **GitHub 镜像**：已同步，Gitee main 与 GitHub main 完全一致
- **Git 工作区**：存在 5 个未跟踪文件（客户交付数据包、仓库导出包等），未纳入本次部署

## 3. PR / 改动范围

本次部署包含 PR !2022 及其修正：

| Commit | 说明 |
|--------|------|
| `618eb8896` | fix: 06234 OSS 角色解析回归 — sysRoleList roleName 不再使用 positionToRoleMapper |
| `908002217` | !2022 fix: 06234 OSS 角色解析回归 — sysRoleList roleName 不再使用 positionToRoleMapper |

**改动文件**：
- `backend/src/main/java/com/xiyu/bid/crm/application/OssRoleResolver.java`
- `backend/src/main/java/com/xiyu/bid/crm/application/OssRoleResolverTest.java`

**新增 Flyway 迁移**：无

## 4. Flyway 预检结果

| 步骤 | 结果 |
|------|------|
| 服务器 `flyway-repair-runner.sh validate` | ✅ 通过（228 migrations，all checksums match） |
| DB 已应用最新版本 | V1165 `add bid system admin role` |
| JAR 内迁移版本重复校验 | ✅ 无重复 |
| 部署中内置 validate | ✅ 通过 |

## 5. 部署步骤

```bash
# 1. 本地打包
RELEASE_ID="908002217-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh

# 2. 上传产物与部署脚本
scp .release/xiyu-bid-release-908002217-api8080.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

# 3. 执行远程部署（SYSTEMCTL_SUDO=true）
RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-908002217-api8080.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:8080/actuator/health \
  RELEASE_ID=908002217-api8080 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh
```

**部署脚本健康检查**：remote-deploy.sh 在 120 次健康检查尝试后报告失败（503）。

## 6. 验证结果

| 检查项 | 命令/路径 | 结果 |
|--------|-----------|------|
| 后端 health | `GET /actuator/health` | ✅ 200 UP |
| 后端 readiness | `GET /actuator/health/readiness` | ✅ 200 UP |
| DB 组件 | health details | ✅ UP |
| Redis 组件 | health details | ✅ UP |
| Sidecar 组件 | health details | ✅ UP |
| AI Provider | health details | ✅ UP |
| 登录路由（空密码） | `POST /api/auth/login {}` | ✅ 400（预期） |
| 项目列表（未认证） | `GET /api/projects` | ✅ 403（预期） |
| CRM 健康（未认证） | `GET /api/integration/crm/health` | ✅ 401（预期） |
| 前端入口 | `GET /` | ✅ 200 |
| 前端资源 | `assets/index-DrN9nJ2a.js` | ✅ 与打包产物一致 |
| 已部署 release 记录 | `/opt/xiyu-bid/deployed-release.json` | ✅ `908002217-api8080` |

## 7. 问题与经验沉淀

### 7.1 remote-deploy.sh 健康检查误报

**现象**：remote-deploy.sh 在重启服务后 120 次健康检查（约 4 分钟）均收到 503，脚本判定部署失败。但手动检查 `/actuator/health` 返回 200 UP，所有组件正常。

**根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`，Kafka 初始化阻塞主线程时延迟 `AvailabilityChangeEvent` 处理，导致 readiness 长时间处于 `OUT_OF_SERVICE`。此问题已在第 8、9、10、13、15 次部署中反复出现，是本项目的已知行为。

**处置**：未执行回滚。等待约 5 分钟后服务自行恢复，所有 smoke 验证通过。

**建议**：后续考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池执行，避免阻塞主线程。同时 remote-deploy.sh 的健康检查可加入 readiness 延迟容忍或单独检查 `/actuator/health/readiness` 状态。

## 8. GitHub 镜像同步

```bash
bash scripts/sync-to-github.sh
```

- Gitee main: `90800221764d49ea9cf323c157889e6f746c3fc7`
- GitHub main: `90800221764d49ea9cf323c157889e6f746c3fc7`
- 状态：完全一致 ✅

## 9. 回滚信息

| 项目 | 值 |
|------|-----|
| 回滚触发 | 未触发 |
| 上一可用 release | `95dca45c1-api8080` |
| 上一 release 目录 | `/opt/xiyu-bid/releases/95dca45c1-api8080` |
| 回滚命令 | `sudo cp /opt/xiyu-bid/releases/95dca45c1-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| DB 备份 | 部署前已自动备份至 `/opt/xiyu-bid/db-backups/` |

## 10. 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连完成
- [x] 基线干净（HEAD = origin/main）
- [x] Flyway validate 通过
- [x] 本地打包成功
- [x] JAR 内迁移无重复
- [x] 产物上传成功
- [x] 远程部署执行完成
- [x] 后端 health / readiness UP
- [x] Smoke 测试通过
- [x] GitHub 镜像同步完成
- [x] 部署报告生成

## 11. 风险提示

1. remote-deploy.sh 的健康检查逻辑在 Kafka SDK readiness 延迟场景下可能误报失败，需人工二次确认 `/actuator/health` 状态。
2. 本次无 DB 迁移，回滚风险较低。
3. 本地存在未跟踪的客户数据包文件，部署报告未纳入这些文件。
