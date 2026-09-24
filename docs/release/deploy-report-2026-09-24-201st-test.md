# 第 201 次部署报告（test）

- **部署日期**: 2026-09-24
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `7fdcd87a2`
- **部署类型**: 代码级部署（无新增 Flyway 迁移）
- **操作人**: Trae 开发宿主（基线 = origin/main `7fdcd87a2`）

## 部署概览

从上一已部署 release `a25b4a2c7`（200th，2026-09-24）增量部署至 `7fdcd87a2`，共 **10 个 commit**，无新增 Flyway 迁移（DB 保持 V1217，与上一部署一致）。

## 基线信息

- 基线: `origin/main` = `7fdcd87a2`（本地锚点 ff-only 同步至最新）
- 工作区状态: 干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CQRpRhrT.js`（与上一部署一致，本次前端无改动）

## 主要改动（a25b4a2c7..7fdcd87a2）

- **fix(ai)**: harden OpenAiCompatibleClient with json_object format, 4096 tokens, and 60s timeout（!2743）— AI 客户端加固：结构化输出 json_object 格式、token 上限 4096、请求超时 60s，配套 123 行单测
- **fix(scoreparse)**: preserve facts across chunk total conflicts（!2746）— 评分解析 chunk 总数冲突时保留已有事实（含前置修复 commit + 契约文档 + 测试增强）
- **docs**: 第 200 次部署报告（8a1b4988b / c0c655987）
- **docs**: AI 客户端加固与结构化约束知识沉淀（lessons-learned #403 + wiki 回填）

> 本次核心为两处健壮性修复：AI 客户端请求加固（json_object/超时/长度）与评分解析 chunk 总数冲突的事实保留。均配套单元测试。无数据库结构变更。

## Flyway 预检结果

- **Step 1 validate**: `flyway-repair-runner.sh` → **VALIDATE OK（277 migrations，checksum 全部匹配）**
- **Step 2 DB 版本**: 已应用至 V1217（2026-09-23 17:24:20，与上一部署一致）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate 通过（277 migrations）
- **结论**: 本次无新增迁移，DB 与源码版本一致，无 pending

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（fetch origin/main + 本地锚点 ff-only 同步至 `7fdcd87a2`）
3. 服务器现状探测（deployed-release=a25b4a2c7，health 全组件 UP，obsEnabled=true）
4. Flyway 预检 3 步法（validate OK → DB V1217 → 内置校验）
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口、jar 内迁移无重复、OBS Detail .upload=2、sidecar 打包）
7. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：健康检查 → Flyway validate → 前端激活 → 后端原子重启（含 sudo chown 前端目录）
8. 前端资源保留（从上一版本 release `a25b4a2c7` cp -rn 旧 assets 到 /srv/www/xiyu-bid/assets/）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，PID 22861，健康检查 3/3 consecutive（第 81 次轮询） |
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
| 旧 assets 保留 | ✅ 已执行（从 a25b4a2c7 cp -rn 幂等，193 个资源） |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ 已自动执行 |

## 回滚

- **回滚锚点**: 上一 release `a25b4a2c7`（/opt/xiyu-bid/releases/a25b4a2c7 仍存在，后端 jar 可重新构建部署）
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
- 核心业务改动为 AI 客户端加固（json_object/4096 tokens/60s 超时）与评分解析 chunk 冲突事实保留，建议回归：
  - 标书 AI 解析/分析场景（AI 客户端新参数是否影响输出）
  - 大文档评分解析分 chunk 处理场景（总分冲突时事实保留）

## 部署确认清单

- [x] 环境门禁（用户确认测试环境 172.16.38.78）
- [x] 基线 = origin/main（7fdcd87a2）
- [x] Flyway 预检通过（VALIDATE OK，DB V1217 = 源码 V1217）
- [x] 打包 + 产物校验通过（obsEnabled=true / 迁移无重复 / 前端入口）
- [x] 部署成功（health + smoke + 前端一致性）
- [x] 部署报告生成
