# 第 35 次生产环境部署报告 — 2026-11-03

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com` |
| Release ID | `9baae6bbc` |
| 基线 commit | `9baae6bbc`（**部署时**的 `origin/main` 与 `agent/trae-init`；提 PR 时 main 已推进至 `842adbf18`） |
| 前一版本 | `d6d6fa454`（2026-11-01 第 34 次生产） |
| 部署时间 | 2026-11-03 19:50–19:56 CST |
| 状态 | ✅ 成功（健康 + Flyway + Smoke + Sidecar 全通过，**本次零数据库迁移**） |
| 回滚状态 | 未执行，锚点就绪（`d6d6fa454` 发布目录 + 生产库备份 6.4 MB） |

环境门禁：用户指令「部署到生产环境」，确认框选择「确认：生产环境 172.16.10.149」，符合生产授权红线。

---

## 部署概览

本次从生产版本 `d6d6fa454` 追平至 `origin/main` 最新 `9baae6bbc`，增量 **18 个 commit**（含 7 个 merge），覆盖 PR `!2950`–`!2956`。

- **零数据库迁移**：部署前后成功迁移最高版本均为 `V1225`，`failed=0`，增量 commit 中无 `db/migration-mysql/` 文件变更。
- **Sidecar 无变更**：源码 SHA `e5fc03f3…` 未变，`remote-deploy.sh` 未重建，容器 `xiyu-sidecar` Up 2 days。
- **本次前端有改动**（`src` 6 个文件），入口 hash 变化为 `assets/index-C2pY2KQc.js`，已执行旧版本 assets 保留。

---

## 变更范围与 PR

`git diff d6d6fa454..9baae6bbc`：**51 files changed, +2301 / -92**。

| 目录 | 文件数 |
|---|---|
| `backend` | 31 |
| `specs` | 7 |
| `src`（前端） | 6 |
| `.wiki` | 4 |
| `docs` | 3 |

commit 类型分布（非 merge）：`fix` 6、`docs` 4、`test` 1。

核心主题：

- **scoreparse（评分解析，主线）**：跨页续文恢复与公共说明排除（!2954）、跨行错分 / 跨表重号 / 数字档位核验（!2953）、闭合原文单元核验分值（!2952）、提取记录与方案候选区分 + 来源诊断展示（!2950）
- **scoreparse 文档**：归档 1117 历史诊断并标明版本边界（!2955）
- **frontend**：诊断标签防继承键污染（`fix(frontend): guard diagnostic labels against inherited keys`）
- **release**：第 34 次生产 / 第 256 次测试部署报告归档（!2951 / !2956）

---

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate`（部署前） | ✅ `VALIDATE OK - all checksums match`（`Successfully validated 285 migrations`） |
| Step 2：DB 已应用版本 vs 源码最新 | ✅ DB 最高 `V1225`（success=1）= 源码/jar 最高 `V1225`；零 pending；`failed=0` |
| Step 3：`remote-deploy.sh` 激活前内置 validate | ✅ `Flyway validate 通过（仅 pending 新迁移为预期状态）` |

打包阶段校验：jar 内迁移资源无重复版本；`FlywayMysqlContainerTest` 容器装配门禁通过。

---

## 部署步骤

