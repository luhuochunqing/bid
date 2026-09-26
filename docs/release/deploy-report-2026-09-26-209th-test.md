# 第 209 次部署报告（test）

- **部署日期**: 2026-09-26
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `e2502f5de`
- **部署类型**: 代码级部署（无新增 Flyway 迁移）
- **操作人**: Trae 开发宿主（基线 = origin/main `e2502f5de`）

## 部署概览

从上一已部署 release `abd0b404a`（208th，2026-09-25）增量部署至 `e2502f5de`，共 **22 个 commit**，无新增 Flyway 迁移（DB 最高版本保持 V1218）。

## 基线信息

- 基线: `origin/main` = `e2502f5de`（`!2767 fix(scoreparse): 修复项目 1382 PDF 评分方案资格门（PDF 表格章节归属 + 目录行排除 + factId 唯一性）`）
- 工作区状态: 锚点分支 `agent/trae-init` ff-only 同步（HEAD = origin/main）
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-C5DsCG0D.js`
- sidecar: schemaVersion=2.2，SHA-256 `39335253a1adf96807589032046e7369dcdb68fbef6dd9328b27895ba5a85d2e`

## 主要改动（abd0b404a..e2502f5de）

- **fix(scoreparse)**: 修复项目 1382 PDF 评分方案资格门（!2767）
  - PDF 表格章节归属 + 目录行排除 + factId 唯一性
  - 多轮评审（R1-R4）自审与评审发现闭环：断点信号跨页传递、章级事件并列安全方向、全角锚点回退、继承守卫识别面、身份统一、回退契约
  - 补强判定边界与回退路径测试覆盖
- **docs(wiki)**: 1382 根因页多轮回填 + 1384 DOCX 评分方案资格失效根因
- **docs(release)**: 第 208 次部署报告（abd0b404a）

> 本次核心为项目 1382 PDF 评分方案资格门修复（scoreparse 后端多轮闭环），无数据库结构变更、无新迁移。

## 打包脚本改动说明（工作区未提交）

- `scripts/release/package-release.sh` 含 2026-09-26 未提交改动：
  - **新增发布包自动清理**：打包后仅保留最近 3 个 release 包（`RELEASE_KEEP_COUNT` 可覆盖），解决 `.release/` 历史包堆积占盘（曾达 17G）
  - **转义修复**：还原 `\\` → `\.`/`\(`（sed/`=~` 中 `\\` 匹配字面反斜杠，原改动导致 SCHEMA_VERSION 校验失败，已修复）
- 用户确认：带上打包，不单独提交

## Flyway 预检结果

- **Step 1 validate**: **VALIDATE OK（278 migrations，all checksums match）**（服务器 flyway-repair-runner）
- **Step 2 DB 版本**: 已应用至 V1218（2026-09-25 16:08 最后应用），本次无新增迁移
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate **通过（278 migrations）**

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（锚点分支 `agent/trae-init` 与 origin/main 一致，HEAD=origin/main）
3. 服务器现状探测（deployed-release=abd0b404a，health HTTP 200 + readiness 200，增量 22 commit，无迁移变更）
4. Flyway 预检 → validate OK（278 migrations）
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`；首次打包因脚本转义 bug 失败，修复后成功）
6. 产物校验（obsEnabled=true、前端 index 入口 `index-C5DsCG0D.js`、jar 内迁移无重复版本、OBS Detail .upload=2、sidecar 打包）
7. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → Flyway validate → 前端激活 → 后端原子重启 → 健康检查
8. 前端资源保留（从上一版本 release abd0b404a `cp -rn` 旧 assets 到 /srv/www/xiyu-bid/assets/，防跨版本 404）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，健康检查 3/3 consecutive（第 80 次轮询） |
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
| 旧 assets 保留 | ✅ 已从 abd0b404a release 目录保留 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ remote-deploy 自动备份（winbid-e2502f5de-*.sql.gz） |
| 临时调试配置 | ⚠️ `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策保留项，非本次新增） |

## 回滚

- **回滚锚点**: 上一 release `abd0b404a`（/opt/xiyu-bid/releases/abd0b404a 仍存在）
- **DB 备份**: remote-deploy 自动备份 `/opt/xiyu-bid/db-backups/winbid-e2502f5de-*.sql.gz`
- **schema 回滚**: 本次无新增迁移，无 schema 变更
- **回滚状态**: 就绪（未执行，验证通过无需回滚）

## 经验沉淀应用

- Flyway 预检 3 步法 ✅
- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- 打包前脚本改动审查（`\\` 转义在 bash `=~`/sed 中语义陷阱，本次部署实际踩中并修复） ⚠️

## 部署确认清单

- [x] 环境门禁（测试 172.16.38.78）
- [x] 早操同步 + 基线确认
- [x] Flyway 预检 3 步
- [x] 本地打包 + 产物校验
- [x] remote-deploy 自动部署
- [x] 健康检查 + Smoke 全通过
- [x] 前端资源保留
- [x] 部署报告
