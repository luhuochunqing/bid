# 第 121 次测试环境部署报告 — 2026-08-11

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 121 次（测试） |
| 部署时间 | 2026-08-11 21:04:13 CST（服务启动） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `f7589eff6` |
| 上一版本 Release | `2200b834f`（2026-08-07 19:15:38 CST，第 120 次测试部署） |
| 基线 commit | `f7589eff6`（origin/main） |
| 激活时间 | 2026-08-11T21:04:13 CST（systemd 启动） |
| 健康检查通过 | 2026-08-11T21:08:41 CST（启动后 4 分 28 秒） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 增量 commit | 10 个 |
| 新增 Flyway 迁移 | 0 个 |
| Smoke 测试 | 8 项全部通过 |
| GitHub 镜像 | ✅ 已同步（两边完全一致） |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- 锚点分支：`agent/trae-init`
- 工作区状态：HEAD = origin/main = `f7589eff6`（agent/trae-init ff-only 同步）
- 早操三连：sync-env.sh 门禁 7/7 通过
- git-wrapper：未激活（不影响本次部署，不涉及 git push 代码）

## 增量改动（2200b834f → f7589eff6，10 个 commit）

### 关键 PR 列表

| PR | 描述 | 关联 |
|---|---|---|
| !2280 | fix: 补齐 bid-SystemAdmin 角色上传/删除文档白名单 | CO-373 角色权限系列（PR #2280 squash merge sha 3d7d6b5） |
| !2282 | fix(performance): 修复业绩合订本导出跨页勾选丢失 ids | 业绩合订本导出 |
| !2277 | docs(release): 第 17 次生产环境部署报告 | 文档 |
| !2276 | docs(release): 第 120 次测试环境部署报告 | 文档 |

### 其他改动

| Commit | 描述 |
|---|---|
| 8bd106ca0 | docs: lessons-learned §110 — 新增角色必须同步所有角色白名单 |
| 99f8e6282 | docs(wiki): 回填 §14 el-table 跨页勾选丢失 ids 根因 |

### 改动范围聚合

| 目录 | 主要内容 |
|---|---|
| backend/security | ProjectDocumentWorkflowPolicy.java — bid-SystemAdmin 角色白名单补齐 |
| backend/performance | 业绩合订本导出跨页勾选 ids 丢失修复 |
| backend/src/test | ProjectDocumentWorkflowPolicyTest 测试覆盖 |
| docs/ | lessons-learned §110、wiki §14 回填、第 17 次 prod + 第 120 次 test 部署报告 |
| .wiki/pages/ | roles-and-permissions.md 角色权限表回填 |

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（245 migrations） |
| Step 2: DB 最近迁移版本 | V1184（create performance export task，2026-08-04） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 |

源码迁移最新版本 V1184 = DB 已应用 V1184，版本一致。**无新增 Flyway 迁移文件。**

## 部署步骤

