# 第 224 次部署报告（test）

## 部署概览

| 项 | 值 |
|---|---|
| 环境 | **测试（test）** |
| 目标主机 | `172.16.38.78`（winbid-01） |
| Release ID | `4ec844422-api8080` |
| 部署时间 | 2026-10-12 15:11 → 15:29:25 CST（打包 15:11→15:26，激活 15:26:44，smoke 完成 15:29:25） |
| 上一版本 | `0f18887de-api8080`（2026-10-11 激活） |
| 部署人 | trae（xiyu-server-deploy skill / `scripts/release/deploy-test.sh`） |
| 访问入口 | `http://172.16.38.78:8080/` |

## 基线信息

- 分支：`agent/trae-init`（锚点分支，HEAD = origin/main，禁止开发，仅部署）
- HEAD commit：`4ec844422` = origin/main
- 工作区：干净（`git status --short` 无输出）
- 前端入口：`assets/index-B-xBsIyN.js`（上一版本 `index-CLxXMKYe.js`）
- 后端 jar：`bid-platform-1.0.3.jar`，builtAt `2026-10-12T07:26:02Z`
- sidecar SHA-256：`6bdb19887538d19baa147aa90b73220de8d304f7fcebd03ab41f6dc4ab893b36`（上一版 `8a38ad28…`），schemaVersion = 2.2
- 后端重启时间：2026-10-12 15:26:45 CST（PID 10877）
- 打包产物：`.release/xiyu-bid-release-4ec844422-api8080.tar.gz`（161M）

## PR 列表

`!2823` `!2824`

## 改动范围（增量 `0f18887de..4ec844422`，共 6 个 commit / 2 个业务 PR）

本次为**评分解析（scoreparse）修复**系列上线，**无数据库迁移文件变更**（24 files changed，+1242/-42）。

| 类别 | 内容 |
|---|---|
| fix(scoreparse) | 修复项目 1387 章节归属与分组评分表解析（!2823） |
| fix(scoreparse) | 恢复已识别评分项展示与人工复核入口（!2824，含 `ScoreParseDrawer` 修复 + 单测） |
| docs | 第 223 次部署报告合入（!2822，非业务） |

## Flyway 预检结果

- Step 1 `flyway-repair-runner.sh validate`（部署前独立执行）→ **VALIDATE OK - all checksums match**
- Step 2 DB 已应用版本 vs 源码最新版本：DB 最新 `V1220`，源码最新 `V1220`，增量无迁移文件变更 → **对齐**
- Step 3 remote-deploy 内置 validate（激活 jar 前）→ 通过（仅 pending 新迁移为预期状态，本次 pending = 0）
- 结论：**无需新增迁移，无 Flyway 风险**

## 部署步骤

