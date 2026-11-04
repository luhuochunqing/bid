# 第 36 次生产环境部署报告 — 2026-11-04

> 编号说明：`docs/release/` 中已有两份「35th-prod」报告（`2026-11-03-35th-prod.md`、`2026-11-04-35th-prod.md`），本次按实际生产部署次数续编为 **36th**。

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com`（内网 `http://172.16.10.149:80`） |
| Release ID | `c28e89db6` |
| 基线 commit | `c28e89db6`（与 `origin/main` HEAD 完全一致） |
| 前一版本 | `d74e0f644`（2026-11-04 09:04 CST 激活） |
| 部署时间 | 2026-11-04 21:36:24–21:36:52 CST |
| 状态 | ✅ 成功（健康 + Flyway + Smoke 全通过，**本次零数据库迁移**） |
| 回滚状态 | 未执行，锚点就绪（`/opt/xiyu-bid/releases/d74e0f644` 完整前后端产物） |

环境门禁：用户通过问答卡片明确选择「确认：生产 172.16.10.149」，符合生产授权红线。

---

## 部署概览

本次从生产版本 `d74e0f644` 追平至 `origin/main` 最新 `c28e89db6`，增量 13 个 commit。

- **零数据库迁移**：部署前后 `flyway_schema_history` 成功记录不变，最高版本仍为 `V1225`，`failed=0`。
- **无 Sidecar 变更**：源码 SHA `e5fc03f3…` 未变，sidecar 冒烟 5/5 通过。
- **前端产物**：同源构建，入口 `assets/index-BnD3EL8c.js`；`sentryEnabled=true`、`obsEnabled=true`，Detail chunk `.upload(` 调用数 = 2。
- **旧资源保留**：已从 `releases/d74e0f644/frontend/assets/` 补回 193 个旧 hash 文件到 `/srv/www/xiyu-bid/assets/`，避免旧标签页 preload 404。

---

## 变更范围与 PR

| PR | 主题 |
|---|---|
| !2959 | 第 35 次生产环境部署报告（补） |
| !2960 | scoreparse 评分模型 fallback 异常保全降级，避免解析任务异常终止 |
| !2961 | scoreparse 同源候选重复计分与原文、OCR 诊断丢失修复 |
| !2962 | 第 35 次生产部署报告（事实与编号修正） |
| !2963 | 提交 PR 前强制清理与双审查门禁 |

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操 `sync-env.sh` | ✅ init 分支与 `origin/main` 一致 |
| 门禁 `check-git-wrapper.sh` | ✅ 7/7 通过（`git` 命中 `scripts/git` 包装器） |
| 基线确认 | HEAD = `c28e89db6` = `origin/main` |
| Flyway validate（生产机） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225，与源码最新版本一致，无 pending |
| 服务器健康（部署前） | `/actuator/health` UP，`xiyu-bid-backend` active |

---

## 部署执行

- 一键脚本：`XIYU_DEV_CONFIRMED=1 XYYU_SYNC_ENV_ACTIVE=1 ENV=prod bash scripts/release/deploy-prod.sh c28e89db6`
- 打包：同源构建（`VITE_API_BASE_URL=`）+ `VITE_OBS_ENABLED=true` + `VITE_SENTRY_DSN` 注入 + `mvn clean -DskipTests package`
- 产物校验：jar 内 284 个 `V*.sql` 无重复；`sentryEnabled=true`；`obsEnabled=true`
- 上传：`scp` 发布包（160 M）至 `/opt/xiyu-bid/incoming/`
- 远程部署：`remote-deploy.sh`（`SYSTEMCTL_SUDO=true`），Flyway validate 通过后激活，健康检查连续 3/3 通过（第 15 次探测）

> 说明：本工作区处于锚点分支 `agent/trae-init`（与 `origin/main` 同 commit），部署前需显式置 `XYYU_SYNC_ENV_ACTIVE=1` 才能通过 session-gate 的锚点复查；这只是维护态标记，未改动任何代码。

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | HTTP 200，`status=UP`（db / redis / sidecar / aiProvider 全 UP） |
| `/actuator/health/readiness` | HTTP 200 UP |
| `POST /api/auth/login`（空体） | HTTP 400（参数校验，路由正常） |
| `GET /api/projects`（未认证） | HTTP 401 |
| `GET /api/integration/crm/health` | HTTP 401 |
| 前端 `/`、`/login` | HTTP 200 / 200 |
| 前端入口 | `assets/index-BnD3EL8c.js`（与 release 一致） |
| 部署记录 | `deployed-release.json` → `releaseId=c28e89db6`，`sentryEnabled=true`，`obsEnabled=true` |
| Sidecar 冒烟 | health 200 + `X-Schema-Version=2.2` + 1371 回放（tables=14, cells=8215）+ v1/v2 双探针，5 通过 0 失败 |
| 临时调试配置 | `/etc/xiyu-bid/backend.env` 仅存 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项，非本次新增） |

---

## 回滚信息

- 代码回滚锚点：`/opt/xiyu-bid/releases/d74e0f644`（前后端完整产物）；jar 侧保留 `/opt/xiyu-bid/shared/backend/app.jar` 前一版本需从该 release 目录取回。
- 数据库回滚：**不需要**（本次零迁移）。
- ⚠️ 数据库备份异常：本次 `winbid-c28e89db6-20261104213648.sql.gz` 仅 371 字节，日志报 `mysqldump: Got error: 1044: "Access denied for user 'ea_bid_pro'@'%' to database 'winbid' when using LOCK TABLES`。该问题在历史备份中亦存在（`d74e0f644` 371 B、`f3ff316f2` 20 B），属既有缺陷。

---

## 经验与风险提示

1. **DB 备份长期失效（需修复）**：`DB_BACKUP_COMMAND` 使用 mysqldump 默认 `LOCK TABLES`，`ea_bid_pro` 无该权限，`mysqldump | gzip` 管道使失败退出码被 gzip 吞掉，仍打印 `DB backup done`，产生"假成功"备份。建议改为 `mysqldump --single-transaction --skip-lock-tables --no-tablespaces`，并对产物做大小/可解压校验。本次因零迁移，回滚不依赖 DB 备份，风险可控。
2. **锚点分支部署标记**：`deploy-prod.sh` 内部 `source dev-env.sh`，在锚点分支会触发 session-gate 拦截（规则 1 需 `XIYU_DEV_CONFIRMED=1`，规则 5 仅认 `XYYU_SYNC_ENV_ACTIVE=1`）。两标记需同时提供。
3. **部署报告编号冲突**：已存在两份 35th-prod，建议后续统一编号来源，避免手工续编。
4. 本次生产部署用户体验无中断，无 readiness/Kafka 延迟。

---

## 部署确认清单

- [x] 环境门禁经用户卡片确认（prod / 172.16.10.149）
- [x] 早操同步，HEAD = `origin/main`
- [x] Flyway validate 通过，无 pending 迁移
- [x] 产物校验（迁移去重 / Sentry / OBS）
- [x] 部署后健康检查 + readiness
- [x] API 路由 Smoke（400 / 401）
- [x] 前端入口与页面可达
- [x] Sidecar 冒烟 5/5
- [x] 旧版 assets 保留
- [ ] DB 备份有效性（**未通过**，见风险 1）
