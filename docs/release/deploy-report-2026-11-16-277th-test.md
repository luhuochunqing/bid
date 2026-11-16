# 测试环境第 277 次部署报告（评分解析 / 用例推荐修复增量）

## 部署基本信息

- **部署时间**: 2026-11-16 22:34 CST 开始 → 22:38:43 DB 备份 → 22:38:53 激活 → 健康检查连续 3/3 通过（总尝试 78 次）
- **部署环境**: 测试环境（winbid-01: `172.16.38.78:8080`，公网入口 `https://winbid-test.ehsy.com`）
- **发布版本 ID**: `96c3be67e`（第 277 次）
- **上一版本**: `040742974`（第 276 次）
- **部署方式**: 标准链路 —— `scripts/release/package-release.sh`（同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）→ `scp` → `remote-deploy.sh`（`SYSTEMCTL_SUDO=true` + fail-closed DB 备份）
- **数据库迁移**: 无新增，`flyway validate` 285 个迁移校验和全部通过，DB 最新版本 `V1225` 未变

## 本轮发布内容

| 次序 | Commit / PR | 内容 |
|---|---|---|
| 1 | `a2b38409b` / `cd28d7316`（!3034） | 修复固定得分规则被表格权重放大导致 OMS/WMS 计分不一致 |
| 2 | `739408196`（!3034） | 简化 `fixedAward` 判定顺序 |
| 3 | `a1d15cc86`（!3034） | 固定得分边界用例合并为参数化测试 |
| 4 | `d357cce13` / `5f2b4999e`（!3033） | 语料 IDF 与 BM25 长度归一修复商品管理方案推荐偏离 |
| 5 | `023a5e819` | 回填固定得分上限缺失根因与修复（wiki） |
| 6 | `96c3be67e` | 第 276 次测试环境部署报告（docs） |

- 增量范围：`040742974..96c3be67e` 共 8 个 commit（含 2 个合并提交）
- 改动面：backend 9 个文件（6 main + 3 test）、frontend 1 个文件（`src/views/Knowledge/views/caseLabels.js`）、docs/wiki 7 个文件

## 部署前预检

| 项 | 结果 |
|---|---|
| 基线 | `agent/trae/docs-deploy-report-276th` HEAD = `96c3be67e`（= `origin/main` `cd28d7316` + 1 docs commit），工作区干净，早操三连通过 |
| 服务器现状 | 上一版 `040742974` 运行中，backend/nginx active，健康 `UP`（db/aiProvider/redis/sidecar/jwt 均 UP） |
| Flyway validate | ✅ `Successfully validated 285 migrations`，checksums 全部一致 |
| DB 已应用版本 | 最新 `1225 score estimate freshness`，源码最新同为 `V1225`，本次无 pending 新迁移 |
| 磁盘 | `/dev/vda1` 99G，已用 74G（79%），剩余 21G |

## 打包与产物校验

| 校验项 | 结果 |
|---|---|
| 容器测试门禁（FlywayMysqlContainerTest） | 通过（Spring 全量上下文装配正常） |
| jar 内 Flyway 迁移 | 284 个 `V*.sql`，无重复版本 |
| `release-metadata.json` | `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false` |
| OBS 直传 | `Detail-*.js` 中 `.upload(` 调用数 = 2 |
| 前端入口 | `assets/index-HCuW1PLc.js` |
| 发布包 | `xiyu-bid-release-96c3be67e.tar.gz`（156M） |

## 部署执行

1. `scp` 发布包 + `remote-deploy.sh` 到 `/opt/xiyu-bid/incoming/`
2. DB 备份（fail-closed）：`winbid-96c3be67e-20261116223843.sql.gz`（16M）
3. Flyway validate 预检通过（任何版本切换之前）
4. 前端激活：自动保留上一版及再前一版 assets（`040742974`、`392d0e94c`），防止旧标签页 404
5. 停止 → 原子替换 jar → 写 `deployed-release.json` → 启动 `xiyu-bid-backend`
6. 健康检查连续 3/3 通过（总尝试 78 次，Kafka 启动期延迟属已知行为）
7. 前端一致性校验通过：`index.html` 引用 `assets/index-HCuW1PLc.js` 与 release 一致

## 验证结果

| 检查 | 期望 | 实测 |
|---|---|---|
| `/actuator/health` | 200 UP | 200，`db`/`aiProvider`/`diskSpace`/`redis`/`sidecar`/`jwt` 均 UP |
| `/actuator/health/readiness` | UP | 200 UP |
| `POST /api/auth/login`（空体） | 400 参数校验 | 400 |
| `/api/projects`（未认证） | 401/403 | 401 |
| `/api/integration/crm/health`（未认证） | 401 | 401 |
| 前端 `/` 与 `/login` | 200 | 200 / 200 |
| 入口 JS | `index-HCuW1PLc.js` | 与 release 一致 |
| 公网入口 `https://winbid-test.ehsy.com/` | 200 且入口一致 | 200，`index-HCuW1PLc.js` |
| 后端日志 | 无 ERROR | 近 10 分钟 0 条 ERROR |
| 启动后 DB 迁移 | 无新增 | 最新仍为 `V1225` |

## 配置与清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍保留在 `/etc/xiyu-bid/backend.env`（测试环境历史决定项，非本次引入）。
- 无其他 `DEBUG` / `TRACE` 临时配置。

## 风险与回滚

- 无新增迁移，无破坏性 schema 变更，DB 回滚压力低。
- 回滚锚点：
  - 上一版 release：`/opt/xiyu-bid/releases/040742974`（jar + 前端完整保留）
  - DB 备份：`/opt/xiyu-bid/db-backups/winbid-96c3be67e-20261116223843.sql.gz`（16M）
  - 回滚步骤见 `docs/release/ROLLBACK.md`
- 前端旧 hash 资源已按机制保留最近 2 个已激活版本（`/srv/www/xiyu-bid/assets` 共 868 个文件），跨版本 preload 404 风险已覆盖。

## 待跟进

- 本次为纯评分解析 / 用例推荐逻辑修复，建议业务侧在测试环境验证：固定得分项（OMS/WMS）计分一致性、商品管理方案推荐结果。
- 本报告需提 PR 合入 main（建议标题：`docs(release): 第 277 次测试环境部署报告`）。
