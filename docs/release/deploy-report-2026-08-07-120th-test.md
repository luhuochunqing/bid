# 第 120 次测试环境部署报告 — 2026-08-07

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 120 次（测试） |
| 部署时间 | 2026-08-07 19:15:38 CST |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `2200b834f` |
| 上一版本 Release | `373cb3625`（2026-08-05 18:28:26 CST，第 119 次测试部署） |
| 基线 commit | `2200b834f`（origin/main） |
| 激活时间 | 2026-08-07T19:15:38 CST |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 增量 commit | 9 个 |
| 新增 Flyway 迁移 | 0 个 |
| Smoke 测试 | 8 项全部通过 |
| GitHub 镜像 | ✅ 已同步（两边完全一致） |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- 锚点分支：`agent/trae-init`
- 工作区状态：干净，HEAD = origin/main = `2200b834f`
- 早操三连：sync-env.sh 门禁 7/7 通过
- VPN 连通性：首次探测超时（未连 VPN），用户连接 VPN 后恢复

## 增量改动（373cb3625 → 2200b834f，9 个 commit）

### 关键 PR 列表

| PR | 描述 | 关联 |
|---|---|---|
| !2274 | fix(workbench): 日历和截止时间模块重复项目显示问题（后端 + 前端去重） | 生产环境仪表盘重复显示 |
| !2275 | fix: 补齐 doc-governance 脚本 header 契约缺失 | 文档治理 |

### workbench 去重改动明细（!2274）

| 层次 | 文件 | 改动 |
|---|---|---|
| 后端 | WorkbenchScheduleQueryService.java | 日历事件去重（Tender 派生类型按 eventType+eventDate+title 业务键，手动事件按 id） |
| 后端 | WorkbenchDeadlineQueryService.java | 截止时间去重（date+name 业务键，deposit 保持原样） |
| 后端 | WorkbenchDeadlinePolicy.java | stats countByTimeWindow 时间戳去重（LinkedHashSet） |
| 前端 | useWorkbenchSchedule.js | 日历前端去重保险（与后端逻辑对称） |
| 前端 | workbench-deadline-core.js | 截止时间前端去重保险（仅 Tender 派生列表） |
| 测试 | 多个单测文件 | 去重纯函数单测（normalizeDedupList / buildDedupKey 等） |

### 改动范围聚合

| 目录 | 主要内容 |
|---|---|
| backend/workbench | 日历/截止时间去重逻辑（Schedule/Deadline QueryService + DeadlinePolicy） |
| src/views/Dashboard | useWorkbenchSchedule.js + workbench-deadline-core.js 前端去重保险 |
| backend/src/test | 去重纯函数单测 + 访问测试 |
| docs/ | 部署报告、lessons、tech-debt-tracker、wiki |
| scripts/ | 脚本 header 契约修复（doc-governance） |

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（245 migrations） |
| Step 2: DB 最近迁移版本 | V1184（create performance export task，2026-08-04） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 |

源码迁移最新版本 V1184 = DB 已应用 V1184，版本一致。**无新增 Flyway 迁移文件。**

## 部署步骤

1. ✅ 本地打包（RELEASE_ID=2200b834f, VITE_API_BASE_URL=, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1）
2. ✅ 产物校验（jar 内迁移无重复，OBS 直传已启用 obsEnabled=true，.upload( 调用数=2）
3. ✅ scp 上传到 `/opt/xiyu-bid/incoming/`
4. ✅ remote-deploy.sh 执行（SYSTEMCTL_SUDO=true）
5. ✅ Flyway validate 通过
6. ✅ 后端服务重启（active/running）
7. ✅ 健康检查通过（consecutive 3/3, total attempts: 80）
8. ✅ 前端一致性验证（assets/index-DmTMHDGK.js）
9. ✅ 上一版本 assets 保留（从 373cb3625 cp -rn 到 /srv/www/xiyu-bid/assets/，282 个文件）

## 验证结果

### Smoke 测试（8 项全通过）

| # | 测试 | 结果 | 备注 |
|---|---|---|---|
| 1 | /actuator/health | ✅ HTTP 200 | UP |
| 2 | /actuator/health/readiness | ✅ HTTP 200 | UP |
| 3 | POST /api/auth/login（空 body） | ✅ HTTP 400 | 参数校验 |
| 4 | GET /api/projects（无认证） | ✅ HTTP 403 | 需认证 |
| 5 | GET /api/integration/crm/health（无认证） | ✅ HTTP 401 | 需认证 |
| 6 | 前端首页 `/` | ✅ HTTP 200 | — |
| 7 | 前端 /login | ✅ HTTP 200 | — |
| 8 | 前端 assets hash | ✅ assets/index-DmTMHDGK.js | 与 release 一致 |

## GitHub 同步

- 当前状态：✅ 两侧 main 完全一致（`2200b834f`）
- 处理方式：已在主工作区 trae 执行 `bash scripts/sync-to-github.sh`（门禁 12/12 通过）

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release ID | `373cb3625` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/373cb3625` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-2200b834f-20260807191529.sql.gz` |
| 回滚命令 | `ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/373cb3625/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'` |

本次无 DB 迁移，回滚无需 Flyway 干预。

## 经验沉淀应用

- ✅ Flyway 预检 3 步法（经验 #1）
- ✅ OBS 直传显式传入 VITE_OBS_ENABLED=true（经验 #10）
- ✅ COPYFILE_DISABLE=1 避免 macOS `._*` 残留（经验 #14）
- ✅ SYSTEMCTL_SUDO=true（经验 #8）
- ✅ --noproxy '*' 避免 Mac HTTP_PROXY 502（经验 #16）
- ✅ 健康检查容忍 Kafka 延迟（经验 #2）
- ✅ 前端 hash 资源跨版本 404 防护：部署后保留上一版本 assets（经验 #18）

## 风险提示

1. **VPN 依赖**：测试服务器 172.16.38.78 需连接内网 VPN 才可达，部署前需确认 VPN 状态
2. **workbench 去重副作用**：业务键不含 id，同标题同日期不同标讯可能被误并去重（已知取舍，后续需数据清理 + 推送路径去重策略加固，已登记 lessons-learned §109 follow-up）
3. **SHOW_DETAILS=always**：`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 为历史保留项（第 13-15 次部署已决定保留），非本次引入

## 部署确认清单

- [x] 环境门禁确认（测试环境）
- [x] 早操三连通过
- [x] Flyway 预检通过
- [x] 打包产物校验通过
- [x] 部署成功
- [x] 健康检查通过
- [x] Smoke 测试通过
- [x] GitHub 镜像同步完成
- [x] 部署报告生成

---

## 变更记录

| 日期 | 变更内容 |
|------|------|
| 2026-08-07 | 首次创建，基于第 120 次测试环境部署结果 |