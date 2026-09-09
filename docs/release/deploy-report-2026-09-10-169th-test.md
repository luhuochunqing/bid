# 第 169 次部署报告（test 环境）

**日期**：2026-09-10
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`452dae98b`
**部署时间**：06:58 - 07:03 CST
**状态**：✅ 成功

## 部署概览

- 增量 2 组 commit（去重后），**无新增 Flyway 迁移**。
- 本次为**标讯事件推送链路修复：SDK 注册地址与续约失效**（PR !2601）：
  - PR !2601：**fix(tenderevent): 修复 SDK 注册地址与续约失效**（`a1553c08a`）+ merge commit（`452dae98b`）
  - PR !2600：第 168 次测试环境部署报告 docs（`db4c73bbd` / `be541072d`）
- 核心变更：`TenderEventRegistrar` 重构 SDK 注册/续约逻辑（132 行变更），`TenderEventSdkConfig` / `TenderEventSdkProperties` 调整，`application.yml` 与 `.env.example` 新增配置项。
- 涉及 `tenderevent` 集成包 3 个源文件 + 2 个测试文件 + 配置文件，无 Flyway 迁移文件变更。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `452dae98b`（origin/main，早操同步后本地 HEAD，零差异）       |
| 前次部署      | `4c83328e5`（2026-09-09 第 168 次部署）                   |
| 增量 commit | `4c83328e5..452dae98b`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 干净（`agent/trae-init` 锚点分支，HEAD = origin/main）     |
| 早操三连      | sync-env 确认 HEAD = origin/main (`452dae98b`) + wrapper 自检通过 |

## 增量 commit 变更

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `db4c73bbd` | PR !2600 | docs(release): 第 168 次测试环境部署报告 |
| `be541072d` | merge | auto-merge PR !2600 |
| `a1553c08a` | PR !2601 | fix(tenderevent): 修复 SDK 注册地址与续约失效 |
| `452dae98b` | merge | auto-merge PR !2601 |

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，273 migrations all checksums match。

**Step 2**：本次增量 `4c83328e5..452dae98b` **无迁移文件变更**（`git diff --name-only` 为空），DB 已应用最新 V1212 无 failed。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

部署后 DB 状态不变，仍为最新版本，无需新迁移验证。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 06:58    | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）                                           |
| 06:58    | 早操三连 + 基线确认（HEAD = origin/main = `452dae98b`，工作区干净）                              |
| 06:58    | 服务器现状检查（前次 4c83328e5 UP，health UP）；增量 2 组 commit；迁移变更 = 无；Flyway 预检 validate 通过（273 迁移） |
| 07:01    | 本地打包：mvn clean package（BUILD SUCCESS，40s）+ sidecar + 前端（RELEASE_ID=452dae98b + VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1） |
| 07:01    | 产物校验：release-metadata.json obsEnabled=true, apiBaseUrl="" 同源；Detail chunk .upload( 调用数=2；jar 内迁移无重复 |
| 07:02    | 上传 archive（288MB）+ remote-deploy.sh（SYSTEMCTL_SUDO=true）→ DB 备份 → Flyway validate → 前端激活 → 原子更新 jar → 重启服务 |
| 07:02    | 健康检查通过（80 次尝试，无 Kafka 延迟）；前端一致性验证 src="/assets/index-DyOixBsg.js" |
| 07:03    | Smoke 测试全通过（health/readiness 200, login 400, projects 403, crm/health 401, 前端 / 与 /login 200） |

## 验证结果

| 检查项            | 结果          |
| --------------- | ----------- |
| 后端健康 (/actuator/health) | ✅ 200 UP（含 readiness UP） |
| 后端就绪 (/actuator/health/readiness) | ✅ 200 UP |
| 登录接口 (/api/auth/login, 空密码) | ✅ 400（验证正常） |
| 项目列表 (/api/projects) | ✅ 403（需认证） |
| CRM 集成健康 (/api/integration/crm/health) | ✅ 401（需认证） |
| 前端首页 (/)          | ✅ 200      |
| 前端登录页 (/login)    | ✅ 200      |
| 前端入口 index        | ✅ index-DyOixBsg.js 与 release 一致 |
| OBS 直传启用           | ✅ obsEnabled=true（release-metadata 确认）, .upload( 调用数=2 |
| Flyway 迁移          | ✅ 无新增迁移，273 迁移 validate 通过 |
| DB 备份              | ✅ winbid-452dae98b-*.sql.gz 已生成 |
| 后端错误日志           | ✅ 无 ERROR 日志（仅 Smoke 触发的预期 WARN） |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留） |

## 回滚信息

- **回滚锚点**：前一版本 `4c83328e5`（/opt/xiyu-bid/releases/4c83328e5/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/winbid-452dae98b-*.sql.gz`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/4c83328e5/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/4c83328e5/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：本次无迁移变更，无需 DB 回滚

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（validate + DB 版本对比确认无迁移 + remote-deploy 内置）
- ✅ Kafka SDK readiness 延迟（本次无延迟，健康检查第 80 次尝试即通过）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验 + release-metadata 二次确认）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'，本次无复发）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）
- ✅ 前端 hash 资源跨版本 404 防护（部署后保留旧 assets）

## 风险提示

1. **SDK 注册/续约变更**：本次为 SDK 注册地址与续约失效修复（PR !2601）。建议在测试环境实地触发一条标讯事件推送，确认注册与续约正常（检查事件网关返回 code=00000 / result=true）。
2. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受（历史多次决定保留）。
3. **无新增迁移**：本次零 schema 变更，Flyway 风险最低。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操三连 + 基线确认（HEAD = origin/main = 452dae98b，工作区干净）
- [x] 服务器现状检查（4c83328e5 UP）
- [x] Flyway 预检 3 步法（validate + 无迁移 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true + index 一致性）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] Smoke 测试（health/readiness/login/projects/crm/前端）
- [x] DB 备份 + 配置文件检查
- [x] 部署报告生成
