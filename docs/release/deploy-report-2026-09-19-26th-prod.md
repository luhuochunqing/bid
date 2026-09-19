# 第 26 次生产环境部署报告 — 2026-09-19

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| Release ID | `5c61e94cc` |
| 基线 commit | `5c61e94cc`（`origin/main`） |
| 前一版本 | `74a753025`（2026-09-19 第 25 次生产） |
| 部署时间 | 2026-09-19 16:34–16:39 CST |
| 状态 | ✅ 成功（健康 + Smoke 全通过） |
| 回滚状态 | 未执行，锚点就绪（`74a753025`） |

---

## 部署概览

本次从生产版本 `74a753025` 更新到 `5c61e94cc`。**本次增量不含任何 MySQL 迁移**（`74a753025..5c61e94cc` 迁移文件 diff 为空，Flyway 预检 275 个迁移全匹配），数据库 schema 无变更，DB 维持已应用的 `V1215` 基线。

本次部署经过环境门禁确认（用户主动发起部署指令，并在确认框选择"确认部署到生产环境"），符合生产授权红线。

---

## 变更范围与 PR/提交

本次发布以 `origin/main`（`5c61e94cc`）为准，增量提交范围 `74a753025..5c61e94cc`，共 20 个 commit。核心变更为**评分解析（scoreparse）系列修复**与配套文档/知识沉淀：

- `!2705`/`ec9849898` **分表完整性校验范围误判 + 部分通过不再覆盖正式评分项**
- `5f5b52166` **scoreparse S4 deletion-first 清理**——删除失去调用方的 deleteByProjectId 与测试投影未消费字段
- `70b5d25c7` **部分表通过不再覆盖正式评分项，事实删除按 parseTaskId 隔离**
- `351cb4610` **分表完整性校验不再误用整份文档正文长度**
- `!2704`/`0ee927d90` **支持独立汇总父项行与横纵合并覆盖场景**下的父项关联与末级过滤
- `0f36023d1` **解耦合并单元格名称继承与父子层级关系**，修复无汇总分值首行细则误删
- `8c023359e` **修复项目30评分解析215分虚高与续行维度丢失缺陷**
- `d004d347c` **空正文解析失败改为扫描版提示而非"请先上传"**（语义误导修复）
- 其余为 scoreparse 教训库（`386`/`387`）、Wiki 回填与第 25 次生产部署报告等文档提交

> 本批代码主要由 `agent/gemini` 等完成并已随 `origin/main` 合入，本次统一从 main 打包部署。

---

## 构建与部署执行

- 通过环境门禁确认 `ENV=prod` 与目标 `172.16.10.149`。
- 早操：当前处于锚点分支 `agent/trae-init`（HEAD=origin/main=`5c61e94cc`，ff-only 同步，工作区干净）。
- 使用 `package-release.sh` 打包（RELEASE_ID=`5c61e94cc`、`VITE_API_BASE_URL=` 同源、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`）。
- 产物校验：jar 构建 BUILD SUCCESS、jar 内 274 个 V 迁移（+1 B 基线 = 275）无重复版本；OBS 直传已启用（`.upload(` 调用数=2 → `obsEnabled=true`）；前端入口 `assets/index-C9c0lzz8.js`；`apiBaseUrl=""` 同源；sidecar schemaVersion=2.0。
- 上传产物至 `/opt/xiyu-bid/incoming/` 并执行远程部署（`remote-deploy.sh`）。
- 生产数据库自动备份：`/opt/xiyu-bid/db-backups/winbid-5c61e94cc-*.sql.gz`。
- 后端服务 `xiyu-bid-backend` 平滑重启（PID 6409），本地 18080 `/actuator/health` 连续 3/3 探针通过。
- Flyway validate 预检：**VALIDATE OK - all checksums match**（275 migrations）。
- 前端资源保留：从上一版本 release（`74a753025`）`cp -rn` 旧 assets 到 `/srv/www/xiyu-bid/assets/`（共 277 个资源），防止旧标签页跨版本 404。

---

## 验证结果

### 1. 基础服务健康检查

| 检查项 | 结果 |
|---|---|
| 后端 `/actuator/health`（服务器本地 18080） | ✅ UP / HTTP 200 |
| `/actuator/health/readiness` | ✅ HTTP 200（无 Kafka readiness 延迟） |
| Nginx 监听 | ✅ `:80` 与 `:8080` 均在监听 |
| 前端入口文件（经 Nginx 8080） | ✅ `index-C9c0lzz8.js` 与 release 完全匹配 |
| Flyway 已应用版本 | ✅ 维持在 `V1215`（无新增迁移） |
| OBS 直传 | ✅ 已启用（`obsEnabled=true`，`.upload(` 调用数=2） |
| 前端 API 地址 | ✅ `apiBaseUrl=""`（同源反代） |

> 注：从本地 Mac 经生产 `8080` 外部探测返回 HTTP 000（本机到生产子网 HTTP 端口无网络路由，SSH 可达但 HTTP 不可达），此为**本地网络连通性问题而非服务故障**。实际服务验证均以服务器本地探针为准，全部通过。

### 2. API 接口路由 Smoke（admin 密码未知，用 400/401 验证路由可达）

| 端点 | 期望 | 实际 |
|---|---|---|
| `POST /api/auth/login` (空体) | 400 参数错误 | ✅ 400 |
| `GET /api/projects` | 401 需认证 | ✅ 401 |

### 3. 前端页面 Smoke（Nginx 8080）

| 端点 | 结果 |
|---|---|
| `/` (root，经 127.0.0.1:8080) | ✅ 200 |

---

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既定决策（第 13/14/15 次部署决定保留，供健康详情可观测性），非临时 debug 配置，**保留**。
- 无 `DEBUG` / `TRACE` 残留。
- 后端日志无异常（唯一 WARN 为本次空密码 smoke 触发的预期 400 参数校验日志）。

---

## 回滚信息与预案

- 后端回滚锚点：`/opt/xiyu-bid/releases/74a753025/backend/app.jar`。
- 前端回滚锚点：`/opt/xiyu-bid/releases/74a753025/frontend/`。
- 本次发布前 DB 备份：`/opt/xiyu-bid/db-backups/winbid-5c61e94cc-*.sql.gz`。
- 回滚命令：
  ```bash
  ssh jetty@172.16.10.149 'sudo /opt/xiyu-bid/bin/remote-deploy.sh rollback'
  ```
- 状态：回滚锚点就绪，暂未执行。

---

## 部署确认清单

- [x] 生产环境服务正常运行（PID 6409，18080 端口 HTTP 200，readiness 200）
- [x] 工作区与 `origin/main` 基线确认（`5c61e94cc`）
- [x] 上一版本前后端回滚锚点就绪（`74a753025`）
- [x] 数据库在部署前已执行全量物理备份
- [x] Flyway validate 预检通过（275 migrations，checksum 全匹配，无新增迁移）
- [x] 环境门禁 + 生产授权已取得（用户主动发起并确认）
- [x] OBS 直传开启、前端同源构建、Smoke 全部通过
- [x] assets 已从上一版本保留（防旧标签页跨版本 404）
- [x] 本部署报告已生成并归档