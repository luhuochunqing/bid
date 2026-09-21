# 第 194 次部署报告（test）

- **部署日期**: 2026-09-21
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `ec27f6162`
- **部署类型**: 代码级部署（无 Flyway 迁移变更）
- **操作人**: Trae 开发宿主（agent/trae-init 锚点分支，与 main ff 同步）

## 部署概览

从上一已部署 release `bafa312d6`（193rd，2026-09-21）增量部署至 `ec27f6162`，共 **4 个 commit**，无新增 Flyway 迁移（DB 停留在 V1215）。

## 基线信息

- 分支: `agent/trae-init`（锚点分支，HEAD == origin/main == `ec27f6162`）
- 工作区状态: 干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-B1gJ48GB.js`

## 主要改动（bafa312d6..ec27f6162）

- **fix(scoreparse)**: 恢复多模态标书评分不完整召回（golden fallback 回归测试）
- **test(scoreparse)**: golden fallback 回归测试
- **docs(scoreparse)**: 记录评分不完整召回根因
- **merge**: PR !2719（炉火纯青/agent/codex/scoreparse-project1382）

## Flyway 预检结果

- **Step 1 validate**: 275 个迁移全部通过，checksum 匹配（VALIDATE OK）
- **Step 2 DB 版本**: 已应用到 V1215（2026-09-16）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate 通过
- **结论**: 本次无迁移变更，DB 状态健康

## 部署步骤

1. 早操三连（dev-env + sync-env + check-git-wrapper）
2. 基线确认（agent/trae-init == origin/main == ec27f6162，工作区干净）
3. 服务器现状探测（deployed-release=bafa312d6，health UP）
4. Flyway 预检 3 步法通过
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口、jar 内迁移无重复、OBS Detail .upload=2）
7. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
8. 前端资源保留检查（上一 release bafa312d6 目录已被清理，测试环境 no-op）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，PID 25416 |
| 健康检查（经 Nginx） | ✅ HTTP 200 UP（81 次探测连续通过） |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（275 migrations） |
| 前端一致性 | ✅ `assets/index-B1gJ48GB.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| Flyway failed 记录 | ✅ 0 条 |

## 回滚

- **回滚锚点**: 上一 release `bafa312d6`（/opt/xiyu-bid/releases/bafa312d6，目录已被 remote-deploy 清理；可重新构建部署）
- **DB 备份**: 已由 remote-deploy.sh 自动执行（本次无迁移，备份主要为保险）
- **回滚状态**: 就绪（未执行，行为正常无需回滚）

## 经验沉淀应用

- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- COPYFILE_DISABLE=1 防 macOS `._*` 残留 ✅
- --noproxy 绕过 Mac HTTP_PROXY 502 ✅
- SYSTEMCTL_SUDO=true ✅（第 15 次事故防复发）
- 无 sentry-logback SentryAppender 手动声明 ✅（第 34 次事故已修复）

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15 次决定保留），非本次引入，保留。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- 无新增迁移，DB schema 风险低。
- scoreparse 多模态评分召回改动建议在测试环境重点回归标书评分解析流程。

## 部署确认清单

- [x] 环境门禁（用户确认测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] 基线 = origin/main
- [x] Flyway 预检通过
- [x] 打包 + 产物校验通过
- [x] 部署成功（health + smoke + 前端一致性）
- [x] 部署报告生成
