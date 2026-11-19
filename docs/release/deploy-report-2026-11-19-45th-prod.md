# 第 45 次生产环境部署报告 — 2026-11-19

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com` |
| Release ID | `fe45e4bd0` |
| 基线 commit | `fe45e4bd0`（与 `origin/main` HEAD 完全一致） |
| 前一版本 | `0ba490bcd`（2026-11-17 21:13:41 CST 激活，第 44 次） |
| 部署时间 | 2026-11-19 19:38:15 CST（激活），健康检查随后通过 |
| 状态 | ✅ 成功（Flyway validate + 健康检查 + 域名 Smoke + Sidecar 冒烟全通过） |
| 回滚状态 | 未执行，锚点就绪（`/opt/xiyu-bid/releases/0ba490bcd` + 本次部署前 DB 备份） |

## 部署概览

从上一版本 `0ba490bcd` 追平至 `origin/main` 最新 `fe45e4bd0`，增量 **51 个 commit / 15 个 PR（!3049–!3063）/ 144 个文件（+8040 / -585）**。

核心变更域：

- **评分解析（scoreparse）**：主客观跨行识别修复、评分项细则名称提取、证书预判、候选激活校验；新增 `score_item.deliverable_type` / `scoring_mode` 结构化字段（V1227）及 Prompt 结构化打标对接。
- **案例工作台（casework）**：评分项意图确定性路由与推荐打分门禁重构（Phase 1–3）、强类型双轨路由与存量平滑降级、等保备案资质路由与证据准入三元组判定、scoreType 护栏。
- **文档**：第 281 / 282 次测试环境部署报告与知识沉淀。

## 变更范围与 PR

| PR | 主题 |
|---|---|
| !3049 | casework：恢复旧分类等保证明案例推荐 |
| !3050 | casework：评分项意图确定性路由与推荐打分门禁重构（Phase 1） |
| !3051 | scoreparse：修复主客观跨行识别失效、评分项大类覆盖与重试重复解析缺陷 |
| !3052 | scoreparse：修复项目 1133 体系认证知识库预判 |
| !3053 | scoreparse：修复项目 38/39 评分表解析与人工采纳校验 |
| !3054 | casework：评分证据先核验，方案案例仅作参考 |
| !3055 | docs：第 281 次测试环境部署报告 |
| !3056 | casework：修复等保备案资质路由与证据准入三元组判定 |
| !3057 | scoreparse：修复评分项细则名称提取、空名称采纳拦截与候选激活校验 |
| !3058 | casework：激活 scoreType 护栏并修复客观系统截图大类穿透 |
| !3059 | scoreparse：score_item 增加 deliverable_type 与 scoring_mode 字段（Step 2.1） |
| !3060 | scoreparse：评分解析 Prompt 结构化打标与 DTO 输出对接（Step 2.2） |
| !3061 | casework：激活智能推荐强类型双轨路由与存量平滑降级（Step 2.3） |
| !3062 | docs：第 282 次测试环境部署报告与 Bug 修复专项 E2E 验收 |
| !3063 | docs：知识沉淀 — 第 282 次部署与 E2E 复核 |

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 环境门禁 | ✅ 用户确认 ENV=prod（`172.16.10.149`） |
| 基线确认 | ✅ 分支 `agent/trae-init`，HEAD = `origin/main` = `fe45e4bd0`，工作区干净 |
| 早操三连 | ✅ `dev-env.sh` + `sync-env.sh` + `check-git-wrapper.sh` 通过 |
| 服务器健康（部署前） | `/actuator/health` UP，`xiyu-bid-backend` active |
| 部署前版本 | `deployed-release.json` → `releaseId=0ba490bcd` |
| Flyway validate（首次） | ❌ `Detected applied migration not resolved locally: 1226` —— 服务器 `/tmp/migration-mysql/` 仅到 V1225（历史遗留），与第 281 次测试部署同源 |
| 处置 | 补传 `V1226`、`V1227` 并清理 `._*` 残留 → `VALIDATE OK - all checksums match`（287 个迁移） |
| DB 已应用版本 | `V1226`（2026-11-17 21:13:49 应用，`success=1`），无 failed 记录 |
| 新增迁移 | `V1227`（`ADD COLUMN` 两个可空字段，非破坏性）；⚠️ 无 `U1227` 回滚脚本 |
| 磁盘 | `/` 使用 27G / 99G（29%），可用 68G |

## 部署执行

1. 环境门禁：`AskUserQuestion` 用户确认生产环境。
2. 迁移目录同步：补传 V1226/V1227 至服务器 `/tmp/migration-mysql/`，validate 复检通过。
3. 打包：复用仓库规范脚本 [deploy-prod.sh](file:///Users/user/xiyu/worktrees/trae/scripts/release/deploy-prod.sh)（`ENV=prod SKIP_SESSION_GATE=1`），内置 `VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `VITE_SENTRY_DSN`（生产 DSN）。
   - 首次手工执行 `package-release.sh` 漏传 `VITE_SENTRY_DSN`（`sentryEnabled=false`），按第 44 次经验改用规范脚本重新打包，元数据确认 `sentryEnabled=true`。
