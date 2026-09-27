# 第 211 次部署报告（test）

- **部署日期**: 2026-09-27
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `47fb5e620-fix1220`
- **部署类型**: 代码级部署 + **新增 Flyway 迁移 V1219 + V1220（含启动阻断修复）**
- **操作人**: Trae 开发宿主（基线 = origin/main `47fb5e620` + 本地 V1220 修复）

## 部署概览

从上一已部署 release `0d8543c92`（210th，2026-09-27 凌晨）增量部署至 `47fb5e620`，共 **8 个 commit**（核心为 PR !2774 scoreparse 版本化抽取），新增 2 个 Flyway 迁移：V1219（score extraction versions）+ **V1220（修复 V1219 的 CHAR→VARCHAR 启动阻断）**。

## 基线信息

- 基线: `origin/main` = `47fb5e620`（`!2774 feat(scoreparse): preserve complete scoring rules and versioned extraction`）
- 工作区状态: 锚点分支 `agent/trae-init` 与 origin/main 一致 + 部署前暂存无关残留改动（`.githooks/pre-push`）
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CF-9RCBy.js`
- sidecar: schemaVersion=2.2，SHA-256 `558982c154ff6059bcacbc971fc09cead72785d651a0788e195fb210bb4ffe5e`

## 主要改动（0d8543c92..47fb5e620 + V1220）

- **feat(scoreparse)**: 保留完整评分规则 + 版本化抽取（!2774）
  - 冻结候选快照（`score_extraction_snapshot` 表）、CAS 激活历史（`score_active_version` 表）
  - `score_parse_fact` 新增 `item_payload`/`payload_version`，`score_item` 新增 `source_fact_id`/`active_version_id`/`rule_fingerprint`
- **docs(architecture)**: 修正评分项解析 3 个月路线图编造数据 + 补充详细开发计划（!2772）
- **docs(release)**: 第 210 次部署报告（e4a252982）

### ⚠️ 本次部署特殊事件：V1219 schema 类型不匹配（启动阻断）

**发现过程**：本地打包时 `FlywayMysqlContainerTest` 5 个测试全部失败，根因为 Hibernate schema-validation：

> `wrong column type encountered in column [projection_fingerprint] in table [score_active_version]; found [char (Types#CHAR)], but expecting [varchar(64)]`

**根因**：V1219 用 `CHAR(64)` 定义 fingerprint 列，但实体 `@Column(length=64)` 期望 `varchar(64)`。测试/生产 profile 均启用 `ddl-auto: validate`，若直接部署后端启动必然失败。

**处理**：用户确认「修复迁移后继续部署」。新增 **V1220**（`MODIFY projection_fingerprint VARCHAR(64) NOT NULL` + `MODIFY rule_fingerprint VARCHAR(64)`）+ U1220 rollback，本地容器测试验证通过后（5/5 green）重新打包部署。

> ⚠️ **V1220 待合入 main**：origin/main 当前不含 V1220（本地创建未提交）。若直接下次部署 origin/main 会重复触发同样失败，且 pre-push 会检测「DB 已执行但源码缺失」。已随部署报告提交，需合入 main。

## Flyway 预检结果

- **Step 1 validate**: **VALIDATE OK（278 migrations，all checksums match）**（部署前，服务器 flyway-repair-runner）
- **Step 2 DB 版本**: 部署前已应用至 V1218，V1219/V1220 pending（预期）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate **通过（278 migrations）**
- **部署后 DB 版本**: V1219 + V1220 均 success=1（17:10:31 / 17:10:32 应用）

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（锚点分支 `agent/trae-init` 与 origin/main 一致，HEAD=origin/main；工作区残留 `.githooks/pre-push` 改动已 stash 隔离）
3. 服务器现状探测（deployed-release=0d8543c92，health HTTP 200 UP，增量 8 commit，迁移变更=V1219）
4. Flyway 预检 → validate OK（278 migrations）
5. **发现启动阻断 → 本地容器测试复现 → 创建 V1220 修复迁移 + U1220 → 容器测试 5/5 green**
6. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
7. 产物校验（obsEnabled=true、前端 index 入口 `index-CF-9RCBy.js`、jar 内 V1219+V1220 无重复版本、OBS Detail .upload=2、sidecar 打包）
8. 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → Flyway validate → 前端激活（sudo chown 修复属主）→ 后端原子重启 → 健康检查（81 次轮询通过）
9. 前端资源保留（从上一版本 release 0d8543c92 `cp -rn` 旧 assets 到 /srv/www/xiyu-bid/assets/，防跨版本 404）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，健康检查 3/3 consecutive（第 81 次轮询） |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP（无 Kafka 延迟） |
| Flyway validate | ✅ VALIDATE OK（278 migrations，部署前后） |
| DB 迁移应用 | ✅ V1219 + V1220 success=1 |
| 前端一致性 | ✅ `assets/index-CF-9RCBy.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 已从 0d8543c92 release 目录保留 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ remote-deploy 自动备份（winbid-47fb5e620-fix1220-*.sql.gz） |
| 临时调试配置 | ⚠️ `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策保留项，非本次新增） |

## 回滚

- **回滚锚点**: 上一 release `0d8543c92`（/opt/xiyu-bid/releases/0d8543c92 仍存在）
- **DB 备份**: remote-deploy 自动备份 `/opt/xiyu-bid/db-backups/winbid-47fb5e620-fix1220-*.sql.gz`
- **schema 回滚**: U1220 回滚 V1220（注意：回滚后 CHAR 会导致 validate 失败，需连同 V1219 一并回滚到 V1218 才安全）；V1219 需 U1219
- **回滚状态**: 就绪（未执行，验证通过无需回滚）

## 经验沉淀应用

- Flyway 预检 3 步法 ✅（提前暴露 V1219 类型不匹配，避免线上启动事故）
- **schema-validation 与迁移类型一致性** ✅ 本次新增经验：V1219 使用 `CHAR(64)` 与 Hibernate `@Column(length=64)`（varchar）不匹配，`ddl-auto=validate` 下后端启动必失败；打包期容器测试（FlywayMysqlContainerTest）是此问题的第一道防线，必须通过
- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅
- 前端资源保留（cp -rn 旧 assets 防跨版本 404） ✅

## 部署确认清单

- [x] 环境门禁（测试 172.16.38.78）
- [x] 早操同步 + 基线确认
- [x] Flyway 预检 3 步
- [x] 启动阻断发现 + V1220 修复迁移（容器测试 5/5 green）
- [x] 本地打包 + 产物校验
- [x] remote-deploy 自动部署
- [x] 健康检查 + Smoke 全通过
- [x] 前端资源保留
- [x] 部署报告
- [ ] **V1220 + 本报告合入 main**（待提交 PR）
