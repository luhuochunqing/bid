# 第 104 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `d82a9ec26` |
| 上一版本 | `07b34a932`（第 103 次，2026-07-20 20:19） |
| 部署时间 | 2026-07-20 22:31 CST |
| 增量 | 17 个 commit（workbench-calendar CO-593/594/596 系列 + CO-582 Word 合订本修复 + 文档） |
| 新增迁移 | 无（最新仍为 V1173） |
| 部署结果 | ✅ 成功（健康检查 4 分 24 秒通过，含 Kafka SDK readiness 延迟，已知行为） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用 |

## 部署原因

第 103 次部署修复 V1173 迁移 SQL 列名错误后，本次部署增量合入工作台日历模块多轮 Google Code Review 修复（CO-593/594/596）+ Word 合订本标题样式修复（CO-582 §3.4），保持测试环境与 main 一致。

## 修复内容

### 1. 工作台日历模块 CO-593/594/596 系列修复（PR !2169/!2170/!2171）

- **CO-593 follow-up**（!2169）：开标模块跳转改为标讯详情
- **CO-594 bugfix**（!2170）：修复翻月弹回 + 标讯事件下钻到标讯详情
  - Google Code Review 修复 - eventType 大小写 bug + 补测试 + 抽顶层常量
  - demo tender fallback 对齐 + 抽取 isRealTenderId + 简化无事件分支
- **CO-596**（!2171）：项目待办 status/stage 字段混淆 + 4 卡片滚动条优化
  - 设计弯路修复 - CSS 规则合并 + 日志降级
  - Google Code Review - 修复测试注释错误
  - 对齐 ControllerTest mock 数据与 CO-593 follow-up

### 2. CO-582 §3.4 Word 合订本标题样式修复（PR !2168）

- 修复 Word 合订本标题未应用 pStyle 导致导航窗格为空

### 3. 文档（PR !2166/!2167）

- 第 103 次测试环境部署报告
- lessons-learned §77 V1173 迁移 SQL 列名错误教训（MySQL 保留字 `size` 陷阱）

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| Worktree | trae（主工作区） |
| 分支 | `agent/trae-init`（锚点分支，ff-only 同步） |
| 同步前 HEAD | `df2570cd0` |
| 同步后 HEAD | `d82a9ec26`（同 `origin/main`） |
| GitHub 镜像同步前落后 | 11 commits |
| GitHub 镜像同步后 | 完全一致 |
| 工作区状态 | 干净（仅 1 个未跟踪 `e2e/playwright-chrome.config.js`） |
| 本地门禁 | 7/7 通过（pre-commit/pre-push hook、git 包装器、15 道门禁入口、agent-locks） |

## PR 列表（增量 17 commits 内）

| PR | 标题 | 类型 |
|---|---|---|
| !2166 | docs(release): 第 103 次测试环境部署报告 (test) | docs |
| !2167 | docs(lessons): §77 V1173 迁移 SQL 列名错误教训（MySQL 保留字 size 陷阱） | docs |
| !2168 | fix(warehouse-word): 修复 Word 合订本标题未应用 pStyle 导致导航窗格为空（CO-582 §3.4） | fix |
| !2169 | fix(workbench): 开标模块跳转改为标讯详情 (CO-593 follow-up) | fix |
| !2170 | fix(workbench-calendar): 修复翻月弹回 + 标讯事件下钻到标讯详情（CO-594 bugfix） | fix |
| !2171 | fix(workbench): CO-596 项目待办 status/stage 字段混淆 + 4 卡片滚动条优化 | fix |

## 改动范围

