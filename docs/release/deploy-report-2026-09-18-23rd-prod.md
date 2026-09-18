# 第 23 次生产环境部署报告 — 2026-09-18

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| Release ID | `a528f3c61` |
| 基线 commit | `a528f3c61`（`origin/main`） |
| 前一版本 | `930e8210e`（2026-09-17 第 22 次生产） |
| 部署时间 | 2026-09-18 12:12–12:14 CST |
| 状态 | ✅ 成功 |
| 回滚状态 | 未执行，锚点就绪 |

## 部署概览

本次从生产版本 `930e8210e` 更新到 `a528f3c61`。增量范围内唯一代码变更为 **PR `!2685` scoreparse 跨页续表大类总分修复**（根治跨页续表大类总分劫持与无表头分值列推断，湖北中烟项目 33 与招商积余项目 1117 案例），配套更新 `DeterministicScoreFactExtractor` / `HeuristicTableColumnMapper`、新增 `ScoreParseContinuationRowTest` 与 `CellAnchoredScoreExtractorTest` 增强，并回填知识库与 lessons-learned。其余为第 189 次测试部署报告与第 23 次生产部署报告等文档变更。

**本次增量不含任何 MySQL 迁移**（`930e8210e..a528f3c61` 迁移文件 diff 为空），数据库 schema 无变更。

该 scoreparse 修复已先在第 189 次测试环境部署（`eeb524522`）上线并验证健康，生产部署就绪。

## 变更范围与 PR/提交

本次发布以 `origin/main`（`a528f3c61`）为准，增量提交范围 `930e8210e..a528f3c61`。可识别 PR 与提交包括：

- `!2686` / `a528f3c61` 第 189 次测试环境部署报告。
- `!2685` / `eeb524522` + `05b0fae92` **scoreparse 跨页续表大类总分修复**（`fix(scoreparse): 根治跨页续表大类总分劫持与无表头分值列推断`），含测试、知识库与 lessons-learned 回填。
- `!2684` / `c7c13a0d5` + `3c657d54d` 第 22 次生产部署报告（2026-09-18）。

完整增量提交范围：`930e8210e..a528f3c61`，共 6 个 commit。

## Flyway 预检与数据库结果

1. 预检前服务器 `flyway-repair-runner.sh validate`：`Successfully validated 275 migrations`、`VALIDATE OK - all checksums match`。
2. DB 最新已应用版本为 `V1215`（add source provenance to knowledge case，`success=1`，2026-09-16 20:52:20）。
3. 本 release 无新增迁移，激活新 jar 前 remote-deploy 自动 validate 通过（275 migrations，checksums match）。
4. 未执行 Flyway repair、`SKIP_FLYWAY_VALIDATE` 或任何破坏性数据库恢复。

## 构建与部署步骤

- 通过环境门禁确认 `ENV=prod` 与目标 `172.16.10.149`。
- 早操：`dev-env.sh` + `sync-env.sh` + `check-git-wrapper.sh`（锚点分支 `agent/trae-init` ff-only 同步）；当前 HEAD 与 `origin/main` 一致（`a528f3c61`），工作区干净。
- 使用 `package-release.sh` 手动打包（RELEASE_ID=`a528f3c61`、`VITE_API_BASE_URL=` 同源、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`）。
- 产物校验：jar 构建 BUILD SUCCESS、迁移无重复；OBS `Detail-*.js` 中 `.upload(` 调用数 `2`（obsEnabled 已启用）；前端入口 `index-D_FjYa43.js`；`apiBaseUrl=""` 同源。
- 上传 `remote-deploy.sh` 并远程部署（`SYSTEMCTL_SUDO=true`，DB 备份开启）。
- 生产数据库备份：`/opt/xiyu-bid/db-backups/winbid-a528f3c61-*.sql.gz`。
- 后端服务 `xiyu-bid-backend` 重启后健康检查通过（连续 3/3，共 15 次尝试）。
- 已修复 `/srv/www/xiyu-bid` assets 属主（chown 统一为 jetty），前端激活一致。
- 从上一版本 `930e8210e` 保留旧 hash assets（`cp -rn` 补齐），降低旧标签页跨版本 404 风险。

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 `/actuator/health`（服务器本地 18080） | ✅ UP / HTTP 200 |
| 后端 readiness（服务器本地） | ✅ HTTP 200 |
| 公网域名 `/actuator/health`（`https://winbid.ehsy.com`） | ✅ HTTP 200 |
| 公网域名 `/actuator/health/readiness` | ✅ HTTP 200 |
| 未认证 `/api/projects` | ✅ HTTP 403 |
| 未认证 CRM health | ✅ HTTP 401 |
| 空登录请求 | ✅ HTTP 400（参数校验消息） |
| 前端入口 | ✅ `assets/index-D_FjYa43.js` 与 release 一致 |
| 前端 `/` 与 `/login` | ✅ HTTP 200（本地 18080 + 公网域名双路径） |
| Flyway 已应用版本 | ✅ 停在 `V1215`（本 release 无新增迁移） |
| OBS 直传 | ✅ 已启用（`.upload(` 调用数=2，`obsEnabled=true`） |
| 前端 API 地址 | ✅ `apiBaseUrl=""`（同源构建） |
| Sentry 前端 | ⚠️ `sentryEnabled=false`（沿用第 21/22 次用户确认口径，本 release 保持） |

说明：执行机访问生产机 `:8080` 存在端口可达性限制，Smoke 经服务器本地 `:18080`（nginx 代理）与公网域名 `winbid.ehsy.com:443` 双路径验证通过。

## 回滚信息

- 后端回滚锚点：`/opt/xiyu-bid/releases/930e8210e/backend/app.jar`。
- 前端回滚锚点：`/opt/xiyu-bid/releases/930e8210e/frontend/`。
- 当前 DB 备份：`/opt/xiyu-bid/db-backups/winbid-a528f3c61-*.sql.gz`。
- 当前环境快照：`/etc/xiyu-bid/backend.env`（未复制、未写入报告）。
- 回滚 owner：本次部署执行 Agent；数据库恢复需 DBA/发布负责人明确确认后执行。
- 本次不需要回滚；未执行任何破坏性恢复命令。

## 经验与风险提示

- **scoreparse 修复已先在测试环境验证**：`!2685` 已于第 189 次测试部署上线并健康，本次为同源修复的生产推进，风险较低。
- **scp 传输确认**：上传后直接检查服务器 incoming 目录确认 `xiyu-bid-release-a528f3c61.tar.gz`（162MB）到位，避免以管道退出码误判。
- 生产仍保留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，此为用户既定保留项，本次未改动。
- 执行机到生产 `:8080` 网络路径不可达，自动化 smoke 应保留服务器本地 `18080` 或公网域名验证路径。
- 后端重启后健康检查第 15 次通过，未出现 Kafka readiness 长延迟。

## 部署确认清单

- [x] 生产环境门禁确认
- [x] 工作区与 `origin/main` 基线确认
- [x] 上一版本前后端回滚锚点确认
- [x] 数据库备份成功
- [x] Flyway validate 通过（275 migrations）
- [x] 产物构建、重复迁移、OBS 校验通过
- [x] 后端重启与健康检查通过
- [x] 遗漏迁移检查：`V1215` 已应用 `success=1`（无新增迁移）
- [x] API 与前端 smoke 通过（本地 18080 + 公网域名双路径）
- [x] 上一版本 assets 已保留
- [x] 本部署报告已生成