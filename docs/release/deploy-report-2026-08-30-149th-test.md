# 第 149 次部署报告（test 环境）

**日期**：2026-08-30
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`a078c76b9-api8080`
**部署时间**：21:19 - 21:40（含 Flyway 预检处置约 8 分钟）
**状态**：✅ 成功

> 序号说明：前一份报告为 148th（2026-08-28，`353ba8bcb`）。服务器记录显示 2026-08-29 23:00 CST 曾部署 `ac5127dca-api8080`，但该次**报告缺失**（本次部署前已确认 `docs/release/` 与 git 历史均无对应报告），在此补记。

## 部署概览

- 增量 6 个 commit（标书质量核查 compliance 系列），无新增 Flyway 迁移。
- 部署前置发现并处置一处 Flyway validate 阻塞：服务器 `/tmp/migration-mysql/` 目录过时 + V1092 良性 checksum mismatch，经目录同步 + `flyway-repair-runner.sh repair` 解决。
- 后端重启后 readiness 503 约 4 分 50 秒（Kafka SDK 已知行为），自恢复，未回滚。

## 基线信息

| 项目 | 值 |
|------|-----|
| 基线 commit | `a078c76b9`（origin/main，HEAD = origin/main 零差异） |
| 前次部署 | `ac5127dca-api8080`（2026-08-29 23:00 CST 激活，**报告缺失**） |
| 增量 commit | 6 个 |
| 新增迁移 | **无**（DB 停留在 V1204，failed 记录 0 条） |
| 工作区状态 | 干净 |
| 早操三连 | sync-env + check-git-wrapper 全过（git wrapper 7/7） |

## 改动范围（6 个增量 commit）

| PR | 描述 |
|----|------|
| !2470 | feat(compliance): 标书质量核查全标书 RAG 语义分页检索与 LLM 结构化输出通道 |
| !2469 | fix(compliance): LLM 裁决解析失败补可观测性日志 + 重试契约单测 |
| !2468 | feat(compliance): 质量核查展示对齐 PRD 2.3 + LLM 裁决解析三层容错 |
| — | docs: 知识沉淀 + wiki 回填（LLM 裁决解析三层防御） |
| — | feat(compliance): 质量核查展示对齐 PRD 2.3（!2468 的原始提交与重落） |

改动统计：30 files changed, +3996 / -209（含 BidDocumentTestFixtures 297 行测试夹具、前端 QualityCheckDetailDialog/QualityCheckItemCard 调整）。

## Flyway 预检与处置（本次重点）

**Step 1 validate 首跑失败（exit 1），两级原因：**

1. **`/tmp/migration-mysql/` 过时**（经验 #10 复发）：目录内缺 V1203/V1204，validate 报 "Detected applied migration not resolved locally"。处置：`rm -rf` 后从本地源码全量重新 scp（273 个 SQL 文件）。
2. **V1092 checksum mismatch**（修复后暴露）：
   - DB 已应用 checksum `1842156770`（2026-06-24 应用）
   - 源码当前 checksum `-1522937517`（2026-07-09 `5e6d28ac7` collation 修复，解决 prod 首次部署阻断）
   - **良性判定**：文件为合法 post-apply 修复；已部署的 `ac5127dca` jar 已含新版 V1092 且应用连续运行正常（应用侧 `application-prod.yml`/`application-mysql.yml` 均设 `validateOnMigrate: false`，启动不拦截 mismatch）；runner 的独立 validate 比应用启动更严格。
   - 处置：`bash /opt/xiyu-bid/bin/flyway-repair-runner.sh repair`（仅对齐 schema history checksum，不动 schema；runner 自动备份 history 到 `/opt/xiyu-bid/backups/flyway-history/flyway_schema_history-20260830-212956.sql`）。
   - 复验：validate exit 0，"Successfully validated 265 migrations"。

**Step 2**：DB 最近迁移 V1204（success=1，2026-08-28）与源码一致，无 pending。
**Step 3**：remote-deploy.sh 内置 validate（jar 激活前）通过。

## 部署步骤时间线（CST）

| 时间 | 步骤 |
|------|------|
| 21:19 | 早操三连 + 服务器现状探测（前次 release `ac5127dca-api8080`，health UP） |
| 21:22 | Flyway 预检：发现 /tmp 目录过时 → 全量同步迁移 SQL |
| 21:29 | repair 对齐 V1092 checksum → validate exit 0 |
| 21:32 | 本地打包完成（mvn 32 秒；jar 内 264 个 V*.sql 无重复；OBS 校验 `.upload(` = 2） |
| 21:33 | scp 154M 压缩包 → 服务器 DB 备份 → AI Provider 守卫 → 前端激活 → Flyway validate → 覆盖 jar → systemctl restart（`SYSTEMCTL_SUDO=true`） |
| 21:34-21:38 | 健康检查 503（readiness OUT_OF_SERVICE，业务接口正常 200） |
| 21:38:45 | readiness 自恢复（启动后 4 分 50 秒） |
| 21:39-21:40 | 外部 Smoke 全绿 |