| 模块 | 文件 | 说明 |
|---|---|---|
| 后端 | `WorkbenchDeadlineQueryService.java` / `WorkbenchProjectTodoQueryService.java` | CO-596 待办 status/stage 字段对齐 |
| 后端测试 | `WorkbenchDeadlineControllerTest.java` / `WorkbenchDeadlineQueryServiceTest.java` / `WorkbenchProjectTodoQueryServiceTest.java` | 测试数据对齐 CO-593 follow-up |
| Spec Kit | `specs/2026-07-19-workbench-deadline-overhaul/spec.md` | CO-596 规格微调 |
| 前端 | `src/views/Dashboard/Workbench.vue` / `useWorkbenchRoleTodos.js` / `useWorkbenchSchedule.js` / `workbench-rebuild-core.js` / `workbench-utils.js` | CO-593/594/596 系列前端修复 |
| 前端样式 | `src/views/Dashboard/styles/workbench-08-rebuild.css` | 4 卡片滚动条优化 |
| 前端测试 | `workbench-utils.spec.js` / `useWorkbenchSchedule.spec.js` / `workbench-rebuild-core.spec.js` | 新增/补全测试 |
| 文档 | `docs/release/deploy-report-2026-07-20-103rd-test.md` / `docs/lessons/lessons-learned.md` | 第 103 次报告 + §77 教训 |

## Flyway 预检 3 步法

| 步骤 | 命令 | 结果 |
|---|---|---|
| Step 1: validate | `bash /opt/xiyu-bid/bin/flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（236 migrations 对齐） |
| Step 2: DB 版本对比 | 查询 `flyway_schema_history` 最新 5 条 | ✅ 最新已应用 V1173（2026-07-20 20:19:05），与源码最新版本一致 |
| Step 3: remote-deploy 内置 | `remote-deploy.sh` 在激活新 jar 前跑 validate | ✅ `Successfully validated 236 migrations` |

## 部署步骤

| # | 步骤 | 结果 |
|---|---|---|
| 0 | 环境门禁（AskUserQuestion 确认） | ✅ 用户确认测试环境 172.16.38.78 |
| 1 | 早操三连（sync-env + check-git-wrapper） | ✅ git wrapper 生效，HEAD=`d82a9ec26` |
| 2 | 基线确认 | ✅ HEAD=origin/main，GitHub 镜像落后 11 commits |
| 3 | 服务器现状 | ✅ 上一版本 `07b34a932` 健康 UP |
| 4 | Flyway 预检 3 步 | ✅ 全绿 |
| 5 | 本地打包 `RELEASE_ID=d82a9ec26` | ✅ BUILD SUCCESS（29.259s） |
| 6 | 产物校验 | ✅ obsEnabled=true / apiBaseUrl="" / 235 个 V*.sql 无重复 / 前端入口 `assets/index-DnfSGxs9.js` |
| 7 | scp + remote-deploy.sh | ✅ Flyway validate 通过，jar 覆盖，systemctl restart |
| 7.5 | 前端资源保留 | ⚠️ `deployed-release.json` 的 `releaseDir` 字段为空（已知缺陷），手动从 `/opt/xiyu-bid/releases/07b34a932/frontend/assets/` cp -rn 保留 |
| 8 | 健康检查 | ✅ 第 1 次轮询通过（22:35:25，约 4 分 24 秒，Kafka SDK readiness 延迟已知行为） |
| 9 | 迁移应用验证 | N/A（本次无新增迁移） |
| 10 | Smoke 测试 | ✅ 全绿 |
| 11 | GitHub 镜像同步 | ✅ 两边 main 完全一致 `d82a9ec26` |
| 12 | 配置清理检查 | ✅ 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决定保留，第 13/14/15 次已确认） |

## 验证结果

### Health（经 Nginx 8080 代理）

```
GET /actuator/health → HTTP 200
{"status":"UP","components":{"aiProvider":"UP","db":"UP","diskSpace":"UP","jwt":"UP","livenessState":"UP","ping":"UP","readinessState":"UP","redis":"UP","sidecar":"UP"}}
```

### Smoke（接口路由验证）

| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `GET /actuator/health` | 200 UP | 200 UP | ✅ |
| `GET /actuator/health/readiness` | 200 UP | 200 UP（readinessState: UP） | ✅ |
| `POST /api/auth/login` (空 body) | 400 | 400 | ✅ |
| `GET /api/projects` | 403 | 403 | ✅ |
| `GET /api/integration/crm/health` | 401 | 401 | ✅ |
| `GET /` | 200 | 200 | ✅ |
| `GET /login` | 200 | 200 | ✅ |
| 前端入口 assets hash | `assets/index-DnfSGxs9.js` | 与 release 一致 | ✅ |

## GitHub 镜像同步

```
Gitee main:  d82a9ec2683778cf7409f06b060e6607a7c4e8d1
GitHub main: d82a9ec2683778cf7409f06b060e6607a7c4e8d1
状态: 完全一致
```

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚 posture | ready（未需要执行） |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/07b34a932/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/07b34a932/backend/app.jar` |
| 上一版本前端 | `/opt/xiyu-bid/releases/07b34a932/frontend/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-d82a9ec26-<timestamp>.sql.gz`（remote-deploy.sh 自动备份） |
| 回滚命令 | `sudo cp /opt/xiyu-bid/releases/07b34a932/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用情况

| 教训 | 应用情况 |
|---|---|
| §1 Flyway 预检 3 步法 | ✅ 严格执行，全绿 |
| §2 Kafka SDK readiness 延迟 | ✅ 已知行为，4 分 24 秒后自恢复 |
| §3 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| §10 OBS 直传漏传 VITE_OBS_ENABLED=true | ✅ 显式传入 `VITE_OBS_ENABLED=true`，产物校验 obsEnabled=true |
| §15 macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1` |
| §18 前端 hash 资源跨版本 404 | ⚠️ `deployed-release.json.releaseDir` 字段为空（已知缺陷），手动从上一版本目录 `cp -rn` 保留 assets |

