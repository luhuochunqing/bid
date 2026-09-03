# 第 155 次部署报告（test 环境）

**日期**：2026-09-03
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`f9e48fc58-api8080`
**部署时间**：15:55 - 16:10 CST
**状态**：✅ 成功（首次 remote-deploy 因 runner 旧版残留失败一次，修复后重跑成功，服务未中断）

## 部署概览

- 增量 17 个 commit（7 条 PR），新增 **1 个 Flyway 迁移**（V1209，DB V1208 → 源码 V1209）。

- 本次以修复类改动为主：

  - **标书结项下载守卫 + 项目档案单项目查询**（PR !2520）：含 V1209 幂等回填迁移（project\_archive 归档头 + archive\_file 明细，LEFT JOIN 防重，非破坏性）

  - **AI 案例推荐匹配度浮层文案**（PR !2519）

  - **保证金回单检查误判修复**（PR !2523）

  - **投标函核查词表扩充**（PR !2524）

  - **Gitee token 四级解析链**（PR !2521，脚本治理）

- Flyway 预检及迁移验证全部通过（270 项迁移全量校验，Checksum 100% 匹配）。

- 本次踩到两个历史经验复发问题（`/tmp/migration-mysql/` 过时、`incoming/` 旧版 runner 残留），均已当场修复，详见"本次异常与处置"。

## 基线信息

| 项目        | 值                                                   |
| --------- | --------------------------------------------------- |
| 基线 commit | `f9e48fc58`（origin/main，锚点分支 ff-only 同步，零差异）        |
| 前次部署      | `3b87534ff`（2026-09-03 13:12 激活，第 154 次部署）          |
| 增量 commit | 17 个（`3b87534ff..f9e48fc58`）                        |
| 新增迁移      | 1 个（V1209，幂等回填，配套 U1209 回滚脚本）                       |
| 工作区状态     | 干净                                                  |
| 早操三连      | 锚点分支 ff-only 同步至 `f9e48fc58` + check-git-wrapper 通过 |

## 改动范围（17 个增量 commit / 7 条 PR）

| PR    | 描述                                                  |
| ----- | --------------------------------------------------- |
| !2518 | docs(release): 第 154 次部署报告 (test)                   |
| !2519 | fix(casework): AI 案例推荐匹配度浮层文案                       |
| !2520 | fix(project,knowledge): 标书结项下载守卫 + 档案单项目查询（含 V1209） |
| !2521 | fix(scripts): Gitee token 四级解析链                     |
| !2522 | docs: wiki 回填                                       |
| !2523 | fix(quality): 保证金回单检查误判                             |
| !2524 | fix(compliance): 投标函核查词表扩充                          |

V1209 回填迁移配套回滚脚本 `U1209__backfill_missing_project_archive_files.sql`（U1205-U1209 全齐）。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → 首跑失败（"applied migration not resolved locally: 1206, 1207, 1208"，服务器 `/tmp/migration-mysql/` 只到 V1205，经验 #10 复发）→ tar-over-ssh 同步 278 个迁移文件后重跑通过，270 migrations，VALIDATE OK - all checksums match。
**Step 2**：DB 最新 applied = V1208（2026-09-03 13:12 应用），源码最高版本 V1209 → 本次待应用 V1209。
**Step 3**：remote-deploy.sh 内置 validate → 首跑因 `incoming/` 旧版 runner 误报 pending V1209 失败（详见异常处置），修复 runner 后重跑通过。

## 部署步骤时间线（CST）

| 时间          | 步骤                                                                       |
| ----------- | ------------------------------------------------------------------------ |
| 15:55       | 锚点分支 ff-only 同步至 `f9e48fc58`（origin/main 零差异，工作区干净）                      |
| 15:56       | 服务器现状探测（前次 release `3b87534ff`，health UP）                                |
| 15:56-15:58 | Flyway 预检：validate 首跑失败（/tmp/migration-mysql 过时）→ tar-over-ssh 同步 → 通过   |
| 15:59-16:03 | 本地打包完成（161M，OBS 直传校验通过）+ scp 上传                                          |
| 16:03       | 首次 remote-deploy 内置 validate 失败（旧版 runner 误报 pending）→ **旧版本仍在运行，服务未中断** |
| 16:05       | 修复：`bin/` 新版 runner 覆盖 `incoming/` 旧版 → 重跑 remote-deploy                 |
| 16:05:55    | 内置 validate 通过 → DB 备份 → 前端激活 → 覆盖 jar → systemctl start                 |
| 16:06       | 后端服务启动（PID 3025）                                                         |
| 16:06:07    | V1209 应用（success=1）                                                      |
| 16:06-16:10 | 脚本内置健康检查 144 attempts 凑齐连续 3 次 UP（Kafka readiness 延迟在 8 分钟窗口内覆盖）         |
| 16:10+      | Smoke 全绿 + OBS 校验 + 旧 assets 保留 + 配置清理检查                                 |

