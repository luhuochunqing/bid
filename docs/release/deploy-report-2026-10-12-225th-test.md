# 第 225 次部署报告（test）

## 部署概览

| 项 | 值 |
|---|---|
| 环境 | **测试（test）** |
| 目标主机 | `172.16.38.78`（winbid-01） |
| Release ID | `2abd78330-api8080` |
| 部署时间 | 2026-10-12 18:27 → 18:41 CST（打包 18:29→18:32:53，激活 18:34:32，smoke 完成 18:41） |
| 上一版本 | `4ec844422-api8080`（同日 15:26 激活） |
| 部署人 | trae（xiyu-server-deploy skill，`package-release.sh` + `remote-deploy.sh` 手工流程） |
| 访问入口 | `http://172.16.38.78:8080/` |

## 基线信息

- 分支：`agent/trae-init`（锚点分支，HEAD = origin/main，禁止开发，仅部署）
- HEAD commit：`2abd78330` = origin/main
- 工作区：干净（`git status --short` 无输出）
- 前端入口：`assets/index-B-xBsIyN.js`（**与上一版本相同** —— 本次增量仅后端变更，前端产物零差异属预期）
- 后端 jar：`bid-platform-1.0.3.jar`，builtAt `2026-10-12T10:32:53Z`
- sidecar SHA-256：`de0f5ce2f837915520e4199eedfa1f49a9caaba1dfd4b440213913d1206d6bf9`（上一版 `6bdb1988…`，源码未变，SHA 差异来自打包时间戳；容器随部署正常重启，schemaVersion = 2.2 不变）
- 后端重启时间：2026-10-12 18:34:32 CST（PID 23450）
- 打包产物：`.release/xiyu-bid-release-2abd78330-api8080.tar.gz`（155M）

## PR 列表

`!2826`（业务）、`!2825`（docs：第 224 次部署报告）

## 改动范围（增量 `4ec844422..2abd78330`，共 4 个 commit / 1 个业务 PR）

本次为**评分解析（scoreparse）价格公式星号转义修复**上线，**无数据库迁移文件变更**（6 files changed，+67/-1）。

| 类别 | 内容 |
|---|---|
| fix(scoreparse) | 修复价格公式星号转义导致的原文绑定失败（!2826：`58ef65e04` bind Markdown escaped price formulas + merge `2abd78330`） |
| 变更明细 | `EvidenceText.java`（+6，星号解转义）、`TextPrimaryEvidenceBinderTest` / `EvidenceTextTest` / `GoldenCasesReplayTest`（+13 各，单测）、新增 golden case `case-price-markdown-star.md` / `.expected.json` |
| docs | 第 224 次部署报告合入（!2825，非业务） |

## Flyway 预检结果

- Step 1 `flyway-repair-runner.sh validate`（部署前独立执行）→ **VALIDATE OK - all checksums match**（280 migrations）
- Step 2 DB 已应用版本 vs 源码最新版本：DB 最新 `V1220`（2026-09-27），源码最新 `V1220`，增量无迁移文件变更 → **对齐**
- Step 3 remote-deploy 内置 validate（激活 jar 前）→ 通过
- 结论：**无需新增迁移，无 Flyway 风险**

## 部署步骤

