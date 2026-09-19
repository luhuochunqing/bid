# 第 25 次生产环境部署报告 — 2026-09-19

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| Release ID | `74a753025` |
| 基线 commit | `74a753025`（`origin/main`） |
| 前一版本 | `6fad0758b`（2026-09-18 第 24 次生产） |
| 部署时间 | 2026-09-19 08:49–08:53 CST |
| 状态 | ✅ 成功（健康 + Smoke 全通过） |
| 回滚状态 | 未执行，锚点就绪（`6fad0758b`） |

---

## 部署概览

本次从生产版本 `6fad0758b` 更新到 `74a753025`。**本次增量不含任何 MySQL 迁移**（`6fad0758b..74a753025` 迁移文件 diff 为空，Flyway 预检 V1215 全匹配），数据库 schema 无变更。

本次部署经过了环境门禁确认（用户主动发起部署指令，并在确认框选择"确认部署到生产环境"），符合第 24 次越权发布事故后确立的生产授权红线。

---

## 变更范围与 PR/提交

本次发布以 `origin/main`（`74a753025`）为准，增量提交范围 `6fad0758b..74a753025`，共约 40 个 commit、197 个变更文件。核心可识别 PR 与提交包括：

- `!2701`/`74a753025` **评分解析超时降级防放大、未认证统一 401 及前端防抖并发防护**（含 audit/security 加固）
- `!2698` **授权拒绝记录 userId/projectId 审计日志**（配合上生产前新增的 ACCESS_DENIED 日志可观测性）
- `!2699`/`!2697` **Sidecar Dockerfile 补入 docx_repair.py 模块 + 截断 docx 容器解析挂死与乱码修复**（EOCD 自愈 + POI 原生降级）
- `!2696`/spec 065 **招标文件解析放行超 50MB**（流式 + 文本量口径）及评分解析生命周期收敛
- `!2695`/`!2693`/`!2694` **中标率口径改为 中标数/(已中标+未中标)** + M0/M1 关键指标文案对齐
- `!2691`/`!2690` **评分解析跨页表头扫描深度放宽、人机协同候选评分标准采纳闭环**

> 说明：`!2691` 来自 agent/antigravity、`!2692`/`!2695` 等为多 agent 合入的 `main` 提交，均已随 `origin/main` 一并部署。

---

## 构建与部署执行

- 通过环境门禁确认 `ENV=prod` 与目标 `172.16.10.149`。
- 早操：当前处于锚点分支 `agent/trae-init`（HEAD=origin/main=`74a753025`，ff-only 同步，工作区干净）。
- 使用 `package-release.sh` 打包（RELEASE_ID=`74a753025`、`VITE_API_BASE_URL=` 同源、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`）。
- 产物校验：jar 构建 BUILD SUCCESS、无重复迁移（275 版本校验通过）；OBS 直传已启用（`.upload(` 调用数=2 → `obsEnabled=true`）；前端入口 `assets/index-DvwjJGZt.js`；`apiBaseUrl=""` 同源；sidecar schemaVersion=2.0。
- 上传产物至 `/opt/xiyu-bid/incoming/` 并执行远程部署（`remote-deploy.sh`）。
- 生产数据库自动备份：`/opt/xiyu-bid/db-backups/winbid-74a753025-*.sql.gz`。
- 后端服务 `xiyu-bid-backend` 平滑重启（PID 13662），本地 18080 `/actuator/health` 连续 3/3 探针通过。
- Flyway validate 预检：**VALIDATE OK - all checksums match**（275 migrations）。
- 前端资源保留：从上一版本 release（`6fad0758b`）`cp -rn` 旧 assets 到 `/srv/www/xiyu-bid/assets/`，防止旧标签页跨版本 404。

---

## 验证结果

### 1. 基础服务健康检查

| 检查项 | 结果 |
|---|---|
| 后端 `/actuator/health`（服务器本地 18080） | ✅ UP / HTTP 200 |
| `/actuator/health`（Nginx 8080 代理，外部访问） | ✅ HTTP 200 |
| `/actuator/health/readiness` | ✅ HTTP 200（无 Kafka readiness 延迟） |
| 前端入口文件 | ✅ `index-DvwjJGZt.js` 与 release 完全匹配 |
| Flyway 已应用版本 | ✅ 维持在 `V1215`（无新增迁移） |
| OBS 直传 | ✅ 已启用（`obsEnabled=true`，`.upload(` 调用数=2） |
| 前端 API 地址 | ✅ `apiBaseUrl=""`（同源反代） |

### 2. API 接口路由 Smoke（admin 密码未知，用 400/401 验证路由可达）

| 端点 | 期望 | 实际 |
|---|---|---|
| `POST /api/auth/login` (空体) | 400 参数错误 | ✅ 400 |
| `GET /api/projects` | 403/401 需认证 | ✅ 401 |
| `GET /api/integration/crm/health` | 401 需认证 | ✅ 401 |

### 3. 前端页面 Smoke（Nginx 8080）

| 端点 | 结果 |
|---|---|
| `/` (root) | ✅ 200 |
| `/login` | ✅ 200 |

---

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既定决策（第 13/14/15 次部署决定保留，供健康详情可观测性），非临时 debug 配置，**保留**。
- 无 `DEBUG` / `TRACE` 残留。

---

## 回滚信息与预案

- 后端回滚锚点：`/opt/xiyu-bid/releases/6fad0758b/backend/app.jar`。
- 前端回滚锚点：`/opt/xiyu-bid/releases/6fad0758b/frontend/`。
- 本次发布前 DB 备份：`/opt/xiyu-bid/db-backups/winbid-74a753025-*.sql.gz`。
- 回滚命令：
  ```bash
  ssh jetty@172.16.10.149 'sudo /opt/xiyu-bid/bin/remote-deploy.sh rollback'
  ```
- 状态：回滚锚点就绪，暂未执行。

---

## 部署确认清单

- [x] 生产环境服务正常运行（PID 13662，18080 端口 HTTP 200，readiness 200）
- [x] 工作区与 `origin/main` 基线确认（`74a753025`）
- [x] 上一版本前后端回滚锚点就绪（`6fad0758b`）
- [x] 数据库在部署前已执行全量物理备份
- [x] Flyway validate 预检通过（275 migrations，checksum 全匹配，无新增迁移）
- [x] 环境门禁 + 生产授权已取得（用户主动发起并确认）
- [x] OBS 直传开启、前端同源构建、Smoke 全部通过
- [x] assets 已从上一版本保留（防旧标签页跨版本 404）
- [x] 本部署报告已生成并归档