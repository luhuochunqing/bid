# 第 198 次部署报告（test）

- **部署日期**: 2026-09-23
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `ea5ad35c`
- **部署类型**: 代码级部署 + 1 个 Flyway 迁移（V1217）
- **操作人**: Trae 开发宿主（agent/trae-init 锚点分支，与 main ff 同步）

## 部署概览

从上一已部署 release `f973bd58`（197th，2026-09-23）增量部署至 `ea5ad35c`，共 **5 个 commit**，新增 Flyway 迁移 **V1217**（DB 从 V1216 升级至 V1217）。

## 基线信息

- 分支: `agent/trae-init`（锚点分支，HEAD == origin/main == `ea5ad35c`）
- 工作区状态: 干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CQRpRhrT.js`（与上一部署一致）

## 主要改动（f973bd58..ea5ad35c）

- **fix(scoreparse)**: 按原文证据和真实计分单元校验评分项（!2739）— 补充 2 个支撑 commit
- **test(scoreparse)**: 覆盖 page split scoring hierarchy 边界测试
- **docs**: 第 197 次部署报告（f973bd58）

> 本次核心为 scoreparse 评分项校验逻辑修正，新增 `score_parse_fact.child_aggregation` 列记录父级到子级的评分聚合关系。已有行保持 NULL，由领域层按历史 SUM 语义解析，属**非破坏性**扩展。

## Flyway 预检结果

- **Step 1 validate**: `flyway-repair-runner.sh` → **VALIDATE OK（276 migrations）**
- **Step 2 DB 版本**: 已应用到 V1216（2026-09-22 20:05）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate 通过（276 migrations）
- **结论**: 本次新增 V1217，预检时 DB 未应用、源码含 V1217（预期 pending 状态），rollback 脚本 `U1217` 已存在

## 部署步骤

1. 早操三连（dev-env + sync-env + check-git-wrapper）
2. 基线确认（agent/trae-init == origin/main == ea5ad35c，工作区干净）
3. 服务器现状探测（deployed-release=f973bd58，health UP / readiness UP，obsEnabled=true）
4. Flyway 预检 3 步法（validate OK / DB V1216 vs 源码含 V1217）
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口、jar 内迁移无重复、OBS Detail .upload=2、sidecar 打包）
7. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → 健康检查 → Flyway validate → 前端激活 → 后端原子重启（含 sudo chown 前端目录）
8. 前端资源保留（从上一版本 f973bd58 cp -rn 旧 assets 到 /srv/www/xiyu-bid/assets/）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，PID 29330，健康检查 3/3 consecutive（第 80 次轮询） |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（276 migrations） |
| V1217 迁移应用 | ✅ `success=1`，installed_on 2026-09-23 17:24:20 |
| child_aggregation 列 | ✅ `varchar(32) NULL` 已存在 |
| 前端一致性 | ✅ `assets/index-CQRpRhrT.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 从 f973bd58 补回（index-CT4Pq_IZ.js / index-Cmq0rLNS.css 与新 index-CQRpRhrT.js 共存） |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ 已自动执行 |

## 回滚

- **回滚锚点**: 上一 release `f973bd58`（/opt/xiyu-bid/releases/f973bd58 仍存在，后端 jar 可重新构建部署）
- **DB 备份**: 已由 remote-deploy.sh 自动执行
- **schema 回滚**: `U1217__add_score_child_aggregation.sql` 存在（DROP COLUMN，幂等），回滚脚本就绪
- **回滚状态**: 就绪（未执行，行为正常无需回滚）

## 经验沉淀应用

- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- COPYFILE_DISABLE=1 防 macOS `._*` 残留 ✅
- --noproxy 绕过 Mac HTTP_PROXY 502 ✅
- SYSTEMCTL_SUDO=true ✅（第 15 次事故防复发）
- 前端 hash 资源跨版本 404 防护 ✅（部署后从上一版本 cp -rn 旧 assets）
- rollback 脚本 U 前缀规范 ✅（U1217 存在）

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15 次决定保留），非本次引入，保留。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- 本次新增 1 个 DDL 迁移（V1217，ADD COLUMN）。`ALTER TABLE` 为在线 DDL，对存量行无影响；已有行 `child_aggregation` 保持 NULL，由领域层按历史 SUM 语义解析，无数据迁移，schema 风险低。
- 核心业务改动为 scoreparse 评分项按原文证据校验，建议回归评分解析完整性场景。

## 部署确认清单

- [x] 环境门禁（用户确认测试环境 172.16.38.78）
- [x] 早操三连通过（锚点分支 ff-only 同步至 origin/main）
- [x] 基线 = origin/main
- [x] Flyway 预检通过（VALIDATE OK，DB V1216 + 源码 V1217）
- [x] 打包 + 产物校验通过（obsEnabled=true / 迁移无重复 / 前端入口）
- [x] 部署成功（health + V1217 应用验证 + smoke + 前端一致性）
- [x] 部署报告生成