# 第 161 次部署报告（test 环境）

**日期**：2026-09-05
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`4bc19117c`
**部署时间**：16:07 - 16:19 CST
**状态**：✅ 成功

## 部署概览

- 增量约 26 个 commit（7 条实质 PR + docs 回填 + auto-merge），**新增 1 个 Flyway 迁移 V1211**（DB 从 V1210 升至 V1211）。

- 本次核心为**历史案例精准推荐（spec061）+ scoreparse 多项修复 + casework 下载权限闸门**：

  - **PR !2551**：落地基于真实数据底座的历史案例精准推荐核心流水线与匹配加固（CaseSubTopicPolicy、ScoreItemCaseMatchPolicy、KnowledgeCase 新增 subTopic 字段、V1211 迁移）

  - **PR !2547 / !2549 / !2550**：scoreparse 项目 1359 AI 评分标准解析修复（编号乱码、权重、缺项乱序、业绩仓储满足状态、四列契约、isCredit 救援收窄）

  - **PR !2545**：标书原文下载禁止投标项目负责人角色（前后端双重闸门）

- Flyway 预检及迁移验证全部通过（271 项迁移全量校验，V1211 成功应用）。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `4bc19117c`（origin/main，锚点分支 ff-only 同步，零差异）          |
| 前次部署      | `5a763f29`（2026-09-04 第 160 次部署）                      |
| 增量 commit | \~26 个（`5a763f29..4bc19117c`）                         |
| 新增迁移      | V1211（`add_sub_topic_to_knowledge_case`）              |
| 工作区状态     | 干净                                                    |
| 早操三连      | ff-only 同步至 `4bc19117c`，git 包装器未激活（部署不涉及 commit/push） |

## 改动范围（\~26 个增量 commit / 7 条实质 PR）

| PR    | 描述                                                        |
| ----- | --------------------------------------------------------- |
| !2551 | feat(casework): 落地基于真实数据底座的历史案例精准推荐核心流水线与匹配加固（含 V1211）    |
| !2550 | fix(scoreparse): 收窄 isCredit 救援为失信语义行并拉齐三处口径              |
| !2549 | fix(scoreparse): 修复项目 1359 AI 评分标准解析编号乱码、权重、缺项乱序与业绩仓储满足状态 |
| !2548 | fix(test): 过滤 Element Plus 未解析组件与指令警告并扩展超时配置              |
| !2547 | fix(scoreparse): 1371 四列契约（分类/仓库匹配/顺序/行 id）               |
| !2546 | docs: 规范文档去除 GitHub/远程 CI 误导内容（Gitee 唯一源）                 |
| !2545 | feat(casework): 标书原文下载禁止投标项目负责人角色（前后端双重闸门）                |

关键代码：`casework` 模块历史案例精准推荐流水线（CaseSubTopicPolicy、ScoreItemCaseMatchPolicy、KnowledgeCase.subTopic）、`scoreparse` 模块 1359 评分标准解析修复与四列契约、`casework` 模块标书原文下载权限闸门；另含 wiki 回填与测试修复 commit。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，271 migrations all checksums match。

**Step 2**：DB 最新 applied = V1210（`success=1`，2026-09-03 21:52 已应用），源码最高版本 = V1211 → 本次待应用迁移 V1211。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

**迁移应用结果**：V1211 `success=1`，installed\_on 2026-09-05 16:13:13。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                                             |
| ------ | -------------------------------------------------------------------------------------------------------------- |
| 16:07  | 环境门禁确认（test / 172.16.38.78）+ 早操（锚点分支 ff-only 至 `4bc19117c`）+ 基线零差异确认                                           |
| 16:08  | 服务器现状探测（前次 release `5a763f29`，health 200 UP）+ 增量 commit / 迁移文件 diff（新增 V1211）                                  |
| 16:09  | Flyway 预检：validate 通过（271 migrations）+ DB 版本对比 V1210 → V1211                                                   |
| 16:10  | 本地打包完成（43.4s，\~154M，obsEnabled=true，同源构建）+ jar 迁移无重复 + OBS .upload=2 校验                                        |
| 16:12  | scp 上传归档（155M）到 `/opt/xiyu-bid/incoming/`（首次连接中断后重传成功）                                                         |
| 16:13  | remote-deploy 执行：DB 备份 → Flyway validate → Frontend 激活 → 原子替换 jar → systemctl 重启（16:13:05）                     |
| 16:18  | 脚本内置健康检查通过（consecutive 3/3，total attempts 145 ≈ 5 分钟，Kafka SDK readiness 延迟，service active/running）+ 前端一致性验证通过 |
| 16:19+ | Smoke 全绿 + 配置清理检查 + 迁移应用验证（V1211 success=1）                                                                    |