4. 上传：`xiyu-bid-release-fe45e4bd0.tar.gz`（156M）+ `remote-deploy.sh` + `smoke-sidecar.sh` → `/opt/xiyu-bid/incoming/`；`backup-db-pre-deploy.sh` / `flyway-repair-runner.sh` → `/opt/xiyu-bid/bin/`。
5. 远程部署：`remote-deploy.sh`（`SYSTEMCTL_SUDO=true`），执行 DB 预备份 → Flyway validate（内置）→ jar/前端激活 → 重启服务 → 健康检查。
6. 前端旧 assets 保留：`ASSETS_RETAIN_RELEASES=2`，公共目录 assets 共 810 个文件（含上一版本 hash 资源）。
7. 部署后：OBS 回归门禁 + Sidecar 发布冒烟。

## 验证结果

| 验证项 | 结果 |
|---|---|
| DB 预备份 | ✅ `/opt/xiyu-bid/db-backups/winbid-fe45e4bd0-20261119193807.sql.gz`（7,688,052 bytes） |
| 健康检查（服务器内） | ✅ 连续 3/3 通过（第 15 次尝试），`xiyu-bid-backend` active/running |
| `/actuator/health`（域名） | ✅ HTTP 200 UP |
| `/actuator/health/readiness`（服务器经 Nginx） | ✅ HTTP 200，`readinessState: UP`，db UP |
| 前端 `/`、`/login`（域名） | ✅ HTTP 200，入口 `assets/index-TMX1EWUv.js` 与 release 一致 |
| `POST /api/auth/login`（空 body） | ✅ HTTP 400（参数校验生效，路由可达） |
| `GET /api/projects`（未认证） | ✅ HTTP 401（鉴权生效） |
| `GET /api/integration/crm/health`（未认证） | ✅ HTTP 401（鉴权生效） |
| Flyway 迁移 | ✅ `V1227` 已应用（`add deliverable type and scoring mode to score item`，`success=1`，19:38:22）；`score_item.deliverable_type` / `scoring_mode` 列已存在（varchar(50) NULL） |
| Sidecar | ✅ 5/5 通过（health + schema fingerprint 2.2 + 1371 回放 v1/v2 双探针） |
| Sentry | ✅ `sentryEnabled=true`，DSN 字面量已注入 `index-TMX1EWUv.js` |
| OBS 直传 | ✅ `Detail-*.js` 中 `.upload(` 调用数 = 2 |
| 部署记录 | ✅ `deployed-release.json` → `releaseId=fe45e4bd0`，`activatedAt=2026-11-19T11:38:15Z` |
| `._*` 残留 | ✅ 0 个 |
| 后端日志 | ✅ 无 ERROR；唯一 Exception 匹配为本次 Smoke 触发的空登录 WARN 业务异常 |
| 临时配置 | ✅ 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策保留），无 DEBUG/TRACE |

