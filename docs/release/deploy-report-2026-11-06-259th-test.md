# 测试环境第 259 次部署报告

## 部署基本信息
- **部署时间**: 2026-11-06 07:44:38 CST（激活）→ 07:47 左右健康检查连续 3/3 通过（脚本 79 次尝试，约 2.6 分钟）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `adbb2facf-api8080`
- **提交 Commit**: `adbb2facf`（!2968）
- **上一版本**: `82b9dbe75-api8080`（2026-11-04 12:23 CST 激活，对应第 258 次；**该次未生成部署报告**，编号沿用实际部署次数）
- **部署方式**: 按 `xiyu-server-deploy` SOP 手动执行 `scripts/release/package-release.sh` + `scripts/release/remote-deploy.sh`（`VITE_API_BASE_URL=""` 同源构建、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`、`SYSTEMCTL_SUDO=true`）
- **增量规模**: 23 个 commit（含 PR !2961、!2963、!2964、!2965、!2966、!2967、!2968 等），92 个文件变更（+3722 / -344）
- **网络与连通性**: 本机企业内网直连 `172.16.38.78`，SSH 正常；外部 curl 统一加 `--noproxy '*'`

---

## 变更核心内容

1. **OCR 表格识别接入 APIHub 中转（PR !2966 + `86daab88c`）**
   - 表格识别改为经 APIHub 中转路由，保留前端账号配置能力。
2. **评分解析（scoreparse）系列修复（PR !2964、!2965、!2961）**
   - 过滤 0 分审查表，避免多物理表格误判；彻底排除 0 分审查表事实污染评分集。
   - 修复 `textScopeAt` O(N²) 性能问题，并保持文本作用域查找语义。
   - 修复同源候选重复计分，保留 OCR 诊断信息。
3. **PR 治理门禁（PR !2963、!2968）**
   - 提交 PR 前强制清理与双审查（`scripts/review-workflow.py`）。
   - 纯文档变更（Markdown / 文档目录内工件）免三技能审查，白名单收紧说明同步进 `AGENTS.md`。
4. **前端设置页调整**
   - `useAiModelSettings.js`、`BaiduOcrSettingsCard.vue`、`ScoreParseDrawer.vue` 及对应单测更新。
5. **文档**
   - 第 35、36 次生产部署报告入库，并修正报告内 git diff 区间与编号冲突。
6. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225。
7. **前端产物**: 入口 hash 为 `assets/index-BoWUA8ZZ.js`。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操 `sync-env.sh` | ✅ `init branch is up-to-date with origin/main` |
| 本地门禁自检（`sync-env.sh` 内置） | 7/7 通过 |
| 基线确认 | `agent/trae-init`，HEAD=`adbb2facf`=`origin/main`，工作区干净 |
| Flyway validate（服务器） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225，与源码最新 `V1225__score_estimate_freshness.sql` 一致，无 pending |
| 服务器部署前状态 | `deployed-release.json` releaseId=`82b9dbe75-api8080`，`xiyu-bid-backend` active，health 200 |
| 磁盘余量 | `/` 28G 可用（72% 已用） |

---

## 部署执行

- **本地打包**：`npm run build:api`（同源）+ `FlywayMysqlContainerTest` 容器门禁 + `mvn clean -DskipTests package`（BUILD SUCCESS 39.2s）
- **产物校验**：`release-metadata.json` 中 `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`；jar 内 Flyway 迁移版本无重复；Detail chunk `.upload(` 调用数 = 2；Sidecar SHA-256 与线上一致（源码无变动，跳过容器重建）
- **上传**：`scp` 归档 `xiyu-bid-release-adbb2facf-api8080.tar.gz`（156 MB）+ `remote-deploy.sh` → `/opt/xiyu-bid/incoming/`
- **远程部署**：
  1. 数据库备份写入 `/opt/xiyu-bid/db-backups/winbid-adbb2facf-api8080-*.sql.gz`
  2. Flyway validate 通过 → 原子切换 jar → `systemctl restart xiyu-bid-backend` → 健康检查连续 3/3 通过（79 次尝试）
  3. 前端目录切换并通过入口一致性校验（`/assets/index-BoWUA8ZZ.js`）
- **旧版本 assets 保留**：已从 `82b9dbe75-api8080` release 目录 `cp -rn` 旧 hash 资源，`assets` 目录现 278 个文件，防跨版本 preload 404
- **Sidecar**：容器 `xiyu-sidecar` 已运行 5 天，`http://127.0.0.1:8000/health` 返回 200

### 部署过程中的一次中止（非事故）

首次执行 `remote-deploy.sh` 在 Flyway validate 预检阶段中止，输出：

```
rm: 无法删除"/tmp/flyway-repair-extract/BOOT-INF/lib/flyway-core-9.22.3.jar": 权限不够
❌ Flyway validate 失败 ...
```

- **根因**：部署前用 `sudo` 手工执行过一次 `flyway-repair-runner.sh validate`，在 `/tmp/flyway-repair-extract` 留下 root 所有的解压文件；随后 `remote-deploy.sh` 以 `jetty` 身份运行，无法清理该目录，导致 runner 失败（并非 checksum mismatch）。
- **处置**：`sudo rm -rf /tmp/flyway-repair-extract` 后重跑部署成功。中止发生在版本激活之前，**旧版本全程在线**（`xiyu-bid-backend` active、health 200、`deployed-release.json` 仍为 `82b9dbe75-api8080`）。

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`adbb2facf-api8080`，activatedAt=`2026-11-05T23:44:38Z` |
| `/actuator/health`（服务器本地） | UP |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200 |
| `/api/auth/login`（空 body） | HTTP 400（参数校验，接口路由正常） |
| `/api/projects`（未认证） | HTTP 401（鉴权生效） |
| `/api/integration/crm/health`（未认证） | HTTP 401（鉴权生效） |
| 前端 `/`、`/login` | HTTP 200 |
| 前端入口一致性 | `assets/index-BoWUA8ZZ.js` 与 release 一致 |
| 数据库迁移 | `flyway_schema_history` 最新 `success=1` 仍为 V1225，无新迁移 |
| 后端启动日志（近 5 分钟） | `Started XiyuBidApplication in 23.231 seconds`，`ERROR` 计数 = 0 |
| Sidecar `/health`（8000） | HTTP 200 |
| `post-deploy-smoke.sh`（无凭证模式） | 通过 6 / 失败 0 / 警告 2（SMOKE_PASSWORD 未设置，跳过登录与业务端点） |
| OBS 直传 | `obsEnabled=true`，Detail chunk `.upload(` = 2 |
| 前端资产保留 | `82b9dbe75` 旧 hash 文件已保留，防跨版本 404 |
| 临时调试配置 | 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决定保留），无 DEBUG/TRACE 残留 |

---

## 部署后配置变更（2026-11-06 08:11 CST）

- **变更项**：测试环境 `/etc/xiyu-bid/backend.env` 新增 `APP_SCORE_PARSE_BAIDU_OCR_GATEWAY_ENABLED=true`
  - 原状态：未设置，代码默认 `false`（`application.yml` → `app.score-parse.baidu-ocr.gateway-enabled`）
  - 作用：表格 OCR 走 APIHub 中转路径，仅测试环境显式开启，生产保持默认关闭
- **备份**：`/etc/xiyu-bid/backend.env.bak-20261106`（文件权限仍为 `640 root`）
- **执行**：`sudo systemctl restart xiyu-bid-backend` → 08:11:34 `Started XiyuBidApplication in 23.411 seconds`，ERROR 计数 = 0
- **验证**：`/proc/<MainPID>/environ` 含 `APP_SCORE_PARSE_BAIDU_OCR_GATEWAY_ENABLED=true`；本地 `18080` health 200（78 次尝试，Kafka readiness 延迟属已知行为）；经 Nginx `8080` health 200，前端入口仍为 `assets/index-BoWUA8ZZ.js`
- **不入库说明**：服务器 `backend.env` 含数据库密码、JWT 密钥、Sidecar 共享密钥等敏感值，禁止提交仓库；仓库模板 `.env.example` 保持 `APP_SCORE_PARSE_BAIDU_OCR_GATEWAY_ENABLED=false`
- **回滚方式**：删除该行（或改回 `false`）后 `sudo systemctl restart xiyu-bid-backend`

---

## 回滚信息
- 回滚锚点：release id `adbb2facf-api8080`；上一已知良好版本 `82b9dbe75-api8080`
- 后端旧 jar：`/opt/xiyu-bid/releases/82b9dbe75-api8080/backend/app.jar`
- 前端旧 assets：已保留在 `/srv/www/xiyu-bid/assets/`
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-adbb2facf-api8080-*.sql.gz`（首次中止与重跑各生成一份）
- 回滚状态：**就绪（未执行）**

---

## 风险提示

1. **第 258 次部署（`82b9dbe75`）缺报告**：2026-11-04 12:23 CST 的部署未生成报告，本次编号按实际部署次数取 259。建议后续报告严格在部署完成当次补齐。
2. **`/tmp/flyway-repair-extract` 权限残留**：不要用 `sudo` 手工跑 `flyway-repair-runner.sh`；如需预检，直接以 `jetty` 身份执行，或执行后 `sudo rm -rf` 清理。
3. **本次无数据库结构变更**：DB 保持 V1225，回滚仅需切回旧 jar + 旧前端，无需 DB 回滚。

---

## 部署确认清单

- [x] 环境门禁确认（用户选择「确认：测试环境 172.16.38.78」）
- [x] 早操同步 + 基线与 origin/main 一致
- [x] Flyway 预检 3 步通过
- [x] 本地打包 + 产物校验（OBS、迁移无重复、同源 baseURL）
- [x] 部署前数据库备份
- [x] 原子切换 + 服务重启 + 健康检查 3/3
- [x] Smoke 测试与 Sidecar 健康验证
- [x] 旧版本 assets 保留
- [x] 部署报告归档
