# 第 215 次部署报告（test）

- **部署日期**: 2026-09-28
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `917e0404d`
- **部署类型**: 代码级部署（**无新增 Flyway 迁移**）
- **操作人**: Trae 开发宿主（基线 = origin/main `917e0404d`）

## 部署概览

从上一已部署 release `5100e8e4`（214th，2026-09-27）增量部署至 `917e0404d`，共 **5 个 commit**。核心变更为 **scoreparse 项目 1382 标书原文恢复**（基于真实 PDF Artifact 修复续表合并、续表闭合与多 Peer 兄弟项连带熔断）+ sidecar `converter.py` 调整，无 DB schema 变更；服务器 DB 已应用 V1220，本次**无新迁移需执行**。

## 基线信息

- 基线: `origin/main` = `917e0404d`（HEAD，PR !2784 / !2785 合入后）
- 工作区状态: 锚点分支 `agent/trae-init` 与 origin/main 一致，工作区干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CDf8nFsr.js`（与上版一致，本次无前端业务代码变更）
- sidecar: schemaVersion=2.2，SHA-256 `61985c8f9a4f5032f01196767d3989a2fcdfd1936a05df559e1e48ae84a48e62`

## 主要改动（5100e8e4..917e0404d）

- **fix(scoreparse)**: 基于真实 PDF Artifact 修复续表合并、续表闭合与多 Peer 连带熔断，恢复标书原文（!2785 + 前置 commit）
  - `backend/.../scoreparse/domain/ChildAggregation.java`
  - `backend/.../scoreparse/domain/LogicalRuleSourceUnit.java`
  - `backend/.../scoreparse/domain/OriginalRuleRestoration.java`
  - `backend/src/test/.../ChildAggregationTest.java`、`GoldenCasesReplayTest.java`、`case-project-1382-pdf-real-shape` golden 用例
  - `document-converter-sidecar/converter.py` + `tests/test_pdf_table_artifact.py`
- **docs**: lessons-learned 回填、wiki 回填 1382 根因、214th 部署报告、decisions.tsv

## Flyway 预检结果

- **Step 1 validate**: 服务器 `flyway-repair-runner.sh validate` → `VALIDATE OK - all checksums match`（280 migrations）
- **Step 2 DB 版本**: 部署前 DB 最新 V1220 success=1（本地源码最新迁移 V1220，无 pending 新迁移）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate **通过（280 migrations，all checksums match）**
- **部署后 DB 版本**: V1219/V1220 success=1（无变化，预期）

## 部署步骤

1. **环境门禁确认**（用户确认测试环境 172.16.38.78）
2. **早操同步**（锚点分支 `agent/trae-init` 与 origin/main 一致，HEAD=917e0404d 基线，工作区干净；`sync-env` 提示锚点分支不开发属预期）
3. **服务器现状探测**（deployed-release=5100e8e4，health HTTP 200 UP，增量 5 commit，迁移变更=无）
4. **Flyway 预检**（validate OK + DB V1220 已应用）
5. **本地打包**（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`，BUILD SUCCESS）
6. **产物校验**（obsEnabled=true、前端 index 入口 `index-CDf8nFsr.js`、jar 内无重复迁移版本、OBS Detail .upload=2、sidecar 打包）
7. **步骤 6.5 backend.env 开关核对**
   - 服务器当前 `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=false`，与第 212/213/214 次报告一致
   - **沿用用户决策：保持 false** —— 本次 1382 标书原文恢复属底层解析逻辑，不受该开关控制（开关仅控制新版激活/快照写入接口的 409 `EXTRACTION_FEATURE_DISABLED` 保护），本次无新增开关决策
8. **上传 + remote-deploy.sh**（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → Flyway validate → 前端激活（sudo chown 修复属主）→ 后端原子重启 → 健康检查（80 次轮询通过）
9. **前端资源保留**（从上一版本 release `5100e8e4` `cp -rn` 旧 assets 到 /srv/www/xiyu-bid/assets，防跨版本 404；本次前端无变更，新版本 assets 与上版一致）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，健康检查 3/3 consecutive（第 80 次轮询），Main PID 12501 |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP（无 Kafka 延迟） |
| Flyway validate | ✅ VALIDATE OK（280 migrations，all checksums match） |
| DB 迁移应用 | ✅ V1219/V1220 success=1（无新迁移） |
| 前端一致性 | ✅ `assets/index-CDf8nFsr.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 已从 5100e8e4 release 目录保留（assets 总数 193） |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ remote-deploy 自动备份（winbid-917e0404d-*.sql.gz） |
| 功能开关 | ⚠️ `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=false`（213 次起用户确认保持现状，本次无新增决策） |
| 临时调试配置 | ⚠️ `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策保留项，非本次新增） |

## 回滚

- **回滚锚点**: 上一 release `5100e8e4`（/opt/xiyu-bid/releases/5100e8e4 仍存在）
- **DB 备份**: remote-deploy 自动备份 `/opt/xiyu-bid/db-backups/winbid-917e0404d-*.sql.gz`
- **schema 回滚**: 本次无新迁移，**无需 schema 回滚**（DB 保持 V1220）
- **回滚状态**: 就绪（未执行，验证通过无需回滚）

## 经验沉淀应用情况

- **Lesson 19（功能开关缺配）**：本次部署前核对 `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED`，确认沿用 213 次起用户决策（保持 false），避免误判为漏配项。
- **Lesson 15（Mac HTTP_PROXY 502）**：全部 smoke 使用 `--noproxy '*'` 规避本地代理。
- **Lesson 8 / 19（OBS 直传）**：打包显式传 `VITE_OBS_ENABLED=true`，产物校验 obsEnabled=true 通过。
- **Lesson 17（前端 hash 404）**：部署后保留上一版本 assets（193 资源）。

## 风险提示

- 功能开关 `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=false` 下，scoreparse **新版激活**接口仍返回 409。若后续需要使用新版激活/快照写入功能，需将开关改为 `true` 并重启后端。本次部署的 1382 标书原文恢复修复在存量 active version 项目上已生效，不受开关影响。

## 部署确认清单

- [x] 环境门禁：用户确认测试环境 172.16.38.78
- [x] 基线：origin/main = 917e0404d，工作区干净
- [x] Flyway 预检：validate OK + DB/源码版本一致
- [x] 本地打包：BUILD SUCCESS + 产物校验通过
- [x] 部署：remote-deploy.sh 完成，后端 active(running)
- [x] 健康检查：HTTP 200 UP
- [x] Smoke：全部通过（400/401 为预期鉴权行为）
- [x] 前端一致性：index-CDf8nFsr.js 匹配
- [x] DB 迁移：V1219/V1220 success=1
- [x] 旧 assets 保留完成
- [x] 临时调试配置：无新增
