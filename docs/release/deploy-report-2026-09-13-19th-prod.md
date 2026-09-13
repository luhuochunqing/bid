# 第 19 次生产环境部署报告 — 2026-09-13

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| Release ID | `c31f62df1` |
| 基线 commit | `c31f62df1d30298bfbce143c12b513c8240c3735` |
| 前一版本 | `d12f173bd` |
| 部署时间 | 2026-09-13 18:56–18:58 CST |
| 状态 | ✅ 成功 |
| 回滚状态 | 未执行，锚点就绪 |

## 部署概览

本次从生产版本 `d12f173bd` 更新到 `c31f62df1`。主要改动覆盖标书质检/评分解析、合规检查、Sidecar 表格契约以及对应数据库迁移。部署前工作区干净，HEAD 与 `origin/main` 一致。

增量范围内新增 MySQL 迁移：`V1205`、`V1206`、`V1207`、`V1208`、`V1209`、`V1210`、`V1211`、`V1212`、`V1214`；未发现 `DROP TABLE`、`DROP COLUMN`、`TRUNCATE` 或 `DELETE FROM`。

## 变更范围与 PR/提交

本次发布以 `origin/main` 的合并结果为准。可识别的近期变更包括：

- `6a9622cf7`：优化资格条件一票否决检查结论。
- `53fd8ed3f`：修复资质提取跨章节粘连导致的一票否决误判。
- `bc2b27680`：前端隐藏内部评分证据详情。
- `d3ec3f42e`：关闭已完成的复核状态。
- `5a7f2f9bc` 及配套提交：多表评分标准自动拆分与归一化告警治理。
- `59fd5218d`、`5395558f4`：Sidecar 鉴权透传与空 key 行为修复。
- `96292fb46`、`d20bb5a02`：大文件 PDF/文档处理流式化与下载稳定性修复。

完整增量提交范围：`d12f173bd..c31f62df1`。

## Flyway 预检与数据库结果

1. 预检前确认数据库实际版本为 MySQL `8.0.43`，Flyway 最新已应用版本为 `V1204`，失败记录数为 `0`。
2. 补齐服务器 `/tmp/migration-mysql/` 中的当前源码迁移与 `B73__full_schema_baseline.sql` 后，运行服务器现有 `flyway-repair-runner.sh validate`。
3. 预检结果：`Successfully validated 274 migrations`、`VALIDATE OK - all checksums match`。
4. 激活后确认 `V1205`、`V1206`、`V1207`、`V1208`、`V1209`、`V1210`、`V1211`、`V1212`、`V1214` 均为 `success=1`，失败迁移数为 `0`。
5. 未执行 Flyway repair、`SKIP_FLYWAY_VALIDATE` 或破坏性数据库恢复。

## 构建与部署步骤

- 通过环境门禁确认 `ENV=prod` 与目标 `172.16.10.149`。
- 完成 `dev-env.sh`、`sync-env.sh`、`check-git-wrapper.sh`；锚点分支与 `origin/main` 同步。
- 使用项目强制入口 `ENV=prod bash scripts/release/deploy-prod.sh c31f62df1`。
- 前端同源构建：`VITE_API_BASE_URL=""`；OBS 直传显式启用。
- 前端 API 地址校验通过；后端容器测试门禁（Spring 全量上下文 + Flyway）通过。
- Jar 内 Flyway 版本无重复；OBS `Detail-*.js` 中 `.upload(` 调用数为 `2`。
- 发布包大小约 `160M`，远程解包、Sidecar 镜像重建、数据库备份和远程 Flyway validate 均通过。
- 生产数据库备份：`/opt/xiyu-bid/db-backups/winbid-c31f62df1-20260913185657.sql.gz`（约 4.1 MB）。
- 后端服务 `xiyu-bid-backend` 重启后健康检查连续 `3/3` 通过。
- 从上一版本 `d12f173bd` 保留旧 hash assets，降低旧标签页跨版本 404 风险。

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 `/actuator/health` | ✅ UP / HTTP 200 |
| 后端 readiness | ✅ HTTP 200 |
| Nginx 配置 | ✅ `nginx -t` 通过 |
| 前端首页、`/login` | ✅ HTTP 200 |
| 未认证 `/api/projects` | ✅ HTTP 403 |
| 未认证 CRM health | ✅ HTTP 401 |
| 空登录请求 | ✅ HTTP 400 |
| 前端入口 | ✅ `assets/index-B2vtEr7J.js` 与 release 一致 |
| 公网首页 `https://winbid.ehsy.com/` | ✅ HTTP 200 |
| 公网健康接口 | ✅ HTTP 200 |
| Sidecar 发布冒烟 | ✅ 5/5，通过 v1/v2 schema 与 1371 fixture 回放 |
| OBS 直传 | ✅ 已启用 |

说明：执行机访问生产机 `:8080` 超时，但生产机本地 `:8080`、执行机可达的 `:80` 以及公网域名均验证通过；判定为检测位置/端口可达性限制，不是服务故障。

## 回滚信息

- 后端回滚锚点：`/opt/xiyu-bid/releases/d12f173bd/backend/app.jar`。
- 前端回滚锚点：`/opt/xiyu-bid/releases/d12f173bd/frontend/`。
- 当前 DB 备份：`winbid-c31f62df1-20260913185657.sql.gz`。
- 当前环境快照：`/etc/xiyu-bid/backend.env`（未复制、未写入报告）。
- 回滚 owner：本次部署执行 Agent；数据库恢复需 DBA/发布负责人明确确认后执行。
- 本次不需要回滚；未执行任何破坏性恢复命令。

## 经验与风险提示

- 生产发布严格使用 `deploy-prod.sh`，保留同源 API、OBS、Sentry 与远程备份门禁。
- Flyway 预检必须包含 baseline 文件；只同步 `V*.sql` 会误报已应用 `B73` 为未解析迁移。
- 不使用 `SKIP_FLYWAY_VALIDATE=1` 绕过校验。
- macOS tar 产生的 xattr 警告未影响解包；后续可改进打包脚本以消除该噪声。
- 生产仍保留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，本次未改动；需按既有安全策略持续评估。
- 执行机到生产 `:8080` 的网络路径不可达，后续自动化 smoke 应保留服务器本地 8080 或公网域名验证路径。

## 部署确认清单

- [x] 生产环境门禁确认
- [x] 工作区与 `origin/main` 基线确认
- [x] 上一版本前后端回滚锚点确认
- [x] 数据库备份成功
- [x] Flyway validate 通过
- [x] 产物构建、重复迁移与 OBS 校验通过
- [x] Sidecar 重建与 smoke 通过
- [x] 后端重启与连续健康检查通过
- [x] 新迁移全部 `success=1`
- [x] API、前端、公网 smoke 通过
- [x] 上一版本 assets 已保留
- [x] 本部署报告已生成
