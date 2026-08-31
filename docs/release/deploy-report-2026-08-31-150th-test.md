# 第 150 次部署报告（test 环境）

**日期**：2026-08-31
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`00d7d13d5`
**部署时间**：16:18 - 16:28 CST
**状态**：✅ 成功

## 部署概览

- 增量 14 个 commit（6 个 PR），无新增 Flyway 迁移（DB 停留 V1204，与源码一致）。
- 本次为常规增量部署：评分解析过滤/截断、AI 标书质量核查超时修复、部门筛选树锚定、2 个纯文档 PR。
- Flyway 预检 3 步全部通过，无需 repair。
- 后端重启后健康检查在 remote-deploy 等待窗口（120×2s）内未返回 3 连成功，属 Kafka SDK readiness 已知延迟；手动等待第 18 次（约 36 秒）即 UP，readiness 200，未回滚。

## 基线信息

| 项目 | 值 |
|------|-----|
| 基线 commit | `00d7d13d5`（origin/main，HEAD = origin/main 零差异） |
| 前次部署 | `a078c76b9-api8080`（2026-08-30 21:33 激活，第 149 次） |
| 增量 commit | 14 个 |
| 新增迁移 | **无**（DB V1204 = 源码 V1204，success=1） |
| 工作区状态 | 干净（部署前 stash 遗留的 `scripts/import-historical-projects.py` 改动，待部署后恢复） |
| 早操三连 | sync-env + check-git-wrapper 全过（git wrapper 7/7） |

## 改动范围（14 个增量 commit / 6 个 PR）

| PR | 描述 |
|----|------|
| !2476 | fix(scoreparse): 合同履约考核表过滤 + 外部系统评分声明诊断，防止非评分项被归一化凑满 100 分 |
| !2475 | fix(compliance): AI 标书质量核查请求超时（显式绑定专用线程池 + 解耦事务提交） |
| !2474 | feat(analytics): M1 部门筛选树锚定西域集团(0001)子树三级展示 |
| !2473 | fix(scoreparse): 评分项字段长度安全截断 + 双层防御 |
| !2472 | docs(analysis): AI 标书质量核查功能现状分析报告（纯文档） |
| !2471 | docs(release): 第 149 次部署报告（纯文档）+ wiki 维护 |

## Flyway 预检 3 步法

**Step 1**：`flyway-repair-runner.sh validate` → exit 0，"Successfully validated 265 migrations"，VALIDATE OK - all checksums match。
**Step 2**：DB 最近迁移 V1204（success=1，2026-08-28 应用）与源码最高版本 V1204 一致，无 pending。
**Step 3**：remote-deploy.sh 内置 validate（jar 激活前）通过。

## 部署步骤时间线（CST）

| 时间 | 步骤 |
|------|------|
| 16:18 | 早操：锚点分支 `agent/trae-init` 触发 session-gate 拦截，改用 sync-env 内部维护模式 ff-only 同步至 origin/main |
| 16:19 | 服务器现状探测（前次 release `a078c76b9-api8080`，health UP） |
| 16:20 | Flyway 预检 3 步通过（validate exit 0，无 repair 需求） |
| 16:22 | 本地打包完成（mvn 39 秒；jar 内迁移无重复；OBS 校验 `.upload(` = 2） |
| 16:24 | scp 154M 压缩包 → DB 备份 → 前端激活 → Flyway validate → 覆盖 jar → systemctl restart（`SYSTEMCTL_SUDO=true`） |
| 16:24-16:25 | remote-deploy 健康等待超时（Kafka 启动延迟，已知行为） |
| 16:25 | 手动等待第 18 次（约 36 秒）health UP，readiness 200 |
| 16:26-16:28 | 外部 Smoke 全绿 + 旧版 assets 保留 |

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

**前端 Smoke（经 Nginx 8080）**：`/` 200、`/login` 200、线上入口 hash `assets/index-C7wgLTta.js` 与本地产物一致。

**产物校验**：`release-metadata.json` obsEnabled=true、apiBaseUrl=""（同源构建）、jar 内 V*.sql 无重复。

**部署后动作**：旧版 assets 已从 `a078c76b9-api8080/frontend/` `cp -rn` 保留（272 个文件，防旧标签页 preload 404，经验 #17）。

**Flyway 应用验证**：V1200-V1204 全部 success=1（本次无新迁移）。

## 回滚信息

| 锚点 | 位置 |
|------|------|
| 前后端制品 | `/opt/xiyu-bid/releases/a078c76b9-api8080/`（jar + frontend） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-00d7d13d5-20260831162414.sql.gz`（部署前自动备份） |
| Nginx 配置锚点 | `/etc/nginx/conf.d/xiyu-bid.conf.bak-20260817`（未变更） |
| 回滚 owner | 部署执行 Agent（trae） |

回滚状态：**就绪，未需要**。

## 经验沉淀应用情况

1. **经验 #2（Kafka SDK readiness 延迟）**：remote-deploy 健康等待超时，手动复检 36 秒即 UP，按已知行为处置，未误判为故障、未回滚。
2. **经验 #15（Mac HTTP_PROXY 502）**：全部 curl 带 `--noproxy '*'`，未受本机代理影响。
3. **经验 #17（前端 hash 资源跨版本 404）**：部署后从上一版本 release 目录 `cp -rn` 保留旧 assets（272 个文件）。
4. **经验 #7（SYSTEMCTL_SUDO）**：显式传入，服务重启一次成功。
5. **锚点分支部署**：session-gate 拦截 `agent/trae-init` 直接开发；sync-env 自带 `XYYU_SYNC_ENV_ACTIVE=1` 维护模式完成 ff-only 同步，HEAD 对齐 origin/main。

## 风险提示

- 本次无新增迁移、无破坏性变更，风险低。
- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 按历史决策保留（第 13/14/15 次确认）。
- 工作区遗留改动 `scripts/import-historical-projects.py`（cust_type_raw 字段补充）已 stash，部署完成后需恢复并确认归属（非本次部署范围）。

## 部署确认清单

- [x] 环境门禁：AskUserQuestion 确认 test（172.16.38.78）
- [x] 早操三连 + 工作区干净 + HEAD=origin/main
- [x] Flyway 预检 3 步（validate / DB 对比 / 部署内置 validate）
- [x] 打包同源构建 + OBS 双保险 + 产物校验
- [x] remote-deploy（DB 备份 → 前端激活 → jar 覆盖 → 重启）
- [x] 健康检查 UP + readiness 200
- [x] Smoke 全绿（5 API + 2 页面 + hash 一致）
- [x] 旧版 assets 保留
- [x] 临时配置检查（无新增）
- [x] 部署报告（本文件）