| 阶段 | 动作 | 结果 |
|---|---|---|
| 0 | 环境门禁确认（prod） | ✅ 用户显式确认 |
| 1 | 基线确认（`sync-env.sh` + `check-git-wrapper`） | ✅ 7/7 门禁就绪，HEAD = `origin/main` = `9baae6bbc`，工作区干净 |
| 2 | 服务器现状核对 | 旧版本 `d6d6fa454`，服务 active，health 200；增量 18 commit / 零迁移 |
| 3 | 打包 `ENV=prod deploy-prod.sh`（同源 + OBS + Sentry） | ✅ `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=true`、Detail `.upload(`=2、无重复迁移 |
| 4 | scp 发布包（161 MB）+ `remote-deploy.sh` | ✅ 上传完成，磁盘余量 71 G |
| 5 | `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） | ✅ 前端激活 → 停服 → 原子换 jar → 启服（Sidecar 跳过重建） |
| 6 | 健康检查 | ✅ 连续 3/3，15 次尝试内通过，无 Kafka readiness 长延迟 |
| 7 | 上一版本 assets 保留 | ✅ 从 `d6d6fa454/frontend/assets` `cp -rn` 回填 108 个旧 hash 文件（当前 assets 301 个） |
| 8 | `._*` 残留清理 | ⚠️ 检出 198 个 macOS 残留，已 `find -delete` 清理至 0 |
| 9 | 部署报告 | 本文件 |

---

## 验证结果

### 后端 / API

| 检查 | 期望 | 实际 |
|---|---|---|
| `/actuator/health`（内网 127.0.0.1:18080 / nginx:8080 / 域名 443） | 200 UP | ✅ 200 / 200 / 200 |
| `/actuator/health/readiness` | 200 UP | ✅ 200 |
| `POST /api/auth/login`（空 body） | 400 参数校验 | ✅ 400 |
| `/api/projects`（未认证） | 401/403 | ✅ 401 |
| `/api/integration/crm/health`（未认证） | 401 | ✅ 401 |

### 前端

| 检查 | 实际 |
|---|---|
| `https://winbid.ehsy.com/` | ✅ 200（0.099 s） |
| `/login`（nginx 8080） | ✅ 200 |
| 入口 asset | `assets/index-C2pY2KQc.js`，与发布包 `releases/9baae6bbc/frontend/index.html` 一致（`deployed-release.json` 不含入口 hash 字段） |
| Nginx 缓存策略 | ✅ `Cache-Control: public, immutable` + `max-age=31536000` |
| 旧版本 assets 保留 | ✅ 301 个（其中 108 个为 `d6d6fa454` 旧 hash 资源） |

### 数据库

| 检查 | 实际 |
|---|---|
| `flyway_schema_history` 最高版本 | ✅ `V1225`（`success=1`，部署前后一致） |
| failed 迁移数 | ✅ 0 |
| 部署前备份（脚本自动） | ❌ **失败**：`mysqldump: Got error: 1044 Access denied for user 'ea_bid_pro'@'%' to database 'winbid' when using LOCK TABLES`，产物仅 371 B（已删除） |
| 部署后补做有效备份 | ✅ `winbid-9baae6bbc-post-20261103210646.sql.gz`（6.4 MB，`gzip -t` 通过，`--single-transaction --no-tablespaces --skip-lock-tables`） |

### 服务与配置

| 检查 | 实际 |
|---|---|
| `xiyu-bid-backend` | ✅ active (running) |
| `xiyu-sidecar` | ✅ Up 2 days；Sidecar 冒烟 5/5 通过（v1/v2 双形态探针、1371 fixture 回放 tables=14 cells=8215） |
| `deployed-release.json` | ✅ `releaseId=9baae6bbc`，`activatedAt=2026-11-03T11:52:48Z`（19:52:48 CST） |
| 临时调试配置检查 | 仅命中 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项，未新增 DEBUG/TRACE） |

---

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点 | release 目录 `/opt/xiyu-bid/releases/d6d6fa454` |
| 后端回滚 | `cp /opt/xiyu-bid/releases/d6d6fa454/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar` → `sudo systemctl restart xiyu-bid-backend` |
| 前端回滚 | 用 `d6d6fa454/frontend` 覆盖 `/srv/www/xiyu-bid`（旧 hash 资源已在本次部署中保留） |
| Sidecar 回滚 | 无需回滚（本次未变更） |
| DB 回滚 | 本次零迁移，无需 DB 回滚；备份 `winbid-9baae6bbc-post-20261103210646.sql.gz`（6.4 MB）就绪 |
| 是否执行 | ❌ 未执行（验证全通过） |

---

## 经验沉淀应用情况

1. **同源构建**：`deploy-prod.sh` 内置 `VITE_API_BASE_URL=`，产物 `apiBaseUrl=""`。
2. **OBS 直传双保险**：`VITE_OBS_ENABLED=true`，产物校验 `obsEnabled=true` + Detail chunk `.upload(` = 2。
3. **`SYSTEMCTL_SUDO=true`**：服务重启成功，无 `Interactive authentication required`（经验 #7）。
4. **上一版本 assets 保留**：`cp -rn` 回填 108 个旧 hash 文件，规避旧标签页资源 404（经验 #17）。
5. **macOS `._*` 残留**：检出 198 个并清理（经验 #13）。注意 `deploy-prod.sh` 未设置 `COPYFILE_DISABLE=1`，残留来源未根除。
6. **锚点分支门禁**：`deploy-prod.sh` 内部 `source scripts/dev-env.sh` 在 `agent/trae-init` 被 `session-gate.sh` 直接 `exit 1`（外层 `|| true` 无法拦截被 source 脚本的 `exit`），本次用 `CHAT_ONLY=1`（官方非开发操作逃生阀）执行。
7. **Sidecar 发布冒烟**：`smoke-sidecar.sh` 5/5 通过，未降级到 Markdown。

