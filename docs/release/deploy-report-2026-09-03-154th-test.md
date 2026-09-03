# 第 154 次部署报告（test 环境）

**日期**：2026-09-03
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`3b87534ff`
**部署时间**：13:04 - 13:15 CST
**状态**：✅ 成功

## 部署概览

- 增量 47 个 commit（12 条 merge PR），新增 **3 个 Flyway 迁移**（V1206/V1207/V1208，DB V1205 → 源码 V1208）。

- 本次包含重点业务功能与修复：

  - **评分证据管道 spec 059 全量推演落地**（PR !2505-!2517 系列，Phase 0-6）：

    - Phase 0-1：解析运行档案可追溯（pipeline\_version/decision\_status/fallback\_reason）+ DocumentArtifact v2 契约 + CanonicalTableGrid 结构化校验

    - Phase 2：确定性证据抽取核心类型（ScoreFact/ScoreEvidence）；归一化偏离显式告警落库

    - Phase 3-4：LLM 收敛为可审计语义适配器 + 完整性阻断 + 人工复核 + 取消静默归一化

    - Phase 5-6：数据库迁移（V1208 双写支持 + 三张事实表）+ 前端抽屉原文证据展开 + 清理交付

  - **AI 标书质量核查多册合并 + 正向承诺修复**（PR !2509/!2510）：多册投标文件合并核查、正向承诺误判修复、附件免除项假通过治理

  - **智能案例推荐算法优化**（PR !2507）：招投标停用词过滤 + 项目专属实体断层加权

  - **Sidecar /extract-tables 异常修复**（PR !2506：非 ValueError 现返回 422）+ vMerge 纵向跨度按 gridSpan 精确计算

  - **标讯事件总线注册生命周期优化**（PR !2503，修复测试挂起）

- Flyway 预检及迁移验证全部通过（266 项迁移全量校验，Checksum 100% 匹配）。

## 基线信息

| 项目        | 值                                                   |
| --------- | --------------------------------------------------- |
| 基线 commit | `3b87534ff`（origin/main，HEAD = origin/main 零差异）     |
| 前次部署      | `b2d9bbc36`（2026-09-01 12:39Z 激活）                   |
| 增量 commit | 47 个（`b2d9bbc36..3b87534ff`）                        |
| 新增迁移      | 3 个（V1206/V1207/V1208）                              |
| 工作区状态     | 干净                                                  |
| 早操三连      | 锚点分支 ff-only 同步至 `3b87534ff` + check-git-wrapper 通过 |

## 改动范围（47 个增量 commit / 12 条 PR 线）

| PR          | 描述                                                                    |
| ----------- | --------------------------------------------------------------------- |
| !2503       | fix(integration): 优化标讯事件总线注册生命周期并修复测试挂起问题                             |
| !2504       | docs(release): 第 153 次部署报告 (test)                                     |
| !2505       | feat(score-parse): Phase 1 结构化表格提取通道 — 从 Word 原始表格结构提取评分项             |
| !2506       | fix(sidecar): /extract-tables 非 ValueError 异常返回 500 → 422             |
| !2507       | feat(casework): 优化智能案例推荐算法，支持招投标停用词过滤与项目专属实体断层加权                      |
| !2509       | fix(compliance): 修复标书质量核查正向承诺误判与附件免除项假通过（含知识沉淀 #205）                  |
| !2510       | feat(compliance): AI标书质量核查支持多册投标文件合并核查 + 反slop 清理                     |
| !2512       | feat(spec059): 评分证据管道 Phase 0-4 — 确定性证据抽取、决策状态机、LLM 可审计适配器、完整性阻断、人工复核 |
| !2513       | fix(scripts): gitee-pr-helper / agent-finish-task.sh 响应校验收敛           |
| !2514       | feat(spec059): Phase 4 完整性阻断、人工复核与取消静默归一化                             |
| !2515       | feat(spec059): Phase 5 数据库迁移、双写支持与前端抽屉原文证据展开                          |
| !2516/!2517 | feat(spec059): Phase 6 清理、门禁与交付 + 前端评分证据 UI 渲染                        |

