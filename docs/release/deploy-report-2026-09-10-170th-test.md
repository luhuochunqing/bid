# 第 170 次部署报告（test 环境）

**日期**：2026-09-10
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`6b72aeba6`
**部署时间**：08:00 - 08:18 CST
**状态**：✅ 成功

## 部署概览

- 增量 4 组 commit（去重后），**无新增 Flyway 迁移**。
- 本次为**标讯事件服务在应用就绪后注册**（PR !2603）：
  - PR !2603：**fix(tenderevent): 应用就绪后注册事件服务**（`d384277a4`）+ merge commit（`6b72aeba6`）
  - PR !2602：第 169 次测试环境部署报告 docs（`96bc9d4ca` / `7e4d29d60`）
- 核心变更：`TenderEventRegistrar`（应用就绪后再注册事件服务）+ `TenderEventSdkConfig` 调整 + 测试同步。涉及 `tenderevent` 集成包 3 个源文件 + 1 个测试文件 + 配置。
- 无 Flyway 迁移文件变更。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `6b72aeba6`（origin/main，早操同步后本地 HEAD，零差异）       |
| 前次部署      | `452dae98b`（2026-09-10 第 169 次部署）                   |
| 增量 commit | `452dae98b..6b72aeba6`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | `agent/trae/deploy-test-170` 临时任务 worktree，HEAD = origin/main |
| 早操三连      | ff-only 同步到 origin/main (`6b72aeba6`) + wrapper 自检通过 |

## 增量 commit 变更

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `96bc9d4ca` | PR !2602 | docs(release): 第 169 次测试环境部署报告 |
| `7e4d29d60` | merge | auto-merge PR !2602 |
| `d384277a4` | PR !2603 | fix(tenderevent): 应用就绪后注册事件服务 |
| `6b72aeba6` | merge | auto-merge PR !2603 |

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，273 migrations all checksums match。

**Step 2**：本次增量 `452dae98b..6b72aeba6` **无迁移文件变更**（`git diff --name-only` 为空），DB 已应用最新 V1212 无 failed。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

部署后 DB 状态不变，仍为最新版本，无需新迁移验证。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 08:00    | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）                                           |
| 08:00    | 早操三连 + 基线确认（锚点分支 ff-only 同步到 origin/main = `6b72aeba6`）                        |
| 08:00    | 服务器现状检查（前次 452dae98b UP，health 200）；增量 4 组 commit；迁移变更 = 无；Flyway 预检 validate 通过（273 迁移） |
| 08:01    | ⚠️ 首次打包失败：Docker 未就绪（disk/crash）导致容器测试门禁失败                                  |
| 08:03    | ⚠️ 二次打包失败：Docker daemon 未启动（`FILE_ERROR_NO_SPACE`，数据盘仅剩 5.7G / 99%）             |
| 08:05    | 清理 `.release` 历史产物（tar.gz + 子目录，13G → 597M），数据盘腾出至 18G 可用                    |
| 08:15    | Docker 重启就绪（Server 29.1.2，mysql:8.0 镜像存在）                                          |
| 08:16    | 本地打包成功：容器测试门禁通过 + jar 迁移无重复 + 前端构建 + sidecar（RELEASE_ID=6b72aeba6 + VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1） |
| 08:17    | 产物校验：release-metadata.json obsEnabled=true, apiBaseUrl="" 同源；Detail chunk .upload( 调用数=2 |
| 08:17    | 上传 archive（288MB）+ remote-deploy.sh（SYSTEMCTL_SUDO=true）→ DB 备份 → Flyway validate → 前端激活 → 原子更新 jar → 重启服务 |
| 08:18    | 健康检查通过（79 次尝试，无 Kafka 延迟）；前端一致性验证 src="/assets/index-DyOixBsg.js" |
| 08:18    | 保留上一版本 452dae98b 旧 assets（193 个文件，防跨版本 404）                                    |
| 08:18    | Smoke 测试全通过（health/readiness 200, login 400, projects 403, crm/health 401, 前端 / 与 /login 200） |

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
| DB 备份              | ✅ winbid-6b72aeba6-*.sql.gz 已生成 |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留） |

## 回滚信息

- **回滚锚点**：前一版本 `452dae98b`（/opt/xiyu-bid/releases/452dae98b/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/winbid-6b72aeba6-*.sql.gz`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/452dae98b/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/452dae98b/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：本次无迁移变更，无需 DB 回滚

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（validate + DB 版本对比确认无迁移 + remote-deploy 内置）
- ✅ Kafka SDK readiness 延迟（本次无延迟，健康检查第 79 次尝试即通过）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验 + release-metadata 二次确认）
- ✅ **磁盘空间不足处置（新增经验）**：Docker 无法启动（`FILE_ERROR_NO_SPACE`）时，清理 `.release` 历史产物（tar.gz + 子目录）可快速腾出空间；必要时重启 Docker Desktop。本次已沉淀为可复用流程。
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'，本次无复发）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）
- ✅ 前端 hash 资源跨版本 404 防护（部署后从 452dae98b 保留旧 assets）

## 风险提示

1. **事件服务注册时机变更**：本次将事件服务注册从启动期改为**应用就绪后**（PR !2603）。建议在测试环境实地触发一条标讯事件推送，确认事件服务能正常注册（SDK 注册/续约在 READY 后恢复，避免 Kafka SDK 初始化阻塞主线程）。
2. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受（历史多次决定保留）。
3. **磁盘空间治理**：本次因数据盘 99% 触发 Docker 无法启动。建议后续部署前先检查 `df -h /System/Volumes/Data`，及时清理 `.release` 历史产物，避免部署被打断。
4. **无新增迁移**：本次零 schema 变更，Flyway 风险最低。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操三连 + 基线确认（HEAD = origin/main = 6b72aeba6，工作区干净）
- [x] 服务器现状检查（452dae98b UP）
- [x] Flyway 预检 3 步法（validate + 无迁移 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true + index 一致性）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] Smoke 测试（health/readiness/login/projects/crm/前端）
- [x] 保留上一版本 assets（防跨版本 404）
- [x] DB 备份 + 配置文件检查
- [x] 部署报告生成