## 验证结果

**后端健康（Nginx 8080 代理路径）**：`{"status":"UP"}`，readiness 200 UP，db UP，redis UP。健康检查累计轮询 145 次 ≈ 5 分钟（Kafka SDK 启动延迟，已知行为，未超 4 分钟软上限但服务最终自恢复）。

**迁移应用验证**：V1211 `add sub topic to knowledge case` 已成功应用（`success=1`，installed\_on 2026-09-05 16:13:13）。

**API Smoke（经 Nginx 8080，`--noproxy '*'`）**：

| 接口                                 | 期望     | 实际       |
| ---------------------------------- | ------ | -------- |
| `/actuator/health`                 | 200 UP | ✅ 200 UP |
| `/actuator/health/readiness`       | 200    | ✅ 200    |
| `POST /api/auth/login`（空 body）     | 400    | ✅ 400    |
| `/api/projects`（未认证）               | 403    | ✅ 403    |
| `/api/integration/crm/health`（未认证） | 401    | ✅ 401    |

**前端 Smoke（经 Nginx 8080）**：

- 根路径 `/`：200

- 登录页 `/login`：200

- 线上入口：`assets/index-BGPE8eEE.js` 与构建产物完全一致

**产物与特性校验**：

- `release-metadata.json`：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）、`sentryEnabled=false`

- OBS 直传校验：Detail chunk `.upload(` 调用数 = 2

- jar 内含 V1211 迁移文件，无重复版本

- `COPYFILE_DISABLE=1` 生效（防 macOS `._*` 残留）

## 本次异常与处置

**无阻塞性异常。**

- **scp 首次上传连接中断**：归档包首次 scp 时 SSH 连接被远端关闭，重传后成功（155M 完整）。属网络瞬时抖动，非部署脚本问题。

- **Kafka readiness 延迟约 5 分钟**：健康检查累计 145 次轮询（约 5 分钟），超出 4 分钟软上限但服务最终自恢复（Kafka SDK 初始化阻塞主线程，已知行为，见部署经验 #2）。

## 回滚信息

| 锚点         | 位置                                                                                                  |
| ---------- | --------------------------------------------------------------------------------------------------- |
| 前后端制品      | `/opt/xiyu-bid/releases/5a763f29/`（jar + frontend）                                                  |
| DB 备份      | 本次部署自动生成于 `/opt/xiyu-bid/db-backups/winbid-4bc19117c-*.sql.gz`                                      |
| 迁移回滚脚本     | `backend/src/main/resources/db/rollback/migration-mysql/U1211__add_sub_topic_to_knowledge_case.sql` |
| Nginx 配置锚点 | `/etc/nginx/conf.d/xiyu-bid.conf.bak-20260817`（未变更）                                                 |
| 回滚 owner   | 部署执行 Agent（trae）                                                                                    |

回滚状态：**就绪，未需要**。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：保留（第 13/14/15 次部署用户决定，测试环境常态配置）。

- 无其他 SHOW\_DETAILS/DEBUG/TRACE 临时调试配置。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）

- [x] 早操三连 + 基线零差异

- [x] Flyway 预检通过（validate + DB 版本对比 V1210→V1211）

- [x] 产物校验（迁移无重复 / obsEnabled / 同源构建）

- [x] DB 备份先行（winbid-4bc19117c-\*.sql.gz）

- [x] 服务重启 + 健康 UP

- [x] 迁移应用验证（V1211 success=1）

- [x] Smoke 全绿

- [x] 配置清理检查

- [x] 部署报告归档

## 风险提示

- 本次新增 V1211 迁移（knowledge\_case 表新增 sub\_topic 列），属加列操作，回滚需执行 U1211 脚本（DROP COLUMN）。

- PR !2551 历史案例精准推荐流水线重构较大，建议在知识库案例推荐场景做人工抽检，确认 subTopic 匹配与推荐精度。

- PR !2545 标书原文下载禁止投标项目负责人角色，属权限行为变更，建议用 bid-projectLeader 角色验证下载确实被拦截，其他角色正常。

- PR !2547/!2549/!2550 scoreparse 多项修复，建议用项目 1359 的评分标准解析做回归验证（编号、权重、缺项、业绩仓储满足状态、isCredit 判定）。

- 健康检查约 5 分钟（Kafka SDK 延迟），属已知行为；如后续需缩短启动窗口，可考虑将 OrganizationEventSdkKafkaStarter 改为 @Async。

