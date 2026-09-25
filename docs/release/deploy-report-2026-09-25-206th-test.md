# 第 206 次部署报告（test）

- **部署日期**: 2026-09-25
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `958f3541d`
- **部署类型**: 代码级部署（新增 Flyway 迁移 V1218）
- **操作人**: Trae 开发宿主（基线 = origin/main `958f3541d`）

## 部署概览

从上一已部署 release `8245c4da8`（205th，2026-09-25 上午）增量部署至 `958f3541d`，共 **8 个 commit**，新增 1 个 Flyway 迁移 **V1218**（数据字典表/字段中文注释补齐）。

> ⚠️ 本次部署伴随一次测试库修复：部署前 Flyway validate 失败（V1218 failed 记录），根因与处置详见下文"部署前修复"。

## 基线信息

- 基线: `origin/main` = `958f3541d`（`!2760 fix(scoreparse): 补齐 DOCX 评分证据转换与资格校验（待真实验收）`）
- 工作区状态: 锚点分支 `agent/trae-init` ff-only 同步（HEAD = origin/main，无 ahead/behind；存在 1 个未提交的 `.githooks/pre-push` 本地修改，与部署内容无关，未随包）
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CwImfc1B.js`
- sidecar: schemaVersion=2.1，SHA-256 `85852b2f...`

## 主要改动（8245c4da8..958f3541d）

- **feat(db)**: V1218 补齐数据字典表/字段中文注释 + 字典生成器动态 SQL 解析增强（!2759 自动合并）
  - 151 张缺表注释的表 + 1636 个缺字段注释的字段补齐中文 COMMENT（仅元数据变更）
  - 字典生成器动态 SQL 解析增强
  - wiki 回填数据字典补齐与生成器解析盲区知识页
- **fix(scoreparse)**: DOCX 评分证据系列修复（!2760）
  - preserve DOCX scheme and evidence scope
  - bind DOCX continuation evidence
  - reject keyword-only table continuation
  - 补齐 DOCX 评分证据转换与资格校验（待真实验收）
  - wiki 记录 DOCX score evidence gaps
- **docs(release)**: 第 205 次部署报告（8a45d6411）

> 本次核心为数据字典注释补齐迁移 V1218 + scoreparse DOCX 评分证据链路修复。V1218 为纯元数据变更（仅 COMMENT/MODIFY 加注释），不改变任何结构属性。

## 部署前修复（V1218 failed 记录处置）

**现象**: Flyway 预检 validate 失败，报 `Detected failed migration to version 1218`。

**根因定位**（证据链）：
1. DB `flyway_schema_history` 存在 V1218 记录 `success=0`（2026-09-25 11:04 一次含 V1218 的部署尝试执行失败）
2. 服务器当前 jar 与 `/tmp/migration-mysql/` 均无 V1218 文件（本次新增），failed 记录为历史遗留
3. 手动执行 V1218 复现错误：`ERROR 1146 Table 'winbid.v1091_staff_users_backup' doesn't exist`
4. 全量对比 V1218 引用 189 张表 vs 测试库实际表：**仅 `v1091_staff_users_backup` 缺失**，实际存在表名为 `v1091_staff_users_backup_emergency`（应急改名，含 8493 行历史备份数据）
5. 全仓 grep 无任何代码引用 `_emergency` 表名，为孤立备份表

**处置**（用户确认后执行）：
1. `RENAME TABLE v1091_staff_users_backup_emergency TO V1091_staff_users_backup`（数据完整保留，8493 行验证通过）
2. 完整执行 V1218 验证全部 189 张表列级匹配（mysql exit 0，0 ERROR）——排除后续隐藏不匹配
3. `flyway-repair-runner.sh repair` 移除 failed 记录 → 后置 validate **OK（277 migrations）**
4. DB 备份: `/opt/xiyu-bid/db-backups/winbid-v1218-diagnose-20260925160039.sql.gz`（处置前全量备份）

**影响面**: 仅测试库 `winbid`；生产环境未受影响（V1091 表名按迁移链执行正常，无同名问题）。

## Flyway 预检结果

- **Step 1 validate**: 初始 **FAILED**（V1218 failed 记录）→ repair 后 **VALIDATE OK（277 migrations）**
- **Step 2 DB 版本**: 修复前已应用至 V1217；V1218 无成功记录（failed）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate **通过（277 migrations）**；V1218 由新 jar 启动时成功应用（`success=1`，16:08:34）

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（fetch origin/main；锚点分支 `agent/trae-init` 与 origin/main 一致，sync-env.sh 因分支保护跳过，git 事实确认 HEAD=origin/main）
3. 服务器现状探测（deployed-release=8245c4da8，health HTTP 200）
4. Flyway 预检 → **发现 V1218 failed → 根因定位 → 修复（RENAME + 完整验证 + repair）** → 复检 OK
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口、jar 内含 V1218、OBS Detail .upload=2、sidecar 打包）
7. scp 上传（162MB）+ remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → Flyway validate → 前端激活 → 后端原子重启 → 健康检查
8. 前端资源保留（从上一版本 release 8245c4da8 `cp -rn` 旧 assets 到 /srv/www/xiyu-bid/assets/，防跨版本 404）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running) PID 19086，健康检查 3/3 consecutive（第 81 次轮询） |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（277 migrations） |
| V1218 迁移 | ✅ success=1（2026-09-25 16:08:34），且部署前已完整验证 189 张表 |
| 前端一致性 | ✅ `assets/index-CwImfc1B.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 已从 8245c4da8 release 目录保留 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ 部署前修复备份 + remote-deploy 自动备份 |

## 回滚

- **回滚锚点**: 上一 release `8245c4da8`（/opt/xiyu-bid/releases/8245c4da8 仍存在，后端 jar 可重新构建部署）
- **DB 备份**:
  - 修复前全量备份: `/opt/xiyu-bid/db-backups/winbid-v1218-diagnose-20260925160039.sql.gz`
  - remote-deploy 自动备份: `/opt/xiyu-bid/db-backups/winbid-958f3541d-*.sql.gz`
  - flyway repair 前置备份: `/opt/xiyu-bid/backups/flyway-history/flyway_schema_history-20260925-160435.sql`
- **schema 回滚**: V1218 为纯元数据变更（COMMENT/MODIFY 加注释），无结构属性变更，回滚脚本 U1218 已配套；V1091 表名恢复为迁移链预期原名（与代码/回滚脚本一致）
- **回滚状态**: 就绪（未执行，行为正常无需回滚）

## 经验沉淀应用

- Flyway 预检 3 步法（validate 提前暴露 V1218 failed 记录，避免启动失败事故） ✅
- 迁移失败根因证据链定位（先对比表清单，再手动复现错误） ✅
- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- COPYFILE_DISABLE=1 防 macOS `._*` 残留 ✅
- --noproxy 绕过 Mac HTTP_PROXY 502 ✅
- SYSTEMCTL_SUDO=true ✅（第 15 次事故防复发）
- 前端 hash 资源跨版本 404 防护（cp -rn 保留旧 assets） ✅

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15 次决定保留），非本次引入，保留。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- **测试库 `v1091_staff_users_backup_emergency` 已改回原名**：请确认该 emergency 改名无业务依赖（全仓 grep 无引用，判定为孤立备份表）。生产库如存在同名 emergency 表，部署 V1218 前需同样处置。
- V1218 补注释迁移涉及 151 张表，虽已验证 189 张表列级匹配且数据无修改，建议抽查几张高频表确认页面正常。
- scoreparse DOCX 证据链路为新增修复（标记"待真实验收"），建议实测 DOCX 评分证据转换场景。
