# 第 200 次部署报告（test）

- **部署日期**: 2026-09-24
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `a25b4a2c7`
- **部署类型**: 代码级部署（无新增 Flyway 迁移）
- **操作人**: Trae 开发宿主（基线 = origin/main `a25b4a2c7`）

## 部署概览

从上一已部署 release `785de2f74`（199th，2026-09-23）增量部署至 `a25b4a2c7`，共 **6 个 commit**，无新增 Flyway 迁移（DB 保持 V1217，与上一部署一致）。

## 基线信息

- 基线: `origin/main` = `a25b4a2c7`（本地锚点 ff-only 同步至最新）
- 工作区状态: 干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CQRpRhrT.js`（与上一部署一致，本次前端无改动）

## 主要改动（785de2f74..a25b4a2c7）

- **fix(scoreparse)**: make text extraction primary across tender files（!2744）— 跨投标文件多文档场景下，文本抽取改为主抽取方式
- **test(scoreparse)**: add multi-document text-primary golden case（3ed3cab42）
- **docs(scoreparse)**: record text-primary multi-file contract（306e6dc92）
- **docs**: 第 199 次部署报告（959d2243e / 75dea6fb4）

> 本次核心为 scoreparse 多文件文本抽取主路径改造：多文档标书评分解析时以文本抽取为优先抽取源，配套新增 golden case 与契约文档。无数据库结构变更。

## Flyway 预检结果

- **Step 1 validate**: `flyway-repair-runner.sh` → **VALIDATE OK（277 migrations，checksum 全部匹配）**
- **Step 2 DB 版本**: 已应用至 V1217（2026-09-23 17:24:20，与上一部署一致）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate 通过（277 migrations）
- **结论**: 本次无新增迁移，DB 与源码版本一致，无 pending

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（fetch origin/main + 本地锚点 ff-only 同步至 `a25b4a2c7`）
3. 服务器现状探测（deployed-release=785de2f74，health UP，obsEnabled=true）
4. Flyway 预检 3 步法（validate OK → DB V1217 → 内置校验）
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口、jar 内迁移无重复、OBS Detail .upload=2、sidecar 打包）
7. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：健康检查 → Flyway validate → 前端激活 → 后端原子重启（含 sudo chown 前端目录）
8. 前端资源保留（从上一版本 release `785de2f74` cp -rn 旧 assets 到 /srv/www/xiyu-bid/assets/）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，PID 17099，健康检查 3/3 consecutive（第 79 次轮询） |
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
| 旧 assets 保留 | ✅ 已执行（从 785de2f74 cp -rn 幂等） |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ 已自动执行 |

## 回滚

- **回滚锚点**: 上一 release `785de2f74`（/opt/xiyu-bid/releases/785de2f74 仍存在，后端 jar 可重新构建部署）
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

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15 次决定保留），非本次引入，保留。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- 本次无数据库结构变更，schema 风险低。
- 核心业务改动为 scoreparse 多文件文本抽取主路径，建议回归多文档标书评分解析场景。

## 部署确认清单

- [x] 环境门禁（用户确认测试环境 172.16.38.78）
- [x] 基线 = origin/main（a25b4a2c7）
- [x] Flyway 预检通过（VALIDATE OK，DB V1217 = 源码 V1217）
- [x] 打包 + 产物校验通过（obsEnabled=true / 迁移无重复 / 前端入口）
- [x] 部署成功（health + smoke + 前端一致性）
- [x] 部署报告生成
