# 第 122 次测试环境部署报告 — 2026-08-12

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 122 次（测试） |
| 部署时间 | 2026-08-12 11:39:24 CST（服务启动） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `0ef90d10c` |
| 上一版本 Release | `a3e034934`（2026-08-12 03:25:02 CST，第 121 次测试部署） |
| 基线 commit | `0ef90d10c`（基于 origin/main rebase 后） |
| 激活时间 | 2026-08-12T11:39:24 CST（systemd 启动） |
| 健康检查通过 | 2026-08-12T11:39:48 CST（启动后 24 秒） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 增量 commit | 2 个（相对上一版本） |
| 新增 Flyway 迁移 | 0 个（V1185 已在先前启动时应用） |
| Smoke 测试 | 6 项全部通过 |
| GitHub 镜像 | 待主工作区同步 |

## 背景

本次部署为【标讯创建事件推送西域 CRM 事件总线】功能（`feat-tender-event-push`）。

上一版本 `a3e034934` 部署后后端 crash-loop，根因：`TenderEventPayloadMapper` 未注册为 Spring Bean，`TenderEventPublishService` 构造函数注入失败。已修复 Bean 注册并重新部署。

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/qoder`
- 任务分支：`agent/qoder/feat-tender-event-push`
- 早操：sync-env.sh 首次因 `.agents/skills`、`skills` 环境噪音文件 stash/rebase 失败，已恢复已跟踪噪音文件后手动 `git rebase origin/main` 成功（对齐 origin/main，领先 4 commit）
- pre-push 门禁：通过（`git push --force-with-lease` 推送任务分支）

## 增量改动（a3e034934 → 0ef90d10c，2 个 commit）

### 关键改动能

| Commit | 描述 |
|---|---|
| 9a7509e2b | fix: TenderEventLogWriter 补 @Service 注解，修复找不到 TenderEventLogPort bean（上一版本已含） |
| 0ef90d10c | fix: TenderEventPayloadMapper 补 @Service 注解，修复找不到 TenderEventPayloadMapper bean（本次修复） |

### 改动范围聚合

| 目录 | 主要内容 |
|---|---|
| backend/integration/tenderevent/application | TenderEventPayloadMapper 类加 `@Service` 注解 |
| backend/integration/tenderevent/infrastructure/persistence | TenderEventLogWriter 类加 `@Service` 注解 |

> 完整功能（Feat `a0f0a79c4`：标讯创建事件推送 + V1185 迁移 + 事件流水表 + 编排服务）已在第 121 次部署时打包，本次仅为 Bean 注册修复补发。

## 环境变量注入（测试部署配置）

以下变量已写入 `/etc/xiyu-bid/backend.env`（本次部署前已存在，无需重复注入）：

| 变量 | 值 | 说明 |
|---|---|---|
| `XIYU_TENDER_EVENT_SDK_ENABLED` | `true` | 启用标讯事件推送 SDK |
| `XIYU_TENDER_EVENT_SERVER_REGISTER_URL` | `http://event-busserver-test.ehsy.com` | 对方事件总线地址 |
| `XIYU_TENDER_EVENT_SERVICE_NAME` | `bid` | service-name（默认） |

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（246 migrations） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 |

**V1185 迁移说明**：`tender_event_logs` 表迁移文件在上一版本（a3e034934）启动时已应罁，本次部署 DB 中 `V1185 success=1`（installed_on 2026-08-12 11:17:47）。本次部署无新增迁移文件。

## 部署步骤

