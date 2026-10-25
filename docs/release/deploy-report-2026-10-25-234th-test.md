# 第 234 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-25 11:00 (UTC+8) |

> 说明：第 233 次（`1de4f2bf2`）于 2026-10-25 10:09 部署到测试环境，但未产出部署报告；本次按序记为第 234 次。

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `f3ff316f2f646a4008b5217d28e4d314ff3e5e39-api8080` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit | `f3ff316f2` |
| 旧版本 | `1de4f2bf27a7c6985132b88d0c1500f048a812bc-api8080`（第 233 次，2026-10-25 10:09） |
| 构建包 | `.release/xiyu-bid-release-f3ff316f2f646a4008b5217d28e4d314ff3e5e39-api8080.tar.gz`（155M，MD5 `187b8d1c84d78c02831e02f2140ba76f`） |
| 增量 | 2 个 commit，5 个文件（+22 / -2） |
| 激活时间 | 2026-10-25T03:00:02Z（11:00:02 UTC+8） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-f3ff316f2f646a4008b5217d28e4d314ff3e5e39-api8080-20261025105952.sql.gz`（15M） |

## PR 列表（增量 2 个 commit，自 1de4f2bf2..f3ff316f2）

| Commit | 说明 |
|---|---|
| `f3ff316f2` | **!2867** fix(scoreparse): 修复项目22业绩阶梯计分与匹配范围 |
| `d41999307` | test(scoreparse): 补齐项目22发布门禁证据 |

## 改动范围

- **数据库迁移**：**零变更**（源码与服务器 DB 均为 V1224，无 pending 迁移）
- **后端**：`scoreparse/domain/IncrementalCountScorePolicy.java`（+2 行，业绩阶梯计分与匹配范围修正）
- **测试**：`GoldenCasesReplayTest.java` + `case-project-1382-pdf-real-shape.expected.json`（+9 行，golden 用例覆盖）
- **前端**：**零变更**（前端产物随构建更新 hash，代码无改动）
- **Sidecar 源码**：**零变更**（指纹 `24c0104f…`，schemaVersion 2.2，与已部署版本一致）
- **文档 / Wiki**：`.wiki/pages/scoreparse-performance-evidence-clarity.md`、`.wiki/log.md`

## Flyway 预检结果

```
部署前：flyway-repair-runner.sh validate -> ✅ Successfully validated 284 migrations, all checksums match
部署前 DB 版本：V1224（clean case page numbers and backfill provenance，2026-10-25 08:55:14）
源码最新版本：V1224
pending 迁移：0
部署后：仍为 V1224（无新迁移应用）
```

## 产物校验

```
jar 内 Flyway 迁移版本无重复：✅
OBS 直传启用：obsEnabled=true，Detail chunk .upload( 调用数 = 2 ✅
前端入口：assets/index-CkGSFnxa.js（与服务器实际加载一致）✅
```

## 执行过程

1. 早操三连：`sync-env.sh` → 锚点分支 `agent/trae-init` 与 `origin/main` 已同步，工作区干净；本地门禁 7/7 通过
2. 服务器现状：旧版本 `1de4f2bf2`，health UP
3. Flyway 预检：validate 通过（284 迁移），DB 版本 = 源码版本 V1224
4. 本地打包（`RELEASE_ID` + `VITE_API_BASE_URL=` + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
5. scp 上传发布包（156M）与 `remote-deploy.sh`
6. `remote-deploy.sh`：停机 → DB 备份 → Flyway validate → 激活前端 → 原子替换 jar → 写 deployed-release.json → 启动服务
7. 健康检查连续 3/3 通过（累计尝试 81 次）；前端版本一致性校验通过
8. 回补上一版本 assets（`cp -rn`，当前 assets 193 个，保留旧 `index-Cow69vtc.js` 供未刷新标签页使用）

## Smoke 验证

| 项目 | 结果 |
|---|---|
| `GET /actuator/health` | HTTP 200 |
| `GET /actuator/health/readiness` | HTTP 200 |
| `POST /api/auth/login`（空体） | HTTP 400（预期参数校验） |
| `GET /api/projects`（未认证） | HTTP 401（预期鉴权） |
| `GET /api/integration/crm/health`（未认证） | HTTP 401（预期鉴权） |
| `GET /` | HTTP 200 |
| `GET /login` | HTTP 200 |
| 前端入口脚本 | `assets/index-CkGSFnxa.js`（与产物一致） |

启动日志（近 8 分钟）：**0 条 ERROR**。

## 已知配置

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：沿用历史决定（第 13/14/15 次部署确认保留），health 端点返回明细，仅测试环境。
- 本次未新增临时调试配置，无配置清理项。

## 回滚方案

1. 前端恢复上一版本：`/opt/xiyu-bid/releases/1de4f2bf27a7c6985132b88d0c1500f048a812bc-api8080/frontend` → `/srv/www/xiyu-bid`
2. 后端 jar 恢复上一版本并重启 `xiyu-bid-backend`（上一版本 release 目录内保留 artifact）
3. 数据库回滚：本次无迁移变更，**无需数据库回滚**；如需兜底可从 `winbid-f3ff316f…-20261025105952.sql.gz` 恢复

## 结论

部署成功。版本 `1de4f2bf2` → `f3ff316f2`，无数据库迁移变更，健康检查与 Smoke 全部通过，OBS 直传与前端资源保留策略均已落实。
