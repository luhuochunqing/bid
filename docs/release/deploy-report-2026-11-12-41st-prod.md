# 第 41 次生产环境部署报告 — 2026-11-12

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com` |
| Release ID | `6d843c212` |
| 基线 commit | `6d843c212`（与 `origin/main` HEAD 完全一致） |
| 前一版本 | `5370cbad2`（2026-11-12 08:31 CST 激活） |
| 部署时间 | 2026-11-12 19:52:44 CST（激活），健康检查随后通过 |
| 状态 | ✅ 成功（Flyway validate + 健康检查 + 内外部 Smoke 全通过，**零数据库迁移**） |
| 回滚状态 | 未执行，锚点就绪（`/opt/xiyu-bid/releases/5370cbad2` + 本次部署前 DB 备份） |

环境门禁：用户通过问答卡片明确选择「确认：生产环境 172.16.10.149」。

---

## 部署概览

从生产版本 `5370cbad2` 追平至 `origin/main` 最新 `6d843c212`，增量 **13 个 commit / 6 个 PR（!3008–!3013）/ 66 个文件（+2689 / -1025）**。

- **零数据库迁移**：`5370cbad2..6d843c212` 无 `db/migration-mysql/` 变更；部署前后 `flyway_schema_history` 最高版本仍为 `V1225`。
- **无 Sidecar 变更**：sidecar SHA `e9bcf69a…`、schemaVersion `2.2` 与上一版一致，未触发容器重建。
- **前端产物**：同源构建（`apiBaseUrl=""`），入口 `assets/index-BF6NLc4o.js`；`obsEnabled=true`，Detail chunk `.upload(` = 2。
- **Sentry**：本次 `sentryEnabled=false`（沿用上一版现状，保持关闭）。
- **旧资源保留**：`remote-deploy.sh` 的 `ASSETS_RETAIN_RELEASES=2` 生效，`/srv/www/xiyu-bid/assets/` 共 448 个文件，避免旧标签页 preload 404。

---

## 变更范围与 PR

| PR | 主题 |
|---|---|
| !3008 | scoreparse：知识库预判全量刷新与结果反馈（verified 结果回执） |
| !3009 | scoreparse：解决大跨度 Rowspan 与平级项解析失真（Project 1123，网格跨行下沉/虚构大项剪枝/补漏幂等去重） |
| !3010 | auth：登录失败提示反映 OSS 真实原因（含 wiki §9.8 根因回填） |
| !3011 | 第 267 次测试环境部署报告 |
| !3012 | scoreparse：按知识库数量计算粗略预计得分 |
| !3013 | 第 268 次测试环境部署报告 |

> 说明：本批次以 scoreparse（评分解析）修复为主，另有 OSS 登录失败提示口径修正与文档补记。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 基线确认 | ✅ 分支 `agent/trae-init`，HEAD = `origin/main` = `6d843c212`，工作区干净 |
| SSH 连通性 | ⚠️ 首次 `Connection closed` 瞬断，重试即恢复（3/3 成功），非服务端故障 |
| 服务器健康（部署前） | `/actuator/health` UP（db / redis / sidecar / aiProvider 全 UP），`xiyu-bid-backend` / `nginx` active |
| 部署前版本 | `deployed-release.json` → `releaseId=5370cbad2` |
| Flyway validate（生产机） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | `V1225`（2026-10-27 应用），与源码最新版本一致，无 pending |
| sudo 权限 | `sudo -n systemctl is-active xiyu-bid-backend` 返回 `SUDO_OK` |
| 磁盘 | `/` 使用 28%（26G / 99G） |

---

## 部署执行

按 `xiyu-server-deploy` 技能标准 SOP 手工执行（未使用一键脚本）：

- 打包：`RELEASE_ID=6d843c212 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh`
  - `mvn clean -DskipTests package` BUILD SUCCESS（3451 源文件，43s），发布包 156 M
  - 产物校验：jar 内 284 个 `V*.sql` 无重复；`obsEnabled=true`；`sentryEnabled=false`；Detail chunk `.upload(` = 2
- 上传：`scp` 发布包 + `remote-deploy.sh` 至 `/opt/xiyu-bid/incoming/`
- 远程部署：`remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）
  - Flyway validate 通过 → 保留 2 版旧 assets → 激活前后端 → `systemctl restart` → 健康检查连续 3/3 通过（第 15 次探测）
  - 一致性校验：前端入口 `assets/index-BF6NLc4o.js` 与 release 一致
- 后端启动耗时：`Started XiyuBidApplication in 21.861 seconds`

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `/actuator/health`（服务器本地 18080） | HTTP 200，`status=UP` |
| `/actuator/health/readiness` | HTTP 200 UP |
| `POST /api/auth/login`（空体） | HTTP 400（参数校验，路由正常） |
| `GET /api/projects`（未认证） | HTTP 401 |
| `GET /api/integration/crm/health` | HTTP 401 |
| 前端 `/`、`/login`（Nginx 8080） | HTTP 200 / 200 |
| 前端入口 | `assets/index-BF6NLc4o.js`（与 release 一致） |
| 公网域名 `https://winbid.ehsy.com/` | HTTP 200，入口 hash 一致 |
| 公网 `/actuator/health` | HTTP 200 `UP` |
| 启动后 ERROR 日志 | 0 条（19:52:40 起） |
| 部署记录 | `deployed-release.json` → `releaseId=6d843c212`，`obsEnabled=true` |
| 旧版 assets | ✅ 448 个文件（保留 2 版） |
| `._*` 残留 | ✅ 无（`COPYFILE_DISABLE=1` 生效） |
| 临时调试配置 | `/etc/xiyu-bid/backend.env` 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项） |

---

## 回滚信息

- 代码回滚锚点：`/opt/xiyu-bid/releases/5370cbad2`（前后端完整产物，jar 178 M）
- 数据库回滚：**不需要**（本次零迁移）
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-6d843c212-post-20261112195346.sql.gz`（7.0 M，211 张表，**部署后补做**）
- 回滚方式：`ssh jetty@172.16.10.149 'sudo /opt/xiyu-bid/bin/remote-deploy.sh rollback'`，或从锚点目录回拷 jar + 前端后重启服务

---

## 经验与风险提示

1. **DB 备份命令缺陷第 3 次复发（P2，建议尽快修复）**：技能/脚本模板中的 `DB_BACKUP_COMMAND` 未带 `--single-transaction`，`mysqldump` 默认走 `LOCK TABLES`；生产账号 `ea_bid_pro` 无该权限，实测报错
   `mysqldump: Got error: 1044: "Access denied for user 'ea_bid_pro'@'%' to database 'winbid'" when using LOCK TABLES`（exit=2）。
   `mysqldump | gzip` 管道吞掉退出码，产出 371 字节"假备份"（`winbid-6d843c212-20261112195240.sql.gz`，仅 16 行头部）。
   - 本次处理：保留证据文件，另用 `--single-transaction --no-tablespaces --routines --triggers` 重做备份，校验 211 张 `CREATE TABLE` 与库表数一致。
   - 建议修复：备份命令加 `set -o pipefail`，并校验 dump 尾部 `-- Dump completed` 与文件大小下限，失败即中止部署。
   - **修复已落地（2026-11-12 当日）**：仓库侧 `scripts/release/backup-db-pre-deploy.sh` 与 `deploy-prod.sh` / `deploy-test.sh` 早已是 fail-closed 实现，本次仅技能文档 `SKILL.md` / `references/release-runbook.md` 仍给出手工内联 mysqldump，已改为
     `DB_BACKUP_COMMAND="bash /opt/xiyu-bid/bin/backup-db-pre-deploy.sh <release-id>"`，并把该脚本加入 scp 上传清单。
   - 验证证据：`scripts/test/prod-db-backup-failclosed-contract.sh` 33 条断言全通过；生产机用新命令实测 `DB backup completed ... (7318692 bytes)`，`CREATE TABLE` 计数 211 与库表数一致，退出码 0。
2. **零迁移降低了本次风险**：无 schema 变更，DB 回滚非必需，备份主要用于数据兜底。
3. **Sentry 仍关闭**：未注入 `VITE_SENTRY_DSN`，前端错误上报保持关闭（与上一版一致）。
4. **SSH 瞬断**：首次连接被对端关闭，无鉴权失败记录，重试即恢复；如复现需关注网络侧策略。

---

## 部署确认清单

- [x] 环境门禁经用户卡片确认（prod / 172.16.10.149）
- [x] 基线确认，HEAD = `origin/main` = `6d843c212`
- [x] Flyway validate 通过，无 pending 迁移
- [x] 产物校验（迁移去重 / OBS / 同源构建 / 入口 hash）
- [x] 部署后健康检查 + readiness
- [x] API 路由 Smoke（400 / 401）
- [x] 前端入口与页面可达（内网 8080 + 公网域名 200）
- [x] 启动后无 ERROR 日志
- [x] 旧版 assets 保留（2 版，448 文件）
- [x] DB 备份有效性（部署后补做，211 张表已验证）
- [ ] 部署报告 PR 合入（待提交）
