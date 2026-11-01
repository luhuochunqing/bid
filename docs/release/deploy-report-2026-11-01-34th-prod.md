# 第 34 次生产环境部署报告 — 2026-11-01

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com`（内网 `http://172.16.10.149:80`） |
| Release ID | `d6d6fa454` |
| 基线 commit | `d6d6fa454`（`origin/main` HEAD） |
| 前一版本 | `f0958752c`（2026-11-01 第 33 次生产） |
| 部署时间 | 2026-11-01 20:29–20:32 CST |
| 状态 | ✅ 成功（健康 + Flyway + Smoke 全通过，**本次零数据库迁移**） |
| 回滚状态 | 未执行，锚点就绪（`f0958752c` 发布目录 + DB 备份 `winbid-d6d6fa454-20261101202917.sql.gz`） |

环境门禁：用户指令「部署到生产系统」，确认框选择「确认：生产环境 172.16.10.149」，符合生产授权红线。

---

## 部署概览

本次从生产版本 `f0958752c` 追平至 `origin/main` 最新 `d6d6fa454`，增量 **16 个 commit**（含 4 个 merge），覆盖 PR `!2946`–`!2949`。

**与上次生产部署的关键差异：本次零数据库迁移**（部署前后成功迁移数均为 284 条 SQL 记录，最高版本仍为 `V1225`，`failed=0`）。

**本次无 Sidecar 变更**：`document-converter-sidecar` 源码 SHA 未变（`e5fc03f3…`），`remote-deploy.sh` 未重建镜像，容器 `xiyu-sidecar` 保持 Up。

**前端 `src/` 无改动**（`git diff f0958752c..HEAD -- src` 为空），前端为同源重建、hash 入口与上一版本一致（`assets/index-xSKpZ2Mv.js`）。

---

## 变更范围与 PR

增量范围 `f0958752c..d6d6fa454` 共 16 个 commit，覆盖 PR：`!2946 !2947 !2948 !2949`。

commit 类型分布（非 merge）：`fix` 6、`docs` 6。

scope 分布：`scoreparse` 5、`release` 4、`compliance` 1。

核心主题：

- **scoreparse（评分解析，本次主线）**：
  - 修复跨页评分表百度 OCR 前序页硬截断与入口路由假成功短路（!2948）
  - 方案总分双向核验、冲突覆盖与超额候选防误判（!2947/!2948 系列）
  - 多评分区间单簇漏扫、中间续页漏扫、超长大表静默截断修复
  - 强化入参判空防线，消除末尾换行格式问题
- **compliance（标书质量核查）**：响应性核查回归法定偏离表第一性原理，消除全文负词定罪（!2947）
- **release**：第 33 次生产部署报告与第 255 次测试环境部署报告归档

### 改动文件分布

| 目录 | 文件数 | 增删 |
|---|---|---|
| `backend` | 14 | +888 / -140 |
| `.wiki` | 10 | +194 / -7 |
| `docs` | 5 | +501 / -0 |
| `src`（前端） | 0 | 0 |
| `document-converter-sidecar` | 0 | 0 |
| **合计** | **29** | **+1583 / -147** |

其中后端生产代码 9 个文件（`compliance/domain/policy` 4、`scoreparse` 5），测试 5 个文件（新增 4 个测试类，共 +471 行）。

### 数据库迁移（本次 0 个）

无新增迁移文件、无回滚脚本变更。DB 部署前最高版本 `V1225`（成功 SQL 迁移 284 条，failed 0），部署后一致。