1. ✅ 修复 `TenderEventPayloadMapper` Bean 注册（加 `@Service`）
2. ✅ commit + 早操（rebase origin/main，处理环境噪音 stash 冲突）
3. ✅ 本地打包（RELEASE_ID=0ef90d10c, VITE_API_BASE_URL=, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1）
4. ✅ 产物校验（jar 迁移无重复、V1185 存在、obsEnabled=true、Detail .upload(=2、index 入口 index-C5uNUZJc.js）
5. ✅ scp 上传到 `/opt/xiyu-bid/incoming/`（archive + remote-deploy.sh）
6. ✅ remote-deploy.sh 执行（SYSTEMCTL_SUDO=true）
7. ✅ Flyway validate 通过（246 migrations validated）
8. ✅ DB 备份完成（`winbid-0ef90d10c-*.sql.gz`）
9. ✅ 后端服务重启（active/running，PID 31758，2026-08-12 11:39:24 CST）
10. ✅ 健康检查通过（3/3，最快一次，无 Kafka 延迟）
11. ✅ 前端一致性验证（assets/index-C5uNUZJc.js）

## 验证结果

### 后端启动验证

- `Started XiyuBidApplication in 23.055 seconds`（无 ERROR / APPLICATION FAILED）
- systemd 无持续重启（crash-loop 已消除）

### Smoke 测试（6 项全通过）

| # | 测试 | 结果 | 备注 |
|---|---|---|---|
| 1 | /actuator/health | ✅ HTTP 200 | UP |
| 2 | /actuator/health/readiness | ✅ HTTP 200 | UP |
| 3 | POST /api/auth/login（空 body） | ✅ HTTP 400 | 参数校验 |
| 4 | GET /api/projects（无认证） | ✅ HTTP 403 | 需认证 |
| 5 | 前端首页 `/` | ✅ HTTP 200 | — |
| 6 | 前端 /login | ✅ HTTP 200 | — |

### 迁移应用验证

| version | description | success | installed_on |
|---|---|---|---|
| 1185 | create tender event logs | 1 | 2026-08-12 11:17:47 |
| 1184 | create performance export task | 1 | 2026-08-04 20:54:47 |

`tender_event_logs` 表已创建，当前 0 行（等待标讯创建触发后写入）。

## 事件推送功能说明

- 功能开关：`XIYU_TENDER_EVENT_SDK_ENABLED=true` 已生效
- 事件总线地址：`http://event-busserver-test.ehsy.com`
- 触发点：标讯创建（人工录入 / 第三方平台 / 批量导入）时异步推送
- `tender_event_logs` 表用于记录事件推送结果，供联调验证
- **联调验证方式**：在测试环境新建一条标讯，随后查询 `tender_event_logs` 表确认是否有推送记录，或在事件总线侧确认收到消息

## GitHub 同步

- 本部署涉及任务分支 `agent/qoder/feat-tender-event-push`，尚未合入 main
- 待 PR 合入 main 后，在主工作区 trae 执行 `bash scripts/sync-to-github.sh` 同步镜像

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release ID | `a3e034934` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-0ef90d10c-*.sql.gz` |
| 回滚命令 | `ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/a3e034934/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'` |

> ⚠️ 注意：上一版本 `a3e034934` 因 Bean 缺失会 crash-loop，回滚到该版本会恢复故障。若需回滚，应回滚到更早的已知良版本（如 `2200b834f`，第 121 次测试部署前版本）。

## 经验沉淀应用

- ✅ Flyway 预检 3 步法（经验 #1）
- ✅ OBS 直传显式传入 VITE_OBS_ENABLED=true（经验 #10）
- ✅ COPYFILE_DISABLE=1 避免 macOS `._*` 残留（经验 #14）
- ✅ SYSTEMCTL_SUDO=true（经验 #8）
- ✅ --noproxy '*' 避免 Mac HTTP_PROXY 502（经验 #16）
- ✅ 凭日志根因定位 Bean 注入失败（`No qualifying bean ... TenderEventPayloadMapper`）

## 风险提示

1. **新迁移类 Bean 注册遗漏**：新增 `@Service`/`@Component` 依赖注入的类若漏加注解，会导致 Spring 启动失败 crash-loop。本次连续两次（TenderEventLogWriter、TenderEventPayloadMapper）出现同类问题。**改进方向**：新增被注入的类时，首选 `@Service` 注解并本地 `mvn test` 前先 `mvn compile` + 启动验证 Bean 装配。
2. **环境噪音文件干扰早操**：worktree 内 `.agents/skills`、`skills` 等目录存在大量 M/D/?? 变更，导致 sync-env.sh 的 stash/rebase 失败。**改进方向**：这些目录应加入 `.gitignore` 或保持工作区干净，避免干扰 main-forward rebase。
3. **回滚锚点需注意**：上一版本 `a3e034934` 为故障版本，回滚预案应指向更早已知良版本。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连通过（含手动处理环境噪音 rebase）
- [x] Flyway 预检通过（3 步法）
- [x] 打包产物校验通过（jar 迁移无重复 + OBS obsEnabled=true + Detail .upload(=2）
- [x] 部署成功（jar 覆盖 + 服务重启）
- [x] 健康检查通过（启动后 24 秒）
- [x] Smoke 测试通过（6/6）
- [x] 迁移应用验证（V1185 success=1，tender_event_logs 表已建）
- [x] 事件功能环境变量注入（ENABLED=true + 事件总线地址 + service-name）
- [x] 部署报告生成

---

## 变更记录

| 日期 | 变更内容 |
|------|------|
| 2026-08-12 | 首次创建，基于第 122 次测试环境部署结果 |