---

## 后续状态变更（2026-11-04 复核，重要）

本报告是 **2026-11-03 19:50 部署当时**的取证快照。此后生产又被升级一次，现场已变化，复核时请以最新 `deployed-release.json` 为准：

| 项目 | 部署当时（本报告） | 2026-11-04 复核现状 |
|---|---|---|
| 生产 release | `9baae6bbc`（19:52 CST 激活） | **`d74e0f644`**（`activatedAt=2026-11-04T01:04:07Z`，即 09:04 CST，由另一次生产部署升级，对应 `docs/release/deploy-report-2026-11-04-35th-prod.md`） |
| `/srv/www/xiyu-bid/assets` | 301（含 108 个旧 hash 文件） | 276 |
| `._*` macOS 残留 | 0（已清理） | 198（再次出现，说明 `deploy-prod.sh` 未设 `COPYFILE_DISABLE=1` 的源头未修） |
| 生产库有效备份 | `winbid-9baae6bbc-post-20261103210646.sql.gz`（6.4 MB） | **最新一份为 `winbid-d74e0f644-20261104090403.sql.gz`，仅 371 B（自动备份再次失败）**，即当前生产**没有**与现版本匹配的有效全量备份 |

**报告编号冲突**：本报告按时间顺序记为第 35 次生产部署；`deploy-report-2026-11-04-35th-prod.md` 同样自标为「第 35 次」（并把 `9baae6bbc` 称作第 34 次），两者编号撞号但描述的是两次不同部署（本报告在前）。合并后建议统一编号（本报告=第 35 次、`d74e0f644` 那次=第 36 次）。

## 风险提示

1. **`deploy-prod.sh` 的 DB 备份存在静默失败缺陷（本次首次暴露）**：`DB_BACKUP_COMMAND` 走 `mysqldump | gzip`，管道退出码取 `gzip`，mysqldump 因 `ea_bid_pro` 缺少 `LOCK TABLES` 权限报 `1044` 时脚本仍打印 `DB backup done`。历史证据：`winbid-f3ff316f2-20261025115943.sql.gz` 仅 20 B（同类失败）。建议单开修复 PR：备份命令加 `--single-transaction --no-tablespaces --skip-lock-tables`，并在远端命令加 `set -o pipefail` + 非空校验。
2. 本次改动集中在**评分解析（scoreparse）**，覆盖跨页续文、跨行归属、跨表重号、数字档位核验等边界，建议业务侧用真实标书做一次评分解析回归（含人工复核采纳路径）。
3. **前端 Sentry 本次启用**（`sentryEnabled=true`，上一版本为 `false`）：`deploy-prod.sh` 已内置生产 DSN，将开始采集前端错误，注意 Sentry 配额与噪声（重点观察 `Unable to preload CSS` 类事件）。
4. 本次零数据库结构变更，回滚窗口极低；如需回滚仅需换回 `d6d6fa454` jar 与前端目录。
5. **同一备份缺陷已复发（2026-11-04）**：`d74e0f644` 那次部署的自动备份 `winbid-d74e0f644-20261104090403.sql.gz` 同样只有 371 B，**现网当前没有与运行版本匹配的有效全量备份**。建议将该修复列为 P1，并在修复前先手工补一份有效备份。

---

## 部署确认清单

- [x] 目标环境经用户显式确认（prod / `172.16.10.149`）
- [x] 基线确认（**部署时** `origin/main` = `9baae6bbc`，工作区干净）
- [x] Flyway validate 通过（部署前 + 激活前两次）
- [ ] DB 全量备份（脚本自动备份失败 → 已补做 6.4 MB 有效备份）
- [x] 发布包产物校验（同源 / OBS / Sentry / 无重复迁移 / 容器装配门禁）
- [x] 健康检查 3/3 通过
- [x] 零迁移，DB 版本一致性验证通过（V1225 / failed 0）
- [x] API + 前端 Smoke 全通过（nginx 8080 + 域名 443）
- [x] 上一版本 assets 保留（301 个，含 108 个旧 hash 文件）
- [x] `._*` 残留清理至 0
- [x] Sidecar 冒烟 5/5 通过
- [x] 回滚锚点就绪（含有效 DB 备份）
- [ ] 业务侧功能回归（待业务确认：评分解析跨页/跨表边界、前端诊断标签）
