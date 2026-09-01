# 第 151 次部署报告（test 环境）

**日期**：2026-09-01
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`117b1e135-api8080`
**部署时间**：10:49 - 10:57 CST
**状态**：✅ 成功

## 部署概览

- 增量 28 个 commit（10 个 PR），包含 1 个新增 Flyway 迁移：`V1205__add_obs_bid_file_key_to_knowledge_case.sql`（DB 升至 V1205，与源码一致）。
- 本次包含重点业务功能与修复：
  - **历史案例 OBS 原版标书直链签发与 UI 三分流**（PR !2486）：后端 `/cases/{id}/download-url` 端点、UI 按 `obsBidFileKey` / `sourceProjectId` / 无原文实现三分流直链与打包下载。
  - **切片导入与数据规范**（PR !2485, !2483）：切片 JSONL 全文保留、历史切片一次性导入知识库、RDS 域名白名单与历史项目 `MANUAL_SINGLE` 存量标记识别。
  - **评分表格过滤与解析加固**（PR !2484, !2481）：评分表格双层过滤决策树、金标测试加固、源项目跳转路由修复、假 ZIP 下载防护、Project 21 MRO 工业品评分标准解析 100 分闭环。
  - **AI 标书核查与性能治理**（PR !2480, !2482）：放宽标书核查前端轮询超时上限并隔离未完成任务状态；清理 AI Slop 死代码并内联单组件专属小型 Composable。
  - **项目按招标主体搜索过滤**（PR !2478）：关键词搜索支持按 `ownerUnit` 招标主体过滤。
- Flyway 预检及迁移验证全部通过（266 项迁移全量校验，Checksum 100% 匹配）。
- 后端重启后健康检查通过（`{"status":"UP"}`，9 个组件全 UP），Nginx 8080 API Smoke 与前端入口校验全绿。

## 基线信息

| 项目 | 值 |
|------|-----|
| 基线 commit | `117b1e135`（origin/main，HEAD = origin/main 零差异） |
| 前次部署 | `00d7d13d5`（2026-08-31 16:24 激活，第 150 次） |
| 增量 commit | 28 个 |
| 新增迁移 | `V1205__add_obs_bid_file_key_to_knowledge_case.sql`（DB V1205 = 源码 V1205，success=1） |
| 工作区状态 | 干净（部署前临时 stash `scripts/import-historical-projects.py`，部署后已安全保留恢复） |
| 早操三连 | sync-env + check-git-wrapper 全过 |

## 改动范围（28 个增量 commit / 10 个 PR）

| PR | 描述 |
|----|------|
| !2486 | feat(case): 历史案例 OBS 原版标书签发下载端点 + UI 三分流 + V1205 迁移 + Spec 058 知识沉淀 |
| !2485 | fix(scripts): load_jsonl_slices 保留 text 全文键（修复全文未入库回退 300 字预览） |
| !2484 | fix(scoreparse): 评分表格过滤决策树/金标测试加固/源项目跳转路由修正/假 ZIP 下载防护 |
| !2483 | feat(case): 新增历史切片导入 knowledge_case 一次性脚本 + MANUAL_SINGLE 识别 + RDS 域名白名单 |
| !2482 | refactor(frontend/scoreparse): 清理 AI Slop 死代码并内联单组件专属小型 Composable |
| !2481 | fix(scoreparse): Project 21 MRO 工业品评分标准解析修复 - 100 分闭环 |
| !2480 | fix(compliance): 放宽标书质量核查前端轮询超时上限并隔离未完成任务状态 |
| !2479 | docs(wiki): 补记项目搜索按招标主体任务的 log.md 操作日志 |
| !2478 | feat(project): 项目关键词搜索支持按招标主体过滤 |
| !2477 | docs(release): 第 150 次部署报告 (test) |

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → 验证通过，266 migrations 校验完毕，VALIDATE OK - all checksums match。
**Step 2**：DB 执行迁移 `V1205`（`knowledge_case` 表新增 `obs_bid_file_key` 字段），与源码最高版本 V1205 一致，无 pending。
**Step 3**：remote-deploy.sh 内置 validate（jar 激活前）通过。

## 部署步骤时间线（CST）

