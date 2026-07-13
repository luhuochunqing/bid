# 西域数智化投标管理平台 — 第 84 次部署报告（测试环境）

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 部署环境 | 测试环境（test） |
| 目标主机 | `winbid-01` / `172.16.38.78` |
| Release ID | `f0366414b-api8080` |
| 部署 Commit | `f0366414b` |
| 上一 Release | `c3f2fe8ca-api8080` |
| 部署时间 | 2026-07-13 08:39 CST |
| 打包时间 | 2026-07-13 08:38 CST |
| 部署人员 | trae Agent |
| 回滚状态 | 无需回滚（服务已恢复健康） |

## 2. 基线信息

- **本地分支**：`agent/trae-init`（锚点分支，仅用于打包与部署，不做开发）
- **HEAD = origin/main**：`f0366414b !2058 fix: 24小时提交后置审计 — 4个关键缺陷修复`
- **GitHub 镜像**：部署前落后 47 commit（未触发同步，本次部署未推送 origin/main）
- **Git 工作区**：干净（`git status --short` 无输出）

## 3. PR / 改动范围

本次部署包含 49 个 commit，从 `c3f2fe8ca` 到 `f0366414b`。按主题归类：

### 3.1 CO-571 / CO-576 Webhook Operator Username 重构（约 12 commit）

| Commit | 说明 |
|--------|------|
| `8771e7fde` | fix(evaluation): 评标中阶段选项顺序调整，公示置于结果已出前 (CO-571) |
| `e39916fbe` | fix(webhook): CO-571 Phase C Review 修正 — ScoreAnalysisService 用 creatorId 兜底 + Resolver 过滤 blank |
| `3bea4ae58` | fix(webhook): CO-571 Phase C 收尾 — 修注释 + revert cosmetic |
| `8d0be4d72` | review(webhook): CO-571 Phase C 补充边界测试 + 同步 PR 描述 |
| `a8516db2d` | refactor(scoreanalysis): CO-571 MAJOR-1 — 移除跨模块 repository 依赖，收口到 TenderCommandService.resolveCreatorId |
| `a7dd5408c` | chore(webhook): CO-571 → CO-576 全局重命名 + 合并重复测试 + 补 resolveCreatorId 单测 |
| `da472ead0` | fix(webhook): rebase 冲突修复 — 合并 !2047 resolveForCrmLookup 与 CO-576 重命名 |
| `5328e6371` | refactor(webhook): CO-571 Phase C 删短工厂与两参 updateStatus，禁止空操作人事件 |
| `0ce025ae0` | !2039 refactor(webhook): CO-576 Phase C 删短工厂与两参 updateStatus，禁止空操作人事件 |
| `1ad7ee495` | !2047 fix(webhook): §4.1/§4.2 listener 改用 resolveForCrmLookup（PM 优先） |
| `6ef90f169` | fix(webhook): §4.1/§4.2 listener 改用 resolveForCrmLookup（PM 优先） |
| `91b24c859` | docs: OperatorUsernameResolver javadoc 使用指引 + lessons-learned 沉淀案例 |

### 3.2 CO-572 / CO-573 项目结项阶段保证金退回（约 5 commit）

| Commit | 说明 |
|--------|------|
| `a5374be30` | !2046 fix(closure): 提交结项申请后表单字段应为只读状态 (CO-572) |
| `13b42a814` | fix(closure): 提交结项申请后表单字段应为只读状态 (CO-572) |
| `90e505c63` | fix(closure): CO-572 后端 PENDING 状态 re-submit 守卫 |
| `1739fa36e` | !2048 feat(closure): CO-573 项目结项阶段保证金退回金额校验规则 |
| `87d74978c` | feat(closure): CO-573 保证金退回金额等值校验 |
| `1fc133e75` | fix(closure): CO-573 前端金额等值按「分」比较，避免浮点误伤 |
| `0ec3a2332` | docs: 恢复 implementation-notes 并追加 CO-573 分比较说明 [skip e2e-ui-sync] |

### 3.3 CO-574 / CO-575 任务看板与保证金放权（约 4 commit）

