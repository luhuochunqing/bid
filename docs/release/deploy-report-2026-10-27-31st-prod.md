# 第 31 次生产环境部署报告 — 2026-10-27

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com`（内网 `http://172.16.10.149:80`） |
| Release ID | `045ee5948` |
| 基线 commit | `045ee5948`（`origin/main` HEAD） |
| 前一版本 | `838ee5742`（2026-10-25 第 30 次生产） |
| 部署时间 | 2026-10-27 06:51–06:53 CST |
| 状态 | ✅ 成功（健康 + Flyway + Smoke 全通过，本次含 1 个数据库迁移） |
| 回滚状态 | 未执行，锚点就绪（`838ee5742` 发布目录 + DB 备份 `winbid-045ee5948-20261027065144.sql.gz`） |

环境门禁：用户指令「部署到生产环境」，确认框选择「确认：生产环境 172.16.10.149」+「当前 origin/main（045ee5948）」，符合生产授权红线。

---

## 部署概览

本次从生产版本 `838ee5742` 追平至 `origin/main` 最新 `045ee5948`，增量 **35 个 commit**，覆盖 PR `!2876`–`!2886` 共 **11 个**（9 个业务/治理修复、2 个部署报告文档）。

**与上次生产部署的关键差异：本次含 1 个数据库迁移** `V1225__score_estimate_freshness.sql`（给 `score_item` 增补两个可空元数据列，幂等写法，不重算/不改写历史分数）。

---

## 变更范围与 PR

增量范围 `838ee5742..045ee5948` 共 35 个 commit，覆盖 PR：`!2876 !2877 !2878 !2879 !2880 !2881 !2882 !2883 !2884 !2885 !2886`。

核心主题：

- **scoreparse（评分解析）**：080 评分项跨章节穿透资格项业绩检索与精准匹配（!2880）；修复评分类型主客观误判（销售额等客观项）（!2881）；过滤全页装饰边框伪表格并增强跨页续表合并与半开区间支持（!2882）；资质规则强制执行 + 过期估算刷新（!2883）；修复纯文本正文引用穿透还原与表格中间列跨行续行判定（!2884）
- **compliance（标书质量核查）**：修复报告 58 误判并收紧授权与图像证据边界（!2879）
- **wiki 治理**：全量复核 78 个过期页面修正漂移（!2877）；`wiki-backfill-active` 收紧为仅刷新 archive 页并新增契约测试（!2878）；补录项目 1117 CA 生产验收（!2885）
- **docs**：第 236 次测试环境部署报告（!2886）

### 改动文件分布

| 目录 | 文件数 | 增删 |
|---|---|---|
| `backend` | 77 | +3297 / -272 |
| `.wiki` | 39 | +454 / -67 |
| `specs` | 13 | +395 / -0 |
| `src`（前端） | 9 | +160 / -7 |
| `docs` | 4 | +686 / -160 |
| `scripts` | 2 | +140 / -54 |
| `document-converter-sidecar` | 2 | +119 / -0 |
| 其他（`decisions.tsv`、`.specify`） | 2 | +10 / -33 |
| **合计** | **148** | **+5261 / -593** |

前端 9 个文件集中在评分解析抽屉与估算刷新：`ScoreParseDrawer.vue`、`useScoreParseDrawer.js`、`useScoreEstimateRefresh.js` 及对应 spec。

### 数据库迁移（本次 1 个）

| 版本 | 文件 | 说明 | 回滚脚本 |
|---|---|---|---|
| `V1225` | `V1225__score_estimate_freshness.sql` | `score_item` 增补 `estimate_input_fingerprint VARCHAR(64) NULL`、`estimated_at DATETIME(6) NULL`（`information_schema` 判存后 ALTER，幂等） | ✅ `U1225__score_estimate_freshness.sql` |

DB 部署前最高版本 `V1224`，部署后应用 `V1225`。迁移仅加可空列，历史分数不重算、不回填，回滚无数据损失。

---

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（284 migrations） |
| Step 2：DB 已应用版本 vs 源码最新 | ✅ DB 最高 `V1224`，源码新增 `V1225`（1 个 pending，符合预期） |
| Step 3：`remote-deploy.sh` 激活前内置 validate | ✅ 二次 validate 通过（`VALIDATE OK - all checksums match`） |

打包阶段另执行容器测试门禁 `FlywayMysqlContainerTest`（Spring 全量上下文装配）通过；jar 内 Flyway 迁移版本无重复。

---

## 部署步骤

| 阶段 | 动作 | 结果 |
|---|---|---|
| 0 | 环境门禁确认（prod + `045ee5948`） | ✅ |
| 1 | 早操三连（`sync-env.sh` + 本地门禁自检 + git wrapper） | ✅ 7/7 门禁就绪，`agent/trae-init` = `origin/main` |
| 2 | 服务器现状核对 | 旧版本 `838ee5742`，服务 `active`，health 200 |
| 3 | 打包 `RELEASE_ID=045ee5948 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1` | ✅ `apiBaseUrl=""`、`obsEnabled=true`、Detail chunk `.upload(` = 2 |
| 4 | scp 发布包 + `remote-deploy.sh` | ✅ DB 备份 → validate → 前端激活 → 停服 → 原子换 jar → 启服 |
| 5 | 健康检查 | ✅ 连续 3/3，第 15 次尝试（约 30s）通过，无 Kafka readiness 长延迟 |
| 6 | 上一版本 assets 保留 | ✅ 从 `838ee5742/frontend/assets` `cp -rn` 回填，`/assets` 现有 277 个文件 |
| 7 | 部署报告 | 本文件 |