## 风险提示

1. **前端 hash 资源跨版本 404 风险（已缓解）**：本次部署前端入口 hash 从 `assets/index-<prev>.js` 变为 `assets/index-DnfSGxs9.js`，已从上一版本 `07b34a932` 目录 `cp -rn` 保留旧 assets，旧标签页可自然刷新。24h 后建议清理过期资源。
2. **`deployed-release.json.releaseDir` 字段为空（已知缺陷）**：导致 `remote-deploy.sh` 后的自动保留 assets 步骤失效，需手动从上一版本目录复制。建议后续修复 `remote-deploy.sh` 写入 `releaseDir` 字段的逻辑。
3. **GitHub 镜像同步前落后 11 commits**：本次同步后已对齐，但每次部署都需检查。
4. **无新增迁移**：本次部署纯代码变更，DB 状态稳定（V1173 已在第 103 次部署应用）。

## 部署确认清单

- [x] 环境门禁通过（AskUserQuestion 用户确认）
- [x] 基线对齐 `origin/main`（HEAD=`d82a9ec26`）
- [x] Flyway 预检 3 步全绿
- [x] 本地打包成功（BUILD SUCCESS，obsEnabled=true）
- [x] 产物校验通过（jar 内无重复 V*.sql，前端 hash 与 release 一致）
- [x] remote-deploy.sh 成功（Flyway validate 通过，jar 覆盖，systemctl restart）
- [x] 前端资源保留（手动从 07b34a932 cp -rn）
- [x] 健康检查通过（4 分 24 秒，readinessState: UP）
- [x] Smoke 测试全绿（health/readiness/3 接口路由/前端页面）
- [x] GitHub 镜像同步（两边 main 完全一致）
- [x] 配置清理检查（仅历史决定保留配置）
- [x] 部署报告生成（本文件）

## 部署总结

第 104 次测试环境部署成功。本次为纯代码部署（无新增迁移），核心是工作台日历模块多轮 Google Code Review 修复（CO-593/594/596）+ Word 合订本标题样式修复（CO-582 §3.4）。部署过程顺利，Kafka SDK readiness 延迟 4 分 24 秒符合预期，Smoke 全绿，GitHub 镜像已对齐。
