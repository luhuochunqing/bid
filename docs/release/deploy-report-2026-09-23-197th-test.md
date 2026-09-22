# 第 197 次部署报告（test）

- **部署日期**: 2026-09-23
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `f973bd58`
- **部署类型**: 代码级部署（无 Flyway 迁移变更）
- **操作人**: Trae 开发宿主（agent/trae-init 锚点分支，与 main ff 同步）

## 部署概览

从上一已部署 release `4b9ed7e54`（196th，2026-09-22）增量部署至 `f973bd58`，共 **30 个 commit**，无新增 Flyway 迁移（DB 保持 V1216）。

## 基线信息

- 分支: `agent/trae-init`（锚点分支，HEAD == origin/main == `f973bd58`）
- 工作区状态: 干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CQRpRhrT.js`（与上一部署一致）

## 主要改动（4b9ed7e54..f973bd58）

- **fix(scoreparse)**: 修复全量测试 5 个既有失败（PDF 总分/父挂接/scoreType）；强制 scoped scoring integrity（!2736 / !2737）
- **feat(scoreparse)**: 透出 AI Provider 诊断元数据与调用遥测至 run_summary（!2730）
- **refactor(feature 067)**: 未开放功能代码级剔除——8 个隐藏功能前端实现剔除 + 孤立死代码页面/孤儿 API/脚本清理 + 契约豁免收紧 + 交付包脱敏
- **docs**: 评分候选采纳并发根因、scoreparse 5 失败修复根因（401）、067 还原操作与 diff 型门禁冲突实践沉淀

> 注：067 功能剔除为**左右规模较大的前端重构**（涉及 8 个隐藏功能、孤立死代码清理），建议测试环境重点回归导出相关功能与菜单可见性。

## Flyway 预检结果

- **Step 1 validate**: 预检时 `flyway-repair-runner.sh` 报 `Detected applied migration not resolved locally: 1216` — 根因为服务器 `/tmp/migration-mysql/` 目录过时（缺 V1216），非代码/DB 问题（skill 经验 #10）。scp 补传 V1216 后重新 validate → **276 migrations 全部通过（VALIDATE OK）**
- **Step 2 DB 版本**: 已应用到 V1216（2026-09-22 20:05）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate 通过（276 migrations）
- **结论**: 本次无新增迁移，DB 状态健康

## 部署步骤

1. 早操三连（dev-env + sync-env + check-git-wrapper）
   - 锚点分支 `agent/trae-init` 落后 origin/main 5 commit，已 ff-only 同步
2. 基线确认（agent/trae-init == origin/main == f973bd58，工作区干净）
3. 服务器现状探测（deployed-release=4b9ed7e54，health UP / readiness UP）
4. Flyway 预检 3 步法（补传 V1216 修复 `/tmp/migration-mysql/` 过时后通过）
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口、jar 内迁移无重复、OBS Detail .upload=2）
7. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → 健康检查 → Flyway validate → 前端激活 → 后端原子重启（含 sudo chown 前端目录）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，PID 19892，健康检查 3/3 consecutive |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（276 migrations） |
| 后端 service（新 PID） | ✅ 已重启生效 |
| 前端一致性 | ✅ `assets/index-CQRpRhrT.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ 已自动执行 |

## 回滚

- **回滚锚点**: 上一 release `4b9ed7e54`（/opt/xiyu-bid/releases/4b9ed7e54 仍存在，后端 jar 可重新构建部署）
- **DB 备份**: 已由 remote-deploy.sh 自动执行（本次无 schema 变更，备份留作保险）
- **回滚状态**: 就绪（未执行，行为正常无需回滚）

## 经验沉淀应用

- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- COPYFILE_DISABLE=1 防 macOS `._*` 残留 ✅
- --noproxy 绕过 Mac HTTP_PROXY 502 ✅
- SYSTEMCTL_SUDO=true ✅（第 15 次事故防复发）
- `/tmp/migration-mysql/` 过时补齐 ✅（经验 #10，本次补传 V1216 后 validate 通过）
- 无 sentry-logback SentryAppender 手动声明 ✅（第 34 次事故已修复）

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15 次决定保留），非本次引入，保留。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- 本次无数据库迁移，schema 风险低。
- 核心业务改动为 scoreparse 强制 scoped scoring integrity（!2736/!2737）与 5 个既有测试失败修复，建议回归评分解析完整性场景。
- **feature 067 为较大前端重构**（8 个隐藏功能剔除 + 孤立死代码清理），建议重点回归：菜单/功能可见性、导出相关页面、涉及隐藏功能的路由和权限。

## 部署确认清单

- [x] 环境门禁（用户确认测试环境 172.16.38.78）
- [x] 早操三连通过（锚点分支 ff-only 同步至 origin/main）
- [x] 基线 = origin/main
- [x] Flyway 预检通过（补传 V1216 修复 /tmp 目录过时，无新增迁移）
- [x] 打包 + 产物校验通过
- [x] 部署成功（health + smoke + 前端一致性）
- [x] 部署报告生成