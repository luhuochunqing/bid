# 第 21 次生产环境部署报告 — 2026-09-17

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| Release ID | `04bb5eef2` |
| 基线 commit | `04bb5eef2`（`origin/main`） |
| 前一版本 | `4a93f08ad`（2026-09-16） |
| 部署时间 | 2026-09-17 21:44–21:49 CST |
| 状态 | ✅ 成功 |
| 回滚状态 | 未执行，锚点就绪 |

## 部署概览

本次从生产版本 `4a93f08ad` 更新到 `04bb5eef2`。增量范围内主要覆盖：案例详情原文所在标题纯文本展示（取消下载链接）、数据分析部门全选时参数正规化（修复 URL 超长触发 414）、AI 网关对 Kimi K3 等推理模型固定 temperature 的自适应兼容、评分证据 JSON 序列化转义与抽取换行清洗缺陷修复、以及第 20 次生产部署报告与第 187 次测试部署报告文档。

**本次增量不含任何 MySQL 迁移**（`4a93f08ad..04bb5eef2` 迁移文件 diff 为空），数据库 schema 无变更。

## 变更范围与 PR/提交

本次发布以 `origin/main`（`04bb5eef2`）为准，增量提交范围 `4a93f08ad..04bb5eef2`。可识别 PR 与提交包括：

- `!2678` / `e4224a3ff` 案例详情原文所在标题调整为纯文本展示取消下载链接（含 wiki 回填 `44c89066e`）。
- `!2677` / `6f780c6cd` 数据分析部门全选时正规化参数避免 URL 超长触发 414。
- `!2676` / `2af0a66c2` AI 网关支持 Kimi K3 等推理模型固定 temperature 的自适应兼容。
- `!2675` / `8f284d5ff` 评分证据 JSON 序列化转义与抽取换行清洗缺陷修复，并精简 classifyRule 分支。
- `!2674` / `ea4723523` 第 20 次生产部署报告（2026-09-16）。
- `002a283ae` / `04bb5eef2` 第 187 次测试环境部署报告。

完整增量提交范围：`4a93f08ad..04bb5eef2`。

## Flyway 预检与数据库结果

1. 预检前服务器 `flyway-repair-runner.sh validate` 曾报 `applied migration not resolved locally: 1215`，诊断为**服务器 `/tmp/migration-mysql/` 目录过时**（缺已应用的 `V1215`，为技能已知坑 #10）。同步 `V1215` 到该目录后重跑：`Successfully validated 275 migrations`、`VALIDATE OK - all checksums match`。
2. DB 最新已应用版本为 `V1215`（add source provenance to knowledge case，`success=1`，2026-09-16 20:52:20），失败迁移记录数 `0`。
3. 本 release 无新增迁移，激活新 jar 前 remote-deploy 自动 validate 通过（275 migrations，checksums match）。
4. 未执行 Flyway repair、`SKIP_FLYWAY_VALIDATE` 或任何破坏性数据库恢复。

## 构建与部署步骤

