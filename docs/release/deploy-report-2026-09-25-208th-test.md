# 第 208 次部署报告（test）

- **部署日期**: 2026-09-25
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `abd0b404a`
- **部署类型**: 代码级部署（无新增 Flyway 迁移）
- **操作人**: Trae 开发宿主（基线 = origin/main `abd0b404a`）

## 部署概览

从上一已部署 release `8bbaadc77`（207th，2026-09-25 上午）增量部署至 `abd0b404a`，共 **4 个 commit**，无新增 Flyway 迁移（DB 最高版本保持 V1218）。

## 基线信息

- 基线: `origin/main` = `abd0b404a`（`!2764 fix: 补齐扫描件 OCR 评分引文原文定位`）
- 工作区状态: 锚点分支 `agent/trae-init` ff-only 同步（HEAD = origin/main，工作区干净）
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-C5DsCG0D.js`
- sidecar: schemaVersion=2.2，SHA-256 `a400b529f70cba78b31c211a8b5e0a82ed9213786cc887cd96be0c8f3dd99d01`

## 主要改动（8bbaadc77..abd0b404a）

- **fix(scoreparse)**: 补齐扫描件 OCR 评分引文原文定位（!2764）
  - bind OCR evidence to exact source locators
  - lock OCR token spacing regression（测试）
  - Sidecar Python 侧 OCR 文本规范化 + table_extractor 适配
  - 新增 golden case `case-ocr-chinese-token-spaces`（扫描件中文 token 空格回归测试）
- **docs(release)**: 第 207 次部署报告（081b0fdae）

> 本次核心为扫描件 OCR 评分引文原文定位补齐（后端 scoreparse + Sidecar Python 双端），含 golden 回归测试。无数据库结构变更、无新迁移、无前端改动。

## Flyway 预检结果

- **Step 1 validate**: **VALIDATE OK（278 migrations，all checksums match）**（服务器 flyway-repair-runner）
- **Step 2 DB 版本**: 已应用至 V1218（上一部署确认），本次无新增迁移
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate **通过（278 migrations）**

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（fetch origin/main；锚点分支 `agent/trae-init` 与 origin/main 一致，HEAD=origin/main，git 事实确认）
3. 服务器现状探测（deployed-release=8bbaadc77，health HTTP 200 + readiness 200，增量 4 commit，无迁移变更）
4. Flyway 预检 → validate OK（278 migrations）
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口 `index-C5DsCG0D.js`、jar 内迁移无重复版本、OBS Detail .upload=2、sidecar 打包）
7. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → Flyway validate → 前端激活（自动 sudo chown 修复属主）→ 后端原子重启 → 健康检查
8. 前端资源保留（从上一版本 release 8bbaadc77 `cp -rn` 旧 assets 到 /srv/www/xiyu-bid/assets/，防跨版本 404）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running) PID 7992，健康检查 3/3 consecutive（第 81 次轮询） |
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
| 旧 assets 保留 | ✅ 已从 8bbaadc77 release 目录保留（共 193 资产） |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ remote-deploy 自动备份（winbid-abd0b404a-20260925204455.sql.gz） |

## 回滚

- **回滚锚点**: 上一 release `8bbaadc77`（/opt/xiyu-bid/releases/8bbaadc77 仍存在，后端 jar 可重新构建部署）
- **DB 备份**: remote-deploy 自动备份 `/opt/xiyu-bid/db-backups/winbid-abd0b404a-*.sql.gz`
- **schema 回滚**: 本次无新增迁移，无 schema 变更
- **回滚状态**: 就绪（未执行，验证通过无需回滚）

## 经验沉淀应用

- Flyway 预检 3 步法 ✅
- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- COPYFILE_DISABLE=1 防 macOS `._*` 残留 ✅
- --noproxy 绕过 Mac HTTP_PROXY 502 ✅
- SYSTEMCTL_SUDO=true ✅（第 15 次事故防复发）
- 前端 hash 资源跨版本 404 防护（cp -rn 保留旧 assets） ✅

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15/206/207 次决定保留），非本次引入，**用户确认保留**。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- OCR 评分引文定位为评分解析关键路径改动，Sidecar schemaVersion 由 2.1 升至 2.2，建议实测"扫描件评分引文原文定位"场景确认符合预期。
- Sidecar 镜像/容器需与本次 sidecar 代码同步升级（Dockerfile 有改动），确认服务器 Sidecar 运行版本与 schemaVersion 2.2 匹配。
