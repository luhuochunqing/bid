# 第 212 次部署报告（test）

- **部署日期**: 2026-09-27
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `d8628303d`
- **部署类型**: 代码级部署（**无新增 Flyway 迁移**）
- **操作人**: Trae 开发宿主（基线 = origin/main `d8628303d`）

## 部署概览

从上一已部署 release `47fb5e620`（211th，2026-09-27 17:10）增量部署至 `d8628303d`，共 **7 个 commit**。核心变更为 PR **!2777 scoreparse 抽取结果展示简化**（非功能性重构，无 DB schema 变更）；服务器 DB 已在此前部署应用 V1219/V1220，本次**无新迁移需执行**。

## 基线信息

- 基线: `origin/main` = `d8628303d`（HEAD，含 !2776 / !2777 / !2775）
- 工作区状态: 锚点分支 `agent/trae-init` 与 origin/main 一致，工作区干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-BsgWTfmc.js`
- sidecar: schemaVersion=2.2，SHA-256 `7c35e93fb5dcd7a3167822eee888d90bc86831c15904b11e66b922ac92b4630d`

## 主要改动（47fb5e620..d8628303d）

- **fix(scoreparse)**: 简化抽取结果展示（!2777 + 2个前置 fix commit）
  - `src/views/Project/stages/components/ScoreExtractionPanel.vue`
  - `src/composables/projectDetail/scoreParseTask.js` + .spec.js + panel spec.js
- **feat(scoreparse)**: 区分抽取与激活差异（c1ac7e447）
- **docs(release)**: GO_LIVE_CHECKLIST 增加 backend.env 功能开关核对项（!2776 / dfd8db7c5）
- **docs**: 知识沉淀 + wiki 回填（!2775）
- **V1220 fix**: V1219 CHAR→VARCHAR 启动阻断修复（b3e51121e，211th 已部署，本次随主基线合入确认）

## Flyway 预检结果

- **Step 1 validate**: 服务器 `/tmp/migration-mysql/` 目录过时（缺 V1219/V1220）触发**良性 mismatch**误报
  - 根因：服务器迁移源目录未同步（第 15 次 lesson 场景）
  - 处置：**未用 repair**（会误将成功迁移标记为 deleted），改用 tar-over-ssh 同步最新 `migration-mysql/` 到服务器 `/tmp/` 后重跑
- **Step 2 DB 版本**: 部署前 V1219/V1220 已 success=1（无 pending 新迁移）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate **通过（280 migrations，all checksums match）**
- **部署后 DB 版本**: V1219 + V1220 均 success=1（无变化，预期）

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（锚点分支 `agent/trae-init` 与 origin/main 一致，HEAD=d8628303d，工作区干净；check-git-wrapper 提示 wrapper 未激活但本任务无推送操作）
3. 服务器现状探测（deployed-release=47fb5e620，health HTTP 200 UP，增量 7 commit，迁移变更=V1220 已在 DB）
4. Flyway 预检 → 首次 validate 因服务器 `/tmp/migration-mysql` 过时失败 → tar 同步迁移源后重跑 validate OK
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口 `index-BsgWTfmc.js`、jar 内无重复迁移版本、OBS Detail .upload=2、sidecar 打包）
7. **步骤 6.5 backend.env 开关核对**：`APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=true` 已配置（本次 !2777 属该开关控制功能，无遗漏）
8. 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → Flyway validate → 前端激活（sudo chown 修复属主）→ 后端原子重启 → 健康检查（80 轮询通过）
9. 前端资源保留（从上一版本 release `47fb5e620-fix1220` `cp -rn` 旧 assets，防跨版本 404）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，健康检查 3/3 consecutive（第 80 次轮询） |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP（无 Kafka 延迟） |
| Flyway validate | ✅ VALIDATE OK（280 migrations，同步迁移源后通过） |
| DB 迁移应用 | ✅ V1219 + V1220 success=1（无新迁移） |
| 前端一致性 | ✅ `assets/index-BsgWTfmc.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 已从 47fb5e620-fix1220 release 目录保留 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ remote-deploy 自动备份（winbid-d8628303d-*.sql.gz） |
| 功能开关 | ✅ APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=true |
| 临时调试配置 | ⚠️ `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策保留项，非本次新增） |

## 回滚

- **回滚锚点**: 上一 release `47fb5e620`（/opt/xiyu-bid/releases/47fb5e620-fix1220 仍存在）
- **DB 备份**: remote-deploy 自动备份 `/opt/xiyu-bid/db-backups/winbid-d8628303d-*.sql.gz`
- **schema 回滚**: 本次无新迁移，**无需 schema 回滚**（V1219/V1220 为上一部署内容）
- **回滚状态**: 就绪（未执行，验证通过无需回滚）

## 经验沉淀应用

- Flyway 预检 3 步法 ✅（提前暴露服务器迁移源目录过时问题）
- **迁移源目录过时处置** ✅ 本次新增细化：validate 报 `Detected applied migration not resolved locally` 时，优先检查服务器 `/tmp/migration-mysql/` 是否过时（缺新版本号），用 tar-over-ssh 同步最新 `migration-mysql/` 后重跑；**不要用 repair**（repair 会把成功迁移标记为 deleted）。服务器无 rsync，用 tar 替代
- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅
- backend.env 功能开关核对（步骤 6.5） ✅
- 前端资源保留（cp -rn 旧 assets 防跨版本 404） ✅

## 部署确认清单

- [x] 环境门禁（测试 172.16.38.78）
- [x] 早操同步 + 基线确认
- [x] Flyway 预检 3 步（含迁移源目录过时修复）
- [x] backend.env 功能开关核对
- [x] 本地打包 + 产物校验
- [x] remote-deploy 自动部署
- [x] 健康检查 + Smoke 全通过
- [x] 前端资源保留
- [x] 部署报告
- [ ] 本报告合入 main（待提交 PR）