---

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate`（部署前） | ✅ `VALIDATE OK - all checksums match`（`Successfully validated 285 migrations`） |
| Step 2：DB 已应用版本 vs 源码最新 | ✅ DB 成功 SQL 迁移 284 条 = 源码 284 个 `V*.sql`；最高 `V1225`；`failed=0`；零 pending 新迁移 |
| Step 3：`remote-deploy.sh` 激活前内置 validate | ✅ 二次 validate 通过 |

打包阶段校验：jar 内 285 个迁移资源（284 个 `V*.sql` + `B73` 基线），无重复版本。

---

## 部署步骤

| 阶段 | 动作 | 结果 |
|---|---|---|
| 0 | 环境门禁确认（prod） | ✅ 用户显式确认 |
| 1 | 基线确认（`sync-env.sh` 维护模式 + `git wrapper` 校验） | ✅ 7/7 门禁就绪，HEAD = `origin/main` = `d6d6fa454`，落后 0，工作区干净 |
| 2 | 服务器现状核对 | 旧版本 `f0958752c`，服务 active，health/readiness/前端均 200；增量 16 commit / 零迁移 |
| 3 | 打包 `RELEASE_ID=d6d6fa454 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1` | ✅ `apiBaseUrl=""`、`obsEnabled=true`、Detail chunk `.upload(` = 2、jar 迁移无重复 |
| 4 | scp 发布包（156 MB）+ `remote-deploy.sh` | ✅ 磁盘余量 71 G |
| 5 | `remote-deploy.sh` 执行（`SYSTEMCTL_SUDO=true`） | ✅ DB 备份 → validate → 前端激活 → 停服 → 原子换 jar → 启服（Sidecar 未变，跳过重建） |
| 6 | 健康检查 | ✅ 连续 3/3，16 次尝试内通过，无 Kafka readiness 长延迟 |
| 7 | 上一版本 assets 保留 | ✅ 从 `f0958752c/frontend/assets` `cp -rn` 回填旧 hash 文件（当前 assets 193 个） |
| 8 | `._*` 残留检查 | ✅ 0 个残留 |
| 9 | 部署报告 | 本文件 |

脚本自动处理事项：前端目录属主统一为 `jetty` 后完成激活（经验 #12 防护生效）；`AI_PROVIDER 守卫`通过。

---

## 验证结果

### 后端 / API

从开发宿主直连 `172.16.10.149:8080` 不可达（已知行为：防火墙仅放通 80/443），Smoke 改走内网 `:80` 与公网域名 `https://winbid.ehsy.com`，结果一致。

| 检查 | 期望 | 实际（内网 80 / 域名 443） |
|---|---|---|
| `/actuator/health` | 200 UP | ✅ 200 / 200 |
| `/actuator/health/readiness` | 200 UP | ✅ 200 / — |
| `POST /api/auth/login`（空 body） | 400 参数校验 | ✅ 400 |
| `/api/projects`（未认证） | 401/403 | ✅ 401 |
| `/api/integration/crm/health`（未认证） | 401 | ✅ 401 |

### 前端

| 检查 | 实际 |
|---|---|
| `https://winbid.ehsy.com/` | ✅ 200 |
| `http://172.16.10.149:80/`、`/login` | ✅ 200 / 200 |
| 入口 asset | `assets/index-xSKpZ2Mv.js`，与发布包 / `deployed-release.json` 一致 |
| Nginx 缓存策略 | ✅ `Cache-Control: public, immutable` + `max-age=31536000` + `Content-Encoding: gzip` |

### 数据库

| 检查 | 实际 |
|---|---|
| `flyway_schema_history` 成功 SQL 迁移数 | ✅ 284 条（部署前后一致，零新迁移） |
| 最高版本（按 `installed_rank`） | ✅ `V1225`，`success=1` |
| failed 迁移数 | ✅ 0 |
| 部署前备份 | ✅ `winbid-d6d6fa454-20261101202917.sql.gz`（6.3 MB） |

### 服务与配置

| 检查 | 实际 |
|---|---|
| `xiyu-bid-backend` | ✅ active (running) |
| `xiyu-sidecar` 容器 | ✅ Up（源码未变，本次未重建），`schemaVersion=2.2` |
| `deployed-release.json` | ✅ `releaseId=d6d6fa454`，`activatedAt=2026-11-01T12:29:24Z`（20:29 CST） |
| 临时调试配置检查 | ✅ 仅命中 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项） |

---

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点 | release 目录 `/opt/xiyu-bid/releases/f0958752c` |
| 后端回滚 | `cp /opt/xiyu-bid/releases/f0958752c/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar` → `sudo systemctl restart xiyu-bid-backend` |
| 前端回滚 | 用 `f0958752c/frontend` 覆盖 `/srv/www/xiyu-bid`（旧 hash 资源已在本次部署中保留） |
| Sidecar 回滚 | 无需回滚（本次未变更） |
| DB 回滚 | 本次零迁移，无需 DB 回滚；部署前全量备份 `winbid-d6d6fa454-20261101202917.sql.gz` 就绪 |
| 是否执行 | ❌ 未执行（验证全通过） |

---

## 经验沉淀应用情况

1. **同源构建**：显式传 `VITE_API_BASE_URL=`，产物 `apiBaseUrl=""`。
2. **OBS 直传双保险**：显式传 `VITE_OBS_ENABLED=true`，产物校验 `obsEnabled=true` + Detail chunk `.upload(` = 2。
3. **DB 备份前置**：`DB_BACKUP_COMMAND` 在激活新 jar 之前执行成功。
4. **上一版本 assets 保留**：`cp -rn` 回填旧 hash 文件，规避旧标签页资源 404（经验 #17）。
5. **`SYSTEMCTL_SUDO=true`**：服务重启成功，无 `Interactive authentication required`（经验 #7）。
6. **Nginx gzip + `/assets/` 强缓存**：本次复验仍生效（经验 #18）。
7. **锚点分支门禁**：`source dev-env.sh` 在 `agent/trae-init` 被 session-gate 拦截，改用 `XYYU_SYNC_ENV_ACTIVE=1 bash scripts/sync-env.sh .`（官方维护豁免）完成等效早操。
8. **测试先行验证**：本次代码与测试环境第 255 次验证版 `38c34354b` 完全一致（HEAD 仅多 2 个报告文档 commit）。

---

## 风险提示

1. 本次改动集中在**评分解析（scoreparse）与标书质量核查（compliance）**两条主线。评分解析修复覆盖跨页表、多区间、超长大表等边界，建议业务侧用真实标书做一次评分解析回归（含人工复核采纳路径）。
2. 响应性核查策略回归「法定偏离表第一性原理」并移除全文负词定罪，建议对历史标书复跑一次核查，确认无新增误判/漏判。
3. `sentryEnabled=false` 与上一版本一致（未注入前端 DSN），前端错误仍依赖后端日志与 Sentry 后端集成。
4. 本次未变更数据库结构，回滚窗口极低；如需回滚，仅需换回 `f0958752c` jar 与前端目录。

---

## 部署确认清单

- [x] 目标环境经用户显式确认（prod / `172.16.10.149`）
- [x] 基线确认（`origin/main` = `d6d6fa454`，工作区干净，落后 0）
- [x] Flyway validate 通过（部署前 + 激活前两次）
- [x] DB 全量备份完成（6.3 MB）
- [x] 发布包产物校验（同源 / OBS / 无重复迁移）
- [x] 健康检查 3/3 通过
- [x] 零迁移，DB 版本一致性验证通过（284 条 / V1225 / failed 0）
- [x] API + 前端 Smoke 全通过（内网 80 + 域名 443）
- [x] 上一版本 assets 保留（193 个 hash 文件）
- [x] `._*` 残留 0 个
- [x] 回滚锚点就绪
- [ ] 业务侧功能回归（待业务确认：评分解析跨页表 + 响应性核查）