## 验证结果

**后端健康（127.0.0.1:18080 与 Nginx 8080 双路径）**：`{"status":"UP"}`，readiness 200。

**迁移应用验证**：V1209 `success=1`，`installed_on=2026-09-03 16:06:07`。

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

- 线上入口：`assets/index-eLFWZvdA.js` 与构建产物完全一致。

- 旧版本 hash 资源已从 `releases/3b87534ff/` 以 `cp -rn` 保留到新 release（防跨版本 404，保留 24h 惯例）。

**产物与特性校验**：

- `release-metadata.json`：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）。

- OBS 直传校验：Detail chunk `.upload(` 调用数 = 2。

- jar 内迁移文件含 V1209，无重复版本。

- 无 macOS `._*` 残留文件（`COPYFILE_DISABLE=1` 生效）。

## 本次异常与处置

**异常 1：`/tmp/migration-mysql/`** **过时（经验 #10 复发）**

- 现象：预检 Step 1 validate 报 "Detected applied migration not resolved locally: 1206, 1207, 1208"。

- 根因：服务器 `/tmp/migration-mysql/` 停留在 V1205，DB 已应用到 V1208（第 154 次部署未同步该目录）。

- 处置：`rsync` 远端不可用（exit 127），改用 tar-over-ssh 流式同步 278 个迁移文件后 validate 通过。

- 后续建议：将迁移目录同步固化进 `deploy-test.sh`（每次部署自动 tar-over-ssh），消除人工依赖。

**异常 2：`incoming/`** **残留旧版 flyway-repair-runner.sh（新问题）**

- 现象：首次 remote-deploy 内置 validate 报 "Detected resolved migration not applied to database: 1209"（pending V1209 被误报为失败）。此时 validate 在版本切换前执行，**旧 jar 仍在运行，服务未中断**。

- 根因：`remote-deploy.sh` 按 `$(dirname $0)/flyway-repair-runner.sh` 解析 runner，即 `/opt/xiyu-bid/incoming/flyway-repair-runner.sh` —— 8 月 28 日旧版，缺少 `.ignoreMigrationPatterns("*:pending")` 配置；`deploy-test.sh` 只把新版 runner scp 到 `bin/`，未覆盖 `incoming/` 旧版。

- 处置：`cp /opt/xiyu-bid/bin/flyway-repair-runner.sh /opt/xiyu-bid/incoming/` 后单独重跑 remote-deploy（产物已上传，无需重新打包），成功。

- 后续建议（需开代码任务）：`deploy-test.sh` 增加 runner scp 到 `incoming/`，或将 `remote-deploy.sh` 默认 `FLYWAY_REPAIR_RUNNER` 指向 `$APP_ROOT/bin/flyway-repair-runner.sh`（bin/ 为唯一权威位置）。

**DB 备份说明**：首次失败的部署也完成了备份（16:03:22），成功部署再备份一次（16:05:49），两份 `winbid-f9e48fc58-api8080-*.sql.gz` 均可用。

## 回滚信息

| 锚点         | 位置                                                                        |
| ---------- | ------------------------------------------------------------------------- |
| 前后端制品      | `/opt/xiyu-bid/releases/3b87534ff/`（jar + frontend）                       |
| DB 备份      | `/opt/xiyu-bid/db-backups/winbid-f9e48fc58-api8080-*.sql.gz`（两份，本次部署自动生成） |
| 迁移回滚脚本     | `U1209__backfill_missing_project_archive_files.sql`                       |
| Nginx 配置锚点 | `/etc/nginx/conf.d/xiyu-bid.conf.bak-20260817`（未变更）                       |
| 回滚 owner   | 部署执行 Agent（trae）                                                          |

回滚状态：**就绪，未需要**。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：保留（第 13/14/15 次部署用户决定，测试环境常态配置）。

- 无其他 SHOW\_DETAILS/DEBUG/TRACE 临时调试配置。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）

- [x] 早操三连 + 基线零差异

- [x] Flyway 预检 3 步通过（含迁移目录同步修复）

- [x] 产物校验（迁移无重复 / obsEnabled / 同源构建）

- [x] DB 备份先行

- [x] 服务重启 + 健康 UP

- [x] 迁移应用验证（V1209）

- [x] Smoke 全绿

- [x] 旧 assets 跨版本保留

- [x] 部署报告归档

## 风险提示

- **两个预检类问题均在本次当场修复，但根治需代码改动**：① `deploy-test.sh` 固化迁移目录同步；② runner 权威路径收敛到 `bin/`。建议尽快开治理任务，避免下次部署重复踩坑。

- 服务器 `/opt/xiyu-bid/incoming/` 历史部署包（9.1G+）建议另行清理（保留最近 N 版 + 回滚锚点版本）。

- V1209 为幂等回填（LEFT JOIN 防重），非破坏性；回滚依赖 U1209 + DB 备份。

