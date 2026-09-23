# 第 199 次部署报告（test）

- **部署日期**: 2026-09-23
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `785de2f74`
- **部署类型**: 代码级部署（无新增 Flyway 迁移）
- **操作人**: Trae 开发宿主（任务分支 `agent/trae/deploy-test-199th`，base = origin/main）

## 部署概览

从上一已部署 release `ea5ad35c`（198th，2026-09-23）增量部署至 `785de2f74`，共 **6 个 commit**，无新增 Flyway 迁移（DB 保持 V1217，与上一部署一致）。

## 基线信息

- 分支: `agent/trae/deploy-test-199th`（任务分支，base = origin/main = `785de2f74`）
- 工作区状态: 干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CQRpRhrT.js`（与上一部署一致）

## 主要改动（ea5ad35c..785de2f74）

- **fix(scoreparse)**: process full tender text in bounded chunks（!2741）— 修复超长标书文本处理，按有界 chunk 处理完整标书文本
- **test(scoreparse)**: 增加 long text golden fixture（4a42d8e7e）
- **docs(scoreparse)**: 记录 bounded text chunking 契约（52ab1e689）
- **docs**: 第 198 次部署报告（b55c49f40 / 6dbf3c2ea）

> 本次核心为 scoreparse 长文本处理修复：避免超长标书正文一次性处理导致的性能/内存问题，改为有界 chunk 分批处理。无数据库结构变更。

## Flyway 预检结果

- **Step 1 validate**: `flyway-repair-runner.sh` 首次执行报错 `Detected applied migration not resolved locally: 1217`
  - **根因**: 服务器 `/tmp/migration-mysql/` 目录过时（仅到 V1216，缺 V1217）——部署经验第 10 条已知问题
  - **处置**: scp 上传源码 `V1217__add_score_child_aggregation.sql` 至服务器 `/tmp/migration-mysql/`，重新 validate → **VALIDATE OK（277 migrations，checksum 全部匹配）**
- **Step 2 DB 版本**: 已应用至 V1217（2026-09-23 17:24:20，与上一部署一致）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate 通过（277 migrations）
- **结论**: 本次无新增迁移，DB 与源码版本一致，无 pending

## 部署步骤

1. 早操三连（dev-env + sync-env + check-git-wrapper）
2. 环境门禁确认（用户确认测试环境 172.16.38.78）
3. 开任务分支 `agent/trae/deploy-test-199th`（base = origin/main = 785de2f74）
4. 服务器现状探测（deployed-release=ea5ad35c，health UP，obsEnabled=true）
5. Flyway 预检 3 步法（validate 首次失败 → 修复 /tmp 目录过时 → OK）
6. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
7. 产物校验（obsEnabled=true、前端 index 入口、jar 内迁移无重复、OBS Detail .upload=2、sidecar 打包）
8. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：健康检查 → Flyway validate → 前端激活 → 后端原子重启（含 sudo chown 前端目录）
9. 前端资源保留（从上一版本 release 目录 cp -rn 旧 assets 到 /srv/www/xiyu-bid/assets/）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，PID 18864，健康检查 3/3 consecutive（第 80 次轮询） |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（277 migrations） |
| V1217 迁移应用 | ✅ `success=1`，installed_on 2026-09-23 17:24:20（上一部署已应用） |
| 前端一致性 | ✅ `assets/index-CQRpRhrT.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 已执行（cp -rn 幂等） |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ 已自动执行 |

## 回滚

- **回滚锚点**: 上一 release `ea5ad35c`（/opt/xiyu-bid/releases/ea5ad35c 仍存在，后端 jar 可重新构建部署）
- **DB 备份**: 已由 remote-deploy.sh 自动执行
- **schema 回滚**: 本次无新增迁移，无需回滚脚本
- **回滚状态**: 就绪（未执行，行为正常无需回滚）

## 经验沉淀应用

- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- COPYFILE_DISABLE=1 防 macOS `._*` 残留 ✅
- --noproxy 绕过 Mac HTTP_PROXY 502 ✅
- SYSTEMCTL_SUDO=true ✅（第 15 次事故防复发）
- 前端 hash 资源跨版本 404 防护 ✅（部署后从上一版本 cp -rn 旧 assets）
- **服务器 /tmp/migration-mysql/ 目录过时处理 ✅（部署经验第 10 条：validate 报"applied migration not resolved locally"时，将源码新迁移 scp 到服务器目录后重新 validate，与 DB checksum 匹配即通过）**

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15 次决定保留），非本次引入，保留。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- 本次无数据库结构变更，schema 风险低。
- 核心业务改动为 scoreparse 长文本有界 chunk 处理，建议回归超长标书评分解析场景。

## 部署确认清单

- [x] 环境门禁（用户确认测试环境 172.16.38.78）
- [x] 早操三连通过（任务分支 base = origin/main）
- [x] 基线 = origin/main（785de2f74）
- [x] Flyway 预检通过（修复 /tmp 目录过时后 VALIDATE OK，DB V1217 = 源码 V1217）
- [x] 打包 + 产物校验通过（obsEnabled=true / 迁移无重复 / 前端入口）
- [x] 部署成功（health + smoke + 前端一致性）
- [x] 部署报告生成
