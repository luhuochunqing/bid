# 第 213 次部署报告（test）

- **部署日期**: 2026-09-27
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `ffdfc587`
- **部署类型**: 代码级部署（**无新增 Flyway 迁移**）
- **操作人**: Trae 开发宿主（基线 = origin/main `ffdfc587`）

## 部署概览

从上一已部署 release `d8628303d`（212th，2026-09-27 18:29）增量部署至 `ffdfc587`，共 **7 个 commit**。核心变更为 **scoreparse 激活/展示修复**（activeDataStatus 展示状态、首次解析激活拦截），无 DB schema 变更；服务器 DB 已应用 V1220，本次**无新迁移需执行**。

## 基线信息

- 基线: `origin/main` = `ffdfc587`（HEAD，含 !2778 / !2779 / !2780 及前置 fix）
- 工作区状态: 锚点分支 `agent/trae-init` 与 origin/main 一致，工作区干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-DI0RcN1I.js`
- sidecar: schemaVersion=2.2，SHA-256 `38e7aba5a48805299e0e9d6a9c54cda8ffba71484c5471c2a72de8de8ea22d6f`

## 主要改动（d8628303d..ffdfc587）

- **fix(scoreparse)**: 在激活保留客户视图（!2780 + 前置 commit 6bbe25ffe）
  - `backend/.../scoreparse/application/ScoreExtractionAppService.java`：`Active` 记录新增 `dataStatus` 字段，用于区分展示状态（NONE/HISTORICAL/COHERENT/MISMATCHED）
  - `src/views/Project/stages/components/ScoreExtractionPanel.vue`
  - `src/views/Project/stages/components/ScoreParseDrawer.vue` + spec
  - `backend/src/test/.../ScoreExtractionPersistenceMysqlTest.java`
- **fix(scoreparse)**: 阻断不完整首次解析激活（504194390）
  - `backend/.../scoreparse/application/ScoreVersionActivationService.java`：声明的总分与可计算总分不一致时拒绝激活
- **docs(release)**: 第 212 次部署报告（!2778）
- **docs(wiki)**: 回填服务器侧迁移源目录过时经验（!2779）

## Flyway 预检结果

- **Step 1 validate**: 服务器 `flyway-repair-runner.sh validate` → `VALIDATE OK - all checksums match`（280 migrations），服务器迁移源目录状态正常（无需同步）
- **Step 2 DB 版本**: 部署前 DB 最新 V1220 success=1（本地源码最新迁移 V1220，无 pending 新迁移）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate **通过（280 migrations，all checksums match）**
- **部署后 DB 版本**: V1220 success=1（无变化，预期）

## 部署步骤

1. **环境门禁确认**（用户确认测试环境 172.16.38.78）
2. **早操同步**（锚点分支 `agent/trae-init` 与 origin/main 一致，HEAD=d8628303d 基线，工作区干净；`sync-env` 提示锚点分支不开发属预期）
3. **服务器现状探测**（deployed-release=d8628303d，health HTTP 200 UP，增量 7 commit，迁移变更=无）
4. **Flyway 预检**（validate OK + DB V1220 已应用）
5. **本地打包**（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`，BUILD SUCCESS）
6. **产物校验**（obsEnabled=true、前端 index 入口 `index-DI0RcN1I.js`、jar 内无重复迁移版本、OBS Detail .upload=2、sidecar 打包）
7. **步骤 6.5 backend.env 开关核对**
   - 服务器当前 `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=false`，与第 212 次报告及 GO_LIVE_CHECKLIST 期望 `true` **不一致**
   - 经用户确认：**保持 false（维持现状）** —— 全新激活/快照写入接口维持 409 `EXTRACTION_FEATURE_DISABLED` 保护；存量 active version 项目的展示逻辑（activeDataStatus）仍生效
8. **上传 + remote-deploy.sh**（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → Flyway validate → 前端激活（sudo chown 修复属主）→ 后端原子重启 → 健康检查（80 轮询通过）
9. **前端资源保留**（从上一版本 release `d8628303d` `cp -rn` 旧 assets 到 /srv/www/xiyu-bid/assets，防跨版本 404，共 277 个资源）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，健康检查 3/3 consecutive（第 80 次轮询），Main PID 18684 |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP（无 Kafka 延迟） |
| Flyway validate | ✅ VALIDATE OK（280 migrations，all checksums match） |
| DB 迁移应用 | ✅ V1220 success=1（无新迁移） |
| 前端一致性 | ✅ `assets/index-DI0RcN1I.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 已从 d8628303d release 目录保留（277 资源） |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ remote-deploy 自动备份（winbid-ffdfc587-*.sql.gz） |
| 功能开关 | ⚠️ `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=false`（用户确认保持现状，非漏配项） |
| 临时调试配置 | ⚠️ `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策保留项，非本次新增） |

## 回滚

- **回滚锚点**: 上一 release `d8628303d`（/opt/xiyu-bid/releases/d8628303d 仍存在）
- **DB 备份**: remote-deploy 自动备份 `/opt/xiyu-bid/db-backups/winbid-ffdfc587-*.sql.gz`
- **schema 回滚**: 本次无新迁移，**无需 schema 回滚**（DB 保持 V1220）
- **回滚状态**: 就绪（未执行，验证通过无需回滚）

## 经验沉淀应用情况

- **Lesson 19（功能开关缺配）**：本次部署前主动核对 `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED`，发现服务器 false 与文档 true 不一致，交由用户决策（保持 false），避免"只更新代码不确认开关"导致误判。
- **Lesson 15（Mac HTTP_PROXY 502）**：全部 smoke 使用 `--noproxy '*'` 规避本地代理。
- **Lesson 8 / 19（OBS 直传）**：打包显式传 `VITE_OBS_ENABLED=true`，产物校验 obsEnabled=true 通过。
- **Lesson 17（前端 hash 404）**：部署后保留上一版本 assets（277 资源）。

## 风险提示

- 功能开关 `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=false` 下，scoreparse **新版激活**接口仍返回 409。若后续需要使用新版激活/快照写入功能，需将开关改为 `true` 并重启后端。本次部署的展示层修复（activeDataStatus）在存量 active version 项目上已生效，不受开关影响。