1. ✅ 本地打包（RELEASE_ID=f7589eff6, VITE_API_BASE_URL=, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1）
2. ✅ 产物校验（jar 内 244 个 V*.sql + B73 基线 = 245 migrations，OBS 直传已启用 obsEnabled=true，Detail chunk .upload( 调用数=2，无 macOS `._*` 残留）
3. ✅ scp 上传到 `/opt/xiyu-bid/incoming/`（archive 154M）
4. ✅ remote-deploy.sh 执行（SYSTEMCTL_SUDO=true）
5. ✅ Flyway validate 通过（245 migrations validated, execution time 00:00.077s）
6. ✅ DB 备份完成（`winbid-f7589eff6-20260811210302.sql.gz`）
7. ✅ 后端服务重启（active/running，PID 8729，2026-08-11 21:04:13 CST）
8. ⚠️ remote-deploy.sh 健康检查脚本 120 次超时（Kafka SDK readiness 延迟）
9. ✅ 手动健康检查通过（2026-08-11 21:08:41 CST，启动后 4 分 28 秒）
10. ✅ 前端一致性验证（assets/index-ayUbZQ1Y.js）
11. ⚠️ 上一版本 assets 保留失败（PREV 解析为空，因 deployed-release.json 已被覆盖；上一版本 release 目录 `/opt/xiyu-bid/releases/2200b834f` 仍存在）

## 验证结果

### Smoke 测试（8 项全通过）

| # | 测试 | 结果 | 备注 |
|---|---|---|---|
| 1 | /actuator/health | ✅ HTTP 200 | UP（所有组件 UP） |
| 2 | /actuator/health/readiness | ✅ HTTP 200 | UP（已恢复） |
| 3 | POST /api/auth/login（空 body） | ✅ HTTP 400 | 参数校验 |
| 4 | GET /api/projects（无认证） | ✅ HTTP 403 | 需认证 |
| 5 | GET /api/integration/crm/health（无认证） | ✅ HTTP 401 | 需认证 |
| 6 | 前端首页 `/` | ✅ HTTP 200 | — |
| 7 | 前端 /login | ✅ HTTP 200 | — |
| 8 | 前端 assets hash | ✅ assets/index-ayUbZQ1Y.js | 与 release 一致 |

### 迁移应用验证

DB 最新 5 条迁移记录（均 success=1）：

| version | description | installed_on |
|---|---|---|
| 1184 | create performance export task | 2026-08-04 20:54:47 |
| 1183 | add custom fields to project tables | 2026-08-02 09:52:53 |
| 1182 | remove unused form definitions | 2026-08-02 09:52:53 |
| 1181 | cleanup audit logs project id | 2026-07-29 18:31:24 |
| 1180 | add knowledge sub permissions | 2026-07-26 23:28:28 |

本次无新迁移应用，与部署前一致。

## Kafka SDK readiness 延迟（已知行为）

| 阶段 | 时间 | 现象 |
|---|---|---|
| 服务启动 | 21:04:13 | systemd active (running) |
| readiness 503 持续 | 21:04:13 ~ 21:08:41 | readinessState=OUT_OF_SERVICE，其他组件 UP |
| 恢复 | 21:08:41 | readinessState=UP，HTTP 200 |

**根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`，Kafka 初始化阻塞主线程导致 `AvailabilityChangeEvent` 延迟处理。

**历史出现**：第 8、9、10、13、15 次均出现，已沉淀为已知行为（skill Lesson #2）。本次延迟 4 分 28 秒，在历史范围 2-5 分钟内。

**业务影响**：readiness 503 期间业务接口实际可用（日志显示 21:07:40 admin 用户 `GET /api/notifications/unread-count` 返回 200），仅 readiness probe 受影响。

## GitHub 同步

- 当前状态：✅ 两侧 main 完全一致（`f7589eff6`）
- 处理方式：已在主工作区 trae 执行 `bash scripts/sync-to-github.sh`
- 同步前：GitHub main 落后 Gitee main 8 个 commit
- 同步后：两边 HEAD = `f7589eff63a8fb9daf8fcd5ba2788a12e183a933`

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release ID | `2200b834f` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/2200b834f` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-f7589eff6-20260811210302.sql.gz` |
| 回滚命令 | `ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/2200b834f/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'` |

本次无 DB 迁移，回滚无需 Flyway 干预。

## 经验沉淀应用

- ✅ Flyway 预检 3 步法（经验 #1）
- ✅ OBS 直传显式传入 VITE_OBS_ENABLED=true（经验 #10）
- ✅ COPYFILE_DISABLE=1 避免 macOS `._*` 残留（经验 #14）
- ✅ SYSTEMCTL_SUDO=true（经验 #8）
- ✅ --noproxy '*' 避免 Mac HTTP_PROXY 502（经验 #16）
- ✅ 健康检查容忍 Kafka 延迟（经验 #2）—— 本次延迟 4 分 28 秒，手动验证恢复
- ⚠️ 前端 hash 资源跨版本 404 防护（经验 #18）—— 本次未成功保留上一版本 assets（PREV 解析为空），但新 assets 已就位，旧标签页可能存在 404 噪声

## 风险提示

1. **前端旧 assets 未保留**：本次部署未成功执行 `cp -rn` 保留上一版本 assets（`deployed-release.json` 已被 remote-deploy.sh 覆盖，PREV 解析为空）。若用户浏览器仍有未刷新的旧标签页，可能触发 `Unable to preload CSS` 404 噪声。**改进方向**：在 remote-deploy.sh 覆盖 deployed-release.json 之前先快照 PREV 值，或在 skill 模板中改为先 ssh 读取再覆盖。
2. **Kafka SDK readiness 延迟**：已知行为，本次 4 分 28 秒。若 remote-deploy.sh 健康检查脚本超时阈值（120 次 * 2 秒 = 240 秒）不够，可能误判为失败。**改进方向**：将健康检查超时从 240 秒提升到 300 秒（5 分钟），覆盖最坏情况。
3. **SHOW_DETAILS=always**：`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 为历史保留项（第 13-15 次部署已决定保留），非本次引入。
4. **git-wrapper 未激活**：本次 sync-env.sh 内部已 source dev-env.sh，但独立运行的 `check-git-wrapper.sh` 显示未激活。不影响本次部署（不涉及 git push 代码），但若后续需要 push 需先 `source scripts/dev-env.sh`。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] Flyway 预检通过（3 步法）
- [x] 打包产物校验通过（jar 迁移无重复 + OBS obsEnabled=true + Detail .upload(=2）
- [x] 部署成功（jar 覆盖 + 服务重启）
- [x] 健康检查通过（手动验证 readinessState UP）
- [x] Smoke 测试通过（8/8）
- [x] 迁移应用验证（无新迁移，DB V1184 与源码一致）
- [x] GitHub 镜像同步完成
- [x] 临时配置清理检查（仅 SHOW_DETAILS=always 历史保留项）
- [x] 部署报告生成

---

## 变更记录

| 日期 | 变更内容 |
|------|------|
| 2026-08-11 | 首次创建，基于第 121 次测试环境部署结果 |
