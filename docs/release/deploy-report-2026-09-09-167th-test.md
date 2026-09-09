# 第 167 次部署报告（test 环境）

**日期**：2026-09-09
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`0c9be61c6`
**部署时间**：18:31 - 18:38 CST
**状态**：✅ 成功

## 部署概览

- 增量 3 组 commit（去重后），**无新增 Flyway 迁移**。
- 本次为**标讯事件推送链路修复 + 项目/招标文档状态守卫**增强：
  - PR !2595：**fix(tenderevent): traceId/spanId 改为纯 18 位数字，修复标讯事件推送被拒**（149 提交 `0819102d0`）+ wiki 更正
  - PR !2596（gemini）：**fix(tenderDoc): 404 修复 + 状态守卫**，含级联清理孤儿招标文件、状态防倒退校验、删除 404 容错（`ea9811ac6`）；**fix(project): 规范 429 错误提示**（`42d13358b`）+ 教训沉淀 wiki（`3232c9fb2`）
  - PR !2594：第 166 次测试环境部署报告 docs（`6cad54648` / `3314f32e3`）
- 涉及 `TenderEventRegistrar`（traceId/spanId 格式）、孤儿招标文件清理与状态防倒退逻辑等。
- 无 Flyway 迁移文件变更。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `0c9be61c6`（origin/main，早操同步后本地 HEAD，零差异）       |
| 前次部署      | `ade7739df`（2026-09-07 第 166 次部署）                   |
| 增量 commit | `ade7739df..0c9be61c6`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 干净（`review/qoder/traepid`，HEAD = origin/main）        |
| 早操三连      | sync-env.sh 同步到 origin/main (`0c9be61c6`) + wrapper 自检通过 |

## 增量 commit 变更

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `3314f32e3` | PR !2594 | docs(release): 第 166 次测试环境部署报告 |
| `6cad54648` | merge | auto-merge PR !2594 |
| `0819102d0` | PR !2595 | fix(tenderevent): traceId/spanId 改为纯 18 位数字 |
| `b2edffee6` | PR !2595 | docs(wiki): 更正标讯事件链路 ID 为 18 位纯数字，回填 result=false 根因 |
| `879f1974d` | merge | auto-merge PR !2595 |
| `ea9811ac6` | PR !2596 | fix(project): 级联清理孤儿招标文件 + 状态防倒退校验 + 删除 404 容错 |
| `3232c9fb2` | PR !2596 | docs: 沉淀项目 1364 孤儿招标文件死锁与状态篡改防御教训 |
| `42d13358b` | PR !2596 | fix(project): 规范 429 错误提示 + 回填 frontend-pitfalls wiki |
| `0c9be61c6` | merge | auto-merge PR !2596 |

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，273 migrations all checksums match。

**Step 2**：本次增量 `ade7739df..0c9be61c6` **无迁移文件变更**（`git diff --name-only` 为空）。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

部署后 DB 状态不变，仍为最新版本，无需新迁移验证。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 18:31    | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）                                           |
| 18:32    | 早操三连：sync-env.sh 同步到 origin/main (`0c9be61c6`) + wrapper 自检；基线确认（HEAD = origin/main） |
| 18:32    | 服务器现状检查（前次 ade7739df UP，health 200 / readiness 200）；增量 3 组 commit；迁移变更 = 无；Flyway 预检 validate 通过（273 迁移） |
| 18:35    | 本地打包：mvn clean package（BUILD SUCCESS，30s）+ sidecar + 前端（RELEASE_ID=0c9be61c6 + VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1） |
| 18:35    | 产物校验：release-metadata.json obsEnabled=true, apiBaseUrl="" 同源；Detail chunk .upload( 调用数=2；jar 内 273 迁移无重复 |
| 18:36    | 上传 archive + remote-deploy.sh（SYSTEMCTL_SUDO=true）→ DB 备份 → Flyway validate → 前端激活 → 原子更新 jar → 重启服务 |
| 18:37    | 健康检查通过（144 次尝试，Kafka SDK 启动延迟属已知行为）；前端一致性验证 src="/assets/index-DyOixBsg.js" |
| 18:37    | 前端资源保留：从上一版本 `ade7739df` cp -rn 旧 assets（193 文件）到 /srv/www/xiyu-bid/assets/ 避免跨版本 404 |
| 18:38    | Smoke 测试全通过（health/readiness 200, login 400, projects 403, crm/health 401, 前端 / 与 /login 200） |

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
| DB 备份              | ✅ winbid-0c9be61c6-20260909183650.sql.gz (13MB) |
| 前端旧 assets 保留      | ✅ 从 ade7739df 保留 193 个旧 hash 资源，避免跨版本 404 |
| 后端错误日志           | ✅ 无 ERROR 日志 |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留） |

## 回滚信息

- **回滚锚点**：前一版本 `ade7739df`（/opt/xiyu-bid/releases/ade7739df/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/winbid-0c9be61c6-20260909183650.sql.gz`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/ade7739df/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/ade7739df/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：本次无迁移变更，无需 DB 回滚

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（validate + DB 版本对比确认无迁移 + remote-deploy 内置）
- ✅ Kafka SDK readiness 延迟（144 次健康检查尝试，未急于回滚）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验 + release-metadata 二次确认）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'，本次无复发）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）
- ✅ 前端 hash 资源跨版本 404 防护（部署后从上一版本 release 目录 `cp -rn` 旧 assets，193 文件）
- ✅ 部署记录 releaseDir JSON 空格陷阱：技能保留命令的 grep 因标准 JSON 带空格取不到 releaseDir，已改为直接从上一版本 release 目录保留 assets

## 风险提示

1. **标讯事件推送格式变更**：本次将 traceId/spanId 改为纯 18 位数字（PR !2595），修复事件推送被拒。建议在测试环境实地发一条标讯事件，确认推送成功（检查 event bus 网关返回 code=00000 / result=true）。
2. **孤儿招标文件状态守卫**：新增级联清理 + 状态防倒退校验（PR !2596），涉及项目删除/招标文件生命周期。建议抽查历史 1364 类孤儿招标文件场景不再死锁，状态不再被篡改。
3. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受（历史多次决定保留）。
4. **无新增迁移**：本次零 schema 变更，Flyway 风险最低。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操三连（sync-env 同步 origin/main = 0c9be61c6 + wrapper 自检）
- [x] 基线确认（HEAD = origin/main = 0c9be61c6，工作区干净）
- [x] 服务器现状检查（ade7739df UP）
- [x] Flyway 预检 3 步法（validate + 无迁移 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true + index 一致性）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] 前端旧 assets 保留（193 文件）
- [x] Smoke 测试（health/readiness/login/projects/crm/前端）
- [x] DB 备份 + 配置文件检查
- [x] 部署报告生成