| 时间 | 步骤 |
|------|------|
| 10:49 | 启动部署：确认基线与前置门禁 |
| 10:50 | 服务器现状探测（前次 release `00d7d13d5`，health UP） |
| 10:51 | Flyway 预检 3 步通过（validate exit 0，无 repair 需求） |
| 10:52 | 本地打包完成（前端 build + 后端 FlywayMysqlContainerTest 容器测试通过 + jar 内迁移版本无重复 + OBS 直传校验通过） |
| 10:53 | scp 压缩包至 `172.16.38.78` |
| 10:54 | remote-deploy 执行：DB 备份 → 前端激活 → 覆盖 jar → systemctl start |
| 10:56 | 健康检查连续 3 次 UP（total attempts: 81） |
| 10:57 | 前端一致性校验与 OBS 校验通过（`.upload(` 调用数 = 2） |
| 11:03 | 外部 Smoke 7 项全绿 + 旧版 assets `cp -rn` 保留完成 |

## 验证结果

**后端健康（127.0.0.1:18080 与 Nginx 8080 双路径）**：`{"status":"UP"}`，9 组件全 UP（aiProvider/db/diskSpace/jwt/liveness/ping/readiness/redis/sidecar）。

**API Smoke（经 Nginx 8080，`--noproxy '*'`）**：

| 接口 | 期望 | 实际 |
|------|------|------|
| `/actuator/health` | 200 UP | ✅ 200 UP |
| `/actuator/health/readiness` | 200 | ✅ 200 |
| `POST /api/auth/login`（空 body） | 400 | ✅ 400 |
| `/api/projects`（未认证） | 403 | ✅ 403 |
| `/api/integration/crm/health`（未认证） | 401 | ✅ 401 |

**前端 Smoke（经 Nginx 8080）**：
- 根路径 `/`：200
- 登录页 `/login`：200
- 线上入口：`src="/assets/index-BIoc5mRe.js"` 与构建产物完全一致。

**产物与特性校验**：
- `release-metadata.json`：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）。
- OBS 直传校验：Detail chunk `.upload(` 调用数 = 2。
- 旧版 assets 保留：已从 `00d7d13d5/frontend/assets` 执行 `cp -rn` 保留，防止跨版本 preload 404（经验 #17）。

## 回滚信息

| 锚点 | 位置 |
|------|------|
| 前后端制品 | `/opt/xiyu-bid/releases/00d7d13d5/`（jar + frontend） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-00d7d13d5-20260831162414.sql.gz` |
| Nginx 配置锚点 | `/etc/nginx/conf.d/xiyu-bid.conf.bak-20260817`（未变更） |
| 回滚 owner | 部署执行 Agent（trae） |

回滚状态：**就绪，未需要**。

## 经验沉淀应用情况

1. **经验 #2（Kafka SDK readiness 延迟）**：后端启动等待约 80 次（160 秒）后顺利 UP，未误判为启动失败。
2. **经验 #15（Mac HTTP_PROXY 502）**：全部 curl 均带 `--noproxy '*'`，绕过本机代理干扰。
3. **经验 #17（前端 hash 资源跨版本 404）**：部署后从上一版本 `00d7d13d5` 的 release 目录 `cp -rn` 保留旧 assets。
4. **经验 #7（SYSTEMCTL_SUDO）**：显式使用 sudo 执行服务启停，平滑无阻断。
5. **OBS 直传固化**：固化 `VITE_OBS_ENABLED=true`，经双重门禁校验通过，防止直传回退。

## 风险提示

- 本次包含数据库迁移 `V1205`，新增 `obs_bid_file_key` 可空字段，为纯增量无锁 DDL，已验证对现有读写无破坏性影响。
- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 按历史决策保留。

## 部署确认清单

- [x] 环境门禁：确认 test（172.16.38.78）
- [x] 早操三连 + 工作区干净 + HEAD=origin/main
- [x] Flyway 预检 3 步（validate / DB 对比 / 部署内置 validate）
- [x] 打包同源构建 + OBS 双保险 + 产物校验
- [x] remote-deploy（前端激活 → jar 覆盖 → 重启 → 健康检查）
- [x] 健康检查 UP + readiness 200
- [x] Smoke 全绿（5 API + 2 页面 + hash 一致）
- [x] 旧版 assets 保留
- [x] 临时配置检查（无新增）
- [x] 部署报告（本文件已生成）
