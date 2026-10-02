# 第 27 次生产环境部署报告 — 2026-10-01

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| Release ID | `1f33f73e0` |
| 基线 commit | `1f33f73e0`（`origin/main`） |
| 前一版本 | `5c61e94cc`（2026-09-19 第 26 次生产） |
| 部署时间 | 2026-10-01 11:07–11:21 CST |
| 状态 | ✅ 成功（健康 + Smoke 全通过） |
| 回滚状态 | 未执行，锚点就绪（`5c61e94cc`） |

---

## 部署概览

本次从生产版本 `5c61e94cc` 全量追平至 `origin/main` 最新 `1f33f73e0`（增量 338 个 commit，约 12 天，均已在测试环境多轮验证后随 main 合入）。**本次增量含 5 个 MySQL 迁移**（V1216–V1220，全部非破坏性），DB schema 由 `V1215` 推进至 `V1220`。

本次部署经环境门禁确认（用户发起部署指令，在确认框选择"确认：生产环境 172.16.10.149"，随后确认全量部署 main 最新与执行生产部署），符合生产授权红线。

---

## 变更范围与 PR/提交

本次发布以 `origin/main`（`1f33f73e0`）为准，增量范围 `5c61e94cc..1f33f73e0` 共 338 个 commit。核心主题为 **scoreparse（评分解析）项目 1382 系列修复与分数抽取版本化能力**，附带大量测试环境部署报告与知识沉淀：

- **评分抽取版本化（新增 2 张表）**：`score_extraction_snapshot`、`score_active_version`（V1219），配套 CHAR→VARCHAR 修正（V1220，防 ddl-auto=validate 崩溃）
- **scoreparse 1382 Task 289–292 系列根因修复**：父项编码歧义、合同考核项误判、28 分项范围守护、别名维度匹配、复核预算崩溃、AUTO_APPROVED 闭环落库等
- **评分证据字段放宽**：`score_parse_evidence.explanation` VARCHAR→TEXT（V1216）
- **子项聚合维度**：`score_parse_fact.child_aggregation`（V1217）
- **表列注释回填**：全量 `MODIFY COLUMN ... COMMENT`（V1218）
- 其余为 scoreparse 教训库、Wiki 回填与第 219–221 次测试部署报告等文档提交

> 本批代码主要由多个 agent 完成并已随 `origin/main` 合入，本次统一从 main 打包部署。

---

## 构建与部署执行

