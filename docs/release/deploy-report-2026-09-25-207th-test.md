# 第 207 次部署报告（test）

- **部署日期**: 2026-09-25
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `8bbaadc77`
- **部署类型**: 代码级部署（无新增 Flyway 迁移）
- **操作人**: Trae 开发宿主（基线 = origin/main `8bbaadc77`）

## 部署概览

从上一已部署 release `958f3541d`（206th，2026-09-25 上午）增量部署至 `8bbaadc77`，共 **5 个 commit**，无新增 Flyway 迁移（DB 最高版本保持 V1218）。

> ⚠️ 本次部署伴随一次 `/tmp/migration-mysql/` 过时目录修复：预检 validate 报 `Detected applied migration not resolved locally: 1218`，根因与处置详见下文"部署前修复"。

## 基线信息

- 基线: `origin/main` = `8bbaadc77`（`!2762 fix(scoreparse): allow reviewed partial score adoption`）
- 工作区状态: 锚点分支 `agent/trae-init` ff-only 同步（HEAD = origin/main，工作区干净）
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-C5DsCG0D.js`
- sidecar: schemaVersion=2.1，SHA-256 `b59da7350dc6c2de4253b164a978602904208aa97a5192b430d8b6d9b5a81781`

## 主要改动（958f3541d..8bbaadc77）

- **fix(scoreparse)**: 允许采纳已评审的部分评分（!2762，!2761 为该功能的 PR 合并）
  - allow reviewed partial score adoption
  - 记录 scoreparse partial adoption boundary（docs）
- **docs(release)**: 第 206 次部署报告（0bf0c47fa）

> 本次核心为 scoreparse 部分评分采纳边界放开，无数据库结构变更、无新迁移。

## 部署前修复（/tmp/migration-mysql/ 过时目录）

**现象**: Flyway 预检 validate 失败，报 `Detected applied migration not resolved locally: 1218`。

**根因定位**（证据链）：
1. DB `flyway_schema_history` 最高版本 V1218（`success=1`，2026-09-25 16:08:34，与当前部署 958f3541d 激活时间吻合）
2. 服务器当前 jar 内含 V1218（`unzip -l app.jar` 确认）；但 `/tmp/migration-mysql/` 目录缺 V1218 文件（该目录是历史遗留的本地迁移扫描目录，未随部署同步更新）
3. flyway-repair-runner 扫描 `/tmp/migration-mysql/` 找不到已应用的 V1218 → 误报 validate 失败

**处置**：
1. `scp` 将本地源码 `V1218__backfill_table_column_comments.sql`（SHA 与 jar 内一致）同步至 `/tmp/migration-mysql/`
2. 重新 validate → **VALIDATE OK（278 migrations，all checksums match）**

**影响面**: 仅预检阶段本地扫描目录缺失；实际运行 jar 与 DB 状态一致，未对线上服务造成影响。

## Flyway 预检结果

- **Step 1 validate**: 初始 **FAILED**（/tmp 缺 V1218）→ 同步 V1218 → **VALIDATE OK（278 migrations）**
- **Step 2 DB 版本**: 已应用至 V1218，与源码 jar 最高版本一致，无 pending 新迁移
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate **通过（278 migrations）**

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（fetch origin/main；锚点分支 `agent/trae-init` 与 origin/main 一致，HEAD=origin/main，git 事实确认）
3. 服务器现状探测（deployed-release=958f3541d，health HTTP 200，增量 5 commit，无迁移变更）
4. Flyway 预检 → **发现 /tmp 缺 V1218 → 同步修复 → 复检 OK**
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口 `index-C5DsCG0D.js`、jar 含 V1218、OBS Detail .upload=2、sidecar 打包）
7. scp 上传（155MB）+ remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → Flyway validate → 前端激活 → 后端原子重启 → 健康检查
8. 前端资源保留（从上一版本 release 958f3541d `cp -rn` 旧 assets 到 /srv/www/xiyu-bid/assets/，防跨版本 404）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running) PID 24516，健康检查 3/3 consecutive（第 81 次轮询） |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（278 migrations） |
| DB 最高版本 | ✅ V1218（success=1），无 pending |
| 前端一致性 | ✅ `assets/index-C5DsCG0D.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 已从 958f3541d release 目录保留 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ remote-deploy 自动备份 |

## 回滚

- **回滚锚点**: 上一 release `958f3541d`（/opt/xiyu-bid/releases/958f3541d 仍存在，后端 jar 可重新构建部署）
- **DB 备份**: remote-deploy 自动备份 `/opt/xiyu-bid/db-backups/winbid-8bbaadc77-*.sql.gz`
- **schema 回滚**: 本次无新增迁移，无 schema 变更
- **回滚状态**: 就绪（未执行，验证通过无需回滚）

## 经验沉淀应用

- Flyway 预检 3 步法（validate 提前暴露 /tmp 过时目录，避免启动失败） ✅
- 服务器 `/tmp/migration-mysql/` 过时目录处置（经验第 10 条：部署时同步更新） ✅
- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- COPYFILE_DISABLE=1 防 macOS `._*` 残留 ✅
- --noproxy 绕过 Mac HTTP_PROXY 502 ✅
- SYSTEMCTL_SUDO=true ✅（第 15 次事故防复发）
- 前端 hash 资源跨版本 404 防护（cp -rn 保留旧 assets） ✅

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15/206 次决定保留），非本次引入，**用户确认保留**。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- scoreparse 部分评分采纳为新增功能改动，建议实测"已评审的部分评分采纳"场景确认符合预期。
- `/tmp/migration-mysql/` 目录建议纳入部署脚本同步逻辑，避免后续新增迁移再次出现 validate 误报。