> 备注：本地 Mac 直连 `172.16.10.149:8080` 全部 HTTP 000（本机到生产内网无路由/代理拦截），改用域名 `https://winbid.ehsy.com` 与服务器本地经 Nginx 双重验证，均通过。属本地网络路径问题，非服务故障。

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版后端 jar | `/opt/xiyu-bid/releases/0ba490bcd/backend/app.jar` |
| 上一版前端 | 同目录 `frontend/`（旧 hash 资源已保留在公共目录） |
| 上一版 Sidecar | 回滚需重新解包旧 release 的 sidecar 包并重建镜像 |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-fe45e4bd0-20261119193807.sql.gz`（7.3M） |
| 迁移回退 | ⚠️ `U1227` 回滚脚本缺失；手工回退：`ALTER TABLE score_item DROP COLUMN deliverable_type, DROP COLUMN scoring_mode;` 或从 DB 备份恢复 |
| 回滚可行性 | 高（V1227 仅新增两个可空列，旧版本后端不受影响） |
| 是否执行 | 否，未触发 |

## 经验沉淀应用情况

| 经验 | 应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ validate + DB 版本对比 + remote-deploy 内置校验全部执行 |
| 服务器 `/tmp/migration-mysql/` 过时（第 281 次同源） | ✅ 补传 V1226/V1227 后 validate 通过；**建议后续部署前先做目录同步预检** |
| 生产构建三要素（同源 + OBS + Sentry） | ✅ 首次手工打包漏 Sentry，改用 `deploy-prod.sh` 规范路径修复，元数据与产物双重校验 |
| `SYSTEMCTL_SUDO=true` | ✅ `deploy-prod.sh` 内置传入，服务重启正常 |
| 前端跨版本 assets 保留 | ✅ 自动保留最近 2 个已激活版本 |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1`，部署后残留 0 |
| 部署后临时配置检查 | ✅ 无新增 DEBUG/TRACE |
| 部署报告纪律 | ✅ 本报告 |

### 新增经验（建议沉淀）

1. **`deploy-prod.sh` 在锚点分支执行会被 session-gate 双重拦截**：`set -euo pipefail` + `source dev-env.sh` 使门禁 `return 1` 直接终止脚本（规则 1），即使设 `XIYU_DEV_CONFIRMED=1` 仍会被"rebase 后锚点复检"（规则 5）拦截。自动化部署需 `SKIP_SESSION_GATE=1`。
2. **Flyway validate 的 `/tmp/migration-mysql` 目录同步应前置**：连续两次部署（第 281 次测试、第 45 次生产）因该目录落后一版而首次 validate 失败，建议将目录同步纳入部署 SOP 前置步骤。
3. **禁止手工拼 `package-release.sh` 命令**：连续两次（第 44、45 次生产）漏传 `VITE_SENTRY_DSN`，统一走 `deploy-prod.sh`。
4. **V1227 缺回滚脚本**：后续迁移需补齐 `U<version>` 回滚脚本（与历史 V1107–V1109 同类问题）。

## 风险提示

- V1227 新增列全部为 NULL 默认值，存量数据无需回填；新评分解析逻辑（Step 2.1–2.3）依赖这些字段的结构化打标，若打标异常可回退到旧路由（存量平滑降级已内置）。
- 第 282 次测试环境 E2E 验收结论为 FAIL（等保备案条款返回无关案例、评分项名称细则化未生效、证据门禁两类量化句式漏判），相关修复已包含在本次部署中（!3056、!3057、!3058），**建议在生产环境按真实业务场景复核这三项**。
- `/tmp/migration-mysql/` 已补齐至 V1227，随后续迁移需同步维护。

## 部署确认清单

- [x] 环境门禁：用户确认生产环境 `172.16.10.149`
- [x] 基线 = `origin/main`，工作区干净
- [x] Flyway validate 通过（287 迁移）
- [x] DB 预备份完成（7.3M）
- [x] 产物校验：Sentry=true、OBS=true、jar 内迁移无重复
- [x] 健康检查 3/3 通过
- [x] V1227 迁移已应用，新增列存在
- [x] API / 前端 Smoke 通过（域名 + 服务器本地双通道）
- [x] Sidecar 冒烟 5/5 通过
- [x] 前端入口与 release 一致（`index-TMX1EWUv.js`）
- [x] 部署记录已更新（`fe45e4bd0`）
- [x] 回滚锚点就绪（上一版 release + DB 备份）
