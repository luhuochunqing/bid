# 第 157 次部署报告（test 环境）

**日期**：2026-09-04
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`b5e189a7a`
**部署时间**：07:35 - 07:43 CST
**状态**：✅ 成功

## 部署概览

- 增量 6 个 commit（3 条 PR），**无新增 Flyway 迁移**（DB 与源码最新版本均为 V1210）。
- 本次以**资质核查模块质量治理与 AI Slop 清理**为主：

  - **确定性资格提取策略 + 质检流水线双轨融合**（PR !2533）：修复 LLM 代码块围栏解析，新增 `DeterministicQualificationExtractor` 兜底，`TenderRequirementsParser` 逻辑收敛
  - **质检模块 AI Slop 清理 + 代码去重**（PR !2534）：清理重复 JSON 解析、废弃 wrapper 与冗余检查，同步 wiki 知识沉淀
  - **第 156 次部署报告归档**（PR !2532）

- Flyway 预检及迁移验证全部通过（271 项迁移全量校验）。

## 基线信息

| 项目        | 值                                                |
| --------- | ------------------------------------------------ |
| 基线 commit | `b5e189a7a`（origin/main，锚点分支 ff-only 同步，零差异）      |
| 前次部署      | `c3fb854d1`（2026-09-03 21:52 激活，第 156 次部署）        |
| 增量 commit | 6 个（`c3fb854d1..b5e189a7a`）                     |
| 新增迁移      | 无（DB 与源码均为 V1210）                                |
| 工作区状态     | 干净                                                |
| 早操三连      | 锚点分支 ff-only 同步至 `b5e189a7a` + check-git-wrapper 通过 |

## 改动范围（6 个增量 commit / 3 条 PR）

| PR    | 描述                                                    |
| ----- | ----------------------------------------------------- |
| !2532 | docs(release): 第 156 次部署报告 (test)                       |
| !2533 | fix(quality): 增加确定性资格提取策略、修复 LLM 代码块围栏解析并实现质检流水线双轨融合 |
| !2534 | refactor(compliance): 清理重复 JSON 解析、废弃 wrapper 与冗余检查 + docs(wiki) 沉淀 |

关键代码：新增 `DeterministicQualificationExtractor.java`（166 行）、`TenderRequirements.java`（21 行），修改 `TenderRequirementsParser`（12 行），配套 3 个新测试类（`DeterministicQualificationExtractorTest`/`TenderRequirementsParserTest`/`BidQualityCheckAsyncExecutorTest`），前端 `QualityCheckDetailDialog.vue` 小幅调整。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` 首次报 `Detected applied migration not resolved locally: 1210`，定位为服务器 `/tmp/migration-mysql/` 目录过时（缺 V1210 文件，skill 第 15 次已知问题），同步 V1210 后 validate → 通过，271 migrations all checksums match。

**Step 2**：DB 最新 applied = V1210（`success=1`，2026-09-03 21:52 已应用），failed 记录 = 0，源码最高版本 = V1210 → 本次无待应用迁移。

**Step 3**：remote-deploy.sh 内置 validate → 通过。

## 部署步骤时间线（CST）

| 时间          | 步骤                                                          |
| ----------- | ----------------------------------------------------------- |
| 07:31       | 环境门禁确认（test / 172.16.38.78）+ 服务器连通性恢复                    |
| 07:34       | 服务器现状探测（前次 release `c3fb854d1`，health UP）                 |
| 07:35       | Flyway 预检：101 迁移目录过时问题定位并同步 V1210 → validate 通过（271 migrations） |
| 07:35       | DB 版本对比：V1210 success=1，failed=0，无待应用                     |
| 07:41-07:42 | 本地打包完成（154M，obsEnabled=true，同源构建）+ scp 上传               |
| 07:43       | remote-deploy 执行：DB 备份 → Flyway validate → Frontend 激活 → 覆盖 jar → systemctl 重启 |
| 07:43       | 脚本内置健康检查 143 attempts 连续 3 次 UP（Kafka readiness 延迟在窗口内覆盖）  |
| 07:43+      | Smoke 全绿 + 旧 assets 保留 + 配置清理检查                              |

## 验证结果

**后端健康（127.0.0.1:18080 与 Nginx 8080 双路径）**：`{"status":"UP"}`，readiness 200。

**迁移应用验证**：本次无新增迁移；V1210 保持 `success=1`（installed_on 2026-09-03 21:52:25）。

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
- 线上入口：`assets/index-CKov07Pq.js` 与构建产物完全一致
- 旧版本 hash 资源已从 `releases/c3fb854d1/frontend/` 以 `cp -rn` 保留到 `/srv/www/xiyu-bid/assets/`（防跨版本 404，保留 24h 惯例）

**产物与特性校验**：

- `release-metadata.json`：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）
- OBS 直传校验：Detail chunk `.upload(` 调用数 = 2
- jar 内迁移文件含 V1210，共 271 个迁移版本，无重复
- 无 macOS `._*` 残留文件（`COPYFILE_DISABLE=1` 生效）

## 本次异常与处置

**无阻塞性异常。** 唯一需记录的是 Flyway validate 首次报 V1210 "applied migration not resolved locally"——经确认是服务器 `/tmp/migration-mysql/` 目录过时所致（DB 正常，failed=0），同步 V1210 文件后 validate 立即通过。已按 skill 预检 Step 1 提前发现并解决，未影响部署。

## 回滚信息

| 锚点         | 位置                                                          |
| ---------- | ----------------------------------------------------------- |
| 前后端制品      | `/opt/xiyu-bid/releases/c3fb854d1/`（jar + frontend）         |
| DB 备份      | `/opt/xiyu-bid/db-backups/winbid-b5e189a7a-*.sql.gz`（本次部署自动生成） |
| 迁移回滚脚本     | 本次无新增迁移，无需回滚脚本                                          |
| Nginx 配置锚点 | `/etc/nginx/conf.d/xiyu-bid.conf.bak-20260817`（未变更）           |
| 回滚 owner   | 部署执行 Agent（trae）                                              |

回滚状态：**就绪，未需要**。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：保留（第 13/14/15 次部署用户决定，测试环境常态配置）。
- 无其他 SHOW\_DETAILS/DEBUG/TRACE 临时调试配置。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 早操三连 + 基线零差异
- [x] Flyway 预检通过（validate + DB 版本对比）
- [x] 产物校验（迁移无重复 / obsEnabled / 同源构建）
- [x] DB 备份先行
- [x] 服务重启 + 健康 UP
- [x] 迁移应用验证（本次无新增，V1210 保持 success）
- [x] Smoke 全绿
- [x] 旧 assets 跨版本保留
- [x] 部署报告归档

## 风险提示

- 本次无数据库结构变更，回滚仅需切回前一版本制品（`c3fb854d1/`）+ DB 备份。
- 确定性资格提取策略（`DeterministicQualificationExtractor`）为新增业务兜底逻辑，建议后续用真实标书在测试环境做一次端到端核验，确认双轨融合后匹配率与 LLM 抽取一致性。
- 服务器 `/opt/xiyu-bid/incoming/` 历史部署包（9.1G+）与 `releases/` 旧版目录建议另行清理（保留最近 N 版 + 回滚锚点版本）。