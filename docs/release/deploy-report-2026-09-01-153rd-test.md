# 第 153 次部署报告（test 环境）

**日期**：2026-09-01
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`b2d9bbc36`
**部署时间**：20:33 - 20:43 CST
**状态**：✅ 成功

## 部署概览

- 增量 26 个 commit（6 条 PR 线），**无新增 Flyway 迁移**（DB V1205 = 源码 V1205，纯代码发版）。

- 本次包含重点业务功能与修复：

  - **部署脚本 5 项风险修复**（PR !2497，P0x2 + P1x2 + P2）：`deploy-test.sh` line 149 移除 `|| true` 假成功掩盖、健康检查等待窗口 4 分钟 → 6 分钟（覆盖 Kafka SDK readiness 延迟）、Flyway validate 提前到前端激活前、DB 备份整合进部署命令、jar 覆盖失败自动恢复。**本次部署脚本健康检查 144 次尝试内凑齐连续 3 次 UP，未再出现第 152 次"120 attempts 误报"，修复实证生效。**

  - **评分表格抽取回归修复**（PR !2495）：修复前附表/合同表误召回，沉淀 Lesson 200 与 Wiki 评分表格抽取分层分流规范。

  - **语义项裁决 max_tokens 1024→4096**（PR !2496）：thinking 系模型内部推理计入输出预算，max_tokens 截断根因治理（lessons #201 对策）。

  - **小预算 LLM thinking 截断治理 + 解析失败聚告警**（PR !2499/!2500，lessons #201 对策落地）。

  - **评分解析 Code Review 修复 + 元数据传播与表结构映射**（PR !2501）。

  - **评分项编号前缀清洗 + 案例正文跨层相似度匹配**（PR !2502）。

- Flyway 预检及迁移验证全部通过（266 项迁移全量校验，Checksum 100% 匹配）。

## 基线信息

| 项目        | 值                                               |
| --------- | ----------------------------------------------- |
| 基线 commit | `b2d9bbc36`（origin/main，HEAD = origin/main 零差异） |
| 前次部署      | `899ebc0a9-api8080`（2026-09-01 06:30:36Z 激活）    |
| 增量 commit | 26 个（`899ebc0a9..b2d9bbc36`）                    |
| 新增迁移      | 无（DB V1205 = 源码 V1205）                          |
| 工作区状态     | 干净                                              |
| 早操三连      | 锚点分支 ff-only 同步至 `b2d9bbc36` + check-git-wrapper 通过 |

## 改动范围（26 个增量 commit / 6 条 PR 线）

| PR    | 描述                                                                                               |
| ----- | ------------------------------------------------------------------------------------------------ |
| !2494 | docs(release): 第 152 次部署报告 (test)                                                             |
| !2495 | fix(scoreparse): 修复评分表格抽取回归与前附表/合同表误召回问题（Lesson 200 + Wiki 分层分流规范）                         |
| !2496 | fix(compliance): 语义项裁决 max_tokens 1024→4096——thinking 系模型内部推理计入输出预算（lessons #201）             |
| !2497 | fix(release): 修复部署脚本 5 项风险（P0x2 + P1x2 + P2）——deploy-test.sh 假成功、健康检查超时窗口、Flyway validate 顺序、DB 备份、jar 覆盖恢复 |
| !2498 | docs: 归档已合并 PR !2481 第三方审查报告（Trae 三透镜）                                                     |
| !2499/!2500 | fix(ai): 小预算 LLM 调用 thinking 截断治理 + 解析失败聚告警（lessons #201 对策落地）                        |
| !2501 | fix(scoreparse): 修复评分解析 Code Review 问题并强化元数据传播与表结构映射                                        |
| !2502 | fix(casework): 增强评分项编号前缀清洗与案例正文跨层相似度匹配                                                    |

另有 docs 类 commit：PR !2481/!2493 第三方审查报告入库（Trae 三透镜 + Kiro LGTM）、lessons #200-#201 条目沉淀与 wiki 回填。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → 验证通过，266 migrations 校验完毕，VALIDATE OK - all checksums match。
**Step 2**：DB 最新 applied = V1205（2026-09-01 08:18 应用），与源码最高版本 V1205 一致，无 pending。
**Step 3**：remote-deploy.sh 内置 validate（jar 激活前）通过（仅 pending 新迁移为预期状态，本次无 pending）。

## 部署步骤时间线（CST）

| 时间          | 步骤                                                                    |
| ----------- | --------------------------------------------------------------------- |
| 20:33       | 锚点分支 ff-only 同步至 `b2d9bbc36`（origin/main 零差异，工作区干净）                     |
| 20:33       | 服务器现状探测（前次 release `899ebc0a9-api8080`，health UP）                     |
| 20:33       | Flyway 预检 3 步通过（validate OK，DB V1205 = 源码 V1205）                      |
| 20:38       | 本地打包完成（BUILD SUCCESS 54.1s，jar 内迁移版本无重复 + OBS 直传校验通过）                 |
| 20:38       | scp 压缩包至 `172.16.38.78`                                            |
| 20:38       | remote-deploy 内置 Flyway validate 通过 → DB 备份 → 前端激活 → 覆盖 jar → systemctl start |
| 20:39:01    | 后端服务启动（PID 7388）                                                     |
| 20:39-20:43 | 脚本内置健康检查 144 attempts 内凑齐连续 3 次 UP（Kafka readiness 延迟在新 6 分钟窗口内覆盖，无误报） |
| 20:43       | 前端入口一致性校验 + 旧版 assets `cp -rn` 保留 + Smoke 8 项全绿                       |

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

- 线上入口：`assets/index-BbQPYpbr.js` 与构建产物完全一致。

**产物与特性校验**：

- `release-metadata.json`：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）。

- OBS 直传校验：Detail chunk `.upload(` 调用数 = 2。

- jar 内迁移文件 265 个，V\*.sql 无重复版本。

- 旧版 assets 保留：已从 `899ebc0a9-api8080/frontend/assets` 执行 `cp -rn` 保留（191 个资源文件），防止跨版本 preload 404（经验 #17）。

- 无 macOS `._*` 残留文件（`COPYFILE_DISABLE=1` 生效）。

## 本次异常与处置

**无异常**。特别说明：

- 第 152 次部署的"脚本健康检查 120 attempts 未凑齐连续 3 次 UP"误报问题，本次由 PR !2497 的健康检查窗口修复（4 分钟 → 6 分钟）解决——本次脚本在 144 attempts 内凑齐连续 3 次 UP，未再触发误报回滚指引。

## 回滚信息

| 锚点         | 位置                                                                |
| ---------- | ----------------------------------------------------------------- |
| 前后端制品      | `/opt/xiyu-bid/releases/899ebc0a9-api8080/`（jar + frontend）       |
| DB 备份      | `/opt/xiyu-bid/db-backups/winbid-b2d9bbc36-*.sql.gz`（本次部署自动生成） |
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

- 服务器 `/opt/xiyu-bid/incoming/` 已积累 9.1G 历史部署包，建议另行清理（保留最近 N 版 + 回滚锚点版本）。

- 服务器 `/tmp/migration-mysql/` 目录为旧版快照（经验 #10），不影响 validate，建议下次部署顺带同步。
