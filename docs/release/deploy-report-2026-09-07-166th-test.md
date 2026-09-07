# 第 166 次部署报告（test 环境）

**日期**：2026-09-07
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`ade7739df`
**部署时间**：22:26 - 22:32 CST
**状态**：✅ 成功

## 部署概览

- 增量 3 个 commit（去重后），**无新增 Flyway 迁移**。
- 本次为 scoreparse（评分解析模块）的**重构收口 + 缺陷修复**：
  - `ef1fa73c5`（PR !2592 gemini）：**fix(scoreparse): 修复多表候选消重、报价细则去重与超载保护继承 (1359)**
  - `b8129daa9`（PR !2593 gemini）：**refactor(scoreparse): 去除冗余样板代码、提取共享正则并收敛重复构造** + wiki 记录
- 涉及 `ScoreItemMergePolicy`、`StructuredTableCandidateExtractor`、`ScoreMergeResultGuard`、`ScoreGlobalMergeOrchestrator` 等核心解析逻辑，含配套单测扩充。
- 无 Flyway 迁移文件变更，主要为评分解析算法修正与代码治理。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `ade7739df`（origin/main，基准仓库 main 分支同步，零差异）       |
| 前次部署      | `56a522a1a`（2026-09-07 第 165 次部署）                   |
| 增量 commit | `56a522a1a..ade7739df`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 干净                                                 |
| 早操三连      | 部署 worktree `agent/trae/deploy-166-test` 基于 origin/main 创建，git 干净 |

## 增量 commit 变更

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `ef1fa73c5` | PR !2592 (gemini) | fix(scoreparse): 修复多表候选消重、报价细则去重与超载保护继承 (1359) |
| `d5fa905c2` | merge | auto-merge PR !2592 |
| `9c8a4d67c` | PR !2592 (gemini) | docs(wiki): 回填 1359 评分解析跨通道消重与超载保护优化至知识库 |
| `b8129daa9` | PR !2593 (gemini) | refactor(scoreparse): 去除冗余样板代码、提取共享正则并收敛重复构造 |
| `686e5d10d` | PR !2593 (gemini) | docs(wiki): 记录 1359 后续代码去重与预编译正则收敛优化 |
| `ade7739df` | merge | auto-merge PR !2593 |

> 注：`93fe4680c` / `44c54944c`（第 165 次部署报告 docs）亦在增量范围，其内容为上一部署报告提交。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，273 migrations all checksums match。

**Step 2**：本次增量 `56a522a1a..ade7739df` **无迁移文件变更**（`git diff --name-only` 为空）。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

部署后 DB 状态不变，仍为 V1212 最新，无需新迁移验证。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 22:22    | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）；创建部署 worktree `agent/trae/deploy-166-test` 基于 origin/main (`ade7739df`) |
| 22:22    | 服务器现状检查（前次 56a522a1a UP）；增量 3 commit；迁移文件变更 = 无；Flyway 预检 validate 通过（273 迁移，DB 最新 V1212） |
| 22:24    | 本地打包：前端 vite build + 容器测试门禁（FlywayMysqlContainerTest 通过）+ mvn clean package + sidecar 打包（VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1） |
| 22:26    | 产物校验：release-metadata.json obsEnabled=true, apiBaseUrl="" 同源；Detail chunk .upload( 调用数=2；jar 内 273 迁移无重复；sidecar SHA256 正确 |
| 22:28    | 上传 archive + remote-deploy.sh（SYSTEMCTL_SUDO=true）→ DB 备份 → Flyway validate → Sidecar 升级 → 前端激活 → 原子更新 jar → 重启服务 |
| 22:28    | 健康检查通过（144 次尝试，Kafka SDK 启动延迟属已知行为）；部署记录写入已确认（activatedAt 14:28:46Z） |
| 22:30    | Smoke 测试全通过（health/readiness 200, login 400, projects 403, 前端 / 与 /login 200）；配置检查；前端 index `B2-dlnwQ.js` 与 release 一致 |
| 22:31    | 清理 incoming 旧归档（保留当前 release，18→1，释放约 3GB）    |

## 验证结果

| 检查项            | 结果          |
| --------------- | ----------- |
| 后端健康 (/actuator/health) | ✅ 200 UP（含 readiness UP） |
| 后端就绪 (/actuator/health/readiness) | ✅ 200 UP |
| 登录接口 (/api/auth/login, 空密码) | ✅ 400（验证正常） |
| 项目列表 (/api/projects) | ✅ 403（需认证） |
| 前端首页 (/)          | ✅ 200      |
| 前端登录页 (/login)    | ✅ 200      |
| 前端入口 index        | ✅ index-B2-dlnwQ.js 与 release 一致 |
| OBS 直传启用           | ✅ obsEnabled=true（部署记录确认）, .upload( 调用数=2 |
| Flyway 迁移          | ✅ 无新增迁移，273 迁移 validate 通过 |
| DB 备份              | ✅ winbid-ade7739df-20260907222835.sql.gz (13MB) |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留） |
| 容器测试门禁           | ✅ FlywayMysqlContainerTest Spring 全量上下文装配正常 |

## 回滚信息

- **回滚锚点**：前一版本 `56a522a1a`（/opt/xiyu-bid/releases/56a522a1a/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/winbid-ade7739df-20260907222835.sql.gz`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/56a522a1a/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/56a522a1a/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：本次无迁移变更，无需 DB 回滚

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（validate + DB 版本对比确认无迁移 + remote-deploy 内置）
- ✅ Kafka SDK readiness 延迟（144 次健康检查尝试，未急于回滚）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验 + 部署记录二次确认）
- ✅ 容器测试门禁（FlywayMysqlContainerTest，本次含被重构 Bean 依赖，验证装配无误）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'，本次无复发）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）
- ✅ 前端 hash 资源跨版本 404 防护（remote-deploy.sh 内置原资源保留）

## 风险提示

1. **scoreparse 核心解析重构**：本次对 `ScoreItemMergePolicy` / `StructuredTableCandidateExtractor` 等核心逻辑做了消重与重构（PR !2592/!2593）。建议在测试环境用历史标书抽查多表候选消重、报价细则去重与超载保护继承结果符合预期，特别关注不同竞品报价行数与评分表结构的场景。
2. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受（历史多次决定保留）。
3. **无新增迁移**：本次零 schema 变更，Flyway 风险最低。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 部署 worktree 基于 origin/main 创建（基线 = ade7739df）
- [x] 基线确认（HEAD = origin/main = ade7739df）
- [x] 服务器现状检查（56a522a1a UP）
- [x] Flyway 预检 3 步法（validate + 无迁移 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1, 容器测试门禁）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true + index 一致性）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] Smoke 测试（health/readiness/login/projects/前端）
- [x] DB 备份 + 配置文件检查
- [x] 旧归档清理（保留当前 release）
- [x] 部署报告生成
