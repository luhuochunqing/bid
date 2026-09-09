# 第 168 次部署报告（test 环境）

**日期**：2026-09-09
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`4c83328e5`
**部署时间**：21:57 - 22:06 CST
**状态**：✅ 成功

## 部署概览

- 增量 2 组 commit（去重后），**无新增 Flyway 迁移**。
- 本次为**标讯事件推送链路改造：直接调用客户 SDK 发送事件**（替代 #2598 的 HTTP 推送路径）：
  - PR !2599：**fix(tenderevent): 直接调用客户 SDK 发送事件**（`68b347174`）+ merge commit（`4c83328e5`）
  - PR !2597：第 167 次测试环境部署报告 docs（`b456f9573` / `0ebf4248f`）
- 核心变更：删除 `TenderEventSdkSender` 及 HTTP body 维护层，`TenderEventSdkProducer` 与 `OpportunityPredictionEventPublisher` 直接注入隔离的 `SystemInteractiveService` 实例并调用四参数 `sendEvent`；traceId 收敛为不超过 18 位（生产由客户 SDK Snowflake 生成，本地无 `HOSTNAME` 时走 87 位安全随机兜底）。
- 涉及 `tenderevent` 集成包 6 个源文件 + 6 个测试文件，无 Flyway 迁移文件变更。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `4c83328e5`（origin/main，早操同步后本地 HEAD，零差异）       |
| 前次部署      | `0c9be61c6`（2026-09-09 第 167 次部署）                   |
| 增量 commit | `0c9be61c6..4c83328e5`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 干净（`agent/trae-init` 锚点分支，HEAD = origin/main）     |
| 早操三连      | sync-env 确认 HEAD = origin/main (`4c83328e5`) + wrapper 自检通过 |

## 增量 commit 变更

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `b456f9573` | PR !2597 | docs(release): 第 167 次测试环境部署报告 |
| `0ebf4248f` | merge | auto-merge PR !2597 |
| `68b347174` | PR !2599 | fix(tenderevent): 直接调用客户 SDK 发送事件 |
| `4c83328e5` | merge | auto-merge PR !2599 |

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，273 migrations all checksums match。

**Step 2**：本次增量 `0c9be61c6..4c83328e5` **无迁移文件变更**（`git diff --name-only` 为空），DB 已应用最新 V1212 无 failed。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

部署后 DB 状态不变，仍为最新版本，无需新迁移验证。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 21:56    | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）                                           |
| 21:57    | 早操三连 + 基线确认（HEAD = origin/main = `4c83328e5`，工作区干净）                              |
| 21:57    | 服务器现状检查（前次 0c9be61c6 UP，health UP）；增量 2 组 commit；迁移变更 = 无；Flyway 预检 validate 通过（273 迁移） |
| 21:58    | 本地打包：mvn clean package（BUILD SUCCESS，33s）+ sidecar + 前端（RELEASE_ID=4c83328e5 + VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1） |
| 21:59    | 产物校验：release-metadata.json obsEnabled=true, apiBaseUrl="" 同源；Detail chunk .upload( 调用数=2；jar 内 272 个 V*.sql 迁移无重复 |
| 21:59    | 上传 archive（301MB）+ remote-deploy.sh（SYSTEMCTL_SUDO=true）→ DB 备份 → Flyway validate → 前端激活（chown 修复非 jetty 属主条目）→ 原子更新 jar → 重启服务 |
| 22:00    | 健康检查通过（144 次尝试，Kafka SDK 启动延迟属已知行为）；前端一致性验证 src="/assets/index-DyOixBsg.js" |
| 22:05    | 前端资源保留：从上一版本 `0c9be61c6` cp -rn 旧 assets（193 文件）到 /srv/www/xiyu-bid/assets/ 避免跨版本 404 |
| 22:05    | Smoke 测试全通过（health/readiness 200, login 400, projects 403, crm/health 401, 前端 / 与 /login 200） |

## 验证结果

| 检查项            | 结果          |
| --------------- | ----------- |
| 后端健康 (/actuator/health) | ✅ 200 UP（含 readiness UP） |
| 后端就绪 (/actuator/health/readiness) | ✅ 200 UP |
| 登录接口 (/api/auth/login, 空密码) | ✅ 400（验证正常） |
| 项目列表 (/api/projects) | ✅ 403（需认证） |
| CRM 集成健康 (/api/integration/crm/health) | ✅ 401（需认证） |
| 前端首页 (/)          | ✅ 200      |
| 前端登录页 (/login)    | ✅ 200      |
| 前端入口 index        | ✅ index-DyOixBsg.js 与 release 一致 |
| OBS 直传启用           | ✅ obsEnabled=true（release-metadata 确认）, .upload( 调用数=2 |
| Flyway 迁移          | ✅ 无新增迁移，273 迁移 validate 通过 |
| DB 备份              | ✅ winbid-4c83328e5-20260909220009.sql.gz (12.7MB) |
| 前端旧 assets 保留      | ✅ 从 0c9be61c6 保留 193 个旧 hash 资源，避免跨版本 404 |
| 后端错误日志           | ✅ 无 ERROR 日志（仅 Smoke 触发的预期 WARN） |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留） |

## 回滚信息

- **回滚锚点**：前一版本 `0c9be61c6`（/opt/xiyu-bid/releases/0c9be61c6/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/winbid-4c83328e5-20260909220009.sql.gz`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/0c9be61c6/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/0c9be61c6/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：本次无迁移变更，无需 DB 回滚

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（validate + DB 版本对比确认无迁移 + remote-deploy 内置）
- ✅ Kafka SDK readiness 延迟（144 次健康检查尝试，未急于回滚）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验 + release-metadata 二次确认）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'，本次无复发）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）
- ✅ 前端 hash 资源跨版本 404 防护（部署后从上一版本 release 目录 `cp -rn` 旧 assets，193 文件）
- ✅ 部署记录 releaseDir JSON 空格陷阱：技能保留命令的 grep 因标准 JSON 带空格取不到 releaseDir（PREV 为空），已改为直接从已知上一版本 release 目录保留 assets

## 风险提示

1. **标讯事件推送链路切换**：本次由 HTTP 推送（#2598）改为直接调用客户 SDK `SystemInteractiveService.sendEvent`（PR !2599）。建议在测试环境实地触发一条标讯事件/商机预测，确认推送成功（检查事件网关返回 code=00000 / result=true，traceId 不超过 18 位）。
2. **traceId 生成策略变更**：无 `HOSTNAME` 的本地环境走 87 位安全随机兜底；生产 Pod 由客户 SDK Snowflake 生成。若线上出现 traceId 超长被拒，需回到 PR !2595 的 18 位约束排查。
3. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受（历史多次决定保留）。
4. **无新增迁移**：本次零 schema 变更，Flyway 风险最低。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操三连 + 基线确认（HEAD = origin/main = 4c83328e5，工作区干净）
- [x] 服务器现状检查（0c9be61c6 UP）
- [x] Flyway 预检 3 步法（validate + 无迁移 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true + index 一致性）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] 前端旧 assets 保留（193 文件）
- [x] Smoke 测试（health/readiness/login/projects/crm/前端）
- [x] DB 备份 + 配置文件检查
- [x] 部署报告生成
