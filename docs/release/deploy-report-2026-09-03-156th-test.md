# 第 156 次部署报告（test 环境）

**日期**：2026-09-03
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`c3fb854d1`
**部署时间**：21:45 - 21:53 CST
**状态**：✅ 成功

## 部署概览

- 增量 16 个 commit（9 条 PR），新增 **1 个 Flyway 迁移**（V1210 修复 V1209 失败迁移，DB V1209 → 源码 V1210）。

- 本次以修复类改动为主：

  - **资质智能匹配 0 匹配 Bug 修复 + 招标文件三级穿透**（PR !2531）：打通招标文件三级穿透并行下发结构化比对清单

  - **V1210 修复 V1209 失败迁移**（PR !2529，CO-606）：`project_archive` 漏写 `updated_at`，全新版本号复刻回填逻辑并补全字段

  - **spec059 1358 漏识别热修复**（PR !2525/!2528）：REVIEW\_REQUIRED 仍落库 + WeightSumCheck 无成员大类守卫

  - **资质核查 AI Slop 清理 + 统一设计令牌**（PR !2530 内其他 commit）

- Flyway 预检及迁移验证全部通过（270 项迁移全量校验，Checksum 100% 匹配）。

## 基线信息

| 项目        | 值                                                   |
| --------- | --------------------------------------------------- |
| 基线 commit | `c3fb854d1`（origin/main，锚点分支 ff-only 同步，零差异）        |
| 前次部署      | `f9e48fc58`（2026-09-03 16:06 激活，第 155 次部署）          |
| 增量 commit | 16 个（`f9e48fc58..c3fb854d1`）                        |
| 新增迁移      | 1 个（V1210，幂等回填，配套 U1210 回滚脚本）                       |
| 工作区状态     | 干净                                                  |
| 早操三连      | 锚点分支 ff-only 同步至 `c3fb854d1` + check-git-wrapper 通过 |

## 改动范围（16 个增量 commit / 9 条 PR）

| PR    | 描述                                                        |
| ----- | --------------------------------------------------------- |
| !2525 | fix(spec059): REVIEW\_REQUIRED 仍落库 + WeightSumCheck 守卫    |
| !2526 | fix(scripts): remote-deploy FLYWAY\_REPAIR\_RUNNER 权威路径收敛 |
| !2527 | docs(release): 第 155 次部署报告 (test)                         |
| !2528 | test(spec059): C 真实 1358 形态端到端金标（根治循环验证）                  |
| !2529 | fix(CO-606): 新增 V1210 修复 V1209 失败迁移                       |
| !2530 | docs(CO-606): 修正 V1209 失败迁移 repair 操作说明                   |
| !2531 | fix(quality): 资质匹配 0 匹配 Bug + 招标文件三级穿透                    |

V1210 回填迁移配套回滚脚本 `U1210__fix_v1209_archive_backfill_updated_at.sql`。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → 通过，270 migrations，VALIDATE OK - all checksums match。
**Step 2**：DB 最新 applied = V1209（`success=1`，2026-09-03 16:06 已应用），源码最高版本 = V1210 → 本次待应用 V1210。
**Step 3**：remote-deploy.sh 内置 validate → 通过（本项目迁移目录已同步至最新，未复发第 155 次的 runner 旧版残留问题）。

## 部署步骤时间线（CST）

| 时间          | 步骤                                                              |
| ----------- | --------------------------------------------------------------- |
| 21:45       | 锚点分支 ff-only 同步至 `c3fb854d1`（origin/main 零差异，工作区干净）             |
| 21:45       | 服务器现状探测（前次 release `f9e48fc58`，health UP）                       |
| 21:46       | Flyway 预检：validate 通过（270 migrations）+ DB V1209 success=1       |
| 21:49-21:51 | 本地打包完成（161M，OBS obsEnabled=true）+ scp 上传                        |
| 21:52       | remote-deploy 执行：DB 备份 → Frontend 激活 → 覆盖 jar → systemctl start |
| 21:52:25    | V1210 应用（success=1）                                             |
| 21:52       | 脚本内置健康检查 145 attempts 凑齐连续 3 次 UP（Kafka readiness 延迟在窗口内覆盖）     |
| 21:53+      | Smoke 全绿 + OBS 校验 + 旧 assets 保留 + 配置清理检查                        |

## 验证结果

**后端健康（127.0.0.1:18080 与 Nginx 8080 双路径）**：`{"status":"UP"}`，readiness 200。

**迁移应用验证**：V1210 `success=1`，`installed_on=2026-09-03 21:52:25`。

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

- 线上入口：`assets/index-CUdWmPCP.js` 与构建产物完全一致。

- 旧版本 hash 资源已从 `releases/f9e48fc58-api8080/` 以 `cp -rn` 保留到 `/srv/www/xiyu-bid/assets/`（防跨版本 404，保留 24h 惯例）。

**产物与特性校验**：

- `release-metadata.json`：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）。

- OBS 直传校验：Detail chunk `.upload(` 调用数 = 2。

- jar 内迁移文件含 V1210，共 270 个迁移版本，无重复。

- 无 macOS `._*` 残留文件（`COPYFILE_DISABLE=1` 生效）。

## 本次异常与处置

**无阻塞性异常。** V1210 成功应用，服务未中断。第 155 次的 runner 旧版残留问题已由第 156 次的基线（含 PR !2526 权威路径收敛 + 迁移目录已更新）避免复发。

**关于 V1210 与 V1209**：V1209 在测试环境已 `success=1`（该环境实际未遭遇失败，V1209 失败发生在仓库开发侧）。V1210 复刻 V1209 逻辑并补全 `project_archive.updated_at`，本次在测试环境正常执行（LEFT JOIN 幂等，未产生重复行）。

## 回滚信息

| 锚点         | 位置                                                             |
| ---------- | -------------------------------------------------------------- |
| 前后端制品      | `/opt/xiyu-bid/releases/f9e48fc58-api8080/`（jar + frontend）    |
| DB 备份      | `/opt/xiyu-bid/db-backups/winbid-c3fb854d1-*.sql.gz`（本次部署自动生成） |
| 迁移回滚脚本     | `U1210__fix_v1209_archive_backfill_updated_at.sql`             |
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

- [x] 迁移应用验证（V1210）

- [x] Smoke 全绿

- [x] 旧 assets 跨版本保留

- [x] 部署报告归档

## 风险提示

- V1210 为幂等回填（LEFT JOIN 防重），非破坏性；回滚依赖 U1210 + DB 备份。

- 服务器 `/opt/xiyu-bid/incoming/` 历史部署包（9.1G+）与 `releases/` 旧版目录建议另行清理（保留最近 N 版 + 回滚锚点版本）。

- 资质智能匹配为复杂业务逻辑（LCS/停用词/三级穿透），建议后续在测试环境用真实标书做一次端到端核验。