- 通过环境门禁确认 `ENV=prod` 与目标 `172.16.10.149`。
- 完成 `dev-env.sh`、`sync-env.sh`、`check-git-wrapper.sh`；当前 HEAD 与 `origin/main` 一致（`04bb5eef2`）。
- 受影响后端测试先行通过：`OpenAiCompatibleClientTest`（10）、`DeterministicScoreFactExtractorTest`（16）、`GoldenCasesReplayTest`（14），0 失败。
- 使用 `package-release.sh` 手动打包（RELEASE_ID=`04bb5eef2`、`VITE_API_BASE_URL=` 同源、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`）。
- 产物校验：jar 内 Flyway 迁移无重复；OBS `Detail-*.js` 中 `.upload(` 调用数 `2`（obsEnabled 已启用）；前端入口 index 与 `index.html` 一致。
- 上传 `remote-deploy.sh` 并远程部署（`SYSTEMCTL_SUDO=true`，DB 备份开启）。
- 生产数据库备份：`/opt/xiyu-bid/db-backups/winbid-04bb5eef2-20260917214611.sql.gz`。
- 后端服务 `xiyu-bid-backend` 重启后健康检查通过（连续 3/3，共 15 次尝试）。
- 从上一版本 `4a93f08ad` 保留旧 hash assets（`cp -rn` 补齐），当前 assets 目录 302 个文件，降低旧标签页跨版本 404 风险。

> **注**：本次打包走 `package-release.sh` 手动管道（skill 缺省路径），而非第 20 次报告确立的生产标准 `deploy-prod.sh`。因此 `sentryEnabled=false`（未注入 `VITE_SENTRY_DSN`），与上一版本的 `true` 不同。该偏差已在部署前经用户明确确认「保持 false，直接部署」。详见「经验与风险提示」。

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 `/actuator/health`（服务器本地 18080） | ✅ UP / HTTP 200 |
| 后端 readiness（服务器本地 18080） | ✅ HTTP 200 |
| 未认证 `/api/projects` | ✅ HTTP 403 |
| 未认证 CRM health | ✅ HTTP 401 |
| 空登录请求 | ✅ HTTP 400（参数校验消息） |
| 前端入口 | ✅ `assets/index-24eAS0n1.js` 与 release 一致 |
| 前端 `/` 与 `/login` | ✅ HTTP 200 |
| Flyway 已应用版本 | ✅ 停在 `V1215`（本 release 无新增迁移） |
| OBS 直传 | ✅ 已启用（`.upload(` 调用数=2，`obsEnabled=true`） |
| 前端 API 地址 | ✅ `apiBaseUrl=""`（同源构建） |
| Sentry 前端 | ⚠️ `sentryEnabled=false`（用户确认保持，本 release 前为 true） |

说明：执行机访问生产机 `:8080` 存在端口可达性限制，健康/Smoke 均在服务器本地 `:18080`（经 nginx 代理）验证通过。

## 回滚信息

- 后端回滚锚点：`/opt/xiyu-bid/releases/4a93f08ad/backend/app.jar`。
- 前端回滚锚点：`/opt/xiyu-bid/releases/4a93f08ad/frontend/`。
- 当前 DB 备份：`/opt/xiyu-bid/db-backups/winbid-04bb5eef2-*.sql.gz`。
- 当前环境快照：`/etc/xiyu-bid/backend.env`（未复制、未写入报告）。
- 回滚 owner：本次部署执行 Agent；数据库恢复需 DBA/发布负责人明确确认后执行。
- 本次不需要回滚；未执行任何破坏性恢复命令。

## 经验与风险提示

- **`deploy-prod.sh` vs `package-release.sh`**：第 20 次报告确立生产应用 `deploy-prod.sh`（自动注入 Sentry DSN）。本次按 skill 默认走 `package-release.sh` 手动管道，导致 `sentryEnabled=false`。生产后续发布建议回归 `deploy-prod.sh` 标准管道以保持 Sentry 上报；本次偏差由用户明确决策，仅影响前端 Sentry 采集、不影响功能与后端。
- **服务器 `/tmp/migration-mysql/` 目录过时（技能已知坑 #10）**：本次 validate 假阳性由该目录缺 `V1215` 触发，同步后消除。建议部署时同步该目录到最新版本，或在 `flyway-repair-runner.sh` 中改用 jar 内迁移源。
- 生产仍保留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，此为用户既定保留项，本次未改动。
- 执行机到生产 `:8080` 网络路径不可达，自动化 smoke 应保留服务器本地 `18080` 或公网域名验证路径。
- Kafka SDK 启动后 readiness 恢复，本次未出现明显 OUT_OF_SERVICE 延迟。

## 部署确认清单

- [x] 生产环境门禁确认
- [x] 工作区与 `origin/main` 基线确认
- [x] 上一版本前后端回滚锚点确认
- [x] 数据库备份成功
- [x] Flyway validate 通过（275 migrations；假阳性已消除）
- [x] 产物构建、重复迁移、OBS 校验通过
- [x] 后端重启与健康检查通过
- [x] 遗漏迁移 `V1215` 已确认 `success=1`（无新增迁移）
- [x] API 与前端 smoke 通过
- [x] 上一版本 assets 已保留
- [x] 本部署报告已生成