# 第 173 次部署报告（test 环境）

**日期**：2026-09-12
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`c4dddb287`
**部署时间**：16:32 - 16:40 CST
**状态**：✅ 成功

## 部署概览

- 增量 commit 自上次部署 `3988e4db8`（第 172 次），**无新增 Flyway 迁移**。
- 主要变更：
  - **PDF 评分表格路由到证据流水线**（PR !2625）：`fix(scoreparse)` 将 PDF 评分表格路由进 evidence pipeline，配套 sidecar（`app.py` / `converter.py`）表格抽取增强与测试；后端 `ScoreItemPersistenceService` / `ScoreParseAppService` / `ScoreReviewCandidateFactMapper` / `StructuredTableExtractionHandler` 同步收敛。
  - **第 172 次部署报告合入**（PR !2624）：docs(release) 第 172 次测试环境部署报告。
- 本次**无新增 Flyway 迁移**（DB 最高版本维持 V1214）。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `c4dddb287`（origin/main，早操同步后本地 HEAD，零差异）       |
| 前次部署      | `3988e4db8`（2026-09-12 第 172 次部署）                   |
| 增量 commit | `3988e4db8..c4dddb287`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 锚点分支 `agent/trae-init`，HEAD = origin/main = `c4dddb287`，工作区干净 |
| 早操三连      | HEAD 已 = origin/main（锚点分支 ff-only 同步）；wrapper 未激活（已知锚点环境行为） |

## 增量 commit 变更（`3988e4db8..c4dddb287`）

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `6d2231b12` | PR !2624 | docs(release): 第 172 次测试环境部署报告 |
| `d4358d985` | PR !2624 | 合并 !2624 |
| `e0800e88e` | PR !2625 | fix(scoreparse): route pdf scoring tables into evidence pipeline |
| `c4dddb287` | PR !2625 | 合并 !2625 |

## 变更文件范围（15 个）

- `backend/src/main/java/com/xiyu/bid/scoreparse/application/`：`ScoreItemPersistenceService` / `ScoreParseAppService` / `ScoreReviewCandidateFactMapper` / `StructuredTableExtractionHandler`
- `backend/src/test/java/com/xiyu/bid/scoreparse/`：`PdfEvidencePipelineTest` / `ScoreItemPersistenceServiceTest` / `GoldenCasesReplayTest` + golden fixture（2 个）
- `document-converter-sidecar/`：`app.py` / `converter.py` / `tests/test_pdf_table_artifact.py`
- `.wiki/`：pages/score-parse-service.md + log
- `docs/release/deploy-report-2026-09-12-172nd-test.md`

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**（`VALIDATE OK - all checksums match`，274 migrations，无 /tmp 过时误报）。

