# 第 171 次部署报告（test 环境）

**日期**：2026-09-10
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`66954b706`
**部署时间**：14:12 - 14:18 CST
**状态**：✅ 成功

## 部署概览

- 增量多组 commit（自上次部署 `6b72aeba6`，第 170 次），**新增 1 个 Flyway 迁移 V1214**。
- 主要变更：
  - **商机预测事件补偿**（PR !2609 / !2610 / !2610）：`feat(apikey)` 新增 PATCH 管理端接口更新 API Key scopes；商机预测事件推送失败补偿；空字段按契约传空字符串；`fix(workbench)` 补充商机预测项目类型。
  - **评分解析修复**（PR !2608）：修复项目 1374 评分解析冗余公式项、优化央企客观业绩匹配及弹窗证据展示。
  - **大文件流式解析优化**（PR !2607）：超大标书流式解析与前端原生下载容灾、大文件流式 PDF/docx 下载与 SAX 解析优化。
  - **新迁移 **V1214**：区分商机预测"已提醒"和"事件库已受理"，支持事件失败后只重推事件。
- 本次**新增 1 个 Flyway 迁移**：`V1214__track_opportunity_event_publish.sql`。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `66954b706`（origin/main，早操同步后本地 HEAD，零差异）       |
| 前次部署      | `6b72aeba6`（2026-09-10 第 170 次部署）                   |
| 增量 commit | `6b72aeba6..66954b706`                               |
| 新增迁移      | `V1214__track_opportunity_event_publish.sql`           |
| 工作区状态     | 锚点分支 `agent/trae-init`，HEAD = origin/main = `66954b706`，工作区干净 |
| 早操三连      | ff-only 同步到 origin/main (`66954b706`)；wrapper 未激活（已知锚点环境行为） |

## 增量 commit 变更

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `3f6a5e3c4` | PR !25xx | docs(release): 第 170 次测试环境部署报告 |
| `e3a719cae` | PR !2609 | feat(apikey): 新增 PATCH 管理端接口更新 API Key scopes |
| `b1b7f0807` | PR !2609 | fix(workbench): 补偿商机预测事件推送失败 |
| `1919ecb41` | PR !2608 | fix(scoreparse): 修复项目1374评分解析冗余公式项、优化央企客观业绩匹配及弹窗证据展示 |
| `d20bb5a02` | PR !2607 | fix(download,sidecar): 优化大文件原生流式下载与超大docx流式SAX解析容灾 |
| `8abfc52fc` | PR !2606 | fix(tenderevent): 空字段按契约传空字符串 |
| `0f211c06d` | PR !2610 | fix(workbench): 补充商机预测项目类型 |
| `66954b706` | merge | auto-merge PR !2610 |

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，273 migrations all checksums match。

**Step 2**：本次增量 `6b72aeba6..66954b706` 含 **1 个新增迁移** `V1214`；DB 已应用最高 V1212，V1213 不存在，V1214 安全递增。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

**部署后迁移验证**：DB 查询确认 **V1214 已成功应用**（2026-09-10 14:17:16，success=1），`opportunity_prediction_reminder_log` 表 3 个新列 `event_published_at` / `event_publish_claim_token` / `event_publish_claimed_at` 已创建。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 14:12    | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）                                           |
| 14:12    | 早操三连（锚点分支 ff-only 到 origin/main = `66954b706`，wrapper 未激活为已知行为）+ 基线确认 |
| 14:12    | 服务器现状检查（前次 6b72aeba6 UP，health 200，服务 active）                                    |
| 14:12    | 增量 commit 确认；迁移变更 = V1214；Flyway 预检 validate 通过（273 迁移）；rollback U1214 存在 |
| 14:14    | 本地打包成功：mvn BUILD SUCCESS + jar 迁移无重复 + sidecar（RELEASE_ID=66954b706 + VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1） |
| 14:16    | 产物校验：release-metadata obsEnabled=true, apiBaseUrl="" 同源；Detail chunk .upload( 调用数=2；前端入口 index-CWPJWl6h.js |
| 14:16    | 上传 archive（288MB）+ remote-deploy.sh → DB 备份 → Flyway validate → 前端激活 → 原子更新 jar → 重启服务 |
| 14:17    | 健康检查通过（80 次尝试，无 Kafka 延迟）；前端一致性验证 src="/assets/index-CWPJWl6h.js" |
| 14:18    | 保留上一版本 6b72aeba6 旧 assets（278 个文件，防跨版本 404）                                    |
| 14:18    | Smoke 测试全通过（health/readiness 200, login 400, projects 403, crm/health 401, 前端 / 与 /login 200） |

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
| 前端入口 index        | ✅ index-CWPJWl6h.js 与 release 一致 |
| OBS 直传启用           | ✅ obsEnabled=true（release-metadata 确认）, .upload( 调用数=2 |
| Flyway 迁移 V1214     | ✅ 已应用（2026-09-10 14:17:16, success=1），3 个新列创建 |
| DB 备份              | ✅ winbid-66954b706-20260910141652.sql.gz 已生成（12.8MB） |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留）；无 DEBUG/TRACE |

## 回滚信息

- **回滚锚点**：前一版本 `6b72aeba6`（/opt/xiyu-bid/releases/6b72aeba6/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/winbid-66954b706-20260910141652.sql.gz`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/6b72aeba6/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/6b72aeba6/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：V1214 有配套回滚脚本 `U1214__track_opportunity_event_publish.sql`（DROP 3 个 event_* 列），紧急时执行：
  `ssh jetty@172.16.38.78 'mysql -h... -p... xiyu_bid_main < /opt/xiyu-bid/.../U1214__track_opportunity_event_publish.sql'`

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（validate + DB 版本对比 1212→1214 + rollback U1214 存在 + remote-deploy 内置）
- ✅ 迁移成功后再激活（V1214 成功后服务 UP）
- ✅ Kafka SDK readiness 延迟（本次无延迟，健康检查第 80 次尝试即通过）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验 + release-metadata 二次确认）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'，本次无复发）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）
- ✅ 前端 hash 资源跨版本 404 防护（部署后从 6b72aeba6 保留旧 assets，278 个文件）

## 风险提示

1. **V1214 数据回填**：迁移将历史提醒记录的 `event_published_at` 回填为 `notified_at`，将历史提醒视为"已完成事件投递"，**避免上线后重放全部历史项目**。请测试环境实测一条商机预测事件推送，确认事件库受理流程正常（已提醒 vs 已受理状态正确区分）。
2. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受（历史多次决定保留）。
3. **API Key scopes 在线更新**：本次新增 `PATCH /api/admin/api-keys/{id}` 接口（PR !2609），替代直接改 DB 更新 scopes 的方式。建议测试环境验证该接口，确认 scope 更新即时生效。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操三连 + 基线确认（HEAD = origin/main = 66954b706，工作区干净）
- [x] 服务器现状检查（6b72aeba6 UP）
- [x] Flyway 预检 3 步法（validate + 1212→1214 递增 + rollback U1214 存在 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true + index 一致性）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] 迁移应用验证（V1214 success=1 + 3 新列）
- [x] Smoke 测试（health/readiness/login/projects/crm/前端）
- [x] 保留上一版本 assets（防跨版本 404）
- [x] DB 备份 + 配置文件检查
- [x] 部署报告生成