| Commit | 说明 |
|--------|------|
| `fa67ca6c5` | !2050 fix(task): CO-574 保证金缴纳任务放权项目负责人 + 修复看板执行人改后不刷新 |
| `34a7ba753` | fix(task): CO-574 保证金缴纳任务放权项目负责人 + 修复看板执行人改后不刷新 |
| `4b705d8bd` | !2051 feat(task): CO-575 任务看板底部增加审核提示信息 |
| `b6608250c` | feat(task): CO-575 任务看板底部增加审核提示信息 |

### 3.4 CO-578 项目详情页负责人/辅助人员字段（约 2 commit）

| Commit | 说明 |
|--------|------|
| `5b542295b` | !2055 feat(project): CO-578 项目详情页公共模块增加投标负责人和投标辅助人员字段 |
| `a63aab607` | feat(project): CO-578 项目详情页公共模块增加投标负责人和投标辅助人员字段 |

### 3.5 投标关键节点企微通知（约 3 commit）

| Commit | 说明 |
|--------|------|
| `a93aadadb` | !2049 feat(wecom): 投标关键节点企微通知触点 |
| `53ff6de34` | feat(wecom): 投标关键节点企微通知触点 |
| `5e87921b8` | fix(wecom): CR #2049 回归修复 |

### 3.6 标讯人工录入 AI 识别增强（约 4 commit）

| Commit | 说明 |
|--------|------|
| `191732b26` | !2052 feat(tender-intake): 增强标讯人工录入AI识别准确率 |
| `754934767` | feat(tender-intake): 增强标讯人工录入AI识别准确率 |
| `6a809958e` | fix(tender-intake): 修复P0日期格式错误 + 补充P1单元测试 + 优化hint质量 |
| `32519dc83` | fix(intake): <candidate_text> prompt injection 防御 — 通用标签转义 |

### 3.7 评分分析事务回滚修复（约 2 commit）

| Commit | 说明 |
|--------|------|
| `73ca4b54c` | fix(scoreanalysis): 移除 updateStatus 异常吞噬，失败时事务回滚 |
| `d8519a74b` | fix(scoreanalysis): setRollbackOnly 确保事务真实回滚，评分与状态同成败 |

### 3.8 通知 store 修复 + 关联 CRM 校验（约 4 commit）

| Commit | 说明 |
|--------|------|
| `445bca7eb` | fix(notifications): 通知 store 静默清零 unreadCount 修复 |
| `bd803d02c` | !2054 fix: 关联CRM商机时校验对接人非空，为空时阻断并提示 |
| `201e58f65` | fix: 关联CRM商机时校验对接人非空，为空时阻断并提示 |
| `0bb80b865` | chore: 移除 contacts 空值校验中的冗余 !contacts 判断 |

### 3.9 测试修复 + 架构治理 + 24小时提交审计（约 8 commit）

| Commit | 说明 |
|--------|------|
| `50673f18a` | !2053 fix(test): 修复 10 个 standaloneSetup 测试因 XML 回退导致 JSON path 断言失败 |
| `68d3dc672` | fix(test): 修复 10 个 standaloneSetup 测试因 XML 回退导致 JSON path 断言失败 |
| `9ae2609b8` | test: 补充回归测试覆盖缺口 — TenderIntake/Webhook/Notification/ClosureGate 核心模块 |
| `d5b5b47fb` | !2056 test: 补充回归测试覆盖缺口 — TenderIntake/Webhook/Notification/ClosureGate 核心模块 |
| `a0fe033da` | fix(test): 修复7项测试失败 + 架构违规治理 |
| `2d12d5835` | !2057 fix(test): 修复7项测试失败 + 架构违规治理（上线前全量测试） |
| `f0366414b` | !2058 fix: 24小时提交后置审计 — 4个关键缺陷修复 |

### 3.10 文档（约 2 commit）

| Commit | 说明 |
|--------|------|
| `23fe1afb2` | !2044 docs(release): 第 83 次测试环境部署报告 |
| `bd8d71992` | docs(release): 第 83 次测试环境部署报告 |

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
# 1. 本地打包（关键：VITE_API_BASE_URL= 显式空触发同源构建；COPYFILE_DISABLE=1 避免 macOS ._ 残留）
RELEASE_ID="f0366414b-api8080" VITE_API_BASE_URL= COPYFILE_DISABLE=1 bash scripts/release/package-release.sh