另有 docs 类 commit：PR !2508 Phase 1-5 补齐、知识沉淀 §205-§212、wiki 回填、U1206 回滚脚本命名对齐。3 个新迁移均配套 rollback 脚本（U1206/U1207/U1208）。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → 验证通过，266 migrations 校验完毕，VALIDATE OK - all checksums match。
**Step 2**：DB 最新 applied = V1205（2026-09-01 08:18 应用），源码最高版本 V1208 → 本次待应用 V1206/V1207/V1208。
**Step 3**：remote-deploy.sh 内置 validate（jar 激活前）通过（仅 pending 新迁移为预期状态）。

## 部署步骤时间线（CST）

| 时间          | 步骤                                                                            |
| ----------- | ----------------------------------------------------------------------------- |
| 13:04       | 锚点分支 ff-only 同步至 `3b87534ff`（origin/main 零差异，工作区干净）                           |
| 13:04       | 服务器现状探测（前次 release `b2d9bbc36`，health UP / readiness UP）                      |
| 13:04-13:05 | Flyway 预检 3 步通过（validate OK，DB V1205 → 源码 V1208，待应用 3 迁移）                     |
| 13:11       | 本地打包完成（BUILD SUCCESS 1:02，jar 内迁移版本无重复 + OBS 直传校验通过）                          |
| 13:11       | scp 压缩包（155M）至 `172.16.38.78`                                                 |
| 13:12       | remote-deploy 内置 Flyway validate 通过 → DB 备份 → 前端激活 → 覆盖 jar → systemctl start |
| 13:12:08    | 后端服务启动（PID 21947）                                                             |
| 13:12       | 脚本内置健康检查 143 attempts 内凑齐连续 3 次 UP（Kafka readiness 延迟在 6 分钟窗口内覆盖）             |
| 13:15       | 迁移应用验证（V1206/V1207/V1208 success=1）+ 事实表抽查 + Smoke 全绿                         |

## 验证结果

**后端健康（127.0.0.1:18080 与 Nginx 8080 双路径）**：`{"status":"UP"}`，readiness 200。

**迁移应用验证**：V1206/V1207/V1208 均 `success=1`，`installed_on=2026-09-03 13:12:16`。`score_parse_task.pipeline_version` 列已建立，`score_parse_evidence/score_parse_fact/score_parse_review` 三张事实表均存在（BASE TABLE）。

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

- 线上入口：`assets/index-DkMAzry_.js` 与构建产物完全一致。

**产物与特性校验**：

- `release-metadata.json`：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）。

- OBS 直传校验：Detail chunk `.upload(` 调用数 = 2。

- jar 内迁移文件含 V1206/V1207/V1208，无重复版本。

- 无 macOS `._*` 残留文件（`COPYFILE_DISABLE=1` 生效）。

## 本次异常与处置

**无异常**。迁移验证首查 `fact_tables=0` 系 SQL 用字面库名导致，改用 `DATABASE()` 复核确认三张表实际存在，V1208 生效无误。

## 回滚信息

| 锚点         | 位置                                                             |
| ---------- | -------------------------------------------------------------- |
| 前后端制品      | `/opt/xiyu-bid/releases/b2d9bbc36/`（jar + frontend）            |
| DB 备份      | `/opt/xiyu-bid/db-backups/winbid-3b87534ff-*.sql.gz`（本次部署自动生成） |
| Nginx 配置锚点 | `/etc/nginx/conf.d/xiyu-bid.conf.bak-20260817`（未变更）            |
| 回滚 owner   | 部署执行 Agent（trae）                                               |

回滚状态：**就绪，未需要**。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：保留（第 13/14/15 次部署用户决定，测试环境常态配置）。

- 无其他 SHOW\_DETAILS/DEBUG/TRACE 临时调试配置。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）

- [x] 早操三连 + 基线零差异

- [x] Flyway 预检 3 步通过

- [x] 产物校验（迁移无重复 / obsEnabled / 同源构建）

- [x] DB 备份先行

- [x] 服务重启 + 健康 UP

- [x] 迁移应用验证（V1206/V1207/V1208）+ 事实表抽查

- [x] Smoke 全绿

- [x] 部署报告归档

## 风险提示

- 服务器 `/opt/xiyu-bid/incoming/` 历史部署包（9.1G+）建议另行清理（保留最近 N 版 + 回滚锚点版本）。

- 服务器 `/tmp/migration-mysql/` 目录为旧版快照（经验 #10），不影响 validate，建议下次部署顺带同步。

- 本次 spec 059 新增三张事实表与多列，回滚需依赖 U1208 及 DB 备份（破坏性 schema 变更风险，见经验 #9）。

