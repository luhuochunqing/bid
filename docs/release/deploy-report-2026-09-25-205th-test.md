# 第 205 次部署报告（test）

- **部署日期**: 2026-09-25
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `8245c4da8`
- **部署类型**: 代码级部署（无新增 Flyway 迁移）
- **操作人**: Trae 开发宿主（基线 = origin/main `8245c4da8`）

## 部署概览

从上一已部署 release `89807e398`（204th，2026-09-24）增量部署至 `8245c4da8`，共 **7 个 commit**，无新增 Flyway 迁移（DB 保持 V1217，与上一部署一致）。

## 基线信息

- 基线: `origin/main` = `8245c4da8`（`!2756 feat: 标讯创建推送接口 bidOpeningTime/registrationDeadline 改为必填`）
- 工作区状态: 干净（fetch 最新 origin/main，本地锚点 ff-only 同步）
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CwImfc1B.js`

## 主要改动（89807e398..8245c4da8）

- **feat(标讯推送)**: 标讯创建推送接口 `bidOpeningTime`/`registrationDeadline` 改为必填（!2756）
  - `TenderPushRequest.java` 字段必填校验 + `TenderPushRequestValidationTest` 配套测试
  - wiki 回填 `integration-tender-api.md`（必填字段变更说明）
- **fix(scoreparse)**: scope candidates to verified scoring plans 评分候选方案限定到已验证的评分方案（!2757）
  - 仅向已验证评分方案投放候选，避免未验证方案混入候选集
  - 配套单元测试调整
- **docs(release)**: 第 204 次部署报告（`deploy-report-2026-09-24-204th-test.md`，!2755 自动合并）

> 本次核心为标讯推送接口必填字段加固 + scoreparse 候选方案范围限定，均配套单元测试。无数据库结构变更。

## Flyway 预检结果

- **Step 1 validate**: `flyway-repair-runner.sh` → **VALIDATE OK（277 migrations，checksum 全部匹配）**
- **Step 2 DB 版本**: 已应用至 V1217（add score child aggregation），与源码 validate 一致（本次无新增迁移）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate 通过（277 migrations）
- **结论**: 本次无新增迁移，DB 与源码版本一致，无 pending

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（fetch origin/main，锚点 `agent/trae-init` ff-only 同步至 `8245c4da8`）
3. 服务器现状探测（deployed-release=89807e398，health/readiness 全 UP，obsEnabled=true）
4. Flyway 预检 3 步法（validate OK → DB 版本一致 → 内置校验）
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口、jar 内迁移 276 个无重复、OBS Detail .upload=2、sidecar 打包）
7. scp 上传（155MB）+ remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：健康检查 → Flyway validate → 前端激活 → 后端原子重启
8. 前端资源保留（从上一版本 release 89807e398 `cp -rn` 旧 assets 到 /srv/www/xiyu-bid/assets/，防跨版本 404）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running) PID 16642，健康检查 3/3 consecutive（第 79 次轮询） |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（277 migrations） |
| 前端一致性 | ✅ `assets/index-CwImfc1B.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 已从 89807e398 release 目录保留 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ 已自动执行 |

## 回滚

- **回滚锚点**: 上一 release `89807e398`（/opt/xiyu-bid/releases/89807e398 仍存在，后端 jar 可重新构建部署）
- **DB 备份**: 已由 remote-deploy.sh 自动执行
- **schema 回滚**: 本次无新增迁移，无需回滚脚本
- **回滚状态**: 就绪（未执行，行为正常无需回滚）

## 经验沉淀应用

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

- 本次无数据库结构变更，schema 风险低。
- 核心业务改动为标讯推送接口必填字段加固，建议回归：
  - 标讯创建推送链路：`bidOpeningTime`/`registrationDeadline` 必填后，缺字段推送是否被正确拦截并返回校验错误
  - scoreparse 评审卡片：候选方案是否仅来源于已验证评分方案