# 2. 上传产物与部署脚本
scp .release/xiyu-bid-release-f0366414b-api8080.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

# 3. 执行远程部署（SYSTEMCTL_SUDO=true，HEALTHCHECK_URL 指向后端内部端口 18080）
RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-f0366414b-api8080.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=f0366414b-api8080 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="source /etc/xiyu-bid/backend.env && mysqldump ... | gzip > /opt/xiyu-bid/db-backups/winbid-f0366414b-api8080-$(date +%Y%m%d%H%M%S).sql.gz" \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh

# 4. 上一版本前端资源保留（防止跨版本 404）
PREV="/opt/xiyu-bid/releases/c3f2fe8ca-api8080"
sudo cp -rn "$PREV/frontend/assets/"* /srv/www/xiyu-bid/assets/
```

**部署脚本健康检查**：remote-deploy.sh 在 120 次健康检查尝试后报告失败（`readinessState=OUT_OF_SERVICE`），但服务进程稳定（active running 4 分钟），所有其他组件 UP。等待约 2 分钟后 readiness 自行恢复为 UP。此为 Kafka SDK 已知行为，详见第 7 节。

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
| 前端入口 hash | `assets/index-LP3-b5F7.js` + `assets/index-Cmq0rLNS.css` | ✅ 与 release 包一致 |
| 已部署 release 记录 | `/opt/xiyu-bid/deployed-release.json` | ✅ `f0366414b-api8080`（2026-07-13T00:39:38Z） |
| 后端 MainPID | systemctl | `11040`（active running） |

## 7. 问题与经验沉淀

### 7.1 remote-deploy.sh 健康检查误报（Kafka SDK readiness 延迟）

**现象**：remote-deploy.sh 在重启服务后 120 次健康检查（约 4 分钟）均收到 503，脚本判定部署失败。但服务进程稳定（active running，无 crash-loop），所有其他组件（db/redis/jwt/sidecar/aiProvider/ping/livenessState）均 UP，仅 `readinessState=OUT_OF_SERVICE`。手动轮询约 2 分钟后 readiness 自行恢复为 UP。

**根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`，Kafka 初始化阻塞主线程时延迟 `AvailabilityChangeEvent` 处理，导致 readiness 长时间处于 `OUT_OF_SERVICE`。此问题已在第 8、9、10、13、15、81、82、83 次部署中反复出现，是本项目的已知行为。

**处置**：未执行回滚。等待约 2 分钟后服务自行恢复（08:44:08 第 2 次轮询即通过），所有 smoke 验证通过。

**建议**（延续第 83 次报告）：后续考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池执行，避免阻塞主线程。同时 remote-deploy.sh 的健康检查可加入 readiness 延迟容忍或单独检查 `/actuator/health/readiness` 状态。

### 7.2 上一版本前端资源保留

部署完成后，手动从上一版本 release 目录 `/opt/xiyu-bid/releases/c3f2fe8ca-api8080/frontend/assets/` 拷贝旧 hash 化静态资源到 `/srv/www/xiyu-bid/assets/`（`cp -rn`，不覆盖新文件，共 255 个文件），保留 24h 让旧标签页自然刷新，避免 `Unable to preload CSS` 跨版本 404 噪声（第 18 条经验）。

> 注：remote-deploy.sh 在写入新 deployed-release.json 后，原脚本中的"自动保留上一版本 assets"逻辑因 deployed-release.json 已被覆盖而失效（PREV 变量指向当前版本）。本次手动指定旧版本号 c3f2fe8ca 完成保留。

### 7.3 临时调试配置检查