**Step 2**：本次增量 `3988e4db8..c4dddb287` **无新增迁移**；DB 已应用最高 V1214（2026-09-10 14:17:16 success=1），本地源码含 V1214，版本一致。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`，274 migrations）。

**部署后迁移验证**：DB 最高版本仍为 **V1214**（success=1），无新增迁移，无需回滚。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 16:32 | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）+ 早操（HEAD = origin/main = `c4dddb287`，工作区干净） |
| 16:32 | 服务器现状检查（前次 3988e4db8 UP，health 200 全组件 UP）                                      |
| 16:33 | 增量 commit 确认（无迁移变更）；Flyway 预检 validate 通过（274 migrations）+ DB V1214 版本一致 |
| 16:34 | 本地打包成功：mvn BUILD SUCCESS（33s）+ jar 迁移无重复 + sidecar（RELEASE_ID=c4dddb287 + VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1） |
| 16:35 | 产物校验：release-metadata obsEnabled=true, apiBaseUrl="" 同源；Detail chunk .upload( 调用数=2；sidecarSha256=90c0dd46 |
| 16:35 | 上传 archive（155MB）+ remote-deploy.sh → DB 备份 → Flyway validate → 前端激活 → 原子更新 jar → 重启服务 |
| 16:36 | 健康检查通过（79 次尝试，无 Kafka 延迟）；后端 v1.0.3 启动成功（23.3s）；前端一致性 src="/assets/index-DAgz5x1Y.js" |
| 16:37 | 从旧 release(3988e4db8) 保留旧 assets（本次前端无变更，assets 总数 193 不变）                   |
| 16:38 | Smoke 测试全通过（health/readiness 200, login 400, projects 403, crm/health 401, 前端 / 与 /login 200） |
| 16:39 | 配置文件检查（SHOW_DETAILS=always 历史保留，无 DEBUG/TRACE）；sidecar /health up + 新代码确认（sha256 与 release 一致） |

## 验证结果

| 检查项            | 结果          |
| --------------- | ----------- |
| 后端健康 (/actuator/health) | ✅ 200 UP |
| 后端就绪 (/actuator/health/readiness) | ✅ 200 UP |
| 登录接口 (/api/auth/login, 空密码) | ✅ 400（验证正常） |
| 项目列表 (/api/projects) | ✅ 403（需认证） |
| CRM 集成健康 (/api/integration/crm/health) | ✅ 401（需认证） |
| 前端首页 (/)          | ✅ 200      |
| 前端登录页 (/login)    | ✅ 200      |
| 前端入口 index        | ✅ index-DAgz5x1Y.js 与 release 一致 |
| OBS 直传启用           | ✅ obsEnabled=true（release-metadata 确认）, .upload( 调用数=2 |
| Flyway 迁移           | ✅ 最高 V1214（success=1），无新增迁移 |
| DB 备份              | ✅ winbid-c4dddb287-<timestamp>.sql.gz 已生成（remote-deploy 自动触发） |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留）；无 DEBUG/TRACE |
| Sidecar 更新          | ✅ app.py/converter.py 16:29 更新，含评分表格路由新代码；/health 返回 {"status":"up"}；release sidecar.tar.gz sha256=90c0dd46 与本地一致 |

## 回滚信息

- **回滚锚点**：前一版本 `3988e4db8`（/opt/xiyu-bid/releases/3988e4db8/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/3988e4db8/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/3988e4db8/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：本次无新增迁移，**无需 DB 回滚**。

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（validate + DB 版本对比核对 + remote-deploy 内置二次 validate）
- ✅ 服务器 /tmp/migration-mysql/ 同步（本次无过时误报，274 migrations 一次通过）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验 + release-metadata 二次确认）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'，本次无复发）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）
- ✅ 前端 hash 资源跨版本 404 防护（部署后从 3988e4db8 保留旧 assets）
- ✅ Kafka readiness 延迟（本次无延迟，第 79 次尝试即通过）

## 风险提示

1. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受（历史多次决定保留）。
2. **评分表格证据链路变更**：本次增量将 PDF 评分表格路由进 evidence pipeline 并升级 sidecar 表格抽取。建议测试环境实测一次含评分表（含跨页表格）的标书 PDF，确认评分表进入证据流水线、金标回归无偏差。
3. **sidecar 与后端联动发布**：sidecar（app.py/converter.py）与后端 scoreparse 逻辑同步更新，若后续仅改 sidecar 需确认与当前后端版本兼容。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操三连 + 基线确认（HEAD = origin/main = c4dddb287，工作区干净）
- [x] 服务器现状检查（3988e4db8 UP）+
- [x] Flyway 预检 3 步法（validate 274 migrations 通过 + V1214 版本核对 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true + index 一致性 + sidecar sha256）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] 迁移应用验证（最高 V1214，无新增）
- [x] Smoke 测试（health/readiness/login/projects/crm/前端）
- [x] 保留上一版本 assets（防跨版本 404）
- [x] DB 备份 + 配置文件检查
- [x] Sidecar 新版本确认（sha256 匹配 + /health up）
- [x] 部署报告生成