- 通过环境门禁确认 `ENV=prod` 与目标 `172.16.10.149`。
- 早操：当前处于锚点分支 `agent/trae-init`（HEAD=origin/main=`1f33f73e0`，ff-only 同步，工作区干净）。
- 使用 `package-release.sh` 打包（RELEASE_ID=`1f33f73e0`、`VITE_API_BASE_URL=` 同源、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`）。
- 产物校验：jar 构建 BUILD SUCCESS、jar 内迁移版本无重复、OBS 直传已启用（`.upload(` 调用数=2 → `obsEnabled=true`）；前端入口 `assets/index-CDf8nFsr.js`；`apiBaseUrl=""` 同源；sidecar schemaVersion=2.2。
- 上传产物至 `/opt/xiyu-bid/incoming/` 并执行远程部署（`remote-deploy.sh`，`SYSTEMCTL_SUDO=true`）。
- 生产数据库自动备份：`/opt/xiyu-bid/db-backups/winbid-1f33f73e0-*.sql.gz`。
- 后端服务 `xiyu-bid-backend` 重启（PID 13696），本地 18080 `/actuator/health` 连续 3/3 探针通过（第 17 次）。
- Flyway validate 预检：**VALIDATE OK - all checksums match**（280 migrations，含 5 个 pending 新迁移为预期）。
- 前端资源保留：从上一版本 release（`5c61e94cc`）`cp -rn` 旧 assets 到 `/srv/www/xiyu-bid/assets/`（共 277 个资源），防止旧标签页跨版本 404。
- sidecar 镜像随部署自动重建（Docker build + tesseract 依赖 + markitdown[all] Python 依赖）。

---

## 验证结果

### 1. 基础服务健康检查

| 检查项 | 结果 |
|---|---|
| 后端 `/actuator/health`（服务器本地 18080） | ✅ UP / HTTP 200 |
| `/actuator/health/readiness` | ✅ HTTP 200（无 Kafka readiness 延迟） |
| MySQL / Redis / Sidecar / AI Provider | ✅ 全部 UP |
| 前端入口文件（经 Nginx 8080） | ✅ `index-CDf8nFsr.js` 与 release 完全匹配 |
| OBS 直传 | ✅ 已启用（`obsEnabled=true`，`.upload(` 调用数=2） |
| 前端 API 地址 | ✅ `apiBaseUrl=""`（同源反代） |

### 2. Flyway 迁移应用验证

| 版本 | 描述 | 状态 |
|---|---|---|
| V1216 | widen score parse evidence explanation | ✅ success=1 |
| V1217 | add score child aggregation | ✅ success=1 |
| V1218 | backfill table column comments | ✅ success=1 |
| V1219 | score extraction versions | ✅ success=1 |
| V1220 | fix score fingerprint char to varchar | ✅ success=1 |

### 3. API 接口路由 Smoke（admin 密码未知，用 400/401 验证路由可达）

| 端点 | 期望 | 实际 |
|---|---|---|
| `POST /api/auth/login` (空体) | 400 参数错误 | ✅ 400 |
| `GET /api/projects` | 401 需认证 | ✅ 401 |

### 4. 前端页面 Smoke（Nginx 8080）

| 端点 | 结果 |
|---|---|
| `/` (root，经 127.0.0.1:8080) | ✅ 200 |
| `/login` | ✅ 200 |

> 注：从本地 Mac 经生产 `8080` 外部探测返回 HTTP 000（本机到生产子网 HTTP 端口无网络路由，SSH 可达但 HTTP 不可达），此为**本地网络连通性问题而非服务故障**。实际服务验证均以服务器本地探针为准，全部通过。

---

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既定决策（第 13/14/15 次部署决定保留，供健康详情可观测性），非临时 debug 配置，**保留**。
- 无 `DEBUG` / `TRACE` 残留。
- 迁移源目录 `/tmp/migration-mysql` 缺失（已知坑点），部署前已用 tar-over-ssh 同步补充至 288 个文件。

---

## 回滚信息与预案

- 后端回滚锚点：`/opt/xiyu-bid/releases/5c61e94cc/backend/app.jar`。
- 前端回滚锚点：`/opt/xiyu-bid/releases/5c61e94cc/frontend/`。
- 本次发布前 DB 备份：`/opt/xiyu-bid/db-backups/winbid-1f33f73e0-*.sql.gz`。
- 新迁移均配套 U 前缀回滚脚本（U1216–U1220，非破坏性操作可安全回滚）。
- 回滚命令：
  ```bash
  ssh jetty@172.16.10.149 'sudo /opt/xiyu-bid/bin/remote-deploy.sh rollback'
  ```
- 状态：回滚锚点就绪，暂未执行。

---

## 部署确认清单

- [x] 生产环境服务正常运行（PID 13696，18080 端口 HTTP 200，readiness 200）
- [x] 工作区与 `origin/main` 基线确认（`1f33f73e0`）
- [x] 上一版本前后端回滚锚点就绪（`5c61e94cc`）
- [x] 数据库在部署前已执行全量物理备份
- [x] Flyway validate 预检通过（280 migrations，checksum 全匹配）
- [x] V1216–V1220 五个迁移全部成功应用
- [x] 环境门禁 + 生产授权已取得（用户主动发起并确认）
- [x] OBS 直传开启、前端同源构建、Smoke 全部通过
- [x] assets 已从上一版本保留（防旧标签页跨版本 404）
- [x] 本部署报告已生成并归档