`/etc/xiyu-bid/backend.env` 中保留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`。此为第 13、14、15、81、82、83 次延续的决定，便于 `/actuator/health` 显示完整组件详情用于诊断。非临时调试配置，无需清理。

### 7.4 部署前 SSH 连通性问题（Mac TUN 模式代理拦截）

**现象**：部署前 ssh 探测 `172.16.38.78` 时出现 `kex_exchange_identification: Connection closed by remote host`，curl 也返回 HTTP 000。`nc -zv` 显示 TCP 22/8080 端口可达，但 SSH 协议握手前被关闭。

**根因**：Mac 代理软件（Clash/Surge 类）的 TUN 模式（utun4 接口，网关 `198.18.0.1`）将所有流量（包括 `172.16.0.0/12` 内网）路由到海外代理出口（`104.168.53.121`），导致内网 IP 无法从公网访问。

**处置**：用户在代理软件中调整内网直连规则后，路由切到 `utun5`（网关 `2.0.1.22`），SSH 和 HTTP 恢复正常。

**教训**：这与第 16 条经验（Mac HTTP_PROXY 导致 502）是同一类问题，但 TUN 模式比 HTTP 代理环境变量更难绕过——`--noproxy '*'` 只能绕过 HTTP 代理环境变量，无法绕过 TUN 接口路由。部署前若发现 SSH 握手失败，应优先检查 `route get <target-IP>` 是否被路由到代理 utun 接口。

## 8. GitHub 镜像同步

本次部署未推送 origin/main（仅打包本地 HEAD `f0366414b` 部署到测试服务器），Gitee main 与 GitHub main 均保持不变。

- Gitee main: `f0366414b9e063fc8386ed69f9e90ea5a62cd8f2`
- GitHub main: 仍落后 47 commit
- 状态：未同步（本次部署不需要 GitHub 同步）

> 如需同步 GitHub 镜像：`bash scripts/sync-to-github.sh`（仅主工作区生效）

## 9. 回滚信息

| 项目 | 值 |
|------|-----|
| 回滚触发 | 未触发 |
| 上一可用 release | `c3f2fe8ca-api8080` |
| 上一 release 目录 | `/opt/xiyu-bid/releases/c3f2fe8ca-api8080` |
| 回滚命令 | `sudo cp /opt/xiyu-bid/releases/c3f2fe8ca-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| DB 备份 | 部署前已自动备份至 `/opt/xiyu-bid/db-backups/winbid-f0366414b-*.sql.gz` |

## 10. 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连 + 基线确认（agent/trae-init ff-only 同步到 origin/main `f0366414b`）
- [x] 服务器现状检查（c3f2fe8ca-api8080，active，health UP）
- [x] Flyway 预检 3 步（VALIDATE OK，DB V1165，无新增迁移）
- [x] 本地打包（VITE_API_BASE_URL= 同源构建，28.3 秒 BUILD SUCCESS）
- [x] 产物校验（227 迁移文件无重复，前端入口 index-LP3-b5F7.js）
- [x] 上传 + 部署到测试服务器（SYSTEMCTL_SUDO=true）
- [x] 健康检查 + Smoke 测试（health 200 UP, readiness 200 UP, 全部 smoke 通过）
- [x] 上一版本前端资源保留（cp -rn 保留 255 个文件，24h 自然刷新）
- [x] 临时调试配置检查（仅 SHOW_DETAILS=always，已知保留）
- [ ] GitHub 镜像同步（本次未同步，47 commit 落后，非必需）

## 11. 风险提示

1. **Kafka SDK readiness 延迟**：本次约 2 分钟延迟属已知行为，恢复比前几次更快。若未来 Kafka broker 不可达时间延长，可能影响生产环境对外服务。建议后续考虑 `@Async` 改造。
2. **remote-deploy.sh 旧 assets 自动保留逻辑失效**：脚本在新 deployed-release.json 写入后才执行保留逻辑，导致 PREV 变量指向当前版本。本次手动指定旧版本号兜底。建议后续修复脚本顺序：先备份 PREV 再写入新 release.json。
3. **GitHub 镜像落后 47 commit**：本次部署未同步 GitHub，不影响功能。若后续需要 AI 工具拉取最新代码，执行 `bash scripts/sync-to-github.sh`。
4. **本次部署包含大量测试修复与架构治理**（CO-571 Phase C、24小时提交审计等）：测试覆盖增强，但部分改动涉及核心 webhook/评分/结项流程，建议上线后关注相关业务路径的 Sentry 告警。