1. 环境门禁：用户声明测试环境 `172.16.38.78`，预检汇总后用户确认执行
2. 早操三连：`sync-env.sh`（init 分支 ff-only，已与 origin/main 对齐）+ `check-git-wrapper.sh`（7 项全绿）
3. 服务器现状检查：上一版本 `0f18887de-api8080`，服务 active，health 200，磁盘 67%
4. Flyway 预检 3 步 → 通过
5. 本地打包（`deploy-test.sh` 固化参数）：`RELEASE_ID=4ec844422-api8080`、`VITE_API_BASE_URL=`（同源）、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`、`VITE_SENTRY_DSN=`（测试环境留空）
6. 产物校验：前端同源（无 dev API 地址）、`FlywayMysqlContainerTest` 容器测试通过、jar 内迁移版本无重复、sidecar 打包完成、**OBS 直传启用（Detail chunk `.upload(` 调用数 = 2）**
7. 上传 `xiyu-bid-release-4ec844422-api8080.tar.gz`（161M）+ 部署（`SYSTEMCTL_SUDO=true`）
8. Remote deploy：**DB 自动备份** → Sidecar 源码变更检测 + 容器升级 + 健康检查 → AI Provider 守卫（`AI_PROVIDER=custom`）→ Flyway validate → 激活前端 → 停后端 → 原子更新 jar → 写部署记录 → 起后端 → 健康检查（连续 3/3，共 79 次尝试）
9. 前端旧 assets 保留：**本次部署后主动执行**（吸取第 223 次事后补执行教训），从上一版本 `0f18887de-api8080` 目录 `cp -rn` 补回旧 hash 资源（193 → 277 个文件）

## 验证结果

### 后端健康

- 服务器本地 `http://127.0.0.1:18080/actuator/health` → **200 UP**（79 次尝试，连续 3/3 通过）
- systemd：`active (running)`，PID 10877，自 2026-10-12 15:26:45 CST

### API Smoke（经 Nginx 8080）

| 检查项 | 期望 | 实际 | 结论 |
|---|---|---|---|
| `GET /actuator/health` | 200 | 200 | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects`（未认证） | 401 | 401 | ✅ |
| `GET /`（前端首页） | 200 | 200 | ✅ |
| 前端入口 hash 与 release 一致 | `index-B-xBsIyN.js` | `index-B-xBsIyN.js` | ✅ |

### Sidecar Smoke

- `GET /health` → 200 UP，响应头 `X-Schema-Version=2.2`
- fixture 回放：v1 形态（tables=14 cells=8215 scoreTables=1）、v1/v2 探针均 200
- 结论：**5/5 通过，SIDECAR SMOKE PASSED**

### 部署后收尾

- 旧 assets 保留：193 → 277 个文件（防旧标签页 preload 404）
- 临时配置检查：仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（第 13-15 次部署用户决定保留项，非临时调试配置，无需清理）

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| session-gate 锚点分支拦截（memory 已沉淀教训） | 首次运行被 `session-gate.sh` 拦截（exit 1），按既定方案 `SKIP_SESSION_GATE=1` 重跑成功；部署属锚点分支正常操作而非开发 |
| 前端 hash 资源跨版本 404（第 223 次教训） | 本次部署完成后主动执行旧 assets 保留（`cp -rn`），不再是事后手工补 |
| Mac HTTP_PROXY 502 | 所有 curl 均带 `--noproxy '*'`，未受影响 |
| Flyway 预检 3 步法 | 部署前独立 validate + DB 版本对比均通过 |
| OBS 直传漏传防护 | `deploy-test.sh` 固化 `VITE_OBS_ENABLED=true` + 打包后 `.upload(` 调用数校验（=2）双保险 |

## 风险提示

1. **无迁移变更**：本次不涉及数据库 schema 变更，回滚仅需回退 jar + 前端产物，无数据回滚风险。
2. **首次运行被 session-gate 拦截**：属已知行为（锚点分支部署），已在报告中记录；建议后续 `deploy-test.sh` 内部自动注入 `SKIP_SESSION_GATE=1`，消除一次性人工干预（待办，见下）。
3. **readiness 延迟未复现**：本次健康检查 79 次尝试内通过，无 Kafka SDK 延迟现象。

## 待办

- [ ] `deploy-test.sh` 增加 `SKIP_SESSION_GATE=1` 自动注入（锚点分支部署场景，避免每次手工绕过）

## 回滚信息

- 回滚目标：`0f18887de-api8080`（release 目录 `/opt/xiyu-bid/releases/0f18887de-api8080` 完整保留）
- 回滚方式：将上一版本 jar 复制回 `/opt/xiyu-bid/shared/backend/app.jar` 并 `sudo systemctl restart xiyu-bid-backend`；前端将 `0f18887de-api8080/frontend/` 覆盖回 `/srv/www/xiyu-bid`
- DB 备份：部署时自动执行（`/opt/xiyu-bid/db-backups/winbid-4ec844422-api8080-*.sql.gz`）
- 回滚状态：**ready（未执行，健康检查与 smoke 全部通过）**

## 部署确认清单

- [x] 环境门禁：用户显式声明并确认测试环境
- [x] 早操三连完成
- [x] Flyway 预检 3 步通过
- [x] DB 备份完成
- [x] 产物校验（同源 API / OBS 启用 / 迁移无重复 / sidecar 打包）
- [x] 健康检查通过（连续 3/3）
- [x] API Smoke 4/4 通过
- [x] Sidecar Smoke 5/5 通过
- [x] 旧 assets 保留
- [x] 临时配置检查（仅历史保留项）
- [x] 部署报告生成（本文档）
