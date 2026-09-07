# 第 164 次部署报告（test 环境）

**日期**：2026-09-07
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`a234171b6`
**部署时间**：11:45 - 11:53 CST
**状态**：✅ 成功

## 部署概览

- 增量 2 个 commit，**无新增 Flyway 迁移**。
- 本次核心为 **scoreparse 项目 1371 评分项解析精度根治**（PR !2586）：
  - `ede8d8ff7`：根治项目 1371 评分项解析精度、Sidecar 发布闭环与跨行分值重叠消重（`StructuredTableCandidateExtractor` 重构 + golden 测试基线 + Sidecar Dockerfile 依赖调整 + 部署脚本增强 `check:frontend-api-base`、远端工具与原资源保留保护）。
  - `a234171b6`：merge PR !2586。
- 无 Flyway 迁移文件变更，主要为后端解析逻辑重构（含 51.7K 行的 golden 测试基线 JSON）。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `a234171b6`（origin/main，锚点分支 ff-only 同步，零差异）          |
| 前次部署      | `110cf4b1b`（2026-09-06 第 163 次部署）                      |
| 增量 commit | 2 个（`110cf4b1b..a234171b6`）                           |
| 新增迁移      | 无                                                    |
| 工作区状态     | 干净                                                 |
| 早操三连      | ff-only 同步至 `a234171b6`，git 包装器激活                    |

## 改动范围（2 个增量 commit）

| commit | 文件 | 描述 |
| ----- | --- | --- |
| `ede8d8ff7` | `StructuredTableCandidateExtractor.java` + Test + golden JSON 基线 + sidecar Dockerfile(.cn) + 部署脚本 + docs | fix(scoreparse): 根治项目 1371 评分项解析精度、Sidecar 发布闭环与跨行分值重叠消重 |
| `a234171b6` | merge commit | auto-merge by gitee-pr-helper.sh PR !2586 |

关键代码：`StructuredTableCandidateExtractor.java`（公司名/评分项解析逻辑重构），`StructuredTableCandidateExtractorTest.java` + `1371_tables_v1.json`（51.7K 行 golden 基线，保障重构后解析行为回归）。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，273 migrations all checksums match。

**Step 2**：本次增量 `110cf4b1b..a234171b6` **无迁移文件变更**（`git diff --name-only` 为空）。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

部署后无新迁移需验证（DB 状态保持不变，273 迁移与本次源码基线一致）。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 11:45    | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）+ 服务器连通性探测（winbid-01 / jetty）             |
| 11:46    | 早操基线同步（extended into agent/trae-init ff-only 同步至 a234171b6，git 状态干净）                    |
| 11:46    | 服务器现状检查（110cf4b1b UP）；增量 2 commit；迁移文件变更 = 无；Flyway 预检 validate 通过（273 迁移）      |
| 11:50    | 本地打包：前端 vite build + mvn clean package + sidecar 打包                                        |
| 11:52    | 产物校验：release-metadata.json obsEnabled=true, apiBaseUrl="" 同源；jar 内迁移无重复；sidecar SHA-256 记录  |
| 11:52    | 上传 archive + remote-deploy.sh（SYSTEMCTL_SUDO=true, DB 备份自动执行）→ 前端激活 → 原子更新 jar → 重启服务   |
| 11:53    | 健康检查通过（145 次尝试，Kafka SDK 启动延迟属已知行为）；保留上一版本 110cf4b1b-api8080 assets              |
| 11:54    | Smoke 测试全通过（health/readiness 200, login 400, projects 403, crm 401, 前端 200）；配置检查             |

## 验证结果

| 检查项            | 结果          |
| --------------- | ----------- |
| 后端健康 (/actuator/health) | ✅ 200 UP   |
| 后端就绪 (/actuator/health/readiness) | ✅ 200 UP |
| 登录接口 (/api/auth/login, 空密码) | ✅ 400（验证正常） |
| 项目列表 (/api/projects) | ✅ 403（需认证） |
| CRM 健康 (/api/integration/crm/health) | ✅ 401（需认证） |
| 前端首页 (/)          | ✅ 200      |
| 前端入口 index        | ✅ index-B2-dlnwQ.js 与 release 一致 |
| OBS 直传启用           | ✅ obsEnabled=true, .upload( 调用数=2 |
| Flyway 迁移          | ✅ 无新增迁移，273 迁移 validate 通过 |
| 上一版本 assets 保留     | ✅ 已 cp -rn 110cf4b1b-api8080 assets |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留） |

## 回滚信息

- **回滚锚点**：前一版本 `110cf4b1b`（/opt/xiyu-bid/releases/110cf4b1b-api8080/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/winbid-a234171b6-*.sql.gz`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/110cf4b1b-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/110cf4b1b-api8080/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：本次无迁移变更，无需 DB 回滚

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（validate + DB 版本对比确认无迁移 + remote-deploy 内置）
- ✅ Kafka SDK readiness 延迟（145 次健康检查尝试，未急于回滚）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验）
- ✅ 前端 hash 资源跨版本 404 防护（部署后从上一版本 110cf4b1b-api8080 保留 assets）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）
- ✅ macOS `._*` 残留清理（COPYFILE_DISABLE=1 + 服务器 find -delete）

## 风险提示

1. **scoreparse 解析逻辑重构**：本次对 `StructuredTableCandidateExtractor` 做了较大重构（跨行分值重叠消重等），虽配套 51.7K 行 golden 基线回归测试，但建议在测试环境对项目 1371 的历史评分项重新解析，验证跨行分值消重与公司名解析结果符合预期。
2. **Sidecar release 闭环**：sidecar Dockerfile 有依赖调整（.cn 变体），若生产后续使用需确认对应镜像构建与发布链路一致。
3. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受。
4. **部署脚本增强**：`remote-deploy.sh` 新增原资源保留保护（防止跨版本 404），已在本次生效验证。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操基线同步 + git 包装器检查
- [x] 基线确认（HEAD = origin/main = a234171b6）
- [x] 服务器现状检查（110cf4b1b UP）
- [x] Flyway 预检 3 步法（validate + 无迁移 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true + sidecar SHA 校验）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] Smoke 测试（health/readiness/login/projects/crm/前端）
- [x] 上一版本 assets 保留
- [x] 临时调试配置检查
- [x] 部署报告生成