## 验证结果

**后端健康（127.0.0.1:18080 与 Nginx 8080 双路径）**：`{"status":"UP"}`，9 组件全 UP（aiProvider/db/diskSpace/jwt/liveness/ping/readiness/redis/sidecar）。

**API Smoke（经 Nginx 8080，`--noproxy '*'`）**：

| 接口 | 期望 | 实际 |
|------|------|------|
| `/actuator/health` | 200 UP | ✅ 200 UP |
| `/actuator/health/readiness` | 200 | ✅ 200 |
| `POST /api/auth/login`（空 body） | 400 | ✅ 400 |
| `/api/projects`（未认证） | 403 | ✅ 403 |
| `/api/integration/crm/health`（未认证） | 401 | ✅ 401 |

**前端 Smoke（经 Nginx 8080）**：`/` 200、`/login` 200、线上入口 hash `assets/index-JZ5MiT2m.js` 与本地产物一致。

**产物校验**：`release-metadata.json` obsEnabled=true、apiBaseUrl=""（同源构建）、jar 内 V*.sql 无重复。

**部署后动作**：旧版 assets 已从 `ac5127dca-api8080/frontend/` `cp -rn` 保留（防旧标签页 preload 404，经验 #17）。

## 回滚信息

| 锚点 | 位置 |
|------|------|
| 前后端制品 | `/opt/xiyu-bid/releases/ac5127dca-api8080/`（jar + frontend） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-a078c76b9-20260830213346.sql.gz`（12M，mysqldump gzip） |
| Flyway history 备份 | `/opt/xiyu-bid/backups/flyway-history/flyway_schema_history-20260830-212956.sql` |
| Nginx 配置锚点 | `/etc/nginx/conf.d/xiyu-bid.conf.bak-20260817`（未变更） |
| 回滚 owner | 部署执行 Agent（trae） |

回滚状态：**就绪，未需要**。

## 经验沉淀应用情况

1. **经验 #10（/tmp/migration-mysql 过时）**：本次复发并升级认知——该目录过时不仅"不影响 validate"，还会让 runner validate 直接 fail（缺 resolved 文件 + 掩盖真实 mismatch）。已在本目录全量重同步；建议后续把 `/tmp/migration-mysql` 同步纳入 remote-deploy 或改为从 jar 内提取。
2. **经验 #15（Mac HTTP_PROXY 502）**：全部 curl 带 `--noproxy '*'`，未受本机代理影响。
3. **经验 #17（前端 hash 资源跨版本 404）**：部署后立即执行 `cp -rn` 保留旧 assets。
4. **经验 #2（Kafka SDK readiness 延迟）**：第 16 次复现（启动后 4 分 50 秒自恢复），按已知行为处置，未误判为故障、未回滚。
5. **经验 #7（SYSTEMCTL_SUDO）**：显式传入，服务重启一次成功。

## 风险提示

- **V1092 repair 影响面**：repair 仅修正 checksum 记录。该文件 2026-07-09 起源码与 DB 记录即不一致，本次只是把隐藏的漂移显式对齐。若 prod 库也存在同样漂移，后续 prod 部署前需同样预检（prod 侧 `validateOnMigrate: false` 同样不拦截启动）。
- **报告缺口**：`ac5127dca`（8-29）部署报告缺失，本次已补记序号说明；建议回归"每次部署必须生成报告"纪律。
- **8 个不符合命名约定的 SQL**：validate 提示 "8 SQL migrations were detected but not run because they did not follow the filename convention"（U* 回滚脚本与 B* baseline 属预期），265 个正式迁移全部 validate OK，不构成阻塞。
- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 按历史决策保留（第 13/14/15 次确认）。

## 部署确认清单

- [x] 环境门禁：AskUserQuestion 确认 test（172.16.38.78）
- [x] 早操三连 + 工作区干净 + HEAD=origin/main
- [x] Flyway 预检 3 步（validate / DB 对比 / 部署内置 validate）
- [x] 打包同源构建 + OBS 双保险 + 产物校验
- [x] remote-deploy（DB 备份 → 前端激活 → jar 覆盖 → 重启）
- [x] 健康检查 UP + readiness 自恢复
- [x] Smoke 全绿（5 API + 2 页面 + hash 一致）
- [x] 旧版 assets 保留
- [x] 临时配置检查（无新增）
- [x] 部署报告（本文件）