---

## 验证结果

### 后端 / API（经 Nginx `:80`，与域名入口同源）

| 检查 | 期望 | 实际 |
|---|---|---|
| `/actuator/health` | 200 UP | ✅ 200 |
| `/actuator/health/readiness` | 200 UP | ✅ 200 |
| `POST /api/auth/login`（空 body） | 400 参数校验 | ✅ 400 |
| `/api/projects`（未认证） | 401/403 | ✅ 401 |
| `/api/integration/crm/health`（未认证） | 401 | ✅ 401 |

### 前端

| 检查 | 实际 |
|---|---|
| `http://172.16.10.149/` | ✅ 200 |
| `http://172.16.10.149/login` | ✅ 200 |
| `https://winbid.ehsy.com/` | ✅ 200 |
| 入口 asset | `assets/index-1_idEp5T.js`，与发布包 / `deployed-release.json` 一致 |

### 数据库

| 检查 | 实际 |
|---|---|
| `flyway_schema_history` 中 `V1225` | ✅ `success=1`，`installed_on=2026-10-27 06:52:03` |
| 部署前备份 | ✅ `winbid-045ee5948-20261027065144.sql.gz`（5.9 MB） |

### 配置清理检查

`sudo grep -E "SHOW_DETAILS|DEBUG|TRACE" /etc/xiyu-bid/backend.env` 仅命中 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项，非本次引入）。

---

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点 | release 目录 `/opt/xiyu-bid/releases/838ee5742` |
| 后端回滚 | `cp /opt/xiyu-bid/releases/838ee5742/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar` → `sudo systemctl restart xiyu-bid-backend` |
| 前端回滚 | 用 `838ee5742/frontend` 覆盖 `/srv/www/xiyu-bid`（旧 hash 资源已在本次部署中保留） |
| DB 回滚 | `V1225` 仅加可空列，反向执行 `U1225__score_estimate_freshness.sql` 即可；本次部署前全量备份已就绪 |
| 是否执行 | ❌ 未执行（验证全通过） |

---

## 经验沉淀应用情况

1. **同源构建**：显式传 `VITE_API_BASE_URL=`，产物 `apiBaseUrl=""`，避免 IP/域名跨域 403。
2. **OBS 直传双保险**：显式传 `VITE_OBS_ENABLED=true`，产物校验 `obsEnabled=true` + Detail chunk `.upload(` = 2。
3. **DB 备份前置**：`DB_BACKUP_COMMAND` 在激活新 jar 之前执行，备份文件按 release id 命名，便于回溯。
4. **上一版本 assets 保留**：从旧 release 目录 `cp -rn` 回填，规避旧标签页 hash 资源 404。
5. **迁移幂等**：`V1225` 用 `information_schema` 判存后 ALTER，规避 Flyway 9.22.3 对 `ADD COLUMN IF NOT EXISTS` 记为 failed 的历史坑。

### 本次新发现（建议沉淀）

- **生产 8080 端口从开发宿主不可达**：`nc` 探测 `172.16.10.149:8080` 端口开放，但 `curl` 建立连接后 10s 无响应（疑似防火墙/WAF 策略只放通 80/443）。本次 Smoke 改走 `http://172.16.10.149:80` 与 `https://winbid.ehsy.com`，结果一致。后续生产 Smoke 建议默认使用 `:80` 或域名，而非 `:8080`。

---

## 风险提示

1. 本次改动集中在评分解析与合规核查两条业务链路（含跨章节业绩穿透、评分类型判定），建议业务侧针对性回归：评分解析抽屉、评分估算刷新、质量核查报告 58。
2. `V1225` 新增字段当前无业务回填，历史分数保持 `NULL` 指纹/时间；若后续依赖「估算是否过期」判定，需确认前端回退展示逻辑符合预期。
3. `sentryEnabled=false` 与上一版本一致（未注入前端 DSN），前端错误追踪仍依赖服务端日志与 Sentry 后端集成。

---

## 部署确认清单

- [x] 目标环境经用户显式确认（prod / `172.16.10.149`）
- [x] 基线确认（`origin/main` = `045ee5948`，工作区干净）
- [x] Flyway validate 通过（部署前 + 激活前两次）
- [x] DB 全量备份完成
- [x] 发布包产物校验（同源 / OBS / 无重复迁移）
- [x] 健康检查 3/3 通过
- [x] 迁移 `V1225` 应用成功
- [x] API + 前端 Smoke 全通过
- [x] 上一版本 assets 保留
- [x] 回滚锚点就绪
- [ ] 业务侧功能回归（待业务确认）
