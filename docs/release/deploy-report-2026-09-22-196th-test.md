# 第 196 次部署报告（test）

- **部署日期**: 2026-09-22
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `4b9ed7e54`
- **部署类型**: 代码级部署（含 1 个 Flyway 迁移变更 V1216）
- **操作人**: Trae 开发宿主（agent/trae-init 锚点分支，与 main ff 同步）

## 部署概览

从上一已部署 release `558856444`（195th，2026-09-22）增量部署至 `4b9ed7e54`，共 **31 个 commit**，新增 1 个 Flyway 迁移（DB 从 V1215 升至 V1216）。

## 基线信息

- 分支: `agent/trae-init`（锚点分支，HEAD == origin/main == `4b9ed7e54`）
- 工作区状态: 干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CQRpRhrT.js`

## 主要改动（558856444..4b9ed7e54）

- **fix(scoreparse)**: 修复项目 1385 评分标准采纳状态冲突；guard formal score adoption against stale parse tasks
- **V1216**: `score_parse_evidence.explanation` 原 VARCHAR(255) 扩为 TEXT，完整保留评分解析证据说明（非破坏性扩列）
- **feat/fix(performance)**: 压测原始证据归档工具链（k6 汇总/采样 + 监控时间序列 + sha256 清单）；监控完整性按声明的组件集合判定；敏感键脱敏状态机收口；jstat/redis-cli 兼容性修复；压测证据工具链按层拆分
- **docs(testing)**: 归档 2026-09-21 复测原始证据包、监控时序图与验收附录

> 注：大量 performance 相关 commit 属于测试证据工具链/文档层，业务代码核心变更为 scoreparse 采纳状态冲突修复。

## Flyway 预检结果

- **Step 1 validate**: 275 个迁移全部通过，checksum 匹配（VALIDATE OK）
- **Step 2 DB 版本**: 已应用到 V1215（2026-09-16），源码最新 V1216（仅差 1 个增量）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate 通过（275 migrations）
- **V1216 迁移**: `ALTER TABLE score_parse_evidence MODIFY COLUMN explanation TEXT`（非破坏性，幂等）
- **回滚脚本**: `U1216__widen_score_parse_evidence_explanation.sql` 已配套
- **结论**: DB 状态健康，仅应用 V1216

## 部署步骤

1. 早操三连（dev-env + sync-env + check-git-wrapper）
2. 基线确认（agent/trae-init == origin/main == 4b9ed7e54，工作区干净）
3. 服务器现状探测（deployed-release=558856444，health UP）
4. Flyway 预检 3 步法通过
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
   - 首次打包失败：`FlywayMysqlContainerTest` 容器测试门禁因本地 Docker daemon 未运行失败（非代码问题）；启动 Docker Desktop 后重打包成功
6. 产物校验（obsEnabled=true、前端 index 入口、jar 内 275 迁移无重复、OBS Detail .upload=2、jar 内含 V1216）
7. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → Sidecar 升级（sha256 由 8d3aa…→a2e60…）→ 健康检查 → Flyway validate → 前端激活 → 后端原子重启
8. 前端资源保留：从上一 release 目录 558856444 手动 cp -rn 旧 assets 到 /srv/www/xiyu-bid/assets/

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running) |
| 健康检查（经 Nginx） | ✅ HTTP 200 UP（81 次探测连续通过） |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（275 migrations） |
| 迁移 V1216 应用 | ✅ version=1216 success=1 |
| 前端一致性 | ✅ `assets/index-CQRpRhrT.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| Sidecar | ✅ 升级后健康检查通过（schemaVersion=2.0） |
| AI Provider 守卫 | ✅ AI_PROVIDER=custom |
| DB 备份 | ✅ 已自动执行 |

## 回滚

- **回滚锚点**: 上一 release `558856444`（/opt/xiyu-bid/releases/558856444-ai-provider-diag-20260922/frontend 仍存在，assets 已保留；后端 jar 可重新构建部署）
- **DB 备份**: 已由 remote-deploy.sh 自动执行（含 V1216 变更前备份）
- **回滚状态**: 就绪（未执行，行为正常无需回滚）

## 经验沉淀应用

- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- COPYFILE_DISABLE=1 防 macOS `._*` 残留 ✅
- --noproxy 绕过 Mac HTTP_PROXY 502 ✅
- SYSTEMCTL_SUDO=true ✅（第 15 次事故防复发）
- 前端 hash 资源跨版本 404 保留策略 ✅（第 17 条经验；本次 deployed-release.json 已覆写，手动从旧 release 目录保留）
- 无 sentry-logback SentryAppender 手动声明 ✅（第 34 次事故已修复）

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15 次决定保留），非本次引入，保留。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- 本次迁移 V1216 为**非破坏性扩列**（VARCHAR→TEXT），schema 风险低。
- 核心业务改动为 scoreparse 采纳状态冲突修复（!2729），建议在测试环境重点回归：项目 1385 及同类项目的评分标准采纳、评分解析与发布流程。
- 大量 performance 工具链改动为测试证据层，不影响生产业务路径，但若需复现压测证据请按新工具链。

## 部署确认清单

- [x] 环境门禁（用户确认测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] 基线 = origin/main
- [x] Flyway 预检通过（V1216 增量 + 回滚脚本配套）
- [x] 打包（Docker 就绪后重打包成功）+ 产物校验通过
- [x] 部署成功（health + smoke + 前端一致性 + 迁移应用验证）
- [x] 部署报告生成