# 第 152 次部署报告（test 环境）

**日期**：2026-09-01
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`899ebc0a9-api8080`
**部署时间**：14:25 - 14:40 CST
**状态**：✅ 成功

## 部署概览

- 增量 18 个 commit（3 个 PR 线），**无新增 Flyway 迁移**（DB V1205 = 源码 V1205，纯代码发版）。

- 本次包含重点业务功能与修复：

  - **评分标准整表级一次性 LLM 抽取与主备降级**（PR !2493）：scoreparse 从逐行抽取升级为整表级一次抽取，LLM 主备降级链路。

  - **合规质检分页契约修复系列**（PR !2492 及子 commit）：Sidecar PDF 转换输出结构化物理分页 `pages` 字段、虚拟分页兜底 + 结构化 pages 消费 + 语义检索单页硬截断、报价跨页回退、敏感词证据确定性关键词定位、开标一览表词表扩充、pages 契约守卫 + 价格空金额校验。

  - **数据分析 M1 部门筛选口径修复**（PR !2490）：部门筛选对齐锚定三级展示树，空列表 fail-closed。

- Flyway 预检及迁移验证全部通过（266 项迁移全量校验，Checksum 100% 匹配）。

- ⚠️ 本次 `remote-deploy.sh` 内置健康检查报"120 attempts 未凑齐连续 3 次 UP"——根因为 Kafka SDK readiness 延迟（OUT\_OF\_SERVICE 持续约 5 分钟）超过脚本 4 分钟等待窗口，属已知行为（经验 #2）的边界情况，**非 crash-loop**；独立验证服务 active 且 health UP，无需回滚。

## 基线信息

| 项目        | 值                                               |
| --------- | ----------------------------------------------- |
| 基线 commit | `899ebc0a9`（origin/main，HEAD = origin/main 零差异） |
| 前次部署      | `46638288e-api8080`（2026-09-01 12:53 CST 激活）    |
| 增量 commit | 18 个（`46638288e..899ebc0a9`）                    |
| 新增迁移      | 无（DB V1205 = 源码 V1205）                          |
| 工作区状态     | 干净                                              |
| 早操三连      | sync-env + check-git-wrapper 全过（本地门禁 7 项通过）     |

## 改动范围（18 个增量 commit / 3 条 PR 线）

| PR    | 描述                                                                                               |
| ----- | ------------------------------------------------------------------------------------------------ |
| !2493 | feat(scoreparse): 评分标准整表级一次性 LLM 抽取与主备降级                                                         |
| !2492 | fix(compliance): 质检分页契约修复系列——Sidecar 结构化物理分页 pages、虚拟分页兜底、报价跨页回退、敏感词证据关键词定位、pages 契约守卫 + 价格空金额校验 |
| !2490 | fix(analytics): M1 部门筛选口径对齐锚定三级展示树并空列表 fail-closed                                               |

另有 docs 类 commit：lessons #196-#199 条目沉淀与 wiki 回填（质检分页契约断裂、部门三级口径错位与空列表 fail-open）。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → 验证通过，266 migrations 校验完毕，VALIDATE OK - all checksums match。
**Step 2**：DB 最新 applied = V1205（2026-09-01 08:18 应用），与源码最高版本 V1205 一致，无 pending。
**Step 3**：remote-deploy.sh 内置 validate（jar 激活前）通过（仅 pending 新迁移为预期状态，本次无 pending）。

## 部署步骤时间线（CST）