1. 环境门禁：用户声明测试环境 `172.16.38.78`，展示确认信息后用户回复「确认」
2. 早操三连：`sync-env.sh`（init 分支已与 origin/main 对齐）+ 本地门禁自检 7 项全绿 + `check-git-wrapper.sh`
3. 服务器现状检查：上一版本 `4ec844422-api8080`（同日 15:26 部署），服务 active，health 200
4. Flyway 预检 3 步 → 通过
5. 本地打包：`RELEASE_ID=2abd78330-api8080`、`VITE_API_BASE_URL=`（同源）、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`
6. 产物校验：`release-metadata.json` obsEnabled=true、jar 内迁移版本无重复、sidecar 打包完成、OBS 直传启用（Detail chunk `.upload(` 调用数 = 2）
7. 上传 `xiyu-bid-release-2abd78330-api8080.tar.gz`（155M）+ 部署（`SYSTEMCTL_SUDO=true`）
8. Remote deploy：**DB 自动备份** → Flyway validate → 激活前端（自动 sudo chown 修复非 jetty 属主条目）→ 停后端 → 原子更新 jar → 写部署记录 → 起后端 → 健康检查（连续 3/3，共 79 次尝试 ≈ 2.6 分钟）→ 前端一致性验证通过
9. 前端旧 assets 保留：从上一版本 `4ec844422-api8080` 目录 `cp -rn` 补回旧 hash 资源（部署后主动执行）

## 验证结果

### 后端健康

- 服务器本地 `http://127.0.0.1:18080/actuator/health` → **200 UP**（79 次尝试，连续 3/3 通过）
- systemd：`active (running)`，PID 23450，自 2026-10-12 18:34:32 CST

### API Smoke（经 Nginx 8080）

| 检查项 | 期望 | 实际 | 结论 |
|---|---|---|---|
| `GET /actuator/health` | 200 | 200 | ✅ |
| `GET /actuator/health/readiness` | 200 | 200 | ✅（readiness 延迟未复现） |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects`（未认证） | 401 | 401 | ✅ |
| `GET /api/integration/crm/health`（未认证） | 401 | 401 | ✅ |
| `GET /`（前端首页） | 200 | 200 | ✅ |
| `GET /login` | 200 | 200 | ✅ |
| 前端入口 hash 与 release 一致 | `index-B-xBsIyN.js` | `index-B-xBsIyN.js` | ✅ |

### Sidecar Smoke

- 容器：`xiyu-sidecar` Up（随部署重启）
- `GET http://127.0.0.1:8000/health` → **200**，响应头 `X-Schema-Version: 2.2`
- 后端探针日志持续 200 OK（`SidecarHealthIndicator` 正常工作）
- 注：服务器 sidecar 端口为 **8000**（8009 为本地开发宿主端口，排查时曾误用，已纠正）

### 部署后收尾

- 旧 assets 保留：从 `4ec844422-api8080` 复制（防旧标签页 preload 404）
- 临时配置检查：仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（第 13-15 次部署用户决定保留项，非临时调试配置，无需清理）

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| 前端 hash 资源跨版本 404 | 部署完成后主动执行旧 assets 保留（`cp -rn` from 上一版本 release 目录） |
| Mac HTTP_PROXY 502 | 所有 curl 均带 `--noproxy '*'`，未受影响 |
| Flyway 预检 3 步法 | 部署前独立 validate + DB 版本对比均通过 |
| OBS 直传漏传防护 | 显式传 `VITE_OBS_ENABLED=true` + 打包后 `.upload(` 调用数校验（=2）双保险 |
| 锚点分支部署 | 早操经 `sync-env.sh`（内置豁免），git 操作均为只读，未触发 session-gate 开发拦截 |

## 风险提示

1. **无迁移变更**：本次不涉及数据库 schema 变更，回滚仅需回退 jar（前端产物与上一版完全一致），无数据回滚风险。
2. **sidecar SHA 变化属预期**：源码未变，SHA 差异来自打包时间戳嵌入；schemaVersion 2.2 与上一版一致。
3. **前端产物零差异**：入口 hash 与上一版相同，浏览器缓存无跨版本风险。

## 回滚信息

- 回滚目标：`4ec844422-api8080`（release 目录 `/opt/xiyu-bid/releases/4ec844422-api8080` 完整保留）
- 回滚方式：将上一版本 jar 复制回 `/opt/xiyu-bid/shared/backend/app.jar` 并 `sudo systemctl restart xiyu-bid-backend`；前端无需回退（产物一致）
- DB 备份：部署时自动执行（`/opt/xiyu-bid/db-backups/winbid-2abd78330-20261012183418.sql.gz`，14.6M）
- 回滚状态：**ready（未执行，健康检查与 smoke 全部通过）**

## 部署确认清单

- [x] 环境门禁：用户显式声明并确认测试环境
- [x] 早操三连完成
- [x] Flyway 预检 3 步通过
- [x] DB 备份完成
- [x] 产物校验（同源 API / OBS 启用 / 迁移无重复 / sidecar 打包）
- [x] 健康检查通过（连续 3/3）
- [x] API Smoke 8/8 通过
- [x] Sidecar Smoke 通过（health 200 + schema 2.2）
- [x] 旧 assets 保留
- [x] 临时配置检查（仅历史保留项）
- [x] 部署报告生成（本文档）
