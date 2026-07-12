# 西域数智化投标管理平台 — 第 82 次部署报告（测试环境）

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 部署环境 | 测试环境（test） |
| 目标主机 | `winbid-01` / `172.16.38.78` |
| Release ID | `66c245218-api8080` |
| 部署 Commit | `66c245218` |
| 上一 Release | `908002217-api8080` |
| 部署时间 | 2026-07-12 08:27 CST |
| 打包时间 | 2026-07-12 08:22 CST |
| 部署人员 | trae Agent |
| 回滚状态 | 无需回滚（服务已恢复健康） |

## 2. 基线信息

- **本地分支**：`agent/trae-init`
- **HEAD = origin/main**：`66c245218 !2032 feat(rate-limit): 限流提示友好化优化（文案/交互/协议三层）`
- **GitHub 镜像**：已同步，Gitee main 与 GitHub main 完全一致
- **Git 工作区**：干净（`git status --short` 无输出）

## 3. PR / 改动范围

本次部署包含 17 个 commit，从 `908002217` 到 `66c245218`：

| Commit | 说明 |
|--------|------|
| `66c245218` | !2032 feat(rate-limit): 限流提示友好化优化（文案/交互/协议三层） |
| `30d12aeab` | fix(rate-limit): Google Code Review 修复（5项） |
| `8e9838e17` | refactor(rate-limit): 系统性设计评估修复（11项） |
| `c15e1e4e7` | feat(rate-limit): 限流提示友好化优化（文案/交互/协议三层） |
| `d57d4af4a` | !2030 fix(ui): 库房附件与项目文档操作列单行展示 |
| `9f1ffcbec` | !2029 fix(resource): CA 列表操作列加宽，三按钮单行展示 |
| `485383a61` | fix(tender): 批量导入自动分配传操作人 + 契约收紧 + CRM批次缓存 |
| `1644b514e` | !2025 fix(warehouse): 列表默认展示已关仓，与筛选「全部」语义对齐 |
| `228da8b00` | !2024 fix(tender): 批量导入模板示例地区改为推荐一级+二级格式 |
| `eb771f6e5` | docs(release): 第 81 次测试环境部署报告 |

**主要改动文件范围**：
- `backend/src/main/java/com/xiyu/bid/config/RateLimitFilter.java`
- `backend/src/main/java/com/xiyu/bid/exception/RateLimitResponseFactory.java`
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderCommandService.java`
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderImportAppService.java`
- `src/api/client.js`
- `src/api/rate-limit-message-resolver.js`
- `src/views/Bidding/list/components/BulkImportDialog.vue`
- `src/components/project/detail/ProjectDetailDocumentsCard.vue`
- `src/views/Resource/CAManagement.vue`

**新增 Flyway 迁移**：无

## 4. OBS 直传修复（本次部署关键修复）

### 4.1 问题

过往测试环境部署中，前端构建未显式传入 `VITE_OBS_ENABLED=true`，导致构建产物中 `isObsEnabled` 被编译为 `false`（`!1`），项目文档等场景的 OBS 大文件直传链路未启用。

### 4.2 修复方式

本次打包命令显式传入 `VITE_OBS_ENABLED=true`：

```bash
RELEASE_ID="66c245218-api8080" VITE_API_BASE_URL= VITE_OBS_ENABLED=true bash scripts/release/package-release.sh
```

### 4.3 验证

由于当前构建配置会对变量名进行压缩，`grep -o "isObsEnabled[^,;]*"` 无法直接命中字面量。通过控制变量对比实验验证 OBS 已启用：

| 构建参数 | Detail chunk 中 `.upload(` 调用数 | 结论 |
|----------|----------------------------------|------|
| `VITE_OBS_ENABLED=true` | 2 | ✅ OBS 直传逻辑保留 |
| `VITE_OBS_ENABLED=false` | 0 | 逻辑被 tree-shake |

release 包 `/tmp/release-check/frontend/assets/Detail-*.js` 中同样存在 2 处 `.upload(` 调用，确认 OBS 直传已随本次部署启用。

## 5. Flyway 预检结果

| 步骤 | 结果 |
|------|------|
| 服务器 `flyway-repair-runner.sh validate` | ✅ 通过（228 migrations，all checksums match） |
| DB 已应用最新版本 | V1165 `add bid system admin role` |
| JAR 内迁移版本重复校验 | ✅ 无重复 |
| 部署中内置 validate | ✅ 通过 |

## 6. 部署步骤

```bash
# 1. 本地打包（关键：VITE_OBS_ENABLED=true）
RELEASE_ID="66c245218-api8080" VITE_API_BASE_URL= VITE_OBS_ENABLED=true bash scripts/release/package-release.sh

# 2. 上传产物与部署脚本
scp .release/xiyu-bid-release-66c245218-api8080.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

# 3. 执行远程部署（SYSTEMCTL_SUDO=true）
RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-66c245218-api8080.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:8080/actuator/health \
  RELEASE_ID=66c245218-api8080 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh
```

**部署脚本健康检查**：remote-deploy.sh 在 120 次健康检查尝试后报告失败（503），但服务在约 5 分钟后自行恢复为 UP。此为已知行为，详见第 8 节。

## 7. 验证结果

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
| 前端登录页 | `GET /login` | ✅ 200 |
| 已部署 release 记录 | `/opt/xiyu-bid/deployed-release.json` | ✅ `66c245218-api8080` |
| OBS 直传启用 | release 包 Detail chunk `.upload(` 调用 | ✅ 已启用 |

## 8. 问题与经验沉淀

### 8.1 remote-deploy.sh 健康检查误报

**现象**：remote-deploy.sh 在重启服务后 120 次健康检查（约 4 分钟）均收到 503，脚本判定部署失败。但手动检查 `/actuator/health` 返回 200 UP，所有组件正常。

**根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`，Kafka 初始化阻塞主线程时延迟 `AvailabilityChangeEvent` 处理，导致 readiness 长时间处于 `OUT_OF_SERVICE`。此问题已在第 8、9、10、13、15、81 次部署中反复出现，是本项目的已知行为。

**处置**：未执行回滚。等待约 5 分钟后服务自行恢复，所有 smoke 验证通过。

**建议**：后续考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池执行，避免阻塞主线程。同时 remote-deploy.sh 的健康检查可加入 readiness 延迟容忍或单独检查 `/actuator/health/readiness` 状态。

## 9. GitHub 镜像同步

```bash
bash scripts/sync-to-github.sh
```

- Gitee main: `66c24521892fe33dfe6f59f189588d2acbc88115`
- GitHub main: `66c24521892fe33dfe6f59f189588d2acbc88115`
- 状态：完全一致 ✅

## 10. 回滚信息

| 项目 | 值 |
|------|-----|
| 回滚触发 | 未触发 |
| 上一可用 release | `908002217-api8080` |
| 上一 release 目录 | `/opt/xiyu-bid/releases/908002217-api8080` |
| 回滚命令 | `sudo cp /opt/xiyu-bid/releases/908002217-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| DB 备份 | 部署前已自动备份至 `/opt/xiyu-bid/db-backups/` |

## 11. 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连 + 基线确认
- [x] 服务器现状检查
- [x] Flyway 预检 3 步
- [x] 本地打包（VITE_OBS_ENABLED=true）
- [x] 产物校验
- [x] 上传 + 部署到测试服务器
- [x] 健康检查 + Smoke 测试
- [x] 迁移应用验证
- [x] GitHub 镜像同步