| 时间          | 步骤                                                                    |
| ----------- | --------------------------------------------------------------------- |
| 14:22       | 早操三连（sync-env ff-only 同步至 `899ebc0a9`，本地门禁 7 项通过）                     |
| 14:24       | 服务器现状探测（前次 release `46638288e-api8080`，health UP）                     |
| 14:26       | Flyway 预检 3 步通过（validate OK，DB V1205 = 源码 V1205）                      |
| 14:28       | 本地打包完成（BUILD SUCCESS 35.9s，jar 内迁移版本无重复 + OBS 直传校验通过）                 |
| 14:29       | scp 压缩包至 `172.16.38.78`（MD5 `208791ce7...` 双端一致）                      |
| 14:30:27    | DB 备份 `winbid-899ebc0a9-20260901143027.sql.gz`（12M）                   |
| 14:30:33    | remote-deploy 内置 Flyway validate 通过 → 前端激活 → 覆盖 jar → systemctl start |
| 14:30:36    | 后端服务启动（PID 15732）                                                     |
| 14:30-14:34 | 脚本内置健康检查 120 attempts 未凑齐连续 3 次 UP（readiness 延迟超窗口，误报 crash-loop 提示）  |
| \~14:36     | readiness 自愈，独立验证 health UP（第 1 次即 UP）                                |
| 14:38       | 前端入口一致性校验 + 旧版 assets `cp -rn` 保留 + Smoke 8 项全绿                       |

## 验证结果

**后端健康（127.0.0.1:18080 与 Nginx 8080 双路径）**：`{"status":"UP"}`，组件全 UP（aiProvider/db/diskSpace/jwt/liveness/ping/readiness/redis/sidecar）。

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

- 线上入口：`assets/index-BbQPYpbr.js` 与构建产物完全一致（前版入口 hash 相同，页面级 chunk 已更新）。

**产物与特性校验**：

- `release-metadata.json`：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）。

- OBS 直传校验：Detail chunk `.upload(` 调用数 = 2。

- jar 内迁移文件 265 个，V\*.sql 无重复版本。

- 旧版 assets 保留：已从 `46638288e-api8080/frontend/assets` 执行 `cp -rn` 保留，防止跨版本 preload 404（经验 #17）。

- 无 macOS `._*` 残留文件（`COPYFILE_DISABLE=1` 生效）。

## 本次异常与处置

**remote-deploy.sh 健康检查误报失败**：

- 现象：脚本要求连续 3 次 health UP，120 attempts（4 分钟）内未达成，打印 crash-loop 诊断与回滚指引。

- 根因：Kafka SDK readiness 延迟（`OrganizationEventSdkKafkaStarter` 阻塞 `ApplicationReadyEvent`）导致 readiness 503 OUT\_OF\_SERVICE 持续约 5 分钟（经验 #2 已知行为），恰好超过脚本 4 分钟等待窗口；期间 db/aiProvider 等组件均 UP。

- 处置：独立验证 `systemctl is-active` = active、health = UP、前端已切换、deployed-release.json 已写入——判定为误报，未回滚。

- 改进建议：`remote-deploy.sh` 健康检查可将 readiness 延迟纳入容忍（区分 `livenessState` UP 与 readiness OUT\_OF\_SERVICE，或延长窗口至 6 分钟），避免已知行为触发误报回滚指引。

## 回滚信息

| 锚点         | 位置                                                                |
| ---------- | ----------------------------------------------------------------- |
| 前后端制品      | `/opt/xiyu-bid/releases/46638288e-api8080/`（jar + frontend）       |
| DB 备份      | `/opt/xiyu-bid/db-backups/winbid-899ebc0a9-20260901143027.sql.gz` |
| Nginx 配置锚点 | `/etc/nginx/conf.d/xiyu-bid.conf.bak-20260817`（未变更）               |
| 回滚 owner   | 部署执行 Agent（trae）                                                  |

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

- [x] Smoke 8 项全绿

- [x] 旧版 assets 保留

- [x] 部署报告归档

## 风险提示

- readiness 延迟期间（14:30-14:36）经 Nginx 的流量会收到 503，属每次重启的已知窗口，本次约 5 分钟偏长（历史 2-4 分钟）。

- 服务器 `/opt/xiyu-bid/incoming/` 已积累 9.1G 历史部署包，建议另行清理（保留最近 N 版 + 回滚锚点版本）。

- 服务器 `/tmp/migration-mysql/` 目录为旧版快照（经验 #10），不影响 validate，建议下次部署顺带同步。

