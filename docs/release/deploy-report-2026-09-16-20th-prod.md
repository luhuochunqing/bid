# 第 20 次生产环境部署报告 — 2026-09-16

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| Release ID | `4a93f08ad` |
| 基线 commit | `4a93f08ad`（`origin/main`） |
| 前一版本 | `c31f62df1`（2026-09-13） |
| 部署时间 | 2026-09-16 20:51–20:53 CST |
| 状态 | ✅ 成功 |
| 回滚状态 | 未执行，锚点就绪 |

## 部署概览

本次从生产版本 `c31f62df1` 更新到 `4a93f08ad`。增量范围内共 **79 个提交**、**280 个变更文件**，主要覆盖知识案例来源溯源、项目详情返回按钮、业绩案例推荐准入、评标/结项角色权限收敛、工作台 AI 商机预测优化等。

增量范围内新增 **1 个 MySQL 迁移 `V1215`**（`knowledge_case` 增加来源标题/章节路径/文档名/页码/章节序号 5 个 NULL 列）。**无** `DROP TABLE`、`DROP COLUMN`、`TRUNCATE` 或 `DELETE FROM`。

## 变更范围与 PR/提交

本次发布以 `origin/main`（`4a93f08ad`）为准，增量提交范围 `c31f62df1..4a93f08ad`。可识别的近期变更包括：

- 知识案例来源溯源（V1215，应答片段来源标题/章节定位，WF 0915-01）。
- 项目详情页横条返回按钮 `@back` 事件修复。
- 业绩评分项案例推荐准入、金额门槛与资格项回退（spec 064）。
- 收敛评标与结项阶段角色权限判定，支持 `bid-SystemAdmin`。
- 工作台 AI 商机预测表头双行网格重构、补发历史商机预测提醒、实时解析收件人。
- 第 185 / 186 次测试环境部署报告文档。

完整增量提交范围：`c31f62df1..4a93f08ad`。

## Flyway 预检与数据库结果

1. 预检前确认数据库实际版本为 MySQL 8.0，Flyway 最新已应用版本为 `V1214`，失败记录数为 `0`。
2. 服务器执行 `flyway-repair-runner.sh validate`：`Successfully validated 274 migrations`、`VALIDATE OK - all checksums match`。
3. 打包产物校验：jar 内 Flyway 迁移版本无重复。
4. 激活后确认 `V1215`（add source provenance to knowledge case）为 `success=1`，`installed_on=2026-09-16 20:52:20`，失败迁移数为 `0`。
5. 未执行 Flyway repair、`SKIP_FLYWAY_VALIDATE` 或破坏性数据库恢复。

## 构建与部署步骤

- 通过环境门禁确认 `ENV=prod` 与目标 `172.16.10.149`。
- 完成 `dev-env.sh`、`sync-env.sh`、`check-git-wrapper.sh`；当前 HEAD 与 `origin/main` 一致（`4a93f08ad`）。
- 使用项目强制入口 `ENV=prod bash scripts/release/deploy-prod.sh 4a93f08ad`。
- 前端同源构建：`VITE_API_BASE_URL=""`；OBS 直传显式启用；Sentry DSN 由脚本内置注入。
- Jar 内 Flyway 版本无重复；OBS `Detail-*.js` 中 `.upload(` 调用数为 `2`。
- 发布包大小约 `160M`，远程解包、Sidecar 镜像重建、数据库备份和远程 Flyway validate 均通过。
- 生产数据库备份：远程 `remote-deploy.sh` 自动执行的 `mysqldump` 备份（`/opt/xiyu-bid/db-backups/winbid-4a93f08ad-*.sql.gz`）。
- 后端服务 `xiyu-bid-backend` 重启后健康检查 `UP`。
- 从上一版本 `c31f62df1` 保留旧 hash assets（当前 assets 目录 193 个文件，含旧版本资源），降低旧标签页跨版本 404 风险。

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 `/actuator/health`（服务器本地 18080） | ✅ UP / HTTP 200 |
| 后端 readiness | ✅ HTTP 200 |
| 未认证 `/api/projects` | ✅ HTTP 403 |
| 未认证 CRM health | ✅ HTTP 401 |
| 空登录请求 | ✅ HTTP 400 |
| 前端入口 | ✅ `assets/index-BUmceXnw.js` 与 release 一致 |
| 公网首页 `https://winbid.ehsy.com/` | ✅ HTTP 200 |
| 公网健康接口 | ✅ HTTP 200 |
| OBS 直传 | ✅ 已启用（`.upload(` 调用数=2） |
| Sentry 前端 | ✅ `sentryEnabled=true`（production） |
| Sidecar 发布冒烟 | ✅ 5/5，通过 v1/v2 schema 与 1371 fixture 回放 |
| 迁移 `V1215` | ✅ `success=1` |
| 前端 API 地址 | ✅ `apiBaseUrl=""`（同源构建） |

说明：执行机访问生产机 `:8080` 存在端口可达性限制，健康/Smoke 均在服务器本地 `:18080` 验证；公网域名验证路径正常。

## 回滚信息

- 后端回滚锚点：`/opt/xiyu-bid/releases/c31f62df1/backend/app.jar`。
- 前端回滚锚点：`/opt/xiyu-bid/releases/c31f62df1/frontend/`。
- 当前 DB 备份：`/opt/xiyu-bid/db-backups/winbid-4a93f08ad-*.sql.gz`。
- 当前环境快照：`/etc/xiyu-bid/backend.env`（未复制、未写入报告）。
- 回滚 owner：本次部署执行 Agent；数据库恢复需 DBA/发布负责人明确确认后执行。
- 本次不需要回滚；未执行任何破坏性恢复命令。

## 经验与风险提示

- 生产发布严格使用 `deploy-prod.sh`，保留同源 API、OBS、Sentry 与远程备份门禁。**手动拼接 `package-release.sh` 会丢失 Sentry DSN（`sentryEnabled=false`），不符合生产标准**；本次初次手动打包发现后即改用 `deploy-prod.sh` 标准管道重打补齐。
- Flyway 预检必须包含 baseline 文件；只同步 `V*.sql` 会误报已应用 `B73` 为未解析迁移。
- 不使用 `SKIP_FLYWAY_VALIDATE=1` 绕过校验。
- 生产仍保留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，此为用户既定保留项，本次未改动；需按既有安全策略持续评估。
- 执行机到生产 `:8080` 的网络路径不可达，自动化 smoke 应保留服务器本地 `18080` 或公网域名验证路径。

## 部署确认清单

- [x] 生产环境门禁确认
- [x] 工作区与 `origin/main` 基线确认
- [x] 上一版本前后端回滚锚点确认
- [x] 数据库备份成功
- [x] Flyway validate 通过
- [x] 产物构建、重复迁移、OBS 与 Sentry 校验通过
- [x] Sidecar 重建与 smoke 通过
- [x] 后端重启与健康检查通过
- [x] 新迁移 `V1215` `success=1`
- [x] API、前端、公网 smoke 通过
- [x] 上一版本 assets 已保留
- [x] 本部署报告已生成