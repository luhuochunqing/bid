# 第 163 次部署报告（test 环境）

**日期**：2026-09-06
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`bc0e2dc47`
**部署时间**：22:37 - 22:42 CST
**状态**：✅ 成功

## 部署概览

- 增量 5 个 commit，**含 1 个新增 Flyway 迁移**（DB V1211 → V1212）。
- 本次核心为 **compliance 标书质检结果详情扩容**（PR !2582）：
  - **PR !2582**：扩容 `check_details` 为 `MEDIUMTEXT`（迁移 V1212），并增强前端失败卡片兜底（避免长质检详情被 TEXT 截断导致显示异常）。
  - 其余为 docs（wiki 回填 + 第 161/162 次部署报告）。
- Flyway 预检通过（272 迁移全量校验 checksum 全匹配），V1212 已成功应用。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `bc0e2dc47`（origin/main，锚点分支 ff-only 同步，零差异）          |
| 前次部署      | `a698cc79f`（2026-09-06 第 162 次部署）                      |
| 增量 commit | 5 个（`a698cc79f..bc0e2dc47`）                           |
| 新增迁移      | V1212（expand compliance check details to mediumtext）   |
| 工作区状态     | 干净                                                 |
| 早操三连      | ff-only 同步至 `bc0e2dc47`，git 包装器激活                    |

## 改动范围（5 个增量 commit）

| commit | 文件 | 描述 |
| ----- | --- | --- |
| `016032f1f` / `bc0e2dc47` | backend compliance 实体 + V1212 迁移 + U1212 回滚 + 前端采问答组件 | fix(compliance): 扩容 check_details 为 MEDIUMTEXT 并增强前端失败卡片兜底 |
| `58e685014` | docs(wiki) | 回填质检结果详情 MEDIUMTEXT 扩容与前端错误卡片知识 |
| `bf609f680` | docs(release) | 第 161+162 次测试环境部署报告 |

关键代码：`ComplianceCheckResult.java`（`check_details` 字段扩容为 MEDIUMTEXT）、`QualityCheckDrawer.vue` + `useQualityCheckDrawer.js`（失败卡片兜底渲染）、迁移 `V1212__expand_compliance_check_details_to_mediumtext.sql`。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，272 migrations all checksums match。

**Step 2**：DB 最新 applied = V1211（`success=1`，2026-09-05 16:13 已应用），源码最高版本 = V1212 → **新增 1 个迁移待应用（V1212）**。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

部署后验证：V1212 已应用（`success=1`，2026-09-06 22:41:24），description = `expand compliance check details to mediumtext`。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 22:37    | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）+ 早操三连（sync-env ff-only + git 包装器激活）       |
| 22:37    | 服务器现状检查（a698cc79f UP，obsEnabled=true）；Flyway 预检 validate 通过（272 迁移）                  |
| 22:38    | DB 版本确认 V1211 = 已应用，源码新增 V1212；本地打包：mvn clean package + 前端 vite build                |
| 22:40    | 产物校验：release-metadata.json obsEnabled=true, apiBaseUrl="" 同源；jar 内 272 迁移含 V1212 无重复       |
| 22:41    | 上传 archive + remote-deploy.sh（SYSTEMCTL_SUDO=true, DB 备份自动执行）→ 前端激活 → 原子更新 jar → 重启服务   |
| 22:41    | 健康检查通过（144 次尝试，Kafka SDK 启动延迟属已知行为）；V1212 迁移应用成功；保留上一版本 a698cc79f assets    |
| 22:42    | Smoke 测试全通过（health/readiness 200, login 400, projects 403, crm 401, 前端 200）；配置检查             |

## 验证结果

| 检查项            | 结果          |
| --------------- | ----------- |
| 后端健康 (/actuator/health) | ✅ 200 UP   |
| 后端就绪 (/actuator/health/readiness) | ✅ 200 UP |
| 登录接口 (/api/auth/login, 空密码) | ✅ 400（验证正常） |
| 项目列表 (/api/projects) | ✅ 403（需认证） |
| CRM 健康 (/api/integration/crm/health) | ✅ 401（需认证） |
| 前端首页 (/)          | ✅ 200      |
| 前端登录 (/login)     | ✅ 200      |
| 前端入口 index        | ✅ index-eHTpZQ3L.js 与 release 一致 |
| OBS 直传启用           | ✅ obsEnabled=true, .upload( 调用数=2 |
| Flyway 迁移          | ✅ V1212 已应用 success=1 |
| 上一版本 assets 保留     | ✅ 已 cp -rn a698cc79f assets（193→277 个） |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留） |

## 回滚信息

- **回滚锚点**：前一版本 `a698cc79f`（/opt/xiyu-bid/releases/a698cc79f/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/winbid-bc0e2dc47-*.sql.gz`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/a698cc79f/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/a698cc79f/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：V1212 为 MEDIUMTEXT 扩容（非破坏性），回滚脚本 `U1212__expand_compliance_check_details_to_mediumtext.sql` 已随部署上传，可在需回退时执行

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（Step 1 validate → Step 2 DB 版本对比 → Step 3 remote-deploy 内置）
- ✅ V1212 rollback 脚本（U1212 前缀）随迁移配套，符合回滚脚本命名规范
- ✅ Kafka SDK readiness 延迟（144 次健康检查尝试，未急于回滚）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验）
- ✅ 前端 hash 资源跨版本 404 防护（部署后从上一版本保留 assets，193→277 个）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）

## 风险提示

1. **V1212 MEDIUMTEXT 扩容**：`check_details` 列类型从 TEXT 扩为 MEDIUMTEXT（非破坏性），历史数据兼容，无需数据回填。
2. **compliance 前端失败卡片**：本次增强兜底渲染（qualityCheckDrawer），建议在测试环境打开历史质检结果验证长详情正常展示。
3. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受，生产部署前需确认。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操三连（sync-env + check-git-wrapper）
- [x] 基线确认（HEAD = origin/main = bc0e2dc47）
- [x] 服务器现状检查（a698cc79f UP）
- [x] Flyway 预检 3 步法（validate + DB 版本对比 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] 迁移应用验证（V1212 success=1）
- [x] Smoke 测试（health/readiness/login/projects/crm/前端）
- [x] 上一版本 assets 保留
- [x] 临时调试配置检查
- [